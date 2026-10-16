package com.codeconheiner.tienda.catalogo;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record ProductoRequest(
        @NotBlank(message = "El SKU es obligatorio")
        @Size(max = 50, message = "El SKU no puede superar 50 caracteres")
        String sku,
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 200, message = "El nombre no puede superar 200 caracteres")
        String nombre,
        String descripcion,
        @NotNull(message = "El precio es obligatorio")
        @DecimalMin(value = "0", message = "El precio debe ser mayor o igual a 0")
        BigDecimal precio,
        @NotNull(message = "La categoría es obligatoria")
        Long categoriaId,
        Long marcaId,
        @Min(value = 0, message = "La garantía no puede ser negativa")
        Integer garantiaMeses,
        @NotNull(message = "El stock es obligatorio")
        @Min(value = 0, message = "El stock no puede ser negativo")
        Integer stock,
        @Min(value = 0, message = "El stock mínimo no puede ser negativo")
        Integer stockMinimo) {}
