package org.juke.framework.metadata;

import java.util.HashMap;
import java.util.Map;

public class PrimitiveReflectUtil {
private static final Map<Class<?>, Class<?>> primitiveWrapperMap;
static {
	primitiveWrapperMap = new HashMap<>();
	primitiveWrapperMap.put(boolean.class, Boolean.class);
	primitiveWrapperMap.put(byte.class, Byte.class);
	primitiveWrapperMap.put(char.class, Character.class);
	primitiveWrapperMap.put(double.class, Double.class);
	primitiveWrapperMap.put(float.class, Float.class);
	primitiveWrapperMap.put(int.class, Integer.class);
	primitiveWrapperMap.put(long.class, Long.class);
	primitiveWrapperMap.put(short.class, Short.class);
	
}
public static boolean isPrimitiveWrapperOf(Class<?> targetClass, Class<?> primitive) {
	
	if (!primitive.isPrimitive()) {
		throw new IllegalArgumentException ("Frist argument has to be primitive type");
		
	}
	return primitiveWrapperMap.get(primitive) == targetClass;
}
public static boolean isAssignableTo(Class<?> from, Class<?> to) {
	if (to.isAssignableFrom(from))
		return true;
	else if (from.isPrimitive())
		return isPrimitiveWrapperOf(to, from);
	else if (to.isPrimitive())
		return isPrimitiveWrapperOf(from, to);
	return false;
}

}
