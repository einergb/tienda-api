package com.codeconheiner.tienda.carrito.dto;

import java.math.BigDecimal;

public record CarritoItemResponse(
        Long id,
        Long productoId,
        String sku,
        String nombreProducto,
        BigDecimal precioUnitario,
        Integer cantidad,
        BigDecimal subtotal,
        Integer stockDisponible) {}
