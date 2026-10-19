package com.codeconheiner.tienda.cupones;

import com.codeconheiner.tienda.cupones.dto.CuponRequest;
import com.codeconheiner.tienda.cupones.dto.CuponResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Gestion de cupones: solo rol ADMIN (regla en SecurityConfig para /api/admin/**). */
@RestController
@RequestMapping("/api/admin/cupones")
public class AdminCuponController {

    private final CuponService service;

    public AdminCuponController(CuponService service) {
        this.service = service;
    }

    @GetMapping
    public List<CuponResponse> listar() {
        return service.listar();
    }

    @GetMapping("/{id}")
    public CuponResponse obtener(@PathVariable Long id) {
        return service.obtener(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CuponResponse crear(@Valid @RequestBody CuponRequest request) {
        return service.crear(request);
    }

    @PutMapping("/{id}")
    public CuponResponse actualizar(@PathVariable Long id, @Valid @RequestBody CuponRequest request) {
        return service.actualizar(id, request);
    }
}
