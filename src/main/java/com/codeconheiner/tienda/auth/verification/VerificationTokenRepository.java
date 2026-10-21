package com.codeconheiner.tienda.auth.verification;

import com.codeconheiner.tienda.common.Hashes;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class VerificationTokenRepository {

    private final JdbcTemplate jdbc;

    public VerificationTokenRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void crear(Long usuarioId, String token, OffsetDateTime expiraEn) {
        String hash = Hashes.sha256Hex(token);
        jdbc.update("INSERT INTO usuarios_verificacion (usuario_id, token_hash, expira_en) VALUES (?, ?, ?)",
                usuarioId, hash, expiraEn);
    }

    public Optional<Long> consumir(String token, OffsetDateTime ahora) {
        String hash = Hashes.sha256Hex(token);
        return jdbc.query("""
                UPDATE usuarios_verificacion
                SET usado_en = ?
                WHERE token_hash = ? AND usado_en IS NULL AND expira_en > ?
                RETURNING usuario_id
                """, (rs, i) -> rs.getLong("usuario_id"), ahora, hash, ahora).stream().findFirst();
    }

    public int contarRecientes(Long usuarioId, OffsetDateTime desde) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM usuarios_verificacion WHERE usuario_id = ? AND creado_en >= ?",
                Integer.class, usuarioId, desde);
        return n == null ? 0 : n;
    }

    public void invalidarTodos(Long usuarioId) {
        jdbc.update("UPDATE usuarios_verificacion SET usado_en = now() WHERE usuario_id = ? AND usado_en IS NULL",
                usuarioId);
    }
}
