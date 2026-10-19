package com.codeconheiner.tienda.cupones.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record CuponResponse(
        Long id,
        String codigo,
        String tipo,
        BigDecimal valor,
        BigDecimal montoMinimo,
        Integer usosMaximos,
        Integer usosActuales,
        OffsetDateTime vigenteDesde,
        OffsetDateTime vigenteHasta,
        boolean activo) {}
