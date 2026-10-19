package com.codeconheiner.tienda.envios;

import com.codeconheiner.tienda.envios.dto.EnvioCrearRequest;
import com.codeconheiner.tienda.envios.dto.EnvioResponse;
import com.codeconheiner.tienda.envios.dto.EnvioActualizarRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Gestion de envios: solo rol ADMIN (regla en SecurityConfig para /api/admin/**). */
@RestController
@RequestMapping("/api/admin/envios")
public class AdminEnvioController {

    private final EnvioService service;

    public AdminEnvioController(EnvioService service) {
        this.service = service;
    }

    @GetMapping
    public List<EnvioResponse> listar(@RequestParam(required = false) String estado) {
        return service.listar(estado);
    }

    @GetMapping("/{id}")
    public EnvioResponse obtener(@PathVariable Long id) {
        return service.obtenerPorId(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EnvioResponse crear(@Valid @RequestBody EnvioCrearRequest request) {
        return service.crear(request);
    }

    @PutMapping("/{id}")
    public EnvioResponse actualizar(@PathVariable Long id, @Valid @RequestBody EnvioActualizarRequest request) {
        return service.actualizar(id, request);
    }
}
