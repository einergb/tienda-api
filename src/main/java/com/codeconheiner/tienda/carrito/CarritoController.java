package com.codeconheiner.tienda.carrito;

import com.codeconheiner.tienda.carrito.dto.CarritoItemRequest;
import com.codeconheiner.tienda.carrito.dto.CarritoResponse;
import com.codeconheiner.tienda.usuarios.UsuarioService;
import jakarta.validation.Valid;
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

@RestController
@RequestMapping("/api/carrito")
public class CarritoController {

    private final CarritoService service;
    private final UsuarioService usuarioService;

    public CarritoController(CarritoService service, UsuarioService usuarioService) {
        this.service = service;
        this.usuarioService = usuarioService;
    }

    @GetMapping
    public CarritoResponse obtenerCarrito() {
        return service.obtenerCarrito(obtenerUsuarioId());
    }

    @PostMapping("/items")
    @ResponseStatus(HttpStatus.CREATED)
    public CarritoResponse agregarItem(@Valid @RequestBody CarritoItemRequest request) {
        return service.agregarItem(obtenerUsuarioId(), request);
    }

    @PutMapping("/items/{productoId}")
    public CarritoResponse actualizarItem(@PathVariable Long productoId,
            @Valid @RequestBody CarritoItemRequest request) {
        return service.actualizarItem(obtenerUsuarioId(), productoId, request);
    }

    @DeleteMapping("/items/{productoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminarItem(@PathVariable Long productoId) {
        service.eliminarItem(obtenerUsuarioId(), productoId);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void vaciarCarrito() {
        service.vaciarCarrito(obtenerUsuarioId());
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
