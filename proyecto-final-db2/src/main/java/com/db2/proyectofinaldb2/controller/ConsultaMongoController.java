package com.db2.proyectofinaldb2.controller;

import com.db2.proyectofinaldb2.service.MongoQueryService;
import com.db2.proyectofinaldb2.service.MongoQueryService.QueryFilters;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/consultas/mongo")
public class ConsultaMongoController {

    private final MongoQueryService queryService;

    public ConsultaMongoController(MongoQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/{numero}")
    public List<Map<String, Object>> execute(@PathVariable int numero,
                                             @RequestParam(required = false) Integer anio,
                                             @RequestParam(required = false) String materia,
                                             @RequestParam(required = false) String carrera,
                                             @RequestParam(required = false) String depto,
                                             @RequestParam(required = false) String tipo,
                                             @RequestParam(required = false) Integer limit,
                                             @RequestParam(required = false) String correlativo) {
        QueryFilters filters = new QueryFilters(anio, materia, carrera, depto, tipo, limit, correlativo);
        return queryService.execute(numero, filters);
    }

    @PostMapping("/6/resumen")
    public List<Map<String, Object>> generateCareerSummary() {
        return queryService.execute(6, new QueryFilters(null, null, null, null, null, null, null));
    }
}
