package com.codeconheiner.tienda.reportes;

import com.codeconheiner.tienda.reportes.dto.InventarioReporteResponse;
import com.codeconheiner.tienda.reportes.dto.ResumenVentasResponse;
import java.time.OffsetDateTime;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Reportes administrativos (rol ADMIN por la regla de /api/admin/** en SecurityConfig). */
@RestController
@RequestMapping("/api/admin/reportes")
public class ReporteController {

    private final ReporteService service;

    public ReporteController(ReporteService service) {
        this.service = service;
    }

    /** Resumen de ventas; parámetros ISO-8601 (p. ej. 2026-01-01T00:00:00Z). Por defecto: últimos 30 días. */
    @GetMapping("/ventas")
    public ResumenVentasResponse ventas(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    OffsetDateTime desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    OffsetDateTime hasta) {
        return service.ventas(desde, hasta);
    }

    @GetMapping("/inventario")
    public InventarioReporteResponse inventario() {
        return service.inventario();
    }
}
