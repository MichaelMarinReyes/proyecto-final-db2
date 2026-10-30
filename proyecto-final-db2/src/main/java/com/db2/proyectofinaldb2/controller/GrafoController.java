package com.db2.proyectofinaldb2.controller;

import com.db2.proyectofinaldb2.dto.CustomQueryRequest;
import com.db2.proyectofinaldb2.dto.GrafoDto;
import com.db2.proyectofinaldb2.dto.QueryResult;
import com.db2.proyectofinaldb2.service.ConsultaEditorService;
import com.db2.proyectofinaldb2.service.GraphService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/grafo")
public class GrafoController {

    private final GraphService graphService;
    private final ConsultaEditorService editorService;

    public GrafoController(GraphService graphService, ConsultaEditorService editorService) {
        this.graphService = graphService;
        this.editorService = editorService;
    }

    @GetMapping("/consultas/{numero}")
    public QueryResult execute(@PathVariable int numero,
                               @RequestParam(required = false) String carrera,
                               @RequestParam(required = false) String depto,
                               @RequestParam(required = false) String materia,
                               @RequestParam(required = false) String tipo,
                               @RequestParam(required = false) Integer anio,
                               @RequestParam(required = false) Integer limit) {
        return graphService.runQueryWithPlan(numero, carrera, depto, materia, tipo, anio, limit);
    }

    @PostMapping("/ejecutar")
    public Map<String, Object> executeEdited(@RequestBody CustomQueryRequest request) {
        return editorService.executeNeo4j(request.codigo(), request.parametros());
    }

    @GetMapping("/visualizacion")
    public GrafoDto visualization(@RequestParam(required = false) String carrera,
                                  @RequestParam(required = false) String depto,
                                  @RequestParam(required = false) String materia,
                                  @RequestParam(required = false) String tipo,
                                  @RequestParam(required = false) Integer anio,
                                  @RequestParam(required = false) Integer limit) {
        return graphService.visualization(carrera, depto, materia, tipo, anio, limit);
    }

    @GetMapping("/nodo/{id}")
    public Map<String, Object> node(@PathVariable String id) {
        return graphService.nodeDetail(id);
    }
}
