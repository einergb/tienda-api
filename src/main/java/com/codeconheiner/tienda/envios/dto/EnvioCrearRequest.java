package com.codeconheiner.tienda.envios.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record EnvioCrearRequest(
        @NotNull(message = "El pedido es obligatorio")
        Long pedidoId,
        @Size(max = 80, message = "La transportadora no puede superar 80 caracteres")
        String transportadora,
        @Size(max = 80, message = "La guía no puede superar 80 caracteres")
        String guia,
        /** PREPARANDO (por defecto) o EN_CAMINO. */
        @Size(max = 15, message = "El estado no puede superar 15 caracteres")
        String estado) {}
