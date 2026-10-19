package com.codeconheiner.tienda.envios.dto;

import java.time.OffsetDateTime;

public record EnvioResponse(
        Long id,
        Long pedidoId,
        String transportadora,
        String guia,
        String estado,
        OffsetDateTime enviadoEn,
        OffsetDateTime entregadoEn) {}
