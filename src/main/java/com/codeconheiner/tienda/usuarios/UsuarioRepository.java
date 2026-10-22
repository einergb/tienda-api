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
            rs.getBoolean("activo"),
            rs.getBoolean("email_verificado"),
            rs.getString("estado"));

    private final JdbcTemplate jdbc;

    public UsuarioRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Usuario> buscarPorEmail(String email) {
        return jdbc.query("""
                SELECT u.id, u.nombre, u.email, u.password_hash, r.nombre AS rol, u.activo, u.email_verificado, u.estado
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
                SELECT u.id, u.nombre, u.email, u.password_hash, r.nombre AS rol, u.activo, u.email_verificado, u.estado
                FROM usuarios u JOIN roles r ON r.id = u.rol_id
                WHERE u.id = ?
                """, MAPPER, id).stream().findFirst();
    }

    public void actualizarPassword(Long id, String passwordHash) {
        jdbc.update("UPDATE usuarios SET password_hash = ? WHERE id = ?", passwordHash, id);
    }

    public Usuario crear(String nombre, String email, String passwordHash, String rol) {
        Long id = jdbc.queryForObject("""
                INSERT INTO usuarios (nombre, email, password_hash, rol_id, activo, email_verificado, estado)
                VALUES (?, ?, ?, (SELECT id FROM roles WHERE nombre = ?), FALSE, FALSE, 'PENDIENTE')
                RETURNING id
                """, Long.class, nombre, email, passwordHash, rol);
        return new Usuario(id, nombre, email, passwordHash, rol, false, false, "PENDIENTE");
    }

    public Usuario crearAdmin(String nombre, String email, String passwordHash) {
        Long id = jdbc.queryForObject("""
                INSERT INTO usuarios (nombre, email, password_hash, rol_id, activo, email_verificado, estado)
                VALUES (?, ?, ?, (SELECT id FROM roles WHERE nombre = 'ADMIN'), TRUE, TRUE, 'ACTIVO')
                RETURNING id
                """, Long.class, nombre, email, passwordHash);
        return new Usuario(id, nombre, email, passwordHash, "ADMIN", true, true, "ACTIVO");
    }

    public Usuario crearCompleto(String nombre, String primerNombre, String segundoNombre, String primerApellido,
            String segundoApellido, String email, String telefono, String passwordHash, String rol) {
        Long id = jdbc.queryForObject("""
                INSERT INTO usuarios (nombre, primer_nombre, segundo_nombre, primer_apellido, segundo_apellido,
                                      email, password_hash, telefono, rol_id, activo, email_verificado, estado)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, (SELECT id FROM roles WHERE nombre = ?), FALSE, FALSE, 'PENDIENTE')
                RETURNING id
                """, Long.class, nombre, primerNombre, segundoNombre, primerApellido, segundoApellido,
                email, passwordHash, telefono, rol);
        return new Usuario(id, nombre, email, passwordHash, rol, false, false, "PENDIENTE");
    }

    public int activarYVerificar(Long id) {
        return jdbc.update("UPDATE usuarios SET activo = TRUE, email_verificado = TRUE, estado = 'ACTIVO' WHERE id = ?",
                id);
    }
}
