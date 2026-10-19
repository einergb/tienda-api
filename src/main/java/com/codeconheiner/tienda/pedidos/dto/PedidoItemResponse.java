package com.codeconheiner.tienda.pedidos.dto;

import java.math.BigDecimal;

public record PedidoItemResponse(
        Long id,
        Long productoId,
        String sku,
        String nombreProducto,
        BigDecimal precioUnitario,
        Integer cantidad,
        BigDecimal subtotal) {}
