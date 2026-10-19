package com.codeconheiner.tienda.carrito.dto;

import java.math.BigDecimal;
import java.util.List;

public record CarritoResponse(
        Long id,
        Long usuarioId,
        List<CarritoItemResponse> items,
        BigDecimal subtotal,
        BigDecimal costoEnvio,
        BigDecimal iva,
        BigDecimal total) {}
