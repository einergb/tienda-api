package com.codeconheiner.tienda.pedidos.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PedidoEstadoRequest(
        @NotBlank(message = "El estado es obligatorio")
        @Size(max = 20, message = "El estado no puede superar 20 caracteres")
        String estado) {}
