package com.codeconheiner.tienda.pagos.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Solicitud de sesión de pago contra una pasarela real o simulada.
 * {@code claveIdempotencia} es opcional: si el cliente reintenta, debe enviar la clave que
 * recibió para que no se duplique la transacción.
 */
public record PagoConPasarelaRequest(
        @NotNull(message = "El pedido es obligatorio")
        Long pedidoId,
        @NotNull(message = "La pasarela es obligatoria")
        String pasarela,
        @Size(max = 64, message = "La clave de idempotencia no puede superar 64 caracteres")
        String claveIdempotencia) {}
