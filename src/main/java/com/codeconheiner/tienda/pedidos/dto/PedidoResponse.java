package com.codeconheiner.tienda.pedidos.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record PedidoResponse(
        Long id,
        String numero,
        String estado,
        BigDecimal subtotal,
        BigDecimal descuento,
        BigDecimal iva,
        BigDecimal costoEnvio,
        BigDecimal total,
        OffsetDateTime reservaExpiraEn,
        OffsetDateTime creadoEn,
        List<PedidoItemResponse> items) {}
