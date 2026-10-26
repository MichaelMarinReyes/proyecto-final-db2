package com.db2.proyectofinaldb2.dto;

import java.util.List;
import java.util.Map;

public record GrafoDto(
        List<Nodo> nodes,
        List<Relacion> links
) {
    public record Nodo(
            String id,
            String label,
            String tipo,
            Map<String, Object> propiedades
    ) {
    }

    public record Relacion(
            String id,
            String source,
            String target,
            String tipo,
            Map<String, Object> propiedades
    ) {
    }
}
