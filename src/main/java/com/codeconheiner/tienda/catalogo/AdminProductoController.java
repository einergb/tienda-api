package com.codeconheiner.tienda.catalogo;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Gestion de productos: solo rol ADMIN (regla en SecurityConfig para /api/admin/**). */
@RestController
@RequestMapping("/api/admin/productos")
public class AdminProductoController {

    private final ProductoService service;

    public AdminProductoController(ProductoService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductoResponse crear(@Valid @RequestBody ProductoRequest request) {
        return service.crear(request);
    }

    @PutMapping("/{id}")
    public ProductoResponse actualizar(@PathVariable long id, @Valid @RequestBody ProductoRequest request) {
        return service.actualizar(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void desactivar(@PathVariable long id) {
        service.desactivar(id);
    }

    @PostMapping("/{id}/imagenes")
    @ResponseStatus(HttpStatus.CREATED)
    public ImagenProductoResponse agregarImagen(@PathVariable long id, @Valid @RequestBody ImagenRequest request) {
        Long imagenId = service.agregarImagen(id, request);
        return new ImagenProductoResponse(imagenId, request.url(), request.orden() == null ? 0 : request.orden(),
                Boolean.TRUE.equals(request.esPrincipal()));
    }

    @DeleteMapping("/{id}/imagenes/{imagenId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminarImagen(@PathVariable long id, @PathVariable long imagenId) {
        service.eliminarImagen(id, imagenId);
    }

    @PostMapping("/{id}/atributos")
    @ResponseStatus(HttpStatus.CREATED)
    public AtributoRequest agregarAtributo(@PathVariable long id, @Valid @RequestBody AtributoRequest request) {
        service.agregarAtributo(id, request);
        return request;
    }

    @DeleteMapping("/{id}/atributos/{clave}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminarAtributo(@PathVariable long id, @PathVariable String clave) {
        service.eliminarAtributo(id, clave);
    }
}
