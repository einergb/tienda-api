package com.codeconheiner.tienda.inventario.dto;

/** Productos activos cuyo stock disponible ya no supera el stock minimo (vista V3). */
public record StockBajoResponse(
        Long productoId,
        String sku,
        String nombre,
        Integer stock,
        Integer stockReservado,
        Integer stockMinimo) {}
