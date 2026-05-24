package org.juke.framework.tuner;

import org.juke.framework.exception.TunerGeneratedException;
import org.juke.framework.support.ISampleService;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

;


public class TunerTest {

	
	
	
	@Test
	void testTune1()  {
		String signature1 = ISampleService.class.getCanonicalName()+".$getMyDataMapAsList.1";
		String signature2 = ISampleService.class.getCanonicalName()+".$getMyDataMapAsList.2";
		String signature3 = ISampleService.class.getCanonicalName()+".$getMyDataMapAsList.3";
		
		
		DelayTunerTask tuner1 = new DelayTunerTask.Builder(signature1, 2000).build();
		DelayTunerTask tuner2 = new DelayTunerTask.Builder(signature2, 3000).build();
		ExceptionTunerTask tuner3 = new ExceptionTunerTask.Builder(signature3, IOException.class.getSimpleName()).build();
		assertEquals(TunerTask.getParticipants().size(), 2);
		assertEquals(TunerTask.getParticipants().get(DelayTunerTask.class.getCanonicalName()).size(),2);
		assertEquals(TunerTask.getParticipants().get(ExceptionTunerTask.class.getCanonicalName()).size(),1);
		
		
		JukeCommandProcessorChain chain=new JukeCommandProcessorChain();
		ProcessObject po=new ProcessObject();
		po.signature=signature2;
		po.json= "{}";
		long start = new Date().getTime();
		try {
			JukeCommandProcessorChain.execute(po);
			
		} catch (Exception e) {
			e.printStackTrace();
		}
		long end = new Date().getTime();
		assertTrue((end-start) >= 1800, "Delay should be at least 1800m[INFO] Running org.juke.remix.service.TestingWebAppTests\n" +
				"[ERROR] Tests run: 1, Failures: 0, Errors: 1, Skipped: 0, Time elapsed: 0.089 s <<< FAILURE! - in org.juke.remix.service.TestingWebAppTests\n" +
				"[ERROR] org.juke.remix.service.TestingWebAppTests  Time elapsed: 0.088 s  <<< ERROR!\n" +
				"java.lang.ExceptionInInitializerError\n" +
				"\tat org.juke.remix.service.TestingWebAppTests.<clinit>(TestingWebAppTests.java:58)\n" +
				"Caused by: java.io.IOException: juketest2.zip not found in test resources\n" +
				"\tat org.juke.remix.service.TestingWebAppTests.<clinit>(TestingWebAppTests.java:48)\n" +
				"\n" +
				"[INFO] \n" +
				"[INFO] Results:\n" +
				"[INFO] \n" +
				"[ERROR] Errors: \n" +
				"[ERROR]   TestingWebAppTests » ExceptionInInitializer\n" +
				"[INFO] \n" +
				"[ERROR] Tests run: 1, Failures: 0, Errors: 1, Skipped: 0s, was: " + (end-start));
		assertTrue((end-start) <= 3500, "Delay should be at most 3500ms, was: " + (end-start));

		
		po.signature=ISampleService.class.getCanonicalName()+".$getMyDataMapAsList.0";
		po.json= "{}";
		 start = new Date().getTime();
		try {
			JukeCommandProcessorChain.execute(po);
			
		} catch (Exception e) {
			e.printStackTrace();
		}
		 end = new Date().getTime();
		assertTrue((end-start)<100);

		
		
		IOException ioe=null;
		try {
			ProcessObject expo=new ProcessObject();
			expo.signature=signature3;
			expo.json= "{}";
			JukeCommandProcessorChain.execute(expo);
			
		} catch (Exception e) {
			if (e instanceof TunerGeneratedException) {
				TunerGeneratedException ex=(TunerGeneratedException) e;
				
				ioe=(IOException) ex.getWrappedException();;
			}
			else {
				e.printStackTrace();
			}
		}
		assertNotNull(ioe);
		
		TunerTask.clear();
		assertEquals(TunerTask.getParticipants().get(DelayTunerTask.class.getCanonicalName()).size(),0);
		
				
	}
	
}
