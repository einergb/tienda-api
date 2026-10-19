package com.codeconheiner.tienda.pagos.pasarela;

/** Sesión o transacción creada en la pasarela; el frontend la usa para completar el pago. */
public record SesionPago(
        String pasarela,
        String referencia,
        String sesionId,
        String urlPago) {}
