package com.codeconheiner.tienda.pagos.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record PagoResponse(
        Long id,
        Long pedidoId,
        String pasarela,
        String referencia,
        String estado,
        BigDecimal monto,
        OffsetDateTime creadoEn) {}
