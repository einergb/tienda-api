package com.codeconheiner.tienda.resenas.dto;

import java.time.OffsetDateTime;

public record ResenaResponse(
        Long id,
        Long productoId,
        Long usuarioId,
        String usuario,
        int calificacion,
        String comentario,
        OffsetDateTime creadoEn) {}
