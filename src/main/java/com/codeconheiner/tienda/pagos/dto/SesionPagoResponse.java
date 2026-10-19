package com.codeconheiner.tienda.pagos.dto;

/**
 * Datos devueltos al frontend para completar el pago en la pasarela (redirección o
 * checkout embebido). La clave de idempotencia se devuelve para que el cliente pueda
 * reintentar la misma operación sin duplicar el cobro.
 */
public record SesionPagoResponse(
        Long pagoId,
        String pasarela,
        String referencia,
        String claveIdempotencia,
        String sesionId,
        String urlPago) {}
