package com.codeconheiner.tienda.webhooks;

import java.math.BigDecimal;

/**
 * Evento normalizado de una pasarela: qué transacción cambió, a qué estado y con qué
 * referencia nuestra (la del pedido). Los parsers de cada pasarela lo producen a partir
 * del JSON crudo que llega en el webhook.
 */
public record WebhookEvento(
        String pasarela,
        String eventoId,
        String referencia,
        boolean aprobado,
        BigDecimal monto) {}
