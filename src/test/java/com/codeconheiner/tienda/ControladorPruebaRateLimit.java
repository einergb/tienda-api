package com.codeconheiner.tienda;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Controlador mínimo para ejercitar el filtro de rate limiting sin beans del catalogo. */
@RestController
class ControladorPruebaRateLimit {

    /** Ruta exclusiva del slice de rate limiting: no debe chocar con ningún controller real. */
    @GetMapping("/api/catalogo/prueba-rate-limit")
    String tienda() {
        return "ok";
    }
}
