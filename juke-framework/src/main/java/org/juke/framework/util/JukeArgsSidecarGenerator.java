package org.juke.framework.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.juke.framework.storage.InputArgsRecord;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Utility to generate .args.json sidecar files for legacy Juke zip recordings.
 * For each response entry (e.g., OrderService.bill.1.json), generates a
 * corresponding OrderService.bill.1.args.json with placeholder argument info.
 */
public class JukeArgsSidecarGenerator {
    public static void main(String[] args) throws Exception {
        System.out.println("JukeArgsSidecarGenerator started");
        if (args.length != 1) {
            System.err.println("Usage: java JukeArgsSidecarGenerator <juke-zip-file>");
            args= new String[]{"C:\\Users\\pmats\\juke\\juke\\juke-remix-rest-service\\src\\test\\resources\\juketest2.zip"};
        }
        File zipFile = new File(args[0]);
        if (!zipFile.exists()) {
            System.err.println("File not found: " + zipFile);
            System.exit(2);
        }
        File tempZip = File.createTempFile("juke-args-patch", ".zip");
        generateArgsSidecars(zipFile, tempZip);
        Files.move(tempZip.toPath(), zipFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        System.out.println(".args.json sidecars generated for: " + zipFile.getAbsolutePath());
    }

    /**
     * Parse juke.json to build a map of (className, methodName) -> list of parameter type classNames.
     */
    private static Map<String, Map<String, List<String>>> parseJukeMetadata(ZipFile zip, ObjectMapper mapper) throws IOException {
        Map<String, Map<String, List<String>>> metadata = new HashMap<>();
        ZipEntry jukeEntry = zip.getEntry("juke.json");
        if (jukeEntry == null) {
            System.out.println("No juke.json found in zip, will use empty parameter types");
            return metadata;
        }
        try (InputStream is = zip.getInputStream(jukeEntry)) {
            JsonNode root = mapper.readTree(is);
            Iterator<Map.Entry<String, JsonNode>> services = root.fields();
            while (services.hasNext()) {
                Map.Entry<String, JsonNode> svc = services.next();
                String className = svc.getValue().path("className").asText();
                Map<String, List<String>> methodMap = new HashMap<>();
                JsonNode methods = svc.getValue().path("methods");
                if (methods.isArray()) {
                    for (JsonNode m : methods) {
                        String methodName = m.path("method").asText();
                        List<String> paramTypes = new ArrayList<>();
                        JsonNode inputParams = m.path("inputParameters");
                        if (inputParams.isArray()) {
                            for (JsonNode p : inputParams) {
                                paramTypes.add(p.path("className").asText());
                            }
                        }
                        methodMap.put(methodName, paramTypes);
                    }
                }
                metadata.put(className, methodMap);
            }
        }
        return metadata;
    }

    /**
     * Return a placeholder value appropriate for the given type name.
     */
    private static Object placeholderForType(String typeName) {
        switch (typeName) {
            case "java.lang.String": return "";
            case "int": case "java.lang.Integer": return 0;
            case "long": case "java.lang.Long": return 0L;
            case "double": case "java.lang.Double": return 0.0;
            case "float": case "java.lang.Float": return 0.0f;
            case "boolean": case "java.lang.Boolean": return false;
            default: return null;
        }
    }

    /**
     * Parse a response entry name like "com.example.IGreetingsService.$greeting.1.json"
     * into [className, methodName]. The method is prefixed with '$' in the entry name.
     */
    private static String[] parseEntryClassAndMethod(String entryName) {
        // Strip .json
        String base = entryName.substring(0, entryName.length() - ".json".length());
        // Split on '.'
        String[] parts = base.split("\\.");
        // Walk from the end: last part is the sequence number, before that is $methodName
        // Everything before $methodName is the fully-qualified class name
        if (parts.length < 3) {
            return new String[]{"unknown", "unknown"};
        }
        // sequence is parts[parts.length - 1]
        String methodPart = parts[parts.length - 2]; // e.g. "$greeting"
        String method = methodPart.startsWith("$") ? methodPart.substring(1) : methodPart;
        // Class name is everything before the method part
        StringBuilder className = new StringBuilder();
        for (int i = 0; i < parts.length - 2; i++) {
            if (className.length() > 0) className.append(".");
            className.append(parts[i]);
        }
        return new String[]{className.toString(), method};
    }

    public static void generateArgsSidecars(File inputZip, File outputZip) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        System.out.println("Opening zip: " + inputZip.getAbsolutePath());
        try (ZipFile zip = new ZipFile(inputZip);
             ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(outputZip))) {

            // Parse juke.json for parameter type metadata
            Map<String, Map<String, List<String>>> metadata = parseJukeMetadata(zip, mapper);

            Enumeration<? extends ZipEntry> entries = zip.entries();
            Set<String> toPatch = new HashSet<>();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                System.out.println("Processing entry: " + entry.getName());
                zos.putNextEntry(new ZipEntry(entry.getName()));
                try (InputStream is = zip.getInputStream(entry)) {
                    is.transferTo(zos);
                }
                zos.closeEntry();
                // Identify response entries (exclude .type., .args., juke.json, etc.)
                String name = entry.getName();
                if (name.endsWith(".json") && !name.contains(".type.") && !name.contains(".args.") && !name.contains("juke")) {
                    toPatch.add(name);
                }
            }
            // For each response entry, add a .args.json sidecar if missing
            for (String resp : toPatch) {
                String base = resp.substring(0, resp.length() - ".json".length());
                String argsName = base + ".args.json";
                if (zip.getEntry(argsName) == null) {
                    System.out.println("Generating sidecar: " + argsName);

                    String[] classAndMethod = parseEntryClassAndMethod(resp);
                    String className = classAndMethod[0];
                    String method = classAndMethod[1];

                    // Look up parameter types from juke.json metadata
                    List<String> paramTypes = Collections.emptyList();
                    Map<String, List<String>> methodMap = metadata.get(className);
                    if (methodMap != null && methodMap.containsKey(method)) {
                        paramTypes = methodMap.get(method);
                    }

                    // Generate placeholder arguments matching the parameter types
                    List<Object> arguments = new ArrayList<>();
                    for (String pt : paramTypes) {
                        arguments.add(placeholderForType(pt));
                    }

                    InputArgsRecord record = new InputArgsRecord(method, paramTypes, arguments);
                    zos.putNextEntry(new ZipEntry(argsName));
                    OutputStream noClose = new FilterOutputStream(zos) {
                        @Override public void close() throws IOException { /* do nothing */ }
                    };
                    mapper.writerWithDefaultPrettyPrinter().writeValue(noClose, record);
                    zos.closeEntry();
                    System.out.println("Generated: " + argsName);
                } else {
                    System.out.println("Sidecar already exists, skipping: " + argsName);
                }
            }
        }
    }
}
