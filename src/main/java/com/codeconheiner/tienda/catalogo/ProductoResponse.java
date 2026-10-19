package com.codeconheiner.tienda.catalogo;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record ProductoResponse(
        Long id,
        String sku,
        String nombre,
        String slug,
        String descripcion,
        BigDecimal precio,
        Long categoriaId,
        String categoria,
        Long marcaId,
        String marca,
        Integer garantiaMeses,
        int stockDisponible,
        boolean activo,
        List<ImagenProductoResponse> imagenes,
        Map<String, String> atributos) {}
