package com.db2.proyectofinaldb2.controller;

import com.db2.proyectofinaldb2.dto.GrafoDto;
import com.db2.proyectofinaldb2.service.GraphService;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/grafo")
public class GrafoController {

    private final GraphService graphService;

    public GrafoController(GraphService graphService) {
        this.graphService = graphService;
    }

    @GetMapping("/consultas/{numero}")
    public List<Map<String, Object>> execute(@PathVariable int numero,
                                             @RequestParam(required = false) String carrera,
                                             @RequestParam(required = false) String depto,
                                             @RequestParam(required = false) String materia,
                                             @RequestParam(required = false) String tipo,
                                             @RequestParam(required = false) Integer anio,
                                             @RequestParam(required = false) Integer limit) {
        return graphService.runQuery(numero, carrera, depto, materia, tipo, anio, limit);
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
