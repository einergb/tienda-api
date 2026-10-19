package com.codeconheiner.tienda.auth;

import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/** Persistencia de refresh tokens (solo se guarda el hash SHA-256). */
@Repository
public class RefreshTokenRepository {

    public record RefreshToken(Long id, Long usuarioId, OffsetDateTime expiraEn, boolean revocado) {}

    private static final RowMapper<RefreshToken> MAPPER = (rs, n) -> new RefreshToken(
            rs.getLong("id"),
            rs.getLong("usuario_id"),
            rs.getObject("expira_en", OffsetDateTime.class),
            rs.getBoolean("revocado"));

    private final JdbcTemplate jdbc;

    public RefreshTokenRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Long crear(Long usuarioId, String tokenHash, OffsetDateTime expiraEn) {
        return jdbc.queryForObject("""
                INSERT INTO refresh_tokens (usuario_id, token_hash, expira_en)
                VALUES (?, ?, ?)
                RETURNING id
                """, Long.class, usuarioId, tokenHash, expiraEn);
    }

    public Optional<RefreshToken> buscarPorHash(String tokenHash) {
        return jdbc.query("SELECT id, usuario_id, expira_en, revocado FROM refresh_tokens WHERE token_hash = ?",
                MAPPER, tokenHash).stream().findFirst();
    }

    public int revocar(String tokenHash) {
        return jdbc.update("UPDATE refresh_tokens SET revocado = TRUE WHERE token_hash = ? AND NOT revocado",
                tokenHash);
    }

    public int revocarTodosDeUsuario(Long usuarioId) {
        return jdbc.update("UPDATE refresh_tokens SET revocado = TRUE WHERE usuario_id = ? AND NOT revocado",
                usuarioId);
    }
}
