package org.juke.framework.storage;

import org.junit.jupiter.api.Test;
import org.juke.framework.support.ISampleService;
import org.juke.framework.support.SampleService;
import org.juke.framework.metadata.JukeClass;
import org.juke.framework.metadata.JukeMethod;
import org.juke.framework.metadata.JukeConfigBuilder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;




public class JukeSerializationTest {

static ObjectMapper objectMapper ;
	
	static {
		
		objectMapper = new ObjectMapper();
		objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
		
	}
	ISampleService service= new SampleService();
	
	@Test
	void serializationTest1() throws Exception {
		
		JukeClass JukeClass=  JukeConfigBuilder.set(ISampleService.class).build();
		BigDecimal[] biggie= new BigDecimal[2];
		biggie[0]=new BigDecimal("23423423423423423423423423423423.3423423423423423");
		biggie[1]= new BigDecimal("278734384738453485837475384573485345.2342342342342342342342365");
		List<HashMap<String,BigDecimal>> listHashMapStringBiDecimal=service.getMyDataMapAsList(biggie, "testit");
		

		String listHashMapStringBiDecimalJson = JukeTransformerUtil.writeValueAsString(listHashMapStringBiDecimal);
		List<HashMap<String,BigDecimal>> rebuiltListHashMapStringBiDecimal = 
				JukeTransformerUtil.readValue(listHashMapStringBiDecimalJson, JukeClass, "getMyDataMapAsList");

		assertEquals(listHashMapStringBiDecimal.size(), rebuiltListHashMapStringBiDecimal.size());
		for (int i=0; i < listHashMapStringBiDecimal.size(); i++) {
			
			assertEquals(listHashMapStringBiDecimal.get(i).size(), rebuiltListHashMapStringBiDecimal.get(i).size());
			for (int j=0 ; j < listHashMapStringBiDecimal.get(i).size(); j++) {
				HashMap<String, BigDecimal> biggieMap = listHashMapStringBiDecimal.get(i);
				HashMap<String, BigDecimal> rebuiltBiggieMap = rebuiltListHashMapStringBiDecimal.get(i);
				
				assertTrue(biggieMap.equals(rebuiltBiggieMap));
				
			}
		}
	
		
		
	}
	@Test
	void serializationTest2() throws Exception {
		
		JukeClass JukeClass=  JukeConfigBuilder.set(ISampleService.class).build();
		BigDecimal[] biggie= new BigDecimal[2];
		biggie[0]=new BigDecimal("23423423423423423423423423423423.3423423423423423");
		biggie[1]= new BigDecimal("278734384738453485837475384573485345.2342342342342342342342365");
		HashMap<String,BigDecimal> biggieMap = service.getMyDataMap(biggie, "testit");
		

		String hashMapStringBiDecimalJson = JukeTransformerUtil.writeValueAsString(biggieMap);
		HashMap<String,BigDecimal> rebuiltBiggieMap = 
				JukeTransformerUtil.readValue(hashMapStringBiDecimalJson, JukeClass, "getMyDataMap");
			
		assertTrue(biggieMap.equals(rebuiltBiggieMap));
		
	}
	@Test
	void serializationTest3() throws Exception {
		
		JukeClass JukeClass=  JukeConfigBuilder.set(ISampleService.class).build();
		BigDecimal[] biggie= new BigDecimal[2];
		biggie[0]=new BigDecimal("23423423423423423423423423423423.3423423423423423");
		biggie[1]= new BigDecimal("278734384738453485837475384573485345.2342342342342342342342365");
		Set biggieSet = new HashSet();
		biggieSet.add(biggie[0]);
		biggieSet.add(biggie[1]);
		
		BigDecimal[] biggieArray = service.getMyDataArrayReverse2(biggieSet);
		

		String hashMapStringBiDecimalJson = JukeTransformerUtil.writeValueAsString(biggieArray);
		BigDecimal[]  rebuiltBiggie = 
				JukeTransformerUtil.readValue(hashMapStringBiDecimalJson, JukeClass, "getMyDataArrayReverse2");
		assertEquals(biggieArray.length, rebuiltBiggie.length);
		for (int i=0; i < biggieArray.length; i++) {
			assertTrue(biggieArray[i].equals(rebuiltBiggie[i]));
			
		}
		
	}
	
	@Test
	void serializationTest4() throws Exception {
		
		JukeClass JukeClass=  JukeConfigBuilder.set(ISampleService.class).build();
		double[] d1= new double[2];
		d1[0] = 2.3334;
		d1[1] = 4.555;
 
		
		Double[] dObj = service.fromSimpleDoubleArray(d1);
		

		String d1Json = JukeTransformerUtil.writeValueAsString(d1);
		Double[]  rebuiltD1 = 
				JukeTransformerUtil.readValue(d1Json, JukeClass, "fromSimpleDoubleArray");
		assertEquals(d1.length, rebuiltD1.length);
		for (int i=0; i < d1.length; i++) {
			assertTrue(d1[i] == rebuiltD1[i]);
			
		}
		
	}
	
	
	@Test
	void serializationTest5() throws Exception {
		
		JukeClass JukeClass=  JukeConfigBuilder.set(ISampleService.class).build();
		Double[] d1= new Double[2];
		d1[0] = 2.3334;
		d1[1] = 4.555;
 
		
		double[] dObj = service.toSimpleDoubleArray(d1);
		

		String d1Json = JukeTransformerUtil.writeValueAsString(d1);
		double[]  rebuiltD1 = 
				JukeTransformerUtil.readValue(d1Json, JukeClass, "toSimpleDoubleArray");
		assertEquals(d1.length, rebuiltD1.length);
		for (int i=0; i < d1.length; i++) {
			assertTrue(d1[i] == rebuiltD1[i]);
			
		}
		
	}
}
