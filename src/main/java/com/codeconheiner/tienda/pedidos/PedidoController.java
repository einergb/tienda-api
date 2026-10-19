package com.codeconheiner.tienda.pedidos;

import com.codeconheiner.tienda.pedidos.dto.PedidoRequest;
import com.codeconheiner.tienda.pedidos.dto.PedidoResponse;
import com.codeconheiner.tienda.usuarios.UsuarioService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/pedidos")
public class PedidoController {

    private final PedidoService service;
    private final UsuarioService usuarioService;

    public PedidoController(PedidoService service, UsuarioService usuarioService) {
        this.service = service;
        this.usuarioService = usuarioService;
    }

    @GetMapping
    public List<PedidoResponse> listar() {
        return service.listarPorUsuario(obtenerUsuarioId());
    }

    @GetMapping("/{id}")
    public PedidoResponse obtener(@PathVariable Long id) {
        return service.obtenerPorId(obtenerUsuarioId(), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PedidoResponse crear(@Valid @RequestBody PedidoRequest request) {
        return service.crear(obtenerUsuarioId(), request);
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
