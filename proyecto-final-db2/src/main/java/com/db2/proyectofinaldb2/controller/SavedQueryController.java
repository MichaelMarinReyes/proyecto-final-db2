package com.db2.proyectofinaldb2.controller;

import com.db2.proyectofinaldb2.dto.SavedQueryRequest;
import com.db2.proyectofinaldb2.service.SavedQueryService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/consultas/guardadas")
public class SavedQueryController {
    private final SavedQueryService service;

    public SavedQueryController(SavedQueryService service) { this.service = service; }

    @GetMapping
    public List<Map<String, Object>> list(@RequestHeader(name = "X-Query-Admin-Key", required = false) String key,
                                          @RequestParam(name = "motor") String engine) {
        service.authorize(key);
        return service.list(engine);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> save(@RequestHeader(name = "X-Query-Admin-Key", required = false) String key,
                                    @Valid @RequestBody SavedQueryRequest request) {
        service.authorize(key);
        return service.save(request);
    }

    @PostMapping("/{id}/ejecutar")
    public Map<String, Object> execute(@RequestHeader(name = "X-Query-Admin-Key", required = false) String key,
                                       @PathVariable String id,
                                       @RequestParam(name = "confirmarEscritura", defaultValue = "false") boolean confirmWrite) {
        service.authorize(key);
        return service.execute(id, confirmWrite);
    }
}
