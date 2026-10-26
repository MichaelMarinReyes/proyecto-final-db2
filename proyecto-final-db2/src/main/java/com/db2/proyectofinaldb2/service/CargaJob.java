package com.db2.proyectofinaldb2.service;

import com.db2.proyectofinaldb2.dto.CargaReporteDto;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.bson.Document;

/**
 * In-memory state of a load job. HTTP threads read snapshots while the ETL
 * worker updates the counters, hence the concurrent collections and atomics.
 */
public class CargaJob {

    public enum Estado {
        EN_PROCESO, EXITOSA, EXITOSA_CON_RECHAZOS, ERROR
    }

    private final String id;
    private final Instant fechaInicio = Instant.now();
    private final List<FileJob> archivos = new CopyOnWriteArrayList<>();
    private volatile Estado estado = Estado.EN_PROCESO;
    private volatile Instant fechaFin;
    private volatile String error;

    public CargaJob(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public Estado getEstado() {
        return estado;
    }

    public Instant getFechaInicio() {
        return fechaInicio;
    }

    public List<FileJob> getArchivos() {
        return archivos;
    }

    public void addFile(FileJob file) {
        archivos.add(file);
    }

    public void finish() {
        boolean anySuccess = archivos.stream().anyMatch(file -> file.getEstado() != Estado.ERROR);
        boolean anyError = archivos.stream().anyMatch(file -> file.getEstado() == Estado.ERROR);
        boolean anyRejected = archivos.stream().anyMatch(file -> file.getRechazados().get() > 0);

        if (!anySuccess) {
            estado = Estado.ERROR;
        } else if (anyError || anyRejected) {
            estado = Estado.EXITOSA_CON_RECHAZOS;
        } else {
            estado = Estado.EXITOSA;
        }
        fechaFin = Instant.now();
    }

    public void fail(String message) {
        this.error = message;
        this.estado = Estado.ERROR;
        this.fechaFin = Instant.now();
    }

    public String getError() {
        return error;
    }

    public long getDuracionMs() {
        Instant end = fechaFin == null ? Instant.now() : fechaFin;
        return end.toEpochMilli() - fechaInicio.toEpochMilli();
    }

    public int getProgreso() {
        if (estado != Estado.EN_PROCESO) {
            return 100;
        }
        if (archivos.isEmpty()) {
            return 0;
        }
        long finished = archivos.stream().filter(file -> file.getEstado() != Estado.EN_PROCESO).count();
        return (int) Math.min(99, finished * 100 / archivos.size());
    }

    public int totalLeidos() {
        return archivos.stream().mapToInt(file -> file.getLeidos().get()).sum();
    }

    public int totalProcesados() {
        return archivos.stream().mapToInt(file -> file.getProcesados().get()).sum();
    }

    public int totalRechazados() {
        return archivos.stream().mapToInt(file -> file.getRechazados().get()).sum();
    }

    public int totalDuplicados() {
        return archivos.stream().mapToInt(file -> file.getDuplicados().get()).sum();
    }

    public CargaReporteDto snapshot() {
        List<CargaReporteDto.ArchivoReporteDto> files = new ArrayList<>();
        for (FileJob file : archivos) {
            files.add(file.snapshot());
        }
        return new CargaReporteDto(
                id,
                estado.name(),
                fechaInicio.toString(),
                fechaFin == null ? null : fechaFin.toString(),
                getDuracionMs(),
                getProgreso(),
                new CargaReporteDto.TotalesDto(totalLeidos(), totalProcesados(), totalRechazados(), totalDuplicados()),
                files
        );
    }

    public Document toDocument() {
        List<Document> files = new ArrayList<>();
        for (FileJob file : archivos) {
            files.add(file.toDocument());
        }
        return new Document("_id", id)
                .append("estado", estado.name())
                .append("fechaInicio", Date.from(fechaInicio))
                .append("fechaFin", fechaFin == null ? null : Date.from(fechaFin))
                .append("duracionMs", getDuracionMs())
                .append("progreso", getProgreso())
                .append("totales", new Document("leidos", totalLeidos())
                        .append("procesados", totalProcesados())
                        .append("rechazados", totalRechazados())
                        .append("duplicados", totalDuplicados()))
                .append("archivos", files)
                .append("error", error);
    }

    public static class FileJob {

        private final String nombre;
        private final Path ruta;
        private final AtomicInteger leidos = new AtomicInteger();
        private final AtomicInteger procesados = new AtomicInteger();
        private final AtomicInteger rechazados = new AtomicInteger();
        private final AtomicInteger duplicados = new AtomicInteger();
        private final List<CargaReporteDto.ErrorRegistroDto> errores =
                Collections.synchronizedList(new ArrayList<>());
        private volatile Estado estado = Estado.EN_PROCESO;
        private volatile long duracionMs;
        private volatile String detalleError;

        public FileJob(String nombre, Path ruta) {
            this.nombre = nombre;
            this.ruta = ruta;
        }

        public String getNombre() {
            return nombre;
        }

        public Path getRuta() {
            return ruta;
        }

        public AtomicInteger getLeidos() {
            return leidos;
        }

        public AtomicInteger getProcesados() {
            return procesados;
        }

        public AtomicInteger getRechazados() {
            return rechazados;
        }

        public AtomicInteger getDuplicados() {
            return duplicados;
        }

        public Estado getEstado() {
            return estado;
        }

        public void setEstado(Estado estado) {
            this.estado = estado;
        }

        public void setDuracionMs(long duracionMs) {
            this.duracionMs = duracionMs;
        }

        public void setDetalleError(String detalleError) {
            this.detalleError = detalleError;
        }

        public void addError(int fila, String causa, String valor) {
            if (errores.size() < 100) {
                errores.add(new CargaReporteDto.ErrorRegistroDto(fila, causa, valor));
            }
        }

        public CargaReporteDto.ArchivoReporteDto snapshot() {
            List<CargaReporteDto.ErrorRegistroDto> copy;
            synchronized (errores) {
                copy = new ArrayList<>(errores);
            }
            return new CargaReporteDto.ArchivoReporteDto(
                    nombre,
                    leidos.get(),
                    procesados.get(),
                    rechazados.get(),
                    duplicados.get(),
                    duracionMs,
                    estado.name(),
                    copy
            );
        }

        public Document toDocument() {
            List<Document> errors = new ArrayList<>();
            synchronized (errores) {
                for (CargaReporteDto.ErrorRegistroDto error : errores) {
                    errors.add(new Document("fila", error.fila())
                            .append("causa", error.causa())
                            .append("valor", error.valor()));
                }
            }
            return new Document("nombre", nombre)
                    .append("registrosLeidos", leidos.get())
                    .append("registrosProcesados", procesados.get())
                    .append("registrosRechazados", rechazados.get())
                    .append("duplicadosOmitidos", duplicados.get())
                    .append("duracionMs", duracionMs)
                    .append("estado", estado.name())
                    .append("errores", errors)
                    .append("detalleError", detalleError);
        }
    }
}
