package org.juke.framework.metadata;




import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.juke.framework.proxy.BaseHandler;


public class JukeParser {
	private static final Logger LOG = LoggerFactory.getLogger(JukeParser.class);
	
	public static final String STATICTYPE = "static";

	public static String getMethodName(BaseHandler<?> item, Method m) {
		return getMethodName(item.getInterfaceClass(), m);

	}
	public static String getMethodName(Class<?> item, Method m) {

		if (m.getName().equals("toString"))
			return item.toString();

		boolean isOverloaded = JukeParser.isOverloaded(item, m.getName());
		String methodName = m.getName();
		if (isOverloaded) {
			methodName += buildParameterSignature(m);
		}
		return methodName;
	}
	public static String getFullName(BaseHandler<?> item, Method m) {

		return m.getDeclaringClass().getName()+".$"+ getMethodName(item, m);

	}



	public static boolean isOverloaded(Class<?> c, String methodName) {
		Method[] interfaceMethods = c.getDeclaredMethods();
		int occurance =0;
		for (int i=0; i < interfaceMethods.length; i++) {
			if (interfaceMethods[i].getName().equalsIgnoreCase(methodName)) {
				occurance++;
			}
			if (occurance >1 ) {			
				return true;
			}
		
		}
		return false;
	}
	public static int buildParameterSignature(Method m) {
		StringBuilder builder =new StringBuilder();
		Type[] types = m.getGenericParameterTypes();
		builder.append("_");
		for (int i=0; i < types.length; i++) {
			if (i > 0) {
				builder.append("_");
			}
			
			builder.append(types[i].getTypeName());
			
		}
		builder.append("_");
		return builder.toString().hashCode();
		
	}
	
public static boolean isAssignableFromArguments(Method m, Object[] args) {

		Class<?>[] methodArgs = m.getParameterTypes();
		if ((args == null || args.length == 0) && methodArgs.length == 0){
			return true;
		}
		else if (args == null || methodArgs.length != args.length) {
			return false;
		}else {
			for (int i=0; i < args.length; i++) {

				if (args[i] != null && (args[i].getClass().isPrimitive() || methodArgs[i].isPrimitive())) {
					if (!PrimitiveReflectUtil.isAssignableTo(args[i].getClass(), methodArgs[i])) {
						return false;
					}
				}
				else if (args[i] != null && !methodArgs[i].isAssignableFrom(args[i].getClass())){
					return false;
				}
			}
		}
		return true;
	}
	public static List<Method> isInMethods(String methodName, Method[] methodsList){
		ArrayList<Method> list = new ArrayList<>();
		
		for (int i=0; i < methodsList.length; i++) {
			if (methodsList[i].getName().equalsIgnoreCase(methodName))
				list.add(methodsList[i]);
		}
		return list;
	}

	
	/**
	 * Unchecked cast helper — callers opt into the cast by naming the target
	 * type. Keeps the suppression in one place rather than scattered across
	 * call sites.
	 */
	@SuppressWarnings("unchecked")
	public static <TSource, TTarget> TTarget cast(TSource toCast) {
		return (TTarget) toCast;
	}

	
}
