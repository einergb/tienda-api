package com.codeconheiner.tienda.envios;

import com.codeconheiner.tienda.envios.dto.EnvioResponse;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class EnvioRepository {

    private static final RowMapper<EnvioResponse> MAPPER = (rs, n) -> new EnvioResponse(
            rs.getLong("id"),
            rs.getLong("pedido_id"),
            rs.getString("transportadora"),
            rs.getString("guia"),
            rs.getString("estado"),
            rs.getObject("enviado_en", OffsetDateTime.class),
            rs.getObject("entregado_en", OffsetDateTime.class));

    private static final String COLUMNAS =
            "id, pedido_id, transportadora, guia, estado, enviado_en, entregado_en";

    private final JdbcTemplate jdbc;

    public EnvioRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<EnvioResponse> obtenerPorPedido(Long pedidoId) {
        return jdbc.query("SELECT " + COLUMNAS + " FROM envios WHERE pedido_id = ?", MAPPER, pedidoId)
                .stream().findFirst();
    }

    public Optional<EnvioResponse> obtenerPorId(Long id) {
        return jdbc.query("SELECT " + COLUMNAS + " FROM envios WHERE id = ?", MAPPER, id).stream().findFirst();
    }

    public List<EnvioResponse> listar(String estado) {
        if (estado == null || estado.isBlank()) {
            return jdbc.query("SELECT " + COLUMNAS + " FROM envios ORDER BY id DESC LIMIT 500", MAPPER);
        }
        return jdbc.query("SELECT " + COLUMNAS + " FROM envios WHERE estado = ? ORDER BY id DESC LIMIT 500",
                MAPPER, estado.trim().toUpperCase());
    }

    /** El trigger de V3 valida que el pedido este pagado y que despacho lleve guia/transportadora. */
    public EnvioResponse crear(Long pedidoId, String transportadora, String guia, String estado) {
        Long id = jdbc.queryForObject("""
                INSERT INTO envios (pedido_id, transportadora, guia, estado)
                VALUES (?, ?, ?, ?)
                RETURNING id
                """, Long.class, pedidoId, transportadora, guia, estado);
        return obtenerPorId(id).orElseThrow(() -> new IllegalStateException("No se pudo leer el envío creado"));
    }

    /** @return filas afectadas (0 si el envio no existe); el trigger setea enviado_en/entregado_en. */
    public int actualizar(Long id, String estado, String transportadora, String guia) {
        return jdbc.update("""
                UPDATE envios
                SET estado = ?, transportadora = COALESCE(?, transportadora), guia = COALESCE(?, guia)
                WHERE id = ?
                """, estado, transportadora, guia, id);
    }
}
