package com.db2.proyectofinaldb2.service;

import com.db2.proyectofinaldb2.exception.ApiException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Locale;
import java.util.regex.Pattern;
import org.bson.Document;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Restricts administrator-authored queries to application data, not server administration. */
@Component
public class CustomQueryPolicy {
    private static final Set<String> COLLECTIONS = Set.of("evaluaciones", "aspirantes", "resumen_carrera");
    private static final Set<String> OPERATIONS = Set.of("find", "aggregate", "insertMany", "updateMany", "deleteMany");
    private static final Pattern FORBIDDEN_CYPHER = Pattern.compile(
            "\\b(CALL|LOAD|USE|SHOW|GRANT|DENY|REVOKE|ALTER|DROP|START|STOP|TERMINATE|DATABASE|DATABASES|CONSTRAINT|INDEX|PASSWORD|ROLE|USER|SERVER)\\b");
    private static final Pattern CYPHER_WRITE = Pattern.compile("\\b(CREATE|INSERT|MERGE|SET|DELETE|REMOVE|FOREACH)\\b");

    public String engine(String value) {
        String engine = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("MONGODB", "NEO4J").contains(engine)) {
            throw invalid("Seleccione MongoDB o Neo4j");
        }
        return engine;
    }

    public boolean writes(String engine, String code) {
        if (code == null || code.isBlank() || code.length() > 20000) {
            throw invalid("La consulta debe contener entre 1 y 20000 caracteres");
        }
        if (engine(engine).equals("MONGODB")) {
            Document command = mongo(code);
            return !Set.of("find", "aggregate").contains(command.getString("operacion"))
                    || containsWriteStage(command);
        }
        String tokens = cypherTokens(code).toUpperCase(Locale.ROOT);
        if (tokens.contains(";") || FORBIDDEN_CYPHER.matcher(tokens).find()) {
            throw invalid("Cypher admite una sola consulta de datos, sin procedimientos ni administración del servidor");
        }
        return CYPHER_WRITE.matcher(tokens).find();
    }

    public Document mongo(String code) {
        Document command;
        try {
            command = Document.parse(code);
        } catch (RuntimeException exception) {
            throw invalid("El código MongoDB debe ser un objeto JSON válido, no JavaScript de mongosh");
        }
        if (command == null || !(command.get("operacion") instanceof String)
                || !OPERATIONS.contains(command.get("operacion"))) {
            throw invalid("Operación válida: find, aggregate, insertMany, updateMany o deleteMany");
        }
        collection(command.get("coleccion"));
        validateTree(command);
        String operation = command.getString("operacion");
        if (Set.of("find", "updateMany", "deleteMany").contains(operation)
                && !(command.get("filtro") instanceof Document)) {
            throw invalid("Indique filtro como objeto JSON; use {} explícitamente para todos los documentos");
        }
        if (operation.equals("aggregate")) {
            if (!(command.get("pipeline") instanceof List<?> pipeline) || pipeline.isEmpty()
                    || pipeline.stream().anyMatch(stage -> !(stage instanceof Document doc) || doc.size() != 1)) {
                throw invalid("pipeline debe ser un arreglo no vacío de etapas JSON");
            }
        }
        if (operation.equals("insertMany") && (!(command.get("documentos") instanceof List<?> documents)
                || documents.isEmpty() || documents.size() > 1000
                || documents.stream().anyMatch(document -> !(document instanceof Document)))) {
            throw invalid("documentos debe contener entre 1 y 1000 objetos JSON");
        }
        if (operation.equals("updateMany")) {
            if (!(command.get("actualizacion") instanceof Document update) || update.isEmpty()
                    || update.keySet().stream().anyMatch(key -> !Set.of("$set", "$unset", "$inc", "$mul", "$min", "$max",
                            "$rename", "$push", "$pull", "$addToSet", "$pop", "$currentDate").contains(key))) {
                throw invalid("actualizacion debe usar operadores de actualización permitidos, como $set o $inc");
            }
        }
        return command;
    }

    private void collection(Object name) {
        if (!(name instanceof String) || !COLLECTIONS.contains(name)) {
            throw invalid("Solo se permiten las colecciones evaluaciones, aspirantes y resumen_carrera");
        }
    }

    private void validateTree(Object value) {
        if (value instanceof Map<?, ?> map) {
            map.forEach((key, child) -> {
                if (Set.of("$where", "$function", "$accumulator", "$changeStream", "$currentOp").contains(key)) {
                    throw invalid("No se permite ejecutar JavaScript ni consultar el servidor desde una consulta guardada");
                }
                if ("$out".equals(key)) collection(child);
                if ("$merge".equals(key)) collection(child instanceof Document doc ? doc.get("into") : child);
                if ("$lookup".equals(key) || "$graphLookup".equals(key)) {
                    if (!(child instanceof Document doc)) throw invalid("Etapa de unión inválida");
                    collection(((Document) child).get("from"));
                }
                if ("$unionWith".equals(key)) collection(child instanceof Document doc ? doc.get("coll") : child);
                validateTree(child);
            });
        } else if (value instanceof List<?> list) list.forEach(this::validateTree);
    }

    private boolean containsWriteStage(Object value) {
        if (value instanceof Map<?, ?> map) {
            return map.containsKey("$out") || map.containsKey("$merge") || map.values().stream().anyMatch(this::containsWriteStage);
        }
        return value instanceof List<?> list && list.stream().anyMatch(this::containsWriteStage);
    }

    // Ignore strings and comments while retaining executable keywords and statement separators.
    private String cypherTokens(String code) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < code.length();) {
            char character = code.charAt(i);
            if (character == '\'' || character == '"' || character == '`') {
                char quote = character;
                i++;
                boolean closed = false;
                while (i < code.length()) {
                    char next = code.charAt(i++);
                    if (next == '\\' && i < code.length()) { i++; continue; }
                    if (next == quote) {
                        if (i < code.length() && code.charAt(i) == quote) { i++; continue; }
                        closed = true;
                        break;
                    }
                }
                if (!closed) throw invalid("La consulta contiene una cadena o identificador sin cerrar");
                result.append(' ');
            } else if (code.startsWith("//", i)) {
                while (i < code.length() && "\n\r\u0085\u2028\u2029".indexOf(code.charAt(i)) < 0) i++;
                result.append(' ');
            } else if (code.startsWith("/*", i)) {
                int depth = 1;
                i += 2;
                while (i < code.length() && depth > 0) {
                    if (code.startsWith("/*", i)) { depth++; i += 2; }
                    else if (code.startsWith("*/", i)) { depth--; i += 2; }
                    else i++;
                }
                if (depth != 0) throw invalid("La consulta contiene un comentario sin cerrar");
                result.append(' ');
            } else {
                result.append(character);
                i++;
            }
        }
        return result.toString();
    }

    private ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }
}
