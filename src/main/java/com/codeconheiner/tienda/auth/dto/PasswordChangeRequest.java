package com.codeconheiner.tienda.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordChangeRequest(
        @NotBlank(message = "La contraseña actual es obligatoria")
        @Size(max = 72, message = "La contraseña no puede superar 72 caracteres")
        String passwordActual,
        @NotBlank(message = "La contraseña nueva es obligatoria")
        @Size(min = 8, max = 72, message = "La contraseña debe tener entre 8 y 72 caracteres")
        String passwordNuevo) {}
