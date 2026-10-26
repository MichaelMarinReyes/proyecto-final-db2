package com.db2.proyectofinaldb2.controller;

import com.db2.proyectofinaldb2.dto.CargaReporteDto;
import com.db2.proyectofinaldb2.service.CargaService;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/cargas")
public class CargaController {

    private final CargaService cargaService;

    public CargaController(CargaService cargaService) {
        this.cargaService = cargaService;
    }

    @PostMapping(value = "/archivos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CargaReporteDto> upload(@RequestParam("files") MultipartFile[] files) {
        CargaReporteDto report = cargaService.start(files);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(report);
    }

    @GetMapping
    public List<Map<String, Object>> history() {
        return cargaService.getJobs();
    }

    @GetMapping("/{id}")
    public Object detail(@PathVariable String id) {
        return cargaService.getJob(id);
    }

    @GetMapping("/{id}/rechazados")
    public List<Map<String, Object>> rejected(@PathVariable String id,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "50") int size) {
        return cargaService.getRejected(id, page, size);
    }
}
