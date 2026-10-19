package com.codeconheiner.tienda.pagos.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PagoRequest(
        @NotNull(message = "El pedido es obligatorio")
        Long pedidoId,
        @NotBlank(message = "La pasarela es obligatoria")
        @Size(max = 30, message = "La pasarela no puede superar 30 caracteres")
        String pasarela,
        @NotBlank(message = "La referencia es obligatoria")
        @Size(max = 100, message = "La referencia no puede superar 100 caracteres")
        String referencia,
        /** PENDIENTE (por defecto), APROBADO o RECHAZADO: simula la respuesta de la pasarela. */
        String estado) {}
