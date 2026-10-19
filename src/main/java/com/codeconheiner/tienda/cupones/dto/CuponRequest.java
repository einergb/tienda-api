package com.codeconheiner.tienda.cupones.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record CuponRequest(
        @NotBlank(message = "El código es obligatorio")
        @Size(max = 40, message = "El código no puede superar 40 caracteres")
        String codigo,
        @NotBlank(message = "El tipo es obligatorio")
        String tipo,
        @NotNull(message = "El valor es obligatorio")
        @DecimalMin(value = "0", inclusive = false, message = "El valor debe ser mayor a 0")
        BigDecimal valor,
        @DecimalMin(value = "0", message = "El monto mínimo no puede ser negativo")
        BigDecimal montoMinimo,
        @Min(value = 1, message = "Los usos máximos deben ser al menos 1")
        Integer usosMaximos,
        OffsetDateTime vigenteDesde,
        OffsetDateTime vigenteHasta,
        Boolean activo) {}
