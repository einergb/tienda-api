package com.codeconheiner.tienda.catalogo;

import com.codeconheiner.tienda.catalogo.storage.StorageService;
import com.codeconheiner.tienda.common.ApiException;
import jakarta.validation.Valid;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Gestion de productos: solo rol ADMIN (regla en SecurityConfig para /api/admin/**). */
@RestController
@RequestMapping("/api/admin/productos")
public class AdminProductoController {

    private final ProductoService service;
    private final StorageService storageService;

    public AdminProductoController(ProductoService service, StorageService storageService) {
        this.service = service;
        this.storageService = storageService;
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

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<ProductoResponse> listar() {
        return service.listarTodos();
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void desactivar(@PathVariable long id) {
        service.desactivar(id);
    }

    @PatchMapping("/{id}/activar")
    @PreAuthorize("hasRole('ADMIN')")
    public ProductoResponse activar(@PathVariable long id) {
        return service.reactivar(id);
    }

    @PostMapping(value = "/{id}/imagenes", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public ImagenProductoResponse agregarImagenJson(@PathVariable long id,
            @Valid @RequestBody ImagenRequest request) {
        Long imagenId = service.agregarImagen(id, request);
        return new ImagenProductoResponse(imagenId, request.url(), request.orden() == null ? 0 : request.orden(),
                Boolean.TRUE.equals(request.esPrincipal()));
    }

    @PostMapping(value = "/imagenes", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, String> subirImagen(@RequestParam(required = false) MultipartFile archivo,
            @RequestParam(required = false) String imagenUrl) {
        if (archivo != null && !archivo.isEmpty()) {
            String key = storageService.subirArchivo(archivo, "productos/sin-asignar");
            String url = storageService.urlPublica(key);
            return Map.of("imagenUrl", url);
        }
        if (imagenUrl != null && !imagenUrl.isBlank()) {
            String trimmed = imagenUrl.trim();
            try {
                URL u = new URL(trimmed);
                String protocolo = u.getProtocol();
                if (!"http".equalsIgnoreCase(protocolo) && !"https".equalsIgnoreCase(protocolo)) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "URL de imagen no válida");
                }
            } catch (MalformedURLException e) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "URL de imagen no válida");
            }
            return Map.of("imagenUrl", trimmed);
        }
        throw new ApiException(HttpStatus.BAD_REQUEST, "Debes enviar 'archivo' o 'imagenUrl'");
    }

    @PostMapping(value = "/{id}/imagenes/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public ImagenProductoResponse agregarImagenUpload(@PathVariable long id,
            @RequestParam MultipartFile archivo,
            @RequestParam(required = false) Integer orden,
            @RequestParam(required = false) Boolean esPrincipal) {
        String url = service.subirImagenGaleria(id, archivo);
        ImagenRequest request = new ImagenRequest(url, orden, esPrincipal);
        Long imagenId = service.agregarImagen(id, request);
        return new ImagenProductoResponse(imagenId, request.url(), request.orden() == null ? 0 : request.orden(),
                Boolean.TRUE.equals(request.esPrincipal()));
    }

    @DeleteMapping("/{id}/imagenes/{imagenId}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminarImagen(@PathVariable long id, @PathVariable long imagenId) {
        service.eliminarImagenGaleria(id, imagenId);
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

    /**
     * Sube la imagen del producto al almacenamiento S3-compatible y guarda solo la clave del
     * objeto en Postgres.
     */
    @PostMapping(value = "/{id}/imagen", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public ProductoResponse subirImagenProducto(@PathVariable long id, @RequestParam("file") MultipartFile file) {
        return service.subirImagen(id, file);
    }

    /** Borra el objeto del almacenamiento y deja imagen_key en null. */
    @DeleteMapping("/{id}/imagen")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminarImagenProducto(@PathVariable long id) {
        service.eliminarImagen(id);
    }
}
