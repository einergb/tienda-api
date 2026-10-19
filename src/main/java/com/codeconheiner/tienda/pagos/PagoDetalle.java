package com.codeconheiner.tienda.pagos;

import java.math.BigDecimal;

/**
 * Fila interna de pagos con los campos de integración (sesión de la pasarela y clave de
 * idempotencia); no se expone directamente en la API pública (solo en el paquete pagos y
 * sus fakes de prueba).
 */
public record PagoDetalle(Long id, Long pedidoId, String pasarela, String referencia, String estado,
        BigDecimal monto, String sesionId, String idempotencyKey) {}
