package com.codeconheiner.tienda.catalogo;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record ProductoRequest(
        @NotBlank @Size(max = 50) String sku,
        @NotBlank @Size(max = 200) String nombre,
        String descripcion,
        @NotNull @DecimalMin("0") BigDecimal precio,
        @NotNull Long categoriaId,
        Long marcaId,
        @Min(0) Integer garantiaMeses,
        @NotNull @Min(0) Integer stock,
        @Min(0) Integer stockMinimo) {}
