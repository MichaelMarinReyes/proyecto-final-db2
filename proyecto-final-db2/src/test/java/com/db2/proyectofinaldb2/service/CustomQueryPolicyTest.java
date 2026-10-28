package com.db2.proyectofinaldb2.service;

import static org.junit.jupiter.api.Assertions.*;
import com.db2.proyectofinaldb2.exception.ApiException;
import org.junit.jupiter.api.Test;

class CustomQueryPolicyTest {
    private final CustomQueryPolicy policy = new CustomQueryPolicy();

    @Test
    void acceptsMongoReadsAndDetectsWrites() {
        assertFalse(policy.writes("MONGODB", """
                {"operacion":"find","coleccion":"evaluaciones","filtro":{}}
                """));
        assertTrue(policy.writes("MONGODB", """
                {"operacion":"updateMany","coleccion":"evaluaciones","filtro":{},"actualizacion":{"$set":{"aprobado":true}}}
                """));
        assertTrue(policy.writes("MONGODB", """
                {"operacion":"aggregate","coleccion":"evaluaciones","pipeline":[{"$match":{}},{"$out":"resumen_carrera"}]}
                """));
    }

    @Test
    void rejectsMongoAdministrativeCollectionsAndCrossDatabaseOutput() {
        for (String code : new String[]{
                "{\"operacion\":\"find\",\"coleccion\":\"consultas_personalizadas\",\"filtro\":{}}",
                "{\"operacion\":\"aggregate\",\"coleccion\":\"evaluaciones\",\"pipeline\":[{\"$out\":{\"db\":\"admin\",\"coll\":\"secret\"}}]}",
                "{\"operacion\":\"aggregate\",\"coleccion\":\"evaluaciones\",\"pipeline\":[{\"$lookup\":{\"from\":\"system.users\",\"as\":\"x\"}}]}"
        }) assertThrows(ApiException.class, () -> policy.writes("MONGODB", code));
    }

    @Test
    void rejectsInvalidMongoStructuresAndJavaScript() {
        for (String code : new String[]{"{}", "null", "[]", "db.evaluaciones.find({})",
                "{\"operacion\":\"deleteMany\",\"coleccion\":\"evaluaciones\"}",
                "{\"operacion\":\"find\",\"coleccion\":\"evaluaciones\",\"filtro\":{\"$where\":\"true\"}}",
                "{\"operacion\":\"aggregate\",\"coleccion\":\"evaluaciones\",\"pipeline\":[]}"
        }) assertThrows(ApiException.class, () -> policy.writes("MONGODB", code));
    }

    @Test
    void detectsCypherWritesWithoutConfusingLiteralsOrComments() {
        assertFalse(policy.writes("NEO4J", "RETURN 'CREATE; DELETE' AS texto // MERGE\n"));
        assertFalse(policy.writes("NEO4J", "/* CREATE */ MATCH (a:Aspirante) RETURN count(a)"));
        assertTrue(policy.writes("NEO4J", "CREATE (:Aspirante {correlativo:'nuevo'})"));
        assertTrue(policy.writes("NEO4J", "MATCH (a:Aspirante) DETACH DELETE a"));
        assertTrue(policy.writes("NEO4J", "INSERT (:Aspirante {correlativo:'nuevo'})"));
        assertTrue(policy.writes("NEO4J", "// comentario\rCREATE (:Aspirante)"));
        assertTrue(policy.writes("NEO4J", "/* exterior /* interior */ // aún comentado */ CREATE (:Aspirante)"));
    }

    @Test
    void rejectsCypherProceduresAdministrativeCommandsAndMultipleStatements() {
        for (String code : new String[]{"CALL dbms.components()", "CALL `db`.`labels`()",
                "LOAD CSV FROM 'https://example.com' AS row RETURN row", "RETURN 1; CREATE ()",
                "CREATE DATABASE otra", "CREATE INDEX FOR (a:Aspirante) ON (a.nombre)",
                "SHOW USERS", "USE system RETURN 1", "RETURN 'sin cerrar", "/* sin cerrar"
        }) assertThrows(ApiException.class, () -> policy.writes("NEO4J", code));
    }

    @Test
    void rejectsUnknownEnginesAndOversizedCode() {
        assertThrows(ApiException.class, () -> policy.writes("SQL", "SELECT 1"));
        assertThrows(ApiException.class, () -> policy.writes("NEO4J", " "));
        assertThrows(ApiException.class, () -> policy.writes("NEO4J", "x".repeat(20001)));
    }
}
