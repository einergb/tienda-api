package com.codeconheiner.tienda.direcciones;

import com.codeconheiner.tienda.direcciones.dto.DireccionRequest;
import com.codeconheiner.tienda.direcciones.dto.DireccionResponse;
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

/** Direcciones de envío del usuario autenticado. La única principal la garantiza un trigger (V3). */
@RestController
@RequestMapping("/api/direcciones")
public class DireccionController {

    private final DireccionService service;
    private final UsuarioService usuarioService;

    public DireccionController(DireccionService service, UsuarioService usuarioService) {
        this.service = service;
        this.usuarioService = usuarioService;
    }

    @GetMapping
    public List<DireccionResponse> listar() {
        return service.listar(obtenerUsuarioId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DireccionResponse crear(@Valid @RequestBody DireccionRequest request) {
        return service.crear(obtenerUsuarioId(), request);
    }

    @PutMapping("/{id}")
    public DireccionResponse actualizar(@PathVariable Long id, @Valid @RequestBody DireccionRequest request) {
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
