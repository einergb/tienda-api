package com.codeconheiner.tienda.pagos.pasarela;

import java.math.BigDecimal;

/**
 * Datos de la transacción que el backend envía a la pasarela. La clave de idempotencia
 * se genera una sola vez por intento de pago y se reutiliza en cada reintento, para que
 * la pasarela no procese dos veces la misma operación.
 */
public record CrearTransaccion(
        String idempotencyKey,
        String referencia,
        BigDecimal monto,
        String moneda,
        String email,
        Long pedidoId) {}
