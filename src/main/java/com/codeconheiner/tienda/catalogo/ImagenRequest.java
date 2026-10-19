package com.codeconheiner.tienda.catalogo;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ImagenRequest(
        @NotBlank(message = "La URL es obligatoria")
        @Size(max = 500, message = "La URL no puede superar 500 caracteres")
        String url,
        @Min(value = 0, message = "El orden no puede ser negativo")
        Integer orden,
        Boolean esPrincipal) {}
