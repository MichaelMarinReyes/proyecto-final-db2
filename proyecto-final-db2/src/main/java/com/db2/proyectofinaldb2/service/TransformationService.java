package com.db2.proyectofinaldb2.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.bson.Document;
import org.springframework.stereotype.Service;

/**
 * Applies the validation and transformation rules defined in docs (ETL rules).
 * All identifiers are in English; rejection reasons are shown to the user in
 * Spanish.
 */
@Service
public class TransformationService {

    public static final Set<String> REQUIRED_FIELDS =
            Set.of("correlativo", "materia", "anio_ingreso", "aprobacion");

    private static final Map<String, String> HEADER_ALIASES = buildAliases();
    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),
            DateTimeFormatter.ofPattern("d/M/yyyy"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
    );

    public Map<String, Integer> mapHeaders(String[] headers) {
        Map<String, Integer> indexes = new HashMap<>();
        for (int i = 0; i < headers.length; i++) {
            String canonical = HEADER_ALIASES.get(normalizeHeader(headers[i]));
            if (canonical != null && !indexes.containsKey(canonical)) {
                indexes.put(canonical, i);
            }
        }
        return indexes;
    }

    public TransformedRow transform(String[] row, Map<String, Integer> indexes, String fileName, int line) {
        String correlativo = clean(value(row, indexes, "correlativo"));
        if (correlativo.isBlank()) {
            throw new RowValidationException("correlativo_aspirante vacío");
        }

        String materia = clean(value(row, indexes, "materia")).toUpperCase(Locale.ROOT);
        if (materia.isBlank()) {
            throw new RowValidationException("materia vacía");
        }

        Integer anioIngreso = parseInt(value(row, indexes, "anio_ingreso"));
        if (anioIngreso == null || anioIngreso < 1990 || anioIngreso > 2100) {
            throw new RowValidationException("anio_de_ingreso inválido");
        }

        Integer oportunidad = parseInt(value(row, indexes, "oportunidad"));
        if (oportunidad == null || oportunidad < 1) {
            throw new RowValidationException("numero_de_fecha_de_evaluacion inválido");
        }

        String approvalText = clean(value(row, indexes, "aprobacion")).toUpperCase(Locale.ROOT);
        boolean approved;
        if (approvalText.contains("NO APROB")) {
            approved = false;
        } else if (approvalText.contains("APROB")) {
            approved = true;
        } else if (approvalText.equals("SI") || approvalText.equals("SÍ") || approvalText.equals("1")) {
            approved = true;
        } else if (approvalText.equals("NO") || approvalText.equals("0")) {
            approved = false;
        } else {
            throw new RowValidationException("aprobacion no reconocida: " + safe(approvalText));
        }

        Integer anioNacimiento = parseYear(value(row, indexes, "anio_nacimiento"));
        Integer edad = null;
        if (anioNacimiento != null) {
            int candidate = anioIngreso - anioNacimiento;
            if (candidate >= 10 && candidate <= 80) {
                edad = candidate;
            }
        }

        LocalDate fecha = parseDate(value(row, indexes, "fecha_asignacion"));
        String carrera = defaultIfBlank(clean(value(row, indexes, "carrera")), "NINGUNO").toUpperCase(Locale.ROOT);
        String departamento = defaultIfBlank(clean(value(row, indexes, "departamento")), "SIN REGISTRO")
                .toUpperCase(Locale.ROOT);
        String municipio = defaultIfBlank(clean(value(row, indexes, "municipio")), "SIN REGISTRO")
                .toUpperCase(Locale.ROOT);
        String tipo = normalizeInstitutionType(clean(value(row, indexes, "tipo")));
        String sexo = normalizeSex(clean(value(row, indexes, "sexo")));
        String approval = approved ? "APROBADO" : "NO APROBADO";

        Date fechaDate = fecha == null ? null
                : Date.from(fecha.atStartOfDay(ZoneOffset.UTC).toInstant());

        String hash = hash(correlativo, fecha, sexo, anioNacimiento, materia, oportunidad,
                anioIngreso, approval, carrera, departamento, municipio, tipo);

        Document mongo = new Document("correlativo_aspirante", correlativo)
                .append("fecha_asignacion", fechaDate)
                .append("anio_nacimiento", anioNacimiento)
                .append("edad_ingreso", edad)
                .append("sexo", sexo)
                .append("materia", materia)
                .append("oportunidad", oportunidad)
                .append("anio_ingreso", anioIngreso)
                .append("aprobacion", approval)
                .append("aprobado", approved)
                .append("carrera_objetivo", carrera)
                .append("departamento_ie", departamento)
                .append("municipio_ie", municipio)
                .append("tipo_ie", tipo)
                .append("archivo_origen", fileName)
                .append("hash_fila", hash);

        String evalKey = correlativo + "|" + materia + "|" + anioIngreso + "|" + oportunidad;
        String municipioKey = departamento + "|" + municipio;
        String institucionKey = tipo + "|" + municipio + "|" + departamento;

        Map<String, Object> graph = new HashMap<>();
        graph.put("correlativo", correlativo);
        graph.put("sexo", sexo);
        graph.put("anio_nacimiento", anioNacimiento);
        graph.put("materia", materia);
        graph.put("carrera", carrera);
        graph.put("anio_ingreso", anioIngreso);
        graph.put("oportunidad", oportunidad);
        graph.put("aprobacion", approval);
        graph.put("aprobado", approved);
        graph.put("fecha", fecha == null ? null : fecha.toString());
        graph.put("edad", edad);
        graph.put("departamento", departamento);
        graph.put("municipio", municipio);
        graph.put("tipo", tipo);
        graph.put("evalKey", evalKey);
        graph.put("municipioKey", municipioKey);
        graph.put("institucionKey", institucionKey);

        return new TransformedRow(mongo, graph, line);
    }

    public String safeRow(String[] row) {
        String joined = String.join(" | ", row);
        return joined.length() > 300 ? joined.substring(0, 300) + "..." : joined;
    }

    private String value(String[] row, Map<String, Integer> indexes, String field) {
        Integer index = indexes.get(field);
        if (index == null || index >= row.length) {
            return "";
        }
        return row[index] == null ? "" : row[index];
    }

    private String clean(String value) {
        return value == null ? "" : value.replace("\uFEFF", "").trim().replaceAll("\\s+", " ");
    }

    private String defaultIfBlank(String value, String fallback) {
        return value.isBlank() ? fallback : value;
    }

    private Integer parseInt(String value) {
        String cleaned = clean(value);
        if (cleaned.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(cleaned);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private Integer parseYear(String value) {
        Integer year = parseInt(value);
        int currentYear = LocalDate.now().getYear();
        if (year == null || year < 1900 || year > currentYear) {
            return null;
        }
        return year;
    }

    private LocalDate parseDate(String value) {
        String cleaned = clean(value);
        if (cleaned.isBlank()) {
            return null;
        }
        for (DateTimeFormatter formatter : DATE_FORMATS) {
            try {
                return LocalDate.parse(cleaned, formatter);
            } catch (Exception ignored) {
                // try the next format
            }
        }
        return null;
    }

    private String normalizeSex(String value) {
        String upper = stripAccents(value).toUpperCase(Locale.ROOT);
        if (upper.equals("M") || upper.startsWith("MASC")) {
            return "M";
        }
        if (upper.equals("F") || upper.startsWith("FEM")) {
            return "F";
        }
        return "DESCONOCIDO";
    }

    private String normalizeInstitutionType(String value) {
        String upper = stripAccents(value).toUpperCase(Locale.ROOT);
        if (upper.isBlank()) {
            return "SIN REGISTRO";
        }
        if (upper.contains("PUBLIC") || upper.contains("MUNICIPAL")
                || upper.contains("OFICIAL") || upper.contains("ESTATAL")) {
            return "PUBLICO";
        }
        if (upper.contains("PRIVAD") || upper.contains("PARTICULAR")) {
            return "PRIVADO";
        }
        return upper;
    }

    private String stripAccents(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
    }

    private String normalizeHeader(String header) {
        String withoutAccents = Normalizer.normalize(header, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return withoutAccents.toLowerCase(Locale.ROOT)
                .replace("\uFEFF", "")
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
    }

    private static Map<String, String> buildAliases() {
        Map<String, String> aliases = new HashMap<>();
        register(aliases, "correlativo", "correlativo_aspirante", "correlativo",
                "correlativo_del_aspirante", "correlativo_estudiante", "no_correlativo");
        register(aliases, "fecha_asignacion", "fecha_asignacion", "fecha_de_asignacion",
                "fecha_asignacion_prueba", "fecha_evaluacion", "fecha_de_evaluacion");
        register(aliases, "sexo", "sexo", "genero");
        register(aliases, "anio_nacimiento", "anio_nacimiento", "ano_nacimiento",
                "anio_de_nacimiento", "ano_de_nacimiento");
        register(aliases, "materia", "materia", "materia_evaluada", "curso");
        register(aliases, "oportunidad", "numero_de_fecha_de_evaluacion",
                "numero_fecha_de_evaluacion", "numero_de_fecha_evaluacion",
                "no_de_fecha_de_evaluacion", "numero_de_evaluacion",
                "numero_de_intento", "oportunidad");
        register(aliases, "anio_ingreso", "anio_de_ingreso", "ano_de_ingreso",
                "anio_ingreso", "ano_ingreso");
        register(aliases, "aprobacion", "aprobacion", "resultado", "resultado_evaluacion");
        register(aliases, "carrera", "carrera_objetivo", "carrera_a_la_que_aspira",
                "carrera_solicitada");
        register(aliases, "departamento", "departamento_institucion_educativa",
                "departamento_de_la_institucion_educativa", "departamento_ie",
                "departamento_del_establecimiento");
        register(aliases, "municipio", "municipio_institucion_educativa",
                "municipio_de_la_institucion_educativa", "municipio_ie",
                "municipio_del_establecimiento");
        register(aliases, "tipo", "tipo_institucion_educativa",
                "tipo_de_institucion_educativa", "tipo_ie", "sector");
        return aliases;
    }

    private static void register(Map<String, String> aliases, String canonical, String... variants) {
        Arrays.stream(variants).forEach(variant -> aliases.put(variant, canonical));
    }

    private String hash(Object... values) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String joined = String.join("|", Arrays.stream(values)
                    .map(value -> value == null ? "" : value.toString())
                    .toList());
            return HexFormat.of().formatHex(digest.digest(joined.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("No se pudo calcular el hash de la fila", exception);
        }
    }

    private String safe(String value) {
        if (value == null) {
            return "";
        }
        return value.length() > 60 ? value.substring(0, 60) : value;
    }

    public record TransformedRow(Document mongo, Map<String, Object> graph, int line) {
    }

    public static class RowValidationException extends RuntimeException {
        public RowValidationException(String message) {
            super(message);
        }
    }
}
