package org.juke.framework.metadata;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.juke.framework.metadata.JukeClass;
import org.juke.framework.metadata.JukeMethod;
import org.juke.framework.metadata.JukeParameter;
import org.juke.framework.metadata.JukeParameterizedType;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.type.TypeFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JukeConfigBuilder {
	private static final Logger LOG = LoggerFactory.getLogger(JukeConfigBuilder.class);

	Class<?> interfaceClass;
	// Phase 5.5 — initialised once at class load; no setter, never reassigned.
	static final ObjectMapper objectMapper = configuredMapper();

	private static ObjectMapper configuredMapper() {
		ObjectMapper m = new ObjectMapper();
		m.enable(SerializationFeature.INDENT_OUTPUT);
		return m;
	}


	public static JavaType constructParameterizedType(JukeParameterizedType pt) throws ClassNotFoundException {
		
		return JukeParameterizedTypeBuilder.fromJukeParameterizedType(pt);
		
			
		
	}
	
	public static JavaType constructParameterizedType(ParameterizedType pt) throws ClassNotFoundException {
		List<Class<?>> ptc = new ArrayList<>();
		JavaType jtype = null;
		Type[] actualTypeArguments = pt.getActualTypeArguments();
		Type raw = pt.getRawType();
		int arguments = actualTypeArguments.length;
		if (arguments == 0) {
			return objectMapper.getTypeFactory().constructFromCanonical(raw.getTypeName());
		}
		if (arguments == 1 && (actualTypeArguments[0] instanceof ParameterizedType)) {
			jtype = constructParameterizedType((ParameterizedType) actualTypeArguments[0]);
			return objectMapper.getTypeFactory().constructParametricType(
					Class.forName(raw.getTypeName()), jtype);
		} else {
			for (Type type : actualTypeArguments) {
				Class<?> cl = Class.forName(type.getTypeName());
				ptc.add(cl);
			}
			Class<?>[] classes = ptc.toArray(new Class<?>[0]);
			return objectMapper.getTypeFactory().constructParametricType(
					Class.forName(raw.getTypeName()), classes);
		}
	}
	public static JavaType constructType(JukeParameter yp ) throws ClassNotFoundException {
		
		TypeFactory factory = objectMapper.getTypeFactory();
		List<JavaType> arguments= new ArrayList<JavaType>();
		if (yp.getList().size() > 0) {
			yp.getList().forEach(item ->{
				
				try {
					JavaType jt = constructType(item);
					arguments.add(jt);
				} catch (ClassNotFoundException e) {
					LOG.warn("Failed to resolve class for nested parameter '{}': {}",
							item.getClassName(), e.getMessage(), e);
				}
			});
			 
		}
		JavaType full = null;
		JavaType inner = null;
		if (yp.isParameterized())
		{
			inner = factory.constructParametricType(Class.forName(yp.getClassName())
					, arguments.toArray(new JavaType[arguments.size()]));
			full = factory.constructParametricType(Class.forName(yp.getClassName()), inner);
		}else if  (yp.isArray()) {
			full = factory.constructArrayType(Class.forName(yp.getClassName()));
	
			}
		else if (yp.getType() != null) {
			full= constructParameterizedType(yp.getType());
			full= constructParameterizedType(yp.getType());
		}
		else {
			full = factory.constructType(Class.forName(yp.getClassName()));
		}
		 


		

		return full;
	}
	public JukeConfigBuilder(Class<?> interfaceClass) {
		this.interfaceClass = interfaceClass;
	}
	
	
	public JukeClass  build() {
		JukeClass c = new JukeClass();
		c.setClassName(interfaceClass.getCanonicalName());
	
		List<JukeMethod> JukeMethods = setJukeMethods(interfaceClass);
		c.setMethods(JukeMethods);
		JukeClass.instance().put(interfaceClass.getCanonicalName(), c);
		return c;
	}
	
	
	public List<JukeMethod> setJukeMethods(Class<?> interfaceClass) {
		List<JukeMethod> JukeMethods = new ArrayList<JukeMethod>();
		// Use getMethods() to include inherited interface methods for concrete classes.
		// getDeclaredMethods() only returns methods declared directly on the class.
		for (Method method : interfaceClass.getMethods()) {
			// Skip Object methods and synthetic/bridge methods
			if (!org.juke.framework.proxy.JukeMethodFilter.shouldIntercept(method)) {
				continue;
			}
			JukeMethods.add(buildMethodMetadata(method));
		}
		return JukeMethods;
	}

	/**
	 * Builds a single {@link JukeMethod} entry from a reflected {@link Method},
	 * capturing its overload signature, input-parameter descriptors, and
	 * output-parameter descriptor.
	 */
	private JukeMethod buildMethodMetadata(Method method) {
		JukeMethod ym = new JukeMethod();
		ym.setOverloadedSignature(JukeParser.buildParameterSignature(method));
		ym.setMethod(method.getName());
		ym.setInputParameters(buildInputParameters(method));
		ym.setOutputResult(buildReturnParameter(method));
		return ym;
	}

	/**
	 * Maps each declared parameter type to a {@link JukeParameter}, normalizing
	 * array suffixes and converting Java primitives ({@code int}, {@code long},
	 * etc.) to their boxed forms for consistent JSON serialization.
	 */
	private List<JukeParameter> buildInputParameters(Method method) {
		List<JukeParameter> params = new ArrayList<>();
		for (java.lang.reflect.Type type : method.getParameterTypes()) {
			JukeParameter parameter = new JukeParameter();
			String name = type.getTypeName();
			System.out.println(name);
			if (name.endsWith("[]")) {
				parameter.setArray(true);
				name = cleanArray(name);
			}
			name = convertSimpleTypeToObject(name);
			name = cleanArray(name);
			parameter.setClassName(name);
			params.add(parameter);
		}
		return params;
	}

	/**
	 * Builds the output-parameter descriptor for a method: the parameterized
	 * type tree (so generics like {@code List<String>} survive marshalling),
	 * the erased class name, and the array flag.
	 */
	private JukeParameter buildReturnParameter(Method method) {
		JukeParameter parameter = new JukeParameter();

		// Preserve parameterized type info for marshalling. The caller-side
		// branch on ParameterizedType is a no-op — {@code fromParameterizedType}
		// takes {@code Type} and handles both cases, so one call suffices.
		JukeParameterizedType ypt = JukeParameterizedTypeBuilder.fromParameterizedType(
				method.getGenericReturnType());
		parameter.setType(ypt);

		String name = method.getReturnType().getTypeName();
		if (name.endsWith("[]")) {
			name = name.substring(0, name.length() - 2);
			parameter.setArray(true);
		}
		name = convertSimpleTypeToObject(name);
		name = cleanArray(name);
		parameter.setClassName(name);
		return parameter;
	}
	
	
	public static String toJSON(JukeClass interfaceClass) throws JsonProcessingException {
	   return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(interfaceClass);
		
	}
	public static JukeClass fromJSON(String json) throws JsonMappingException, JsonProcessingException {
		   return objectMapper.readValue(json, JukeClass.class);
			
		}
	public static JukeConfigBuilder set(Class<?> c) {
		return new JukeConfigBuilder(c);
	}
	private static String cleanArray(String name) {
		if (name.endsWith("[]")) {
			return name.substring(0,name.length() - 2);
			
		}
		return name;
		
	}
	public static String convertSimpleTypeToObject(String simpleType) {
		String post="[]";
		if (simpleType.endsWith(post)) {
			simpleType = cleanArray(simpleType);
			
		}else {
			post="";
		}
		
		switch (simpleType) {
			case "int":
				return "Integer" + post;
				
			case "double":
				return "Double" + post;
			case "boolean":
				return "Boolean" + post;
			
			case "long":
				return "Long" + post;
		
			case "char":
				return "Character" + post;
			case "float":
				return "Float" + post;
			case "short":
				return "Short" + post;
			case "byte":
				return "Byte" + post;
			default:
				return simpleType + post;
			
		} 
		
	}
}
