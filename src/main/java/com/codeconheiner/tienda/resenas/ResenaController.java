package com.codeconheiner.tienda.resenas;

import com.codeconheiner.tienda.resenas.dto.ResenaRequest;
import com.codeconheiner.tienda.resenas.dto.ResenaResponse;
import com.codeconheiner.tienda.usuarios.UsuarioService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Resenas de productos. La lectura por producto es publica (SecurityConfig permite
 * GET /api/resenas/producto/*); escribir exige autenticacion y el trigger de V3
 * solo admite productos comprados y entregados.
 */
@RestController
@RequestMapping("/api/resenas")
public class ResenaController {

    private final ResenaService service;
    private final UsuarioService usuarioService;

    public ResenaController(ResenaService service, UsuarioService usuarioService) {
        this.service = service;
        this.usuarioService = usuarioService;
    }

    @GetMapping("/producto/{productoId}")
    public List<ResenaResponse> listarPorProducto(@PathVariable Long productoId) {
        return service.listarPorProducto(productoId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResenaResponse crear(@Valid @RequestBody ResenaRequest request) {
        return service.crear(obtenerUsuarioId(), request);
    }

    @PutMapping("/{id}")
    public ResenaResponse actualizar(@PathVariable Long id, @Valid @RequestBody ResenaRequest request) {
        return service.actualizar(obtenerUsuarioId(), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable Long id) {
        service.eliminar(obtenerUsuarioId(), id);
    }

    private Long obtenerUsuarioId() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        Long id = usuarioService.obtenerIdPorEmail(email);
        if (id == null) {
            throw new com.codeconheiner.tienda.common.ApiException(org.springframework.http.HttpStatus.UNAUTHORIZED,
                    "Autenticación requerida: inicia sesión o proporciona un token válido.");
        }
        return id;
    }
}
