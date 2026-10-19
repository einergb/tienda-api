package com.codeconheiner.tienda.cupones.dto;

import java.math.BigDecimal;

/** Resultado de validar un cupón contra un subtotal (sin crear un pedido). */
public record ValidacionCuponResponse(
        Long id,
        String codigo,
        String tipo,
        BigDecimal valor,
        BigDecimal descuento) {}
