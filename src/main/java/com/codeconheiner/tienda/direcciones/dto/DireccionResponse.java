package com.codeconheiner.tienda.direcciones.dto;

public record DireccionResponse(
        Long id,
        String alias,
        String destinatario,
        String telefono,
        String departamento,
        String ciudad,
        String direccion,
        String indicaciones,
        boolean esPrincipal) {}
