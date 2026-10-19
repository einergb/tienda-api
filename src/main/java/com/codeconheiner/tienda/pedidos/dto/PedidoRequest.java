package com.codeconheiner.tienda.pedidos.dto;

import jakarta.validation.constraints.NotNull;

public record PedidoRequest(
        @NotNull(message = "La dirección es obligatoria")
        Long direccionId,
        Long cuponId) {}
