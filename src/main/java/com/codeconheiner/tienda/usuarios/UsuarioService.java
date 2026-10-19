package com.codeconheiner.tienda.usuarios;

import org.springframework.stereotype.Service;

@Service
public class UsuarioService {

    private final UsuarioRepository repo;

    public UsuarioService(UsuarioRepository repo) {
        this.repo = repo;
    }

    public Long obtenerIdPorEmail(String email) {
        return repo.buscarPorEmail(email).map(Usuario::id).orElse(null);
    }
}
