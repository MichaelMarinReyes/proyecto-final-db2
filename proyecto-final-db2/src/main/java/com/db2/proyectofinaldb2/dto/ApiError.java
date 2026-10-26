package com.db2.proyectofinaldb2.dto;

public record ApiError(
        String timestamp,
        int status,
        String error,
        String mensaje,
        String ruta
) {
}
