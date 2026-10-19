package com.codeconheiner.tienda.usuarios;

import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class UsuarioRepository {

    private static final RowMapper<Usuario> MAPPER = (rs, i) -> new Usuario(
            rs.getLong("id"),
            rs.getString("nombre"),
            rs.getString("email"),
            rs.getString("password_hash"),
            rs.getString("rol"),
            rs.getBoolean("activo"));

    private final JdbcTemplate jdbc;

    public UsuarioRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Usuario> buscarPorEmail(String email) {
        return jdbc.query("""
                SELECT u.id, u.nombre, u.email, u.password_hash, r.nombre AS rol, u.activo
                FROM usuarios u JOIN roles r ON r.id = u.rol_id
                WHERE LOWER(u.email) = LOWER(?)
                """, MAPPER, email).stream().findFirst();
    }

    public boolean existePorEmail(String email) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM usuarios WHERE LOWER(email) = LOWER(?)",
                Integer.class, email);
        return n != null && n > 0;
    }

    public Optional<Usuario> buscarPorId(Long id) {
        return jdbc.query("""
                SELECT u.id, u.nombre, u.email, u.password_hash, r.nombre AS rol, u.activo
                FROM usuarios u JOIN roles r ON r.id = u.rol_id
                WHERE u.id = ?
                """, MAPPER, id).stream().findFirst();
    }

    public void actualizarPassword(Long id, String passwordHash) {
        jdbc.update("UPDATE usuarios SET password_hash = ? WHERE id = ?", passwordHash, id);
    }

    public Usuario crear(String nombre, String email, String passwordHash, String rol) {
        Long id = jdbc.queryForObject("""
                INSERT INTO usuarios (nombre, email, password_hash, rol_id)
                VALUES (?, ?, ?, (SELECT id FROM roles WHERE nombre = ?))
                RETURNING id
                """, Long.class, nombre, email, passwordHash, rol);
        return new Usuario(id, nombre, email, passwordHash, rol, true);
    }
}
