package com.db2.proyectofinaldb2.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

/**
 * Executed query shown to the user. {@code codigo} is directly editable in the
 * frontend and {@code parametros} is only used by Cypher statements.
 *
 * <p>The application excludes null values globally, but Cypher parameters such
 * as an unused year are intentionally null. They are included here so the user
 * can see every parameter and re-send the same values without Neo4j failing
 * because a parameter is missing.</p>
 */
public record QueryView(
        String motor,
        String codigo,
        String nota,
        @JsonInclude(value = JsonInclude.Include.ALWAYS, content = JsonInclude.Include.ALWAYS)
        Map<String, Object> parametros) {

    public QueryView(String motor, String codigo, String nota) {
        this(motor, codigo, nota, null);
    }
}
