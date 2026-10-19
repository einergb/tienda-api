package com.codeconheiner.tienda.resenas;

import com.codeconheiner.tienda.resenas.dto.ResenaRequest;
import com.codeconheiner.tienda.resenas.dto.ResenaResponse;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class ResenaRepository {

    private static final RowMapper<ResenaResponse> MAPPER = (rs, n) -> new ResenaResponse(
            rs.getLong("id"),
            rs.getLong("producto_id"),
            rs.getLong("usuario_id"),
            rs.getString("usuario"),
            rs.getInt("calificacion"),
            rs.getString("comentario"),
            rs.getObject("creado_en", OffsetDateTime.class));

    private static final String COLUMNAS =
            "r.id, r.producto_id, r.usuario_id, u.nombre AS usuario, r.calificacion, r.comentario, r.creado_en";

    private final JdbcTemplate jdbc;

    public ResenaRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<ResenaResponse> listarPorProducto(Long productoId) {
        return jdbc.query("SELECT " + COLUMNAS
                + " FROM resenas r JOIN usuarios u ON u.id = r.usuario_id"
                + " WHERE r.producto_id = ? ORDER BY r.id DESC", MAPPER, productoId);
    }

    public Optional<ResenaResponse> buscarPropia(Long id, Long usuarioId) {
        return jdbc.query("SELECT " + COLUMNAS
                + " FROM resenas r JOIN usuarios u ON u.id = r.usuario_id"
                + " WHERE r.id = ? AND r.usuario_id = ?", MAPPER, id, usuarioId).stream().findFirst();
    }

    /** El trigger de V3 exige pedido ENTREGADO (compra verificada) antes de insertar. */
    public Long crear(Long usuarioId, ResenaRequest r) {
        return jdbc.queryForObject("""
                INSERT INTO resenas (producto_id, usuario_id, calificacion, comentario)
                VALUES (?, ?, ?, ?)
                RETURNING id
                """, Long.class, r.productoId(), usuarioId, r.calificacion(), r.comentario());
    }

    /** @return filas afectadas (0 si la resena no pertenece al usuario) */
    public int actualizar(Long id, Long usuarioId, ResenaRequest r) {
        return jdbc.update("""
                UPDATE resenas SET calificacion = ?, comentario = ?
                WHERE id = ? AND usuario_id = ?
                """, r.calificacion(), r.comentario(), id, usuarioId);
    }

    public int eliminar(Long id, Long usuarioId) {
        return jdbc.update("DELETE FROM resenas WHERE id = ? AND usuario_id = ?", id, usuarioId);
    }
}
