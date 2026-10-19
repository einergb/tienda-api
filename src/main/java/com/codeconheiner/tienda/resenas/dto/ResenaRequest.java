package com.codeconheiner.tienda.resenas.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ResenaRequest(
        @NotNull(message = "El producto es obligatorio")
        Long productoId,
        @NotNull(message = "La calificación es obligatoria")
        @Min(value = 1, message = "La calificación debe estar entre 1 y 5")
        @Max(value = 5, message = "La calificación debe estar entre 1 y 5")
        Integer calificacion,
        @Size(max = 1000, message = "El comentario no puede superar 1000 caracteres")
        String comentario) {}
