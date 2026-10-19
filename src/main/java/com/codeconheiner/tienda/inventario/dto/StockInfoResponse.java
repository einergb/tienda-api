package com.codeconheiner.tienda.inventario.dto;

public record StockInfoResponse(
        Long productoId,
        Integer stock,
        Integer stockReservado,
        Integer stockDisponible,
        Integer stockMinimo) {}
