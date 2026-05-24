# Test info

- Name: Juke demo — full record / replay walk-through >> Phase 3 — stop session, verify cookies cleared and live service returns
- Location: C:\Users\pmats\IdeaProjects\Juke\juke-samples\juke-sample-session\src\test\playwright\e2e\juke-demo.spec.ts:652:7

# Error details

```
Error: page.goto: net::ERR_CONNECTION_REFUSED at http://localhost:8080/greeting?name=Zelda
Call log:
  - navigating to "http://localhost:8080/greeting?name=Zelda", waiting until "load"

    at fetchGreeting (C:\Users\pmats\IdeaProjects\Juke\juke-samples\juke-sample-session\src\test\playwright\e2e\juke-demo.spec.ts:223:26)
    at C:\Users\pmats\IdeaProjects\Juke\juke-samples\juke-sample-session\src\test\playwright\e2e\juke-demo.spec.ts:669:24
```

# Test source

```ts
  123 |           }
  124 |           .stage {
  125 |             font-size: 1.1rem;
  126 |             letter-spacing: 0.3em;
  127 |             text-transform: uppercase;
  128 |             color: #8fb3d9;
  129 |             margin-bottom: 2.5rem;
  130 |           }
  131 |           h1 {
  132 |             font-size: clamp(2.2rem, 5vw, 4rem);
  133 |             font-weight: 600;
  134 |             margin-bottom: 1.5rem;
  135 |             line-height: 1.1;
  136 |           }
  137 |           p {
  138 |             font-size: clamp(1rem, 2vw, 1.4rem);
  139 |             line-height: 1.55;
  140 |             max-width: 800px;
  141 |             color: #d6e0eb;
  142 |           }
  143 |           .countdown {
  144 |             margin-top: 3rem;
  145 |             font-size: 0.95rem;
  146 |             color: #6f88a3;
  147 |             font-variant-numeric: tabular-nums;
  148 |           }
  149 |         </style>
  150 |       </head>
  151 |       <body>
  152 |         <div class="stage">Phase ${phaseNumber} of ${TOTAL_PHASES}</div>
  153 |         <h1>${title}</h1>
  154 |         <p>${body}</p>
  155 |         <div class="countdown" id="cd">resuming…</div>
  156 |         <script>
  157 |           let seconds = Math.round(${INTRO_MS} / 1000);
  158 |           const el = document.getElementById('cd');
  159 |           el.textContent = 'resuming in ' + seconds + 's';
  160 |           const tick = setInterval(() => {
  161 |             seconds -= 1;
  162 |             if (seconds <= 0) { clearInterval(tick); el.textContent = 'resuming…'; return; }
  163 |             el.textContent = 'resuming in ' + seconds + 's';
  164 |           }, 1000);
  165 |         </script>
  166 |       </body>
  167 |     </html>`;
  168 |   await page.setContent(html, { waitUntil: 'load' });
  169 |   await page.waitForTimeout(INTRO_MS);
  170 | }
  171 |
  172 | /**
  173 |  * Each helper below performs one Juke control-plane or REST call and
  174 |  * asserts the response shape. The page.goto pattern keeps every action
  175 |  * visible in the URL bar so the demo viewer can follow along.
  176 |  */
  177 | async function startRecording(page: Page, track: string, label?: string) {
  178 |   const params = new URLSearchParams({ track });
  179 |   if (label) params.set('label', label);
  180 |   const res = await page.goto(`${SERVER}/service/record/start?${params}`);
  181 |   expect(res?.status(), 'record/start status').toBe(200);
  182 |   await readableDelay(page);
  183 | }
  184 |
  185 | async function endRecording(page: Page) {
  186 |   // /service/record/end streams the ZIP back as the response body —
  187 |   // Playwright treats the navigation as a download, which navigates
  188 |   // away from any visible page. Catch the resulting nav exception
  189 |   // because it doesn't indicate a problem with the recording itself.
  190 |   const res = await page.goto(`${SERVER}/service/record/end`).catch(() => null);
  191 |   if (res) {
  192 |     expect(res.status(), 'record/end status').toBeLessThan(400);
  193 |   }
  194 |   await readableDelay(page);
  195 | }
  196 |
  197 | async function startSession(
  198 |   page: Page,
  199 |   track: string,
  200 |   description?: string,
  201 | ): Promise<{ sessionId: string }> {
  202 |   const params = new URLSearchParams({ track });
  203 |   if (description) params.set('description', description);
  204 |   const res = await page.goto(`${SERVER}/service/session/start?${params}`);
  205 |   expect(res?.status(), 'session/start status').toBe(200);
  206 |   const body = await res!.json();
  207 |   expect(body.status).toBe('active');
  208 |   expect(body.track).toBe(track);
  209 |   expect(body.sessionId).toBeTruthy();
  210 |   await readableDelay(page);
  211 |   return { sessionId: body.sessionId };
  212 | }
  213 |
  214 | async function stopSession(page: Page) {
  215 |   const res = await page.goto(`${SERVER}/service/session/stop`);
  216 |   expect(res?.status(), 'session/stop status').toBe(200);
  217 |   const body = await res!.json();
  218 |   expect(body.status).toBe('stopped');
  219 |   await readableDelay(page);
  220 | }
  221 |
  222 | async function fetchGreeting(page: Page, name: string): Promise<{ id: number; content: string }> {
> 223 |   const res = await page.goto(`${SERVER}/greeting?name=${encodeURIComponent(name)}`);
      |                          ^ Error: page.goto: net::ERR_CONNECTION_REFUSED at http://localhost:8080/greeting?name=Zelda
  224 |   expect(res?.status(), `/greeting?name=${name} status`).toBe(200);
  225 |   const body = (await res!.json()) as { id: number; content: string };
  226 |   // Hold the response on screen so the viewer can read it before the
  227 |   // next nav. Without this the next page.goto in the test wipes the
  228 |   // JSON away after only ~slowMo milliseconds.
  229 |   await readableDelay(page);
  230 |   return body;
  231 | }
  232 |
  233 | async function expectCookies(ctx: BrowserContext, names: string[]) {
  234 |   const cookies = await ctx.cookies();
  235 |   for (const name of names) {
  236 |     expect(
  237 |       cookies.find(c => c.name === name),
  238 |       `cookie '${name}' should be present`,
  239 |     ).toBeTruthy();
  240 |   }
  241 | }
  242 |
  243 | async function expectNoCookies(ctx: BrowserContext, names: string[]) {
  244 |   const cookies = await ctx.cookies();
  245 |   for (const name of names) {
  246 |     expect(
  247 |       cookies.find(c => c.name === name),
  248 |       `cookie '${name}' should be absent`,
  249 |     ).toBeUndefined();
  250 |   }
  251 | }
  252 |
  253 | /**
  254 |  * Shape of one element returned by GET /service/recording/inputs?track=…
  255 |  */
  256 | interface RecordedInput {
  257 |   file: string;
  258 |   sequence: number;
  259 |   method: string;
  260 |   parameterTypes: string[];
  261 |   arguments: unknown[];
  262 | }
  263 |
  264 | /**
  265 |  * Shape of a session entry in GET /service/recording/report?track=…
  266 |  */
  267 | interface SessionReport {
  268 |   sessionId: string;
  269 |   description: string;
  270 |   startedAt: string;
  271 |   stoppedAt: string;
  272 |   callCount: number;
  273 |   overallStatus: 'COMPLETED' | 'COMPLETED_WITH_DEVIATIONS';
  274 |   calls: Array<{
  275 |     sequence: number;
  276 |     method: string;
  277 |     recordedArguments: unknown[];
  278 |     actualArguments: unknown[];
  279 |     inputMatched: boolean;
  280 |   }>;
  281 | }
  282 |
  283 | interface RecordingReport {
  284 |   track: string;
  285 |   label: string;
  286 |   recordedAt: string;
  287 |   sessions: SessionReport[];
  288 | }
  289 |
  290 | /**
  291 |  * Render a full-screen "mismatch report" card comparing what names the test
  292 |  * sent in this replay session against what was captured in the recording.
  293 |  * Enterprise edition: styled comparison card.
  294 |  *
  295 |  * A row is green when the sent value appears in the recorded arguments (i.e.
  296 |  * the test happened to match the recording); it is red otherwise — which is
  297 |  * exactly the scenario we want to highlight here.
  298 |  */
  299 | async function showMismatchReport(
  300 |   page: Page,
  301 |   sentNames: string[],
  302 |   recordedInputs: RecordedInput[],
  303 | ) {
  304 |   const rows = sentNames.map((sent, i) => {
  305 |     const rec = recordedInputs[i];
  306 |     const recordedArg = rec ? String(rec.arguments[0] ?? '—') : '—';
  307 |     const matched = sent === recordedArg;
  308 |     return `
  309 |       <tr class="${matched ? 'match' : 'mismatch'}">
  310 |         <td class="seq">${i + 1}</td>
  311 |         <td class="sent">${sent}</td>
  312 |         <td class="recorded">${recordedArg}</td>
  313 |         <td class="badge">${matched ? '✓&nbsp;MATCH' : '✗&nbsp;MISMATCH'}</td>
  314 |       </tr>`;
  315 |   }).join('');
  316 |
  317 |   const html = `<!DOCTYPE html>
  318 | <html>
  319 |   <head>
  320 |     <meta charset="utf-8"/>
  321 |     <title>Juke — Input Mismatch Report</title>
  322 |     <style>
  323 |       * { box-sizing: border-box; margin: 0; padding: 0; }
```