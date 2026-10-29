package com.db2.proyectofinaldb2.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.bson.Document;
import org.junit.jupiter.api.Test;

class TransformationServiceTest {

    @Test
    void rejectsOversizedOrControlCharacterValues() {
        var service = new TransformationService();
        var indexes = service.mapHeaders(new String[]{"correlativo", "materia", "oportunidad", "anio_ingreso", "aprobacion"});
        for (String value : new String[]{"X".repeat(4097), "A\u0000B"}) {
            assertThrows(TransformationService.RowValidationException.class,
                    () -> service.transform(new String[]{value, "FISICA", "1", "2024", "SI"}, indexes, "a.csv", 2));
        }
    }

    private final TransformationService service = new TransformationService();

    private static final String[] HEADERS = {
            "Correlativo aspirante",
            "Fecha asignación",
            "Sexo",
            "Año nacimiento",
            "Materia",
            "Número de fecha de evaluación",
            "Año de ingreso",
            "Aprobación",
            "Carrera objetivo",
            "Departamento institución educativa",
            "Municipio institución educativa",
            "Tipo institución educativa"
    };

    @Test
    void mapsAccentedHeadersAndCommonAliases() {
        Map<String, Integer> indexes = service.mapHeaders(HEADERS);

        assertEquals(0, indexes.get("correlativo"));
        assertEquals(1, indexes.get("fecha_asignacion"));
        assertEquals(5, indexes.get("oportunidad"));
        assertEquals(6, indexes.get("anio_ingreso"));
        assertEquals(11, indexes.get("tipo"));

        Map<String, Integer> aliases = service.mapHeaders(new String[]{
                "Correlativo del aspirante", "Fecha de evaluación", "Género",
                "Año de nacimiento", "Materia evaluada", "Número de intento",
                "Año ingreso", "Resultado", "Carrera a la que aspira",
                "Departamento de la institución educativa",
                "Municipio de la institución educativa",
                "Tipo de institución educativa"
        });
        assertEquals(0, aliases.get("correlativo"));
        assertEquals(3, aliases.get("anio_nacimiento"));
        assertEquals(5, aliases.get("oportunidad"));
        assertEquals(7, aliases.get("aprobacion"));
        assertEquals(11, aliases.get("tipo"));
    }

    @Test
    void transformsValidRowIntoMongoAndGraphDocuments() {
        Map<String, Integer> indexes = service.mapHeaders(HEADERS);
        String[] row = {
                "A1B2C3D4", "15/01/2018", "F", "2000", "matematica", "2",
                "2018", "APROBADO", "Ingeniería en Sistemas", "Guatemala",
                "Mixco", "Pública"
        };

        TransformationService.TransformedRow transformed = service.transform(row, indexes, "datos.csv", 2);
        Document mongo = transformed.mongo();

        assertEquals("A1B2C3D4", mongo.getString("correlativo_aspirante"));
        assertEquals("MATEMATICA", mongo.getString("materia"));
        assertEquals("APROBADO", mongo.getString("aprobacion"));
        assertEquals(true, mongo.getBoolean("aprobado"));
        assertEquals(18, mongo.getInteger("edad_ingreso"));
        assertEquals("F", mongo.getString("sexo"));
        assertEquals("INGENIERÍA EN SISTEMAS", mongo.getString("carrera_objetivo"));
        assertEquals("PUBLICO", mongo.getString("tipo_ie"));
        assertNotNull(mongo.get("fecha_asignacion"));
        assertEquals(64, mongo.getString("hash_fila").length());

        assertEquals("A1B2C3D4|MATEMATICA|2018|2", transformed.graph().get("evalKey"));
        assertEquals("PUBLICO|MIXCO|GUATEMALA", transformed.graph().get("institucionKey"));
    }

    @Test
    void acceptsAlternativeApprovalAndSexValues() {
        Map<String, Integer> indexes = service.mapHeaders(HEADERS);
        String[] row = {
                "B2C3D4E5", "2018-01-16", "Masculino", "1999", "FISICA", "1",
                "2018", "NO", "Ingeniería Civil", "Guatemala", "Guatemala", "Privada"
        };

        Document mongo = service.transform(row, indexes, "datos.csv", 3).mongo();

        assertEquals("M", mongo.getString("sexo"));
        assertEquals("NO APROBADO", mongo.getString("aprobacion"));
        assertEquals(false, mongo.getBoolean("aprobado"));
        assertEquals("PRIVADO", mongo.getString("tipo_ie"));
    }

    @Test
    void rejectsRowsWithoutRequiredValues() {
        Map<String, Integer> indexes = service.mapHeaders(HEADERS);
        String[] withoutSubject = {
                "C3D4E5F6", "2018-01-16", "M", "1999", " ", "1",
                "2018", "APROBADO", "MEDICINA", "Guatemala", "Guatemala", "Pública"
        };
        String[] withoutCorrelative = {
                " ", "2018-01-16", "M", "1999", "FISICA", "1",
                "2018", "APROBADO", "MEDICINA", "Guatemala", "Guatemala", "Pública"
        };
        String[] unknownApproval = {
                "C3D4E5F6", "2018-01-16", "M", "1999", "FISICA", "1",
                "2018", "PENDIENTE", "MEDICINA", "Guatemala", "Guatemala", "Pública"
        };

        assertTrue(assertThrows(TransformationService.RowValidationException.class,
                () -> service.transform(withoutSubject, indexes, "datos.csv", 4)).getMessage()
                .contains("materia vacía"));
        assertTrue(assertThrows(TransformationService.RowValidationException.class,
                () -> service.transform(withoutCorrelative, indexes, "datos.csv", 5)).getMessage()
                .contains("correlativo"));
        assertTrue(assertThrows(TransformationService.RowValidationException.class,
                () -> service.transform(unknownApproval, indexes, "datos.csv", 6)).getMessage()
                .contains("aprobacion no reconocida"));
    }

    @Test
    void keepsMissingOptionalValuesExplicit() {
        Map<String, Integer> indexes = service.mapHeaders(HEADERS);
        String[] row = {
                "D4E5F6G7", "", "X", "1800", "QUIMICA", "1",
                "2018", "0", "", "", "", ""
        };

        Document mongo = service.transform(row, indexes, "datos.csv", 7).mongo();

        assertEquals("DESCONOCIDO", mongo.getString("sexo"));
        assertEquals("NINGUNO", mongo.getString("carrera_objetivo"));
        assertEquals("SIN REGISTRO", mongo.getString("departamento_ie"));
        assertEquals("SIN REGISTRO", mongo.getString("tipo_ie"));
        assertEquals(null, mongo.get("anio_nacimiento"));
        assertEquals(null, mongo.get("edad_ingreso"));
    }

    @Test
    void rejectsAmbiguousApprovalSubstringsAndAcceptsExplicitSeparators() {
        Map<String, Integer> indexes = service.mapHeaders(HEADERS);
        String[] row = {"A", "2018-01-15", "M", "2000", "MATEMATICA", "1", "2018",
                "APROBADO", "MEDICINA", "GUATEMALA", "MIXCO", "PUBLICO"};
        for (String invalid : new String[]{"DESAPROBADO", "APROBACION PENDIENTE", "APROBADO PARCIAL", "NO APROBADO PENDIENTE"}) {
            row[7] = invalid;
            assertThrows(TransformationService.RowValidationException.class,
                    () -> service.transform(row, indexes, "prueba.csv", 2));
        }
        for (String negative : new String[]{"NO_APROBADO", "no-aprobado", "NO", "0"}) {
            row[7] = negative;
            assertEquals(false, service.transform(row, indexes, "prueba.csv", 2).mongo().getBoolean("aprobado"));
        }
        row[7] = "sí";
        assertEquals(true, service.transform(row, indexes, "prueba.csv", 2).mongo().getBoolean("aprobado"));
    }

    @Test
    void keepsImpossibleCalendarDatesNullInsteadOfAdjustingThem() {
        String[] row = {"A", "31/02/2024", "M", "2000", "MATEMATICA", "1", "2024",
                "APROBADO", "MEDICINA", "GUATEMALA", "MIXCO", "PUBLICO"};
        assertEquals(null, service.transform(row, service.mapHeaders(HEADERS), "prueba.csv", 2).mongo().get("fecha_asignacion"));
        row[1] = "29/02/2024";
        assertNotNull(service.transform(row, service.mapHeaders(HEADERS), "prueba.csv", 2).mongo().get("fecha_asignacion"));
    }

    @Test
    void preservesPreprocessorProvenanceButRecalculatesDerivedFields() {
        String[] headers = java.util.Arrays.copyOf(HEADERS, HEADERS.length + 5);
        System.arraycopy(new String[]{"archivo_origen", "fila_origen", "hash_fila", "aprobado", "edad_ingreso"}, 0,
                headers, HEADERS.length, 5);
        String[] row = {"A", "2018-01-15", "M", "2000", "MATEMATICA", "1", "2018",
                "APROBADO", "MEDICINA", "GUATEMALA", "MIXCO", "PUBLICO", "original.csv", "42", "forjado", "false", "99"};
        Document result = service.transform(row, service.mapHeaders(headers), "normalizados.csv", 2).mongo();
        assertEquals("original.csv", result.getString("archivo_origen"));
        assertEquals("normalizados.csv", result.getString("archivo_carga"));
        assertEquals(42, result.getInteger("fila_origen"));
        assertEquals(true, result.getBoolean("aprobado"));
        assertEquals(18, result.getInteger("edad_ingreso"));
        assertEquals(64, result.getString("hash_fila").length());
    }
}
