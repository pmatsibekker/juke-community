package org.juke.remix.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.juke.framework.config.ConfigUtil;
import org.juke.framework.coverage.CoverageContributor;
import org.juke.framework.exception.JukeAccessException;
import org.juke.framework.proxy.JukeState;
import org.juke.framework.session.JukeSessionEntry;
import org.juke.framework.session.SessionRegistry;
import org.juke.framework.storage.JukeZipDAOImpl;
import org.juke.remix.session.RemixJukeRecordingFinaliser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
@Controller
// Master enablement gate: previously @Profile({"record","replay"}) which
// required activating one of those Spring profiles. Replaced with the
// uniform juke.enabled=true switch so YAML drives everything — set
// juke.enabled=true and this controller is reachable; flip the actual
// global mode at runtime via /service/record/start or /service/replay/start.
@ConditionalOnProperty(name = "juke.enabled", havingValue = "true")
@RequestMapping("/service")
public class RemixWebController {
	private static Logger log = LoggerFactory.getLogger(RemixWebController.class);

	// Phase 6 SRP split: the formerly monolithic {@code RemixService} is now
	// three focused services. Injecting all three here keeps the controller
	// as a thin HTTP adapter.
	@Autowired
	RecordingService recordingService;

	@Autowired
	ReplayService replayService;

	@Autowired
	TunerService tunerService;

	// Phase 2 — give the JukeRecordingFinaliser the track name before recording starts so
	// the Phase 2 /service/juke/stop response (and the bundle manifest) can use it as the
	// bundle name. The legacy /service/record/* path stays untouched — this is just a hand-off.
	@Autowired
	RemixJukeRecordingFinaliser jukeRecordingFinaliser;

	// Injected for the /service/recording/report endpoint which needs completed-session history.
	@Autowired
	SessionRegistry sessionRegistry;

	// Optional: present when juke-coverage is on the classpath and juke.coverage.enabled=true.
	// Contributes a live coverage snapshot (server JaCoCo + UI nyc) at the top of the report.
	@Autowired(required = false)
	CoverageContributor coverageContributor;

	@RequestMapping(value="/replay/disable", method = {RequestMethod.GET}, produces= {"application/json"})
	public @ResponseBody
	ResponseEntity<String> disable(){
		if (JukeState.getGlobaljuke() == null) {
			return new ResponseEntity<String>("Unavailable Service", HttpStatus.INTERNAL_SERVER_ERROR);

		}
		String response=null;
		try{
			response = replayService.disable();
		}catch (JukeAccessException e) {
			e.printStackTrace();
		}

		if (RemixUtil.OK.equalsIgnoreCase(response))
			return new ResponseEntity<String>("juke Mocking is now disabled.",HttpStatus.OK);
		else
			return new ResponseEntity<String>("Failing to disable juke.",HttpStatus.INTERNAL_SERVER_ERROR);
	}
	@RequestMapping(value="/replay/enable", method = {RequestMethod.GET}, produces= {"application/json"})
	public @ResponseBody
	ResponseEntity<String> enable(){
		if (JukeState.getGlobaljuke() == null) {
			return new ResponseEntity<String>("Unavailable Service", HttpStatus.INTERNAL_SERVER_ERROR);

		}
		String response=null;
		try{
			response = replayService.enable();
		}catch (JukeAccessException e) {
			e.printStackTrace();
		}

		if (RemixUtil.OK.equalsIgnoreCase(response))
			return new ResponseEntity<String>("juke Mocking is now enabled.",HttpStatus.OK);
		else
			return new ResponseEntity<String>("Failing to enable juke.",HttpStatus.INTERNAL_SERVER_ERROR);
	}
	//http://localhost:8080/service/replay/start?track=juke-sample-ui-2
	@RequestMapping(value="/replay/start", method = {RequestMethod.GET}, produces= {"application/json"})
	public @ResponseBody
	ResponseEntity<String> beginReplay(@RequestParam("track") String track){
		if (JukeState.getGlobaljuke() == null) {
			return new ResponseEntity<String>("Unavailable Service", HttpStatus.INTERNAL_SERVER_ERROR);

		}
		if (track == null || track.trim().length() == 0)
			track = "track";


		String response=null;
		try{
			response = replayService.start(track);
		}catch (JukeAccessException e) {
			e.printStackTrace();
		}

		if (RemixUtil.OK.equalsIgnoreCase(response))
			return new ResponseEntity<String>("Starting Repla Test Run: "+track,HttpStatus.OK);
		else
			return new ResponseEntity<String>("Ending Repla Test Run "+track,HttpStatus.INTERNAL_SERVER_ERROR);
	}
	@RequestMapping(value="/record/start", method = {RequestMethod.GET}, produces= {"application/json"})
	public @ResponseBody
	ResponseEntity<String> beginRecord(
			@RequestParam("track") String track,
			@RequestParam(value = "label", required = false) String label) {
		if (JukeState.getGlobaljuke() == null) {
			return new ResponseEntity<String>("Unavailable Service", HttpStatus.INTERNAL_SERVER_ERROR);

		}
		if (track == null || track.trim().length() == 0)
			track = "track";


		String response=null;
		try{
			jukeRecordingFinaliser.rememberTrack(track);
			jukeRecordingFinaliser.rememberLabel(label);
			response = recordingService.start(track);
		}catch (JukeAccessException e) {
			e.printStackTrace();
		}

		if (RemixUtil.OK.equalsIgnoreCase(response))
			return new ResponseEntity<String>("Starting Test Run: "+track,HttpStatus.OK);
		else
			return new ResponseEntity<String>("Ending Test Run "+track,HttpStatus.INTERNAL_SERVER_ERROR);
	}

	@RequestMapping(value="/record/end", method = {RequestMethod.GET}, produces= {"application/json"})
	public @ResponseBody
	ResponseEntity<String> endRecord(HttpServletResponse response){
		if (JukeState.getGlobaljuke() == null) {
			return new ResponseEntity<String>("Unavailable Service", HttpStatus.INTERNAL_SERVER_ERROR);
		}

		// Write juke-metadata.json (label + timestamp) into the ZIP before stop()
		// closes it. The finaliser already holds whatever label was set on record/start.
		jukeRecordingFinaliser.flushMetadata();

		// KI-3 fix: flush the recording first so we know whether path() resolves
		// before we commit any response headers.
		String path;
		try {
			recordingService.stop();
			path = recordingService.path();
		} catch (JukeAccessException e) {
			log.error("Failed to stop recording", e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body("Failed to stop recording: " + e.getMessage());
		}

		String name = ConfigUtil.getJukeZip();
		response.setStatus(HttpServletResponse.SC_OK);
		response.addHeader("Content-Disposition", "attachment; filename=\"" + name + ".zip\"");

		try {
			RemixUtil.write(path, response);
		} catch (IOException e) {
			// KI-3 fix: previously the ResponseEntity.status(500) was created
			// then discarded — the surfaced 500 was actually the early
			// "Unavailable Service" branch hit on a previous call. Surface the
			// real cause now. Spring will warn if response headers were already
			// flushed, but the exception body is still recorded server-side.
			log.error("Failed to stream recording from {}", path, e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body("Failed to stream recording: " + e.getMessage());
		}
		return ResponseEntity.ok().build();
	}

	//http://<host>/service/remix/delaySchedule?classAndMethodSequence=com.example.IGreetingsService.$greeting.1&waitTimeInMS=10000
	@RequestMapping(value="/remix/delaySchedule", method = {RequestMethod.GET}, produces= {"application/json"})
	public ResponseEntity<String> remixDelaySchedule(@RequestParam("classAndMethodSequence") String classAndMethodSequence,
														@RequestParam("waitTimeInMS") long waitTimeInMS){
		if (JukeState.getGlobaljuke() == null) {
			return new ResponseEntity<String>("Unavailable Service", HttpStatus.INTERNAL_SERVER_ERROR);

		}

		tunerService.scheduleDelay(classAndMethodSequence, waitTimeInMS);
		return new ResponseEntity<String> ("adding delay in processing for"+classAndMethodSequence+" = "+ waitTimeInMS, HttpStatus.OK);


}

	//http://localhost:8080/service/remix/clear
	@RequestMapping(value="/remix/clear", method = {RequestMethod.GET}, produces= {"application/json"})
	public ResponseEntity<String> remixClear(){
		if (JukeState.getGlobaljuke() == null) {
			return new ResponseEntity<String>("Unavailable Service", HttpStatus.INTERNAL_SERVER_ERROR);
		}
		tunerService.clearSchedules();
		return new ResponseEntity<String>("cleared all scheduled delays and exceptions", HttpStatus.OK);
	}
	/**
	 * Returns the recorded input arguments for every call in a track's ZIP.
	 *
	 * <p>Each element in the returned array represents one {@code .args.json}
	 * sidecar entry and has the shape:
	 * <pre>
	 * {
	 *   "file":           "IGreetingsService.greeting.1.args.json",
	 *   "sequence":       1,
	 *   "method":         "greeting",
	 *   "parameterTypes": ["java.lang.String"],
	 *   "arguments":      ["Alice"]
	 * }
	 * </pre>
	 *
	 * <p>Intended for the demo Phase 4 comparison report: navigate to this
	 * endpoint after a mismatched replay session to see what the recording
	 * expected vs what the test actually sent.
	 *
	 * <p>Example: {@code GET /service/recording/inputs?track=demo}
	 */
	@GetMapping(value = "/recording/inputs", produces = "application/json")
	public ResponseEntity<Object> recordedInputs(@RequestParam("track") String track) {
		String zipPath = ConfigUtil.getJukePath();
		try {
			JukeZipDAOImpl dao = new JukeZipDAOImpl(zipPath, track);
			Set<String> fileNames = dao.getFileNames();
			ObjectMapper mapper = new ObjectMapper();
			List<Map<String, Object>> results = new ArrayList<>();

			fileNames.stream()
					.filter(name -> name.endsWith(".args.json"))
					.sorted()
					.forEach(fileName -> {
						// strip the trailing ".json" to get the identifier used by asString()
						String identifier = fileName.substring(0, fileName.length() - 5);
						Map<String, Object> entry = new LinkedHashMap<>();
						entry.put("file", fileName);
						try {
							String json = dao.asString(identifier);
							@SuppressWarnings("unchecked")
							Map<String, Object> parsed = mapper.readValue(json, Map.class);
							// Flatten the nested InputArgsRecord fields to the top level
							// so callers get: sequence, method, parameterTypes, arguments
							Object seq = extractSequence(fileName);
							entry.put("sequence", seq);
							entry.put("method",         parsed.getOrDefault("method", ""));
							entry.put("parameterTypes", parsed.getOrDefault("parameterTypes", List.of()));
							entry.put("arguments",      parsed.getOrDefault("arguments", List.of()));
						} catch (Exception e) {
							entry.put("error", e.getMessage());
						}
						results.add(entry);
					});

			return ResponseEntity.ok(results);
		} catch (Exception e) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND)
					.body(Map.of("error", "Track '" + track + "' not found: " + e.getMessage()));
		}
	}

	/** Extracts the numeric sequence from a filename like "Foo.bar.2.args.json" → 2. */
	private static Object extractSequence(String fileName) {
		try {
			// e.g. "IGreetingsService.greeting.1.args.json"
			//       split on "."  →  [..., "1", "args", "json"]
			String[] parts = fileName.split("\\.");
			// the sequence is the third-to-last segment before ".args.json"
			if (parts.length >= 3) {
				return Integer.parseInt(parts[parts.length - 3]);
			}
		} catch (NumberFormatException ignored) { /* fall through */ }
		return fileName;
	}

	/**
	 * Returns a structured report for a track showing all completed replay sessions,
	 * each with its description, call history, and overall input-match status.
	 *
	 * <p>Community edition: clean, pretty-printed JSON. Enterprise edition would
	 * add the styled comparison card rendered by the Playwright spec.
	 *
	 * <p>The recording label is read from the {@code juke-metadata.json} entry
	 * written into the ZIP when {@code /service/record/start?label=…} was called.
	 *
	 * <p>Example: {@code GET /service/recording/report?track=demo}
	 */
	@GetMapping(value = "/recording/report", produces = "application/json")
	public ResponseEntity<String> recordingReport(@RequestParam("track") String track) {
		ObjectMapper mapper = new ObjectMapper();
		mapper.enable(SerializationFeature.INDENT_OUTPUT);

		Map<String, Object> report = new LinkedHashMap<>();

		// ── Coverage snapshot at the top (present when juke-coverage is enabled) ──
		// Snapshot is taken at report time so it reflects code exercised during
		// the most recent replay session. Server coverage is live (JaCoCo
		// in-process); UI coverage reflects the last nyc/Playwright run.
		if (coverageContributor != null) {
			report.put("coverage", coverageContributor.coverageSnapshot());
		}

		report.put("track", track);

		// ── Read label / recordedAt from juke-metadata.json in the ZIP ─────────
		String zipPath = ConfigUtil.getJukePath();
		try {
			JukeZipDAOImpl dao = new JukeZipDAOImpl(zipPath, track);
			try {
				String metaJson = dao.asString("juke-metadata");
				@SuppressWarnings("unchecked")
				Map<String, Object> meta = mapper.readValue(metaJson, Map.class);
				report.put("label",      meta.getOrDefault("label",      ""));
				report.put("recordedAt", meta.getOrDefault("recordedAt", ""));
			} catch (Exception noMeta) {
				report.put("label",      "");
				report.put("recordedAt", "");
			}
		} catch (Exception e) {
			try {
				return ResponseEntity.status(HttpStatus.NOT_FOUND)
						.contentType(MediaType.APPLICATION_JSON)
						.body(mapper.writeValueAsString(
								Map.of("error", "Track '" + track + "' not found: " + e.getMessage())));
			} catch (Exception ignore) {
				return ResponseEntity.status(HttpStatus.NOT_FOUND).body("{\"error\":\"Track not found\"}");
			}
		}

		// ── Build sessions array from completed-session history ─────────────────
		List<Map<String, Object>> sessionsList = new ArrayList<>();
		for (JukeSessionEntry entry : sessionRegistry.getCompletedSessions(track)) {
			Map<String, Object> sm = new LinkedHashMap<>();
			sm.put("sessionId",   entry.getSessionId());
			sm.put("description", entry.getDescription() != null ? entry.getDescription() : "");
			sm.put("startedAt",   entry.getCreatedAt().toString());
			sm.put("stoppedAt",   entry.getStoppedAt() != null ? entry.getStoppedAt().toString() : "");

			List<JukeSessionEntry.CallRecord> history = entry.getCallHistory();
			sm.put("callCount", history.size());

			boolean anyMismatch = history.stream().anyMatch(c -> !c.inputMatched());
			sm.put("overallStatus", anyMismatch ? "COMPLETED_WITH_DEVIATIONS" : "COMPLETED");

			List<Map<String, Object>> calls = new ArrayList<>();
			for (JukeSessionEntry.CallRecord call : history) {
				Map<String, Object> cm = new LinkedHashMap<>();
				cm.put("sequence",          call.sequence());
				cm.put("method",            call.method());
				cm.put("recordedArguments", call.recordedArguments());
				cm.put("actualArguments",   call.actualArguments());
				cm.put("inputMatched",      call.inputMatched());
				calls.add(cm);
			}
			sm.put("calls", calls);
			sessionsList.add(sm);
		}
		report.put("sessions", sessionsList);

		try {
			return ResponseEntity.ok()
					.contentType(MediaType.APPLICATION_JSON)
					.body(mapper.writeValueAsString(report));
		} catch (Exception e) {
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body("{\"error\":\"Failed to serialize report\"}");
		}
	}

	//http://localhost:8080/service/remix/exceptionSchedule?classAndMethodSequence=com.example.IGreetingsService.$greeting.3&exception=IOException&exceptionMessage=IOException
	@RequestMapping(value="/remix/exceptionSchedule", method = {RequestMethod.GET}, produces= {"application/json"})
	public ResponseEntity<String> remixExceptionScheduleSchedule(@RequestParam("classAndMethodSequence") String classAndMethodSequence,
														@RequestParam("exception") String exception,	@RequestParam("exceptionMessage") String exceptionMessage ) throws Exception{
		if (JukeState.getGlobaljuke() == null) {
			return new ResponseEntity<String>("Unavailable Service", HttpStatus.INTERNAL_SERVER_ERROR);

		}

		tunerService.scheduleException(classAndMethodSequence, exception,exceptionMessage);
		return new ResponseEntity<String> ("adding exception in processing for"+classAndMethodSequence+" = "+ exception, HttpStatus.OK);


}



}
