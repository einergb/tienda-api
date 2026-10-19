package com.codeconheiner.tienda.direcciones.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DireccionRequest(
        @Size(max = 50, message = "El alias no puede superar 50 caracteres")
        String alias,
        @NotBlank(message = "El destinatario es obligatorio")
        @Size(max = 120, message = "El destinatario no puede superar 120 caracteres")
        String destinatario,
        @NotBlank(message = "El teléfono es obligatorio")
        @Size(max = 30, message = "El teléfono no puede superar 30 caracteres")
        String telefono,
        @NotBlank(message = "El departamento es obligatorio")
        @Size(max = 80, message = "El departamento no puede superar 80 caracteres")
        String departamento,
        @NotBlank(message = "La ciudad es obligatoria")
        @Size(max = 80, message = "La ciudad no puede superar 80 caracteres")
        String ciudad,
        @NotBlank(message = "La dirección es obligatoria")
        @Size(max = 200, message = "La dirección no puede superar 200 caracteres")
        String direccion,
        @Size(max = 300, message = "Las indicaciones no pueden superar 300 caracteres")
        String indicaciones,
        Boolean esPrincipal) {}
