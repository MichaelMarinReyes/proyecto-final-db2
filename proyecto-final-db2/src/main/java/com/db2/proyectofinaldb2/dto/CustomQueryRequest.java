package com.db2.proyectofinaldb2.dto;

import java.util.Map;

/** Body used to run an edited query from the query viewer. */
public record CustomQueryRequest(String codigo, Map<String, Object> parametros) {
}
