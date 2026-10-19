package com.codeconheiner.tienda.direcciones;

import com.codeconheiner.tienda.direcciones.dto.DireccionRequest;
import com.codeconheiner.tienda.direcciones.dto.DireccionResponse;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class DireccionRepository {

    private static final RowMapper<DireccionResponse> MAPPER = (rs, n) -> new DireccionResponse(
            rs.getLong("id"),
            rs.getString("alias"),
            rs.getString("destinatario"),
            rs.getString("telefono"),
            rs.getString("departamento"),
            rs.getString("ciudad"),
            rs.getString("direccion"),
            rs.getString("indicaciones"),
            rs.getBoolean("es_principal"));

    private static final String COLUMNAS =
            "id, alias, destinatario, telefono, departamento, ciudad, direccion, indicaciones, es_principal";

    private final JdbcTemplate jdbc;

    public DireccionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<DireccionResponse> listarPorUsuario(Long usuarioId) {
        return jdbc.query("SELECT " + COLUMNAS
                + " FROM direcciones WHERE usuario_id = ? ORDER BY es_principal DESC, id", MAPPER, usuarioId);
    }

    public Optional<DireccionResponse> buscarPropia(Long id, Long usuarioId) {
        return jdbc.query("SELECT " + COLUMNAS + " FROM direcciones WHERE id = ? AND usuario_id = ?",
                MAPPER, id, usuarioId).stream().findFirst();
    }

    public Long crear(Long usuarioId, DireccionRequest r) {
        boolean principal = Boolean.TRUE.equals(r.esPrincipal());
        if (principal) {
            // El índice único parcial ux_direcciones_principal (V6) exige que no
            // quede otra principal antes de insertar; el trigger de V3 es la red
            // de seguridad para escrituras que no pasen por aquí.
            desmarcarPrincipales(usuarioId, null);
        }
        return jdbc.queryForObject("""
                INSERT INTO direcciones (usuario_id, alias, destinatario, telefono, departamento, ciudad,
                                         direccion, indicaciones, es_principal)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING id
                """, Long.class, usuarioId, r.alias(), r.destinatario().trim(), r.telefono().trim(),
                r.departamento().trim(), r.ciudad().trim(), r.direccion().trim(), r.indicaciones(),
                principal);
    }

    /** @return filas afectadas (0 si la direccion no pertenece al usuario) */
    public int actualizar(Long id, Long usuarioId, DireccionRequest r) {
        boolean principal = Boolean.TRUE.equals(r.esPrincipal());
        if (principal) {
            desmarcarPrincipales(usuarioId, id);
        }
        return jdbc.update("""
                UPDATE direcciones
                SET alias = ?, destinatario = ?, telefono = ?, departamento = ?, ciudad = ?, direccion = ?,
                    indicaciones = ?, es_principal = ?
                WHERE id = ? AND usuario_id = ?
                """, r.alias(), r.destinatario().trim(), r.telefono().trim(), r.departamento().trim(),
                r.ciudad().trim(), r.direccion().trim(), r.indicaciones(), principal,
                id, usuarioId);
    }

    private void desmarcarPrincipales(Long usuarioId, Long exceptoId) {
        if (exceptoId == null) {
            jdbc.update("UPDATE direcciones SET es_principal = FALSE WHERE usuario_id = ? AND es_principal",
                    usuarioId);
        } else {
            jdbc.update("""
                    UPDATE direcciones SET es_principal = FALSE
                    WHERE usuario_id = ? AND es_principal AND id <> ?
                    """, usuarioId, exceptoId);
        }
    }

    public int eliminar(Long id, Long usuarioId) {
        return jdbc.update("DELETE FROM direcciones WHERE id = ? AND usuario_id = ?", id, usuarioId);
    }
}
