package com.codeconheiner.tienda.usuarios;

public record Usuario(Long id, String nombre, String email, String passwordHash, String rol, boolean activo,
        boolean emailVerificado, String estado) {
    public Usuario(Long id, String nombre, String email, String passwordHash, String rol, boolean activo) {
        this(id, nombre, email, passwordHash, rol, activo, true, "ACTIVO");
    }
}
