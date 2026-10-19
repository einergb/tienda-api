package com.codeconheiner.tienda.pedidos;

import com.codeconheiner.tienda.pedidos.dto.PedidoEstadoRequest;
import com.codeconheiner.tienda.pedidos.dto.PedidoResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Gestion de pedidos: solo rol ADMIN (regla en SecurityConfig para /api/admin/**). */
@RestController
@RequestMapping("/api/admin/pedidos")
public class AdminPedidoController {

    private final PedidoService service;

    public AdminPedidoController(PedidoService service) {
        this.service = service;
    }

    @GetMapping
    public List<PedidoResponse> listar(@RequestParam(required = false) String estado) {
        return service.listarTodos(estado);
    }

    @GetMapping("/{id}")
    public PedidoResponse obtener(@PathVariable Long id) {
        return service.obtenerPorIdAdmin(id);
    }

    @PutMapping("/{id}/estado")
    public PedidoResponse cambiarEstado(@PathVariable Long id, @Valid @RequestBody PedidoEstadoRequest request) {
        return service.cambiarEstado(id, request.estado());
    }
}
