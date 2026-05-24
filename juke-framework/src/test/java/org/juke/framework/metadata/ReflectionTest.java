package org.juke.framework.metadata;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;


public class ReflectionTest {
	static ObjectMapper mapper;
	static {
		
		mapper=new ObjectMapper();
		mapper.enable(SerializationFeature.INDENT_OUTPUT);
		mapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
	}
	
	
	@Test
	void reflectionTestArray() throws ClassNotFoundException {
		
		Object obj[]= new Object[2];
		obj[0]= "test";
		obj[1]= new Integer(3);
	
	
		
		try {
			String objString=mapper.writeValueAsString(obj);
		
			 Object[] obj3 = mapper.readValue(objString, mapper.getTypeFactory().constructArrayType(Class.forName("java.lang.Object")));
			
			String objString2=mapper.writeValueAsString(obj3);
			assertEquals(objString, objString2);
				
		} catch (JsonProcessingException e) {
			e.printStackTrace();
		}
				
	}
	
	
	@Test
	void reflectionTestVector() throws ClassNotFoundException {
		
		Vector obj=new Vector<Object>();
		obj.add("test");
		obj.add( new Integer(3));
	
		
		try {
			String objString=mapper.writeValueAsString(obj);
			JavaType javaType = mapper.constructType(obj.getClass());
			 
			Object obj2= mapper.readValue(objString,Class.forName("java.util.Vector"));
			String objString2=mapper.writeValueAsString(obj2);
			assertEquals(objString, objString2);
				
		} catch (JsonProcessingException e) {
			e.printStackTrace();
		}
				
	}
	
	
	@Test
	void reflectionTestSet() throws ClassNotFoundException {
		
		Set obj=new HashSet<Object>();
		obj.add("test");
		obj.add( new Integer(3));
	
		
		try {
			String objString=mapper.writeValueAsString(obj);
			JavaType javaType = mapper.constructType(obj.getClass());
			 
			Object obj2= mapper.readValue(objString,Class.forName("java.util.Set"));
			String objString2=mapper.writeValueAsString(obj2);
			assertEquals(objString, objString2);
				
		} catch (JsonProcessingException e) {
			e.printStackTrace();
		}
				
	}
	
	@Test
	void reflectionTestMap() throws ClassNotFoundException {
		
		Map map=new HashMap<String,Object>();
		map.put("test","test2");
		map.put("test2", new Integer(3));
	
		
		try {
			String mapString=mapper.writeValueAsString(map);
			JavaType javaType = mapper.constructType(map.getClass());
			 
			Map map5= mapper.readValue(mapString,javaType);
		
			Object map4= mapper.readValue(mapString,Class.forName("java.util.Map"));
			String mapString2=mapper.writeValueAsString(map4);
			assertEquals(mapString,mapString2);
				
		} catch (JsonProcessingException e) {
			e.printStackTrace();
		}
				
	}
	@Test
	void reflectionTestParametizedMap() throws ClassNotFoundException {
		
		Map map=new HashMap<String,BigDecimal>();
		map.put("test",new BigDecimal("23423423423423423423423423423423.3423423423423423"));
		map.put("test2", new BigDecimal("278734384738453485837475384573485345.2342342342342342342342365"));
	
		
		try {
			String mapString=mapper.writeValueAsString(map);
			JavaType javaType = mapper.getTypeFactory().constructParametricType(Class.forName("java.util.HashMap"),
					Class.forName("java.lang.String"),
					Class.forName("java.math.BigDecimal"));
			 
			Map<String,BigDecimal> map5= mapper.readValue(mapString,javaType);
		

			String mapString2=mapper.writeValueAsString(map5);
			assertEquals(mapString,mapString2);
				
		} catch (JsonProcessingException e) {
			e.printStackTrace();
		}
				
	}
}
