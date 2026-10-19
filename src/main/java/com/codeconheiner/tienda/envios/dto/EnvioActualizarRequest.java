package com.codeconheiner.tienda.envios.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EnvioActualizarRequest(
        @NotBlank(message = "El estado es obligatorio")
        @Size(max = 15, message = "El estado no puede superar 15 caracteres")
        String estado,
        @Size(max = 80, message = "La transportadora no puede superar 80 caracteres")
        String transportadora,
        @Size(max = 80, message = "La guía no puede superar 80 caracteres")
        String guia) {}
