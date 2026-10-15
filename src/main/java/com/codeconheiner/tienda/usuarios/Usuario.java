package com.codeconheiner.tienda.usuarios;

public record Usuario(Long id, String nombre, String email, String passwordHash, String rol, boolean activo) {}
