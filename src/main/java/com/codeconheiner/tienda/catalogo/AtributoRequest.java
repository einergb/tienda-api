package com.codeconheiner.tienda.catalogo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AtributoRequest(
        @NotBlank(message = "La clave es obligatoria")
        @Size(max = 80, message = "La clave no puede superar 80 caracteres")
        String clave,
        @NotBlank(message = "El valor es obligatorio")
        @Size(max = 200, message = "El valor no puede superar 200 caracteres")
        String valor) {}
