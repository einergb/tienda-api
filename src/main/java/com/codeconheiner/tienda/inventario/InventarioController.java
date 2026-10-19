package com.codeconheiner.tienda.inventario;

import com.codeconheiner.tienda.inventario.dto.StockInfoResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/inventario")
public class InventarioController {

    private final InventarioService service;

    public InventarioController(InventarioService service) {
        this.service = service;
    }

    @GetMapping("/productos/{productoId}/stock")
    public StockInfoResponse obtenerStock(@PathVariable Long productoId) {
        return service.obtenerStock(productoId);
    }
}
