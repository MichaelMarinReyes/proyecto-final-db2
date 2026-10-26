package com.db2.proyectofinaldb2.controller;

import com.db2.proyectofinaldb2.service.CatalogService;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/catalogos")
public class CatalogoController {

    private final CatalogService catalogService;

    public CatalogoController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping("/{tipo}")
    public List<Map<String, Object>> get(@PathVariable String tipo) {
        return catalogService.get(tipo);
    }
}
