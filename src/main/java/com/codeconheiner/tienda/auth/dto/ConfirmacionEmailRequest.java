package com.codeconheiner.tienda.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record ConfirmacionEmailRequest(
        @NotBlank(message = "El token es obligatorio")
        String token) {
}
