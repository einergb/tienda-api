package com.codeconheiner.tienda.pagos;

import com.codeconheiner.tienda.common.ApiException;
import com.codeconheiner.tienda.pagos.dto.PagoRequest;
import com.codeconheiner.tienda.pagos.dto.PagoResponse;
import com.codeconheiner.tienda.pedidos.PedidoRepository;
import com.codeconheiner.tienda.pedidos.dto.PedidoResponse;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PagoService {

    private static final Set<String> ESTADOS = Set.of("PENDIENTE", "APROBADO", "RECHAZADO");

    private final PagoRepository repo;
    private final PedidoRepository pedidos;

    public PagoService(PagoRepository repo, PedidoRepository pedidos) {
        this.repo = repo;
        this.pedidos = pedidos;
    }

    public List<PagoResponse> listarPorPedido(Long pedidoId) {
        return repo.listarPorPedido(pedidoId);
    }

    /**
     * Registra un intento de pago del pedido propio. El monto siempre es el total del pedido;
     * si el estado es APROBADO, los triggers de V3 marcan el pedido como PAGADO y descuentan stock.
     */
    @Transactional
    public PagoResponse crear(Long usuarioId, PagoRequest request) {
        PedidoResponse pedido = pedidos.obtenerPorId(usuarioId, request.pedidoId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Pedido no encontrado"));
        if (!"PENDIENTE".equals(pedido.estado())) {
            throw new ApiException(HttpStatus.CONFLICT, "El pedido no admite pagos en el estado actual");
        }
        String estado = request.estado() == null || request.estado().isBlank()
                ? "PENDIENTE"
                : request.estado().trim().toUpperCase(Locale.ROOT);
        if (!ESTADOS.contains(estado)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Estado de pago no válido");
        }
        BigDecimal monto = pedido.total();
        return repo.crear(pedido.id(), request.pasarela().trim(), request.referencia().trim(), estado, monto);
    }
}
