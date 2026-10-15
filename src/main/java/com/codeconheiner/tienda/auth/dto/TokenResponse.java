package com.codeconheiner.tienda.auth.dto;

public record TokenResponse(String token, String tipo, long expiraEnSegundos, String nombre, String rol) {}
