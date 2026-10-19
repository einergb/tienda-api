package com.codeconheiner.tienda.auth;

import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * Persistencia de refresh tokens (solo se guarda el hash SHA-256).
 *
 * Cada inicio de sesión abre una familia de tokens ({@code sesion_id}) y cada
 * rotación encadena el token nuevo por {@code reemplazado_por}. Un token ya
 * rotado ({@code usado_en} no nulo) que vuelva a presentarse es un reuso: la
 * familia entera se revoca.
 */
@Repository
public class RefreshTokenRepository implements RefreshTokenStore {

    private static final RowMapper<Sesion> MAPPER = (rs, n) -> {
        OffsetDateTime usado = rs.getObject("usado_en", OffsetDateTime.class);
        return new Sesion(
                rs.getLong("id"),
                rs.getLong("usuario_id"),
                rs.getLong("sesion_id"),
                rs.getObject("expira_en", OffsetDateTime.class),
                rs.getBoolean("revocado"),
                usado != null,
                usado,
                rs.getObject("revocado_en", OffsetDateTime.class));
    };

    private static final String COLUMNAS =
            "id, usuario_id, sesion_id, expira_en, revocado, usado_en, revocado_en";

    private final JdbcTemplate jdbc;

    public RefreshTokenRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Long crearFamilia(Long usuarioId, String tokenHash, OffsetDateTime expiraEn) {
        // El trigger de V6 asigna sesion_id = id cuando no se indica familia.
        return jdbc.queryForObject("""
                INSERT INTO refresh_tokens (usuario_id, token_hash, expira_en)
                VALUES (?, ?, ?)
                RETURNING id
                """, Long.class, usuarioId, tokenHash, expiraEn);
    }

    @Override
    public Long crearEnFamilia(Long usuarioId, Long sesionId, String tokenHash, OffsetDateTime expiraEn) {
        return jdbc.queryForObject("""
                INSERT INTO refresh_tokens (usuario_id, token_hash, expira_en, sesion_id)
                VALUES (?, ?, ?, ?)
                RETURNING id
                """, Long.class, usuarioId, tokenHash, expiraEn, sesionId);
    }

    @Override
    public Optional<Sesion> buscarPorHash(String tokenHash) {
        return jdbc.query("SELECT " + COLUMNAS + " FROM refresh_tokens WHERE token_hash = ?",
                MAPPER, tokenHash).stream().findFirst();
    }

    @Override
    public int marcarUsado(String tokenHash, Long reemplazadoPor) {
        return jdbc.update("""
                UPDATE refresh_tokens
                SET usado_en = now(), reemplazado_por = ?
                WHERE token_hash = ? AND NOT revocado AND usado_en IS NULL
                """, reemplazadoPor, tokenHash);
    }

    @Override
    public int revocarFamilia(Long usuarioId, Long sesionId) {
        return jdbc.update("""
                UPDATE refresh_tokens
                SET revocado = TRUE, revocado_en = COALESCE(revocado_en, now())
                WHERE usuario_id = ? AND sesion_id = ? AND NOT revocado
                """, usuarioId, sesionId);
    }

    @Override
    public int revocarTodosDeUsuario(Long usuarioId) {
        return jdbc.update("""
                UPDATE refresh_tokens
                SET revocado = TRUE, revocado_en = COALESCE(revocado_en, now())
                WHERE usuario_id = ? AND NOT revocado
                """, usuarioId);
    }

    @Override
    public int purgarAntiguos(int diasRetencion) {
        return jdbc.update("""
                DELETE FROM refresh_tokens
                WHERE expira_en < now()
                   OR (revocado_en IS NOT NULL AND revocado_en < now() - make_interval(days => ?))
                """, diasRetencion);
    }
}
