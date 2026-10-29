package com.db2.proyectofinaldb2.dto;

import java.util.List;

public record CargaReporteDto(
        String id,
        String estado,
        String fechaInicio,
        String fechaFin,
        long duracionMs,
        int progreso,
        TotalesDto totales,
        List<ArchivoReporteDto> archivos,
        String error
) {
    public record TotalesDto(
            int leidos,
            int procesados,
            int rechazados,
            int duplicados,
            int pendientes,
            int sincronizadosNeo4j
    ) {
    }

    public record ArchivoReporteDto(
            String nombre,
            int registrosLeidos,
            int registrosProcesados,
            int registrosRechazados,
            int duplicadosOmitidos,
            long duracionMs,
            String estado,
            List<ErrorRegistroDto> errores,
            int registrosPendientes,
            int sincronizadosNeo4j,
            String detalleError
    ) {
    }

    public record ErrorRegistroDto(
            int fila,
            String causa,
            String valor
    ) {
    }
}
