package com.codeconheiner.tienda.facturas;

import com.codeconheiner.tienda.common.ApiException;
import com.codeconheiner.tienda.config.TiendaProperties;
import com.codeconheiner.tienda.facturas.dto.FacturaResponse;
import com.codeconheiner.tienda.pedidos.PedidoRepository;
import com.codeconheiner.tienda.pedidos.dto.PedidoItemResponse;
import com.codeconheiner.tienda.pedidos.dto.PedidoResponse;
import com.codeconheiner.tienda.seguridad.Auditoria;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Emite la factura de un pedido. La emisión es idempotente: el primer acceso la crea
 * (numeración FAC-AAAA-NNNNNN en la base) y los siguientes devuelven la misma factura.
 */
@Service
public class FacturaService {

    /** Solo los estados posteriores al pago permiten facturar. */
    private static final Set<String> ESTADOS_FACTURABLES =
            Set.of("PAGADO", "EN_PREPARACION", "ENVIADO", "ENTREGADO");

    private final FacturaRepository repo;
    private final PedidoRepository pedidos;
    private final TiendaProperties props;
    private final Auditoria auditoria;

    public FacturaService(FacturaRepository repo, PedidoRepository pedidos, TiendaProperties props,
            Auditoria auditoria) {
        this.repo = repo;
        this.pedidos = pedidos;
        this.props = props;
        this.auditoria = auditoria;
    }

    public FacturaResponse emitir(Long usuarioId, Long pedidoId) {
        PedidoResponse pedido = pedidos.obtenerPorId(usuarioId, pedidoId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Pedido no encontrado"));
        return emitirPara(pedido);
    }

    public FacturaResponse emitirAdmin(Long pedidoId) {
        PedidoResponse pedido = pedidos.obtenerPorIdAdmin(pedidoId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Pedido no encontrado"));
        return emitirPara(pedido);
    }

    private FacturaResponse emitirPara(PedidoResponse pedido) {
        if (!ESTADOS_FACTURABLES.contains(pedido.estado())) {
            throw new ApiException(HttpStatus.CONFLICT, "La factura solo se emite para pedidos pagados");
        }
        FacturaEmision emision = repo.emitir(pedido.id(), pedido.total());
        auditoria.registrarActual("factura.emitida", "facturas", String.valueOf(emision.id()),
                java.util.Map.of("pedidoId", pedido.id(), "numero", emision.numero()));
        EnvioCopia envio = repo.buscarEnvioPedido(pedido.id()).orElse(null);
        return new FacturaResponse(
                emision.id(),
                emision.numero(),
                emision.emitidaEn(),
                props.nombre(),
                props.moneda(),
                pedido.id(),
                pedido.numero(),
                pedido.creadoEn(),
                envio == null ? null : envio.destinatario(),
                envio == null ? null : envio.telefono(),
                envio == null ? null : envio.departamento(),
                envio == null ? null : envio.ciudad(),
                envio == null ? null : envio.direccion(),
                items(pedido.items()),
                pedido.subtotal(),
                pedido.descuento(),
                pedido.iva(),
                pedido.costoEnvio(),
                pedido.total());
    }

    private static List<FacturaResponse.Item> items(List<PedidoItemResponse> items) {
        return items.stream()
                .map(i -> new FacturaResponse.Item(i.sku(), i.nombreProducto(), i.cantidad(),
                        i.precioUnitario(), i.subtotal()))
                .toList();
    }
}
