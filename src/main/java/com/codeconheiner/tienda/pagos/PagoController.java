package com.codeconheiner.tienda.pagos;

import com.codeconheiner.tienda.pagos.dto.PagoRequest;
import com.codeconheiner.tienda.pagos.dto.PagoResponse;
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
@RequestMapping("/api/pagos")
public class PagoController {

    private final PagoService service;
    private final UsuarioService usuarioService;

    public PagoController(PagoService service, UsuarioService usuarioService) {
        this.service = service;
        this.usuarioService = usuarioService;
    }

    @GetMapping("/pedido/{pedidoId}")
    public List<PagoResponse> listarPorPedido(@PathVariable Long pedidoId) {
        return service.listarPorPedido(pedidoId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PagoResponse crear(@Valid @RequestBody PagoRequest request) {
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
