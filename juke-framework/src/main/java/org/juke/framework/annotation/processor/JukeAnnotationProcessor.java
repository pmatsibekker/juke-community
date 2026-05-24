package org.juke.framework.annotation.processor;

import org.juke.framework.annotation.Juke;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.Writer;
import java.util.Set;

/**
 * Annotation processor for the {@link Juke} annotation.
 * This processor generates code for fields and methods annotated with {@link Juke}
 * to wrap them with a JukeFactory instance.
 */
@SupportedAnnotationTypes("org.juke.framework.annotation.Juke")
public class JukeAnnotationProcessor extends AbstractProcessor {

    private Types typeUtils;
    private Elements elementUtils;
    private Filer filer;
    private Messager messager;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.typeUtils = processingEnv.getTypeUtils();
        this.elementUtils = processingEnv.getElementUtils();
        this.filer = processingEnv.getFiler();
        this.messager = processingEnv.getMessager();
    }

    /**
     * Always reports the latest source version supported by the running JDK so
     * this processor never triggers the "Supported source version … less than …"
     * internal compiler error when compiling with Java 22+.
     */
    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (annotations.isEmpty()) {
            return false;
        }

        for (Element element : roundEnv.getElementsAnnotatedWith(Juke.class)) {
            if (element.getKind() != ElementKind.FIELD && 
                element.getKind() != ElementKind.METHOD &&
                element.getKind() != ElementKind.PARAMETER) {
                error(element, "Only fields, methods and parameters can be annotated with @Juke");
                continue;
            }
            
            // For parameters, we don't need to generate compile-time code
            // They'll be handled at runtime by JukeInitializer
            if (element.getKind() == ElementKind.PARAMETER) {
                continue; // Skip parameters - they're handled at runtime
            }

            // Get the enclosing class
            TypeElement typeElement = (TypeElement) element.getEnclosingElement();
            
            try {
                processElement(element, typeElement);
            } catch (Exception e) {
                // Include the full stack trace in the messager note so compile logs
                // still capture diagnostic detail without polluting stderr.
                java.io.StringWriter sw = new java.io.StringWriter();
                e.printStackTrace(new java.io.PrintWriter(sw));
                error(element, "Error processing @Juke annotation: " + e.getMessage());
                messager.printMessage(Diagnostic.Kind.NOTE, "Juke processor stack trace:\n" + sw);
            }
        }
        
        return true;
    }

    private void processElement(Element element, TypeElement typeElement) throws IOException {
        String jukeState = element.getAnnotation(Juke.class).value();
        
        // Generate unique class name
        String packageName = elementUtils.getPackageOf(typeElement).getQualifiedName().toString();
        String className = typeElement.getSimpleName() + "_Juke_" + element.getSimpleName();
        String fullClassName = packageName + "." + className;
        
        // Create source file
        JavaFileObject jfo = filer.createSourceFile(fullClassName, element);
        
        try (Writer writer = jfo.openWriter()) {
            if (element.getKind() == ElementKind.FIELD) {
                generateJukeFieldWrapper(writer, packageName, className, typeElement, (VariableElement) element, jukeState);
            } else if (element.getKind() == ElementKind.METHOD) {
                generateJukeMethodWrapper(writer, packageName, className, typeElement, (ExecutableElement) element, jukeState);
            }
        }
    }
    
    private void generateJukeFieldWrapper(Writer writer, String packageName, String className, 
                                        TypeElement typeElement, VariableElement fieldElement, String jukeState) throws IOException {
        // Get field type
        TypeMirror fieldType = fieldElement.asType();
        String fieldName = fieldElement.getSimpleName().toString();
        
        // Check if the field type is an interface
        if (fieldType.getKind() != TypeKind.DECLARED) {
            error(fieldElement, "Field must be an interface type");
            return;
        }
        
        TypeElement typeFieldElement = (TypeElement) ((DeclaredType) fieldType).asElement();
        if (typeFieldElement.getKind() != ElementKind.INTERFACE) {
            error(fieldElement, "Field must be an interface type");
            return;
        }
        String interfaceType = typeFieldElement.getQualifiedName().toString();
        // Generate the wrapper class
        writer.write("package " + packageName + ";\n\n");
        writer.write("import org.juke.framework.proxy.JukeFactory;\n");
        writer.write("import org.juke.framework.proxy.JukeState;\n");
        writer.write("import " + interfaceType + ";\n");
        writer.write("import java.lang.reflect.Field;\n\n");
        writer.write("/**\n");
        writer.write(" * Generated wrapper for @Juke annotated field.\n");
        writer.write(" */\n");
        writer.write("public class " + className + " {\n");
        writer.write("    /**\n");
        writer.write("     * Wraps the field with JukeFactory.\n");
        writer.write("     * @param instance Instance containing the field\n");
        writer.write("     */\n");
        writer.write("    public static void wrapField(" + typeElement.getQualifiedName() + " instance) {\n");
        writer.write("        try {\n");
        writer.write("            Field field = " + typeElement.getQualifiedName() + ".class.getDeclaredField(\"" + fieldName + "\");\n");
        writer.write("            field.setAccessible(true);\n");
        writer.write("            Object original = field.get(instance);\n");
        writer.write("            if (original != null) {\n");
        writer.write("                Object wrapped = new JukeFactory<" + interfaceType + ">().newInstance((" + interfaceType + ") original, " + interfaceType + ".class, JukeState." + jukeState.toUpperCase() + ");\n");
        writer.write("                field.set(instance, wrapped);\n");
        writer.write("            }\n");
        writer.write("        } catch (Exception e) {\n");
        writer.write("            throw new RuntimeException(e);\n");
        writer.write("        }\n");
        writer.write("    }\n");
        writer.write("}\n");
    }
    
    private void generateJukeMethodWrapper(Writer writer, String packageName, String className, 
                                         TypeElement typeElement, ExecutableElement methodElement, String jukeState) throws IOException {
        // Get method return type
        TypeMirror returnType = methodElement.getReturnType();
        String methodName = methodElement.getSimpleName().toString();
        
        // Check if the return type is an interface
        if (returnType.getKind() != TypeKind.DECLARED) {
            error(methodElement, "Method return type must be an interface");
            return;
        }
        
        TypeElement typeReturnElement = (TypeElement) ((DeclaredType) returnType).asElement();
        if (typeReturnElement.getKind() != ElementKind.INTERFACE) {
            error(methodElement, "Method return type must be an interface");
            return;
        }
        
        String interfaceType = typeReturnElement.getQualifiedName().toString();
        
        // Generate the wrapper class
        writer.write("package " + packageName + ";\n\n");
        writer.write("import org.juke.framework.proxy.JukeFactory;\n");
        writer.write("import org.juke.framework.proxy.JukeState;\n");
        writer.write("import " + interfaceType + ";\n\n");
        
        writer.write("/**\n");
        writer.write(" * Generated wrapper for @Juke annotated method.\n");
        writer.write(" */\n");
        writer.write("public class " + className + " {\n");
        writer.write("    /**\n");
        writer.write("     * Wraps the return value of the method with JukeFactory.\n");
        writer.write("     * @param result The original return value\n");
        writer.write("     * @return The wrapped return value\n");
        writer.write("     */\n");
        writer.write("    public static " + interfaceType + " wrapResult(" + interfaceType + " result) {\n");
        writer.write("        if (result != null) {\n");
        writer.write("            return new JukeFactory<" + interfaceType + ">().newInstance(\n");
        writer.write("                result,\n");
        writer.write("                " + interfaceType + ".class,\n");
        writer.write("                JukeState." + jukeState.toUpperCase() + ");\n");
        writer.write("        }\n");
        writer.write("        return result;\n");
        writer.write("    }\n");
        writer.write("}\n");
    }

    private void error(Element element, String message) {
        messager.printMessage(Diagnostic.Kind.ERROR, message, element);
    }
}