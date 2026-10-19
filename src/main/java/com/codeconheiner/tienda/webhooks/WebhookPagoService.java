package com.codeconheiner.tienda.webhooks;

import com.codeconheiner.tienda.pagos.PagoDetalle;
import com.codeconheiner.tienda.pagos.PagoRepository;
import com.codeconheiner.tienda.seguridad.Auditoria;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Aplica los eventos de las pasarelas a los pagos: deduplica, localiza el intento por la
 * referencia que nosotros enviamos y lo actualiza. Al pasar un pago a APROBADO, el trigger
 * de V3 ({@code trg_pago_aprobado_update}) marca el pedido como PAGADO y descuenta el stock.
 */
@Service
public class WebhookPagoService {

    private static final Logger log = LoggerFactory.getLogger(WebhookPagoService.class);

    private final WebhookEventosRepository eventos;
    private final PagoRepository pagos;
    private final Auditoria auditoria;

    public WebhookPagoService(WebhookEventosRepository eventos, PagoRepository pagos, Auditoria auditoria) {
        this.eventos = eventos;
        this.pagos = pagos;
        this.auditoria = auditoria;
    }

    /**
     * @return resultado legible para la respuesta del webhook: {@code procesado},
     *         {@code duplicado}, {@code ya-procesado}, {@code referencia-desconocida}
     *         o {@code monto-no-coincide}.
     */
    @Transactional
    public String procesar(WebhookEvento evento) {
        if (!eventos.registrarSiNuevo(evento.pasarela(), evento.eventoId())) {
            return "duplicado";
        }
        PagoDetalle pago = pagos.buscarPorReferencia(evento.referencia()).orElse(null);
        if (pago == null) {
            log.warn("Webhook {} sin pago para la referencia {}",
                    evento.pasarela(), evento.referencia());
            return "referencia-desconocida";
        }
        if ("APROBADO".equals(pago.estado()) || "RECHAZADO".equals(pago.estado())) {
            return "ya-procesado";
        }
        if (evento.monto() != null && evento.monto().compareTo(pago.monto()) != 0) {
            // Nunca aprobar con un monto que no coincide con el registrado.
            log.error("Webhook {}: monto {} no coincide con el pago {} ({})",
                    evento.pasarela(), evento.monto(), pago.id(), pago.monto());
            return "monto-no-coincide";
        }
        String estado = evento.aprobado() ? "APROBADO" : "RECHAZADO";
        if (pagos.actualizarEstado(pago.id(), estado) == 0) {
            return "referencia-desconocida";
        }
        log.info("Webhook {}: pago {} {} (pedido {})",
                evento.pasarela(), pago.id(), estado, pago.pedidoId());
        auditoria.registrar(null, "webhook." + evento.pasarela(), "pagos", String.valueOf(pago.id()),
                Map.of("eventoId", evento.eventoId(), "estado", estado));
        return "procesado";
    }
}
