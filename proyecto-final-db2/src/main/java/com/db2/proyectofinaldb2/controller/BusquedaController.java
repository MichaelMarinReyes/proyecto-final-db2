package com.db2.proyectofinaldb2.controller;

import com.db2.proyectofinaldb2.service.SearchService;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class BusquedaController {

    private final SearchService searchService;

    public BusquedaController(SearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping("/buscar")
    public List<Map<String, Object>> search(@RequestParam String tipo,
                                            @RequestParam(name = "q", required = false,
                                                    defaultValue = "") String query) {
        return searchService.search(tipo, query);
    }

    @GetMapping("/aspirantes/{correlativo}")
    public Map<String, Object> aspirant(@PathVariable String correlativo) {
        return searchService.getAspirant(correlativo);
    }

    @GetMapping("/aspirantes/{correlativo}/historial")
    public List<Map<String, Object>> history(@PathVariable String correlativo) {
        return searchService.getHistory(correlativo);
    }
}
