package com.codeconheiner.tienda.inventario;

import com.codeconheiner.tienda.inventario.dto.StockBajoResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Alertas de inventario: solo rol ADMIN (regla en SecurityConfig para /api/admin/**). */
@RestController
@RequestMapping("/api/admin/inventario")
public class AdminInventarioController {

    private final InventarioService service;

    public AdminInventarioController(InventarioService service) {
        this.service = service;
    }

    @GetMapping("/stock-bajo")
    public List<StockBajoResponse> stockBajo() {
        return service.listarStockBajo();
    }
}
