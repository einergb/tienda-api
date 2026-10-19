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
        return jdbc.queryForObject("""
                INSERT INTO direcciones (usuario_id, alias, destinatario, telefono, departamento, ciudad,
                                         direccion, indicaciones, es_principal)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING id
                """, Long.class, usuarioId, r.alias(), r.destinatario().trim(), r.telefono().trim(),
                r.departamento().trim(), r.ciudad().trim(), r.direccion().trim(), r.indicaciones(),
                Boolean.TRUE.equals(r.esPrincipal()));
    }

    /** @return filas afectadas (0 si la direccion no pertenece al usuario) */
    public int actualizar(Long id, Long usuarioId, DireccionRequest r) {
        return jdbc.update("""
                UPDATE direcciones
                SET alias = ?, destinatario = ?, telefono = ?, departamento = ?, ciudad = ?, direccion = ?,
                    indicaciones = ?, es_principal = ?
                WHERE id = ? AND usuario_id = ?
                """, r.alias(), r.destinatario().trim(), r.telefono().trim(), r.departamento().trim(),
                r.ciudad().trim(), r.direccion().trim(), r.indicaciones(), Boolean.TRUE.equals(r.esPrincipal()),
                id, usuarioId);
    }

    public int eliminar(Long id, Long usuarioId) {
        return jdbc.update("DELETE FROM direcciones WHERE id = ? AND usuario_id = ?", id, usuarioId);
    }
}
