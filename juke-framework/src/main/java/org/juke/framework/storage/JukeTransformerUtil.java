package org.juke.framework.storage;

import java.util.List;

import org.juke.framework.metadata.JukeClass;
import org.juke.framework.metadata.JukeMethod;
import org.juke.framework.metadata.JukeParameter;
import org.juke.framework.metadata.JukeParameterizedType;
import org.juke.framework.metadata.JukeParameterizedTypeBuilder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

public class JukeTransformerUtil {
	
static ObjectMapper objectMapper ;

static {
		
		objectMapper = new ObjectMapper();
		objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
		
	}



    public static  <T> T readValue(String content, JukeMethod method)
        throws JsonProcessingException, JsonMappingException, ClassNotFoundException
    {

	   JukeParameter outputParameter = method.getOutputResult();
	   JukeParameterizedType ypt=  outputParameter.getType() ;
	   JavaType jt = JukeParameterizedTypeBuilder.fromJukeParameterizedType(ypt);

	   return objectMapper.readValue(content, jt);
    }

    public static  <T> T readValue(String content, JukeClass JukeClass, String method)
        throws JsonProcessingException, JsonMappingException, ClassNotFoundException, Exception
    {
	   List<JukeMethod> methods= JukeClass.getMethodsByName(method);

	   //!!replace  with something that handles multiple methods with same name!!
	   if(methods.size()==0) {
		   throw new Exception("No such method "+method+" in interface class "+JukeClass.getClassName() );
	   }
	  return readValue(content, methods.get(0));

    }

    public static  <T> T readValue(String content, JukeMethod method, JavaType valueType)
        throws JsonProcessingException, JsonMappingException, ClassNotFoundException
    {

	   JukeParameter outputParameter = method.getOutputResult();
	   JukeParameterizedType ypt=  outputParameter.getType() ;
	   JavaType jt = JukeParameterizedTypeBuilder.fromJukeParameterizedType(ypt);

	   return objectMapper.readValue(content, jt);
    }

    public static  <T> T readValue(String content, JukeClass JukeClass, String method, JavaType valueType)
        throws JsonProcessingException, JsonMappingException, ClassNotFoundException, Exception
    {
	   List<JukeMethod> methods= JukeClass.getMethodsByName(method);
	   //!!replace  with something that handles multiple methods with same name!!
	   if(methods.size()==0) {
		   throw new Exception("No such method "+method+" in interface class "+JukeClass.getClassName() );
	   }
	  return readValue(content, methods.get(0), valueType);

    }

   public static String writeValueAsString(Object value)
	        throws JsonProcessingException{
	   
	   return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
	   
	   
   }

   /**
    * Deserializes JSON content using a specific runtime type rather than the
    * declared return type from JukeMethod metadata. This solves the problem
    * where the declared return type is Object but the actual recorded type
    * is a concrete class (e.g., RestTemplate.getForEntity responseType).
    *
    * @param content       the JSON content to deserialize
    * @param runtimeType   the actual Class to deserialize into
    * @param <T>           the target type
    * @return the deserialized object
    */
   public static <T> T readValueAsType(String content, Class<T> runtimeType)
           throws JsonProcessingException, JsonMappingException {
       return objectMapper.readValue(content, runtimeType);
   }
}
