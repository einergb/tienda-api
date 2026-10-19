package com.codeconheiner.tienda.carrito.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CarritoItemRequest(
        @NotNull(message = "El producto es obligatorio")
        Long productoId,
        @NotNull(message = "La cantidad es obligatoria")
        @Min(value = 1, message = "La cantidad debe ser mayor o igual a 1")
        Integer cantidad) {}
