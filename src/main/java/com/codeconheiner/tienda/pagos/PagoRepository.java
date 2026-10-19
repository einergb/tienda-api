package com.codeconheiner.tienda.pagos;

import com.codeconheiner.tienda.pagos.dto.PagoResponse;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class PagoRepository {

    private static final RowMapper<PagoResponse> MAPPER = (rs, n) -> new PagoResponse(
            rs.getLong("id"),
            rs.getLong("pedido_id"),
            rs.getString("pasarela"),
            rs.getString("referencia"),
            rs.getString("estado"),
            rs.getBigDecimal("monto"),
            rs.getObject("creado_en", OffsetDateTime.class));

    private static final String COLUMNAS = "id, pedido_id, pasarela, referencia, estado, monto, creado_en";

    private final JdbcTemplate jdbc;

    public PagoRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<PagoResponse> listarPorPedido(Long pedidoId) {
        return jdbc.query("SELECT " + COLUMNAS + " FROM pagos WHERE pedido_id = ? ORDER BY id DESC",
                MAPPER, pedidoId);
    }

    /**
     * Registra el intento de pago con el monto total del pedido. Los triggers de V3 validan
     * el monto al aprobarlo y marcan el pedido como PAGADO.
     */
    public PagoResponse crear(Long pedidoId, String pasarela, String referencia, String estado, BigDecimal monto) {
        Long id = jdbc.queryForObject("""
                INSERT INTO pagos (pedido_id, pasarela, referencia, estado, monto)
                VALUES (?, ?, ?, ?, ?)
                RETURNING id
                """, Long.class, pedidoId, pasarela, referencia, estado, monto);
        return jdbc.query("SELECT " + COLUMNAS + " FROM pagos WHERE id = ?", MAPPER, id).stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("No se pudo leer el pago recién creado"));
    }
}
