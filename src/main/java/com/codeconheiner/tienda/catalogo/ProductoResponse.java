package com.codeconheiner.tienda.catalogo;

import com.fasterxml.jackson.annotation.JsonIgnore;
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
        Map<String, String> atributos,
        /** Clave del objeto en el almacenamiento: nunca se expone, solo se usa para calcular imagenUrl. */
        @JsonIgnore String imagenKey,
        /** URL publica de la imagen principal (null si el producto no tiene imagen). */
        String imagenUrl) {

    public ProductoResponse(Long id, String sku, String nombre, String slug, String descripcion, BigDecimal precio,
            Long categoriaId, String categoria, Long marcaId, String marca, Integer garantiaMeses, int stockDisponible,
            boolean activo, List<ImagenProductoResponse> imagenes, Map<String, String> atributos) {
        this(id, sku, nombre, slug, descripcion, precio, categoriaId, categoria, marcaId, marca, garantiaMeses,
                stockDisponible, activo, imagenes, atributos, null, null);
    }
}
