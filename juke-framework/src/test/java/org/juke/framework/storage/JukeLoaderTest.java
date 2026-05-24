package org.juke.framework.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.juke.framework.proxy.JukeFactory;
import org.juke.framework.proxy.JukeNameFormatter;
import org.juke.framework.proxy.JukeState;
import org.juke.framework.support.ISampleService;
import org.juke.framework.support.SampleService;
import org.juke.framework.metadata.JukeClass;
import org.juke.framework.metadata.JukeConfigBuilder;
import org.juke.framework.metadata.DataProgramSchedule;
import org.juke.framework.metadata.JukeStateBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;

public class JukeLoaderTest {
static ObjectMapper objectMapper ;

	static {

		objectMapper = new ObjectMapper();
		objectMapper.enable(SerializationFeature.INDENT_OUTPUT);

	}
	ISampleService service = new SampleService();

	@TempDir
	Path tempDir;

	/**
	 * Reset global mutable state so this test is not affected by — nor affects —
	 * other tests. Earlier tests in the suite set {@code juke.path} to JUnit
	 * {@code @TempDir} paths which JUnit deletes when those tests finish, leaving
	 * a dangling path that would break the zip writes below.
	 */
	@BeforeEach
	void resetJukeState() {
		System.clearProperty("juke.path");
		System.clearProperty("juke.zip");
		JukeNameFormatter.clearMappings();
		JukeState.setGlobaljuke(JukeState.IGNORE);
		JukeFactory.setGlobaljuke(JukeState.IGNORE);
	}

	@AfterEach
	void clearJukeState() {
		System.clearProperty("juke.path");
		System.clearProperty("juke.zip");
		JukeNameFormatter.clearMappings();
		JukeState.setGlobaljuke(JukeState.IGNORE);
		JukeFactory.setGlobaljuke(JukeState.IGNORE);
	}

	@Test
	void loadTest0() throws Exception {
		String jukePath = tempDir.toString();
		JukeState.setGlobaljuke(JukeState.JUKE);
		JukeFactory.setGlobaljuke(JukeState.JUKE);
		System.setProperty("juke.path", jukePath);
		System.setProperty("juke.zip","juke2");
		JukeHelper.setJukeDao(new JukeZipDAOImpl(jukePath, "juke2"));
		
		ISampleService wrapped=new JukeFactory<ISampleService>().newInstance(this.service,ISampleService.class, JukeState.JUKE);
	
		
	
		JukeClass JukeClass=  JukeConfigBuilder.set(ISampleService.class).build();
		HashMap<String,JukeClass> juke=new HashMap<>();
		juke.put(ISampleService.class.getCanonicalName(),JukeClass);
		BigDecimal[] biggie= new BigDecimal[2];
		biggie[0]=new BigDecimal("23423423423423423423423423423423.3423423423423423");
		biggie[1]= new BigDecimal("278734384738453485837475384573485345.2342342342342342342342365");
	
		
		List<HashMap<String,BigDecimal>> listHashMapStringBiDecimal2= wrapped.getMyDataMapAsList(biggie, "testit");
		HashMap<String,BigDecimal> biggieMap = wrapped.getMyDataMap(biggie, "testit");
		JukeHelper.getJukeDAO().write();
		assert(listHashMapStringBiDecimal2.size() == 2);
		assert(biggieMap.size() == 2);

		// Verify the zip landed under our temp dir (no longer renames to C:/temp —
		// that was a cosmetic artifact that leaked state between runs).
		assert(new File(JukeHelper.getJukeDAO().path()).exists());

	}
	@Test
	void loadTest1() throws Exception {
		new File("/temp").mkdirs();
		if (new File ("/temp/juke.zip").exists())
			new File ("/temp/juke.zip").delete();

		
		JukeClass JukeClass=  JukeConfigBuilder.set(ISampleService.class).build();
		HashMap<String,JukeClass> juke=new HashMap<>();
		juke.put(ISampleService.class.getCanonicalName(),JukeClass);
		BigDecimal[] biggie= new BigDecimal[2];
		biggie[0]=new BigDecimal("23423423423423423423423423423423.3423423423423423");
		biggie[1]= new BigDecimal("278734384738453485837475384573485345.2342342342342342342342365");
		List<HashMap<String,BigDecimal>> listHashMapStringBiDecimal=service.getMyDataMapAsList(biggie, "testit");
		HashMap<String,BigDecimal> biggieMap = service.getMyDataMap(biggie, "testit");
		

		String listHashMapStringBiDecimalJson = JukeTransformerUtil.writeValueAsString(listHashMapStringBiDecimal);
		String hashMapStringBiDecimalJson = JukeTransformerUtil.writeValueAsString(biggieMap);
		String tmpdir=System.getProperty("java.io.tmpdir");
		String orig=objectMapper.writeValueAsString(juke);
		//File f=new File(new File(tmpdir),ISampleService.class.getName()+".json" );
		JukeZipDAOImpl zip=new JukeZipDAOImpl("C:/temp", "juke");
		zip.writeToFile(ISampleService.class.getCanonicalName()+".$getMyDataMapAsList-1641989625", listHashMapStringBiDecimalJson);
		zip.writeToFile(ISampleService.class.getCanonicalName()+".$getMyDataMapAsList-1641989625", listHashMapStringBiDecimalJson);
		zip.writeToFile(ISampleService.class.getCanonicalName()+".$getMyDataMapAsList-1641989625", listHashMapStringBiDecimalJson);
		zip.writeToFile(ISampleService.class.getCanonicalName()+".$getMyDataMap", hashMapStringBiDecimalJson);
		zip.writeToFile("juke", objectMapper.writeValueAsString(juke));
		zip.write();

		String path =zip.path();
		System.out.println(path);

		new File(path).renameTo(new File("C:/temp/juke.zip"));

		JukeZipDAOImpl zip2=new JukeZipDAOImpl("C:/temp", "juke");
		assert(new File(zip2.path()).exists());
		JukeHelper.setJukeDao(zip2);
		String jukeTxt=JukeHelper.getJukeDAO().asString( "juke");
		HashMap<String,JukeClass> juke2Class= zip2.getJukeClassMap();
		JukeState.setGlobaljuke(JukeState.REPLAY);
	
		
		System.setProperty("juke.path","C:/temp");
		System.setProperty("juke.zip","juke");
		
		ISampleService wrapped=new JukeFactory<ISampleService>().newInstance(this.service,ISampleService.class, JukeState.REPLAY);
		List<HashMap<String,BigDecimal>> list=wrapped.getMyDataMapAsList(biggie, "testit");
        List<HashMap<String,BigDecimal>> listB=wrapped.getMyDataMapAsList(biggie, "testit");
        List<HashMap<String,BigDecimal>> listV=wrapped.getMyDataMapAsList(biggie, "testit");
        List<HashMap<String,BigDecimal>> listV2=wrapped.getMyDataMapAsList(biggie, "testit");


        ZipUtil zipper = new ZipUtil("C:/temp","juke");
		zipper.open();
		//return new DelayTuner.Builder(sequencedItem, 0).build();
		JukeStateBuilder built= new JukeStateBuilder.Builder(zipper.getZipName()).build();
		DataProgramSchedule sched= built.getSchedule();
		assert (sched != null);
		
		
		System.out.println("done");
	}
	

}
