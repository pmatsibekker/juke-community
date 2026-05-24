package org.juke.framework.annotation;

import org.juke.framework.exception.JukeConfigurationException;
import org.juke.framework.proxy.JukeFactory;
import org.juke.framework.proxy.JukeState;

import java.lang.reflect.Constructor;
import java.lang.reflect.Parameter;

/**
 * Utility class for initializing Juke annotations at runtime.
 * This class provides methods to initialize Juke-annotated fields
 * when annotation processing is not available or for runtime injection.
 * 
 * Also provides helper methods for constructor-based dependency injection
 * when @Juke annotations are used on constructor parameters.
 */
public class JukeInitializer {

    /**
     * Wraps an object with JukeFactory.
     *
     * @param <T>         the interface type
     * @param original    the original object to wrap
     * @param interfaceClass the interface class
     * @return the wrapped object
     */
    public static <T> T wrap(T original, Class<T> interfaceClass) {
        return wrap(original, interfaceClass, JukeState.JUKE);
    }

    /**
     * Wraps an object with JukeFactory using the specified JukeState.
     *
     * @param <T>         the interface type
     * @param original    the original object to wrap
     * @param interfaceClass the interface class
     * @param jukeState   the JukeState to use
     * @return the wrapped object
     */
    public static <T> T wrap(T original, Class<T> interfaceClass, String jukeState) {
        if (original == null) {
            return null;
        }
        
        return new JukeFactory<T>().newInstance(original, interfaceClass, jukeState);
    }
    
    /**
     * Normalizes a JukeState value to match JukeState constants.
     * 
     * @param stateValue the state value from annotation
     * @return normalized JukeState value
     */
    public static String normalizeJukeState(String stateValue) {
        if (stateValue == null || stateValue.trim().isEmpty()) {
            return JukeState.JUKE;
        }
        
        String normalized = stateValue.trim().toLowerCase();
        switch (normalized) {
            case "juke":
                return JukeState.JUKE;
            case "record":
                return JukeState.RECORD;
            case "replay":
                return JukeState.REPLAY;
            case "ignore":
                return JukeState.IGNORE;
            case "none":
            case "":
                return JukeState.NONE;
            case "disable":
                return JukeState.DISABLE;
            default:
                return JukeState.JUKE;
        }
    }
    
    /**
     * Initializes all fields annotated with @Juke in the provided object.
     * This method uses reflection to find annotated fields and wrap them.
     *
     * @param object the object whose fields should be initialized
     */
    public static void initializeAnnotatedFields(Object object) {
        if (object == null) {
            return;
        }
        
        Class<?> clazz = object.getClass();
        
        // Process all fields in the class hierarchy
        while (clazz != null && clazz != Object.class) {
            processClassFields(object, clazz);
            clazz = clazz.getSuperclass();
        }
    }
    
    /**
     * Processes fields in a specific class.
     * 
     * @param object the object instance
     * @param clazz the class to process
     */
    private static void processClassFields(Object object, Class<?> clazz) {
        for (java.lang.reflect.Field field : clazz.getDeclaredFields()) {
            Juke annotation = field.getAnnotation(Juke.class);
            if (annotation != null && annotation.autoWrap()) {
                try {
                    field.setAccessible(true);
                    Object fieldValue = field.get(object);
                    
                    if (fieldValue != null && field.getType().isInterface()) {
                        @SuppressWarnings("unchecked")
                        Object wrapped = wrap(fieldValue, (Class<Object>) field.getType(), 
                                            normalizeJukeState(annotation.value()));
                        field.set(object, wrapped);
                    }
                } catch (IllegalAccessException e) {
                    throw new JukeConfigurationException(
                            "Failed to initialize @Juke annotated field: " + field.getName(), e);
                }
            }
        }
    }
    
    /**
     * Helper method for constructor-based injection. Processes constructor parameters
     * and wraps those annotated with @Juke.
     * 
     * This is typically called from within a constructor after parameter assignment.
     * 
     * @param constructorClass the class containing the constructor
     * @param parameterValues the parameter values passed to the constructor
     * @return array of processed parameter values (some may be wrapped)
     */
    public static Object[] processConstructorParameters(Class<?> constructorClass, Object... parameterValues) {
        if (parameterValues == null || parameterValues.length == 0) {
            return parameterValues;
        }
        
        // Find the constructor that matches the parameter count
        Constructor<?> matchingConstructor = null;
        for (Constructor<?> constructor : constructorClass.getConstructors()) {
            if (constructor.getParameterCount() == parameterValues.length) {
                matchingConstructor = constructor;
                break;
            }
        }
        
        if (matchingConstructor == null) {
            return parameterValues;
        }
        
        Object[] processedValues = new Object[parameterValues.length];
        Parameter[] parameters = matchingConstructor.getParameters();
        
        for (int i = 0; i < parameters.length; i++) {
            Parameter param = parameters[i];
            Juke jukeAnnotation = param.getAnnotation(Juke.class);
            
            if (jukeAnnotation != null && jukeAnnotation.autoWrap() && 
                parameterValues[i] != null && param.getType().isInterface()) {
                
                @SuppressWarnings("unchecked")
                Object wrapped = wrap(parameterValues[i], (Class<Object>) param.getType(), 
                                    normalizeJukeState(jukeAnnotation.value()));
                processedValues[i] = wrapped;
            } else {
                processedValues[i] = parameterValues[i];
            }
        }
        
        return processedValues;
    }
}