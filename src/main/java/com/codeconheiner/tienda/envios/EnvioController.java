package com.codeconheiner.tienda.envios;

import com.codeconheiner.tienda.envios.dto.EnvioResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/envios")
public class EnvioController {

    private final EnvioService service;

    public EnvioController(EnvioService service) {
        this.service = service;
    }

    @GetMapping("/pedido/{pedidoId}")
    public EnvioResponse obtenerPorPedido(@PathVariable Long pedidoId) {
        return service.obtenerPorPedido(pedidoId);
    }
}
