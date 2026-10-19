package com.codeconheiner.tienda.envios;

import com.codeconheiner.tienda.common.ApiException;
import com.codeconheiner.tienda.envios.dto.EnvioCrearRequest;
import com.codeconheiner.tienda.envios.dto.EnvioActualizarRequest;
import com.codeconheiner.tienda.envios.dto.EnvioResponse;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EnvioService {

    private static final Set<String> ESTADOS_CREACION = Set.of("PREPARANDO", "EN_CAMINO");
    private static final Set<String> ESTADOS_ACTUALIZACION = Set.of("EN_CAMINO", "ENTREGADO", "DEVUELTO");

    private final EnvioRepository repo;

    public EnvioService(EnvioRepository repo) {
        this.repo = repo;
    }

    public EnvioResponse obtenerPorPedido(Long pedidoId) {
        return repo.obtenerPorPedido(pedidoId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Envio no encontrado"));
    }

    public EnvioResponse obtenerPorId(Long id) {
        return repo.obtenerPorId(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Envio no encontrado"));
    }

    public List<EnvioResponse> listar(String estado) {
        return repo.listar(estado);
    }

    /** Crea el envio de un pedido pagado; al insertarlo V3 pasa el pedido a EN_PREPARACION. */
    @Transactional
    public EnvioResponse crear(EnvioCrearRequest request) {
        String estado = normalizar(request.estado(), "PREPARANDO");
        if (!ESTADOS_CREACION.contains(estado)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Estado de envío no válido");
        }
        return repo.crear(request.pedidoId(), texto(request.transportadora()), texto(request.guia()), estado);
    }

    /** Despacha o entrega: V3 valida los cambios y propaga el estado al pedido. */
    @Transactional
    public EnvioResponse actualizar(Long id, EnvioActualizarRequest request) {
        String estado = normalizar(request.estado(), null);
        if (estado == null || !ESTADOS_ACTUALIZACION.contains(estado)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Estado de envío no válido");
        }
        if (repo.actualizar(id, estado, texto(request.transportadora()), texto(request.guia())) == 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Envio no encontrado");
        }
        return repo.obtenerPorId(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Envio no encontrado"));
    }

    private static String normalizar(String estado, String porDefecto) {
        if (estado == null || estado.isBlank()) {
            return porDefecto;
        }
        return estado.trim().toUpperCase(Locale.ROOT);
    }

    private static String texto(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
