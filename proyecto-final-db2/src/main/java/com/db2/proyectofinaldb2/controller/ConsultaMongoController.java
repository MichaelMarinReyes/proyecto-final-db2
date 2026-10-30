package com.db2.proyectofinaldb2.controller;

import com.db2.proyectofinaldb2.dto.CustomQueryRequest;
import com.db2.proyectofinaldb2.dto.QueryResult;
import com.db2.proyectofinaldb2.service.ConsultaEditorService;
import com.db2.proyectofinaldb2.service.MongoQueryService;
import com.db2.proyectofinaldb2.service.MongoQueryService.QueryFilters;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/consultas/mongo")
public class ConsultaMongoController {

    private final MongoQueryService queryService;
    private final ConsultaEditorService editorService;

    public ConsultaMongoController(MongoQueryService queryService,
                                   ConsultaEditorService editorService) {
        this.queryService = queryService;
        this.editorService = editorService;
    }

    @GetMapping("/{numero}")
    public QueryResult execute(@PathVariable int numero,
                               @RequestParam(required = false) Integer anio,
                               @RequestParam(required = false) String materia,
                               @RequestParam(required = false) String carrera,
                               @RequestParam(required = false) String depto,
                               @RequestParam(required = false) String tipo,
                               @RequestParam(required = false) Integer limit,
                               @RequestParam(required = false) String correlativo) {
        QueryFilters filters = new QueryFilters(anio, materia, carrera, depto, tipo, limit, correlativo);
        return queryService.executeWithPlan(numero, filters);
    }

    @PostMapping("/6/resumen")
    public QueryResult generateCareerSummary() {
        return queryService.executeWithPlan(6, new QueryFilters(null, null, null, null, null, null, null));
    }

    @PostMapping("/ejecutar")
    public Map<String, Object> executeEdited(@RequestBody CustomQueryRequest request) {
        return editorService.executeMongo(request.codigo());
    }
}
