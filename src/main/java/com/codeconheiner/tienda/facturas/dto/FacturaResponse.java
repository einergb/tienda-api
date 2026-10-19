package com.codeconheiner.tienda.facturas.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/** Factura del pedido: datos fiscales de la tienda, del envío, items y totales. */
public record FacturaResponse(
        Long id,
        String numero,
        OffsetDateTime emitidaEn,
        String tienda,
        String moneda,
        Long pedidoId,
        String numeroPedido,
        OffsetDateTime fechaPedido,
        String destinatario,
        String telefono,
        String departamento,
        String ciudad,
        String direccion,
        List<Item> items,
        BigDecimal subtotal,
        BigDecimal descuento,
        BigDecimal iva,
        BigDecimal costoEnvio,
        BigDecimal total) {

    public record Item(String sku, String nombre, Integer cantidad, BigDecimal precioUnitario,
            BigDecimal subtotal) {}
}
