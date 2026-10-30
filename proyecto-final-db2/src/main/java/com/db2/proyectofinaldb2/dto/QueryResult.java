package com.db2.proyectofinaldb2.dto;

import java.util.List;
import java.util.Map;

/** Rows produced by a query together with the statement that produced them. */
public record QueryResult(List<Map<String, Object>> filas, QueryView consulta) {
}
