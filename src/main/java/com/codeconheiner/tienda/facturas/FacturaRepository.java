package com.codeconheiner.tienda.facturas;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class FacturaRepository {

    private static final RowMapper<FacturaEmision> MAPPER = (rs, n) -> new FacturaEmision(
            rs.getLong("id"),
            rs.getString("numero"),
            rs.getBigDecimal("total"),
            rs.getObject("emitida_en", OffsetDateTime.class));

    private final JdbcTemplate jdbc;

    public FacturaRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<FacturaEmision> buscarPorPedido(Long pedidoId) {
        return jdbc.query("SELECT id, numero, total, emitida_en FROM facturas WHERE pedido_id = ?",
                        MAPPER, pedidoId).stream().findFirst();
    }

    /** Copia de la dirección de envío registrada al crear el pedido (V1 la guarda en pedidos). */
    public Optional<EnvioCopia> buscarEnvioPedido(Long pedidoId) {
        return jdbc.query("""
                SELECT envio_destinatario AS destinatario, envio_telefono AS telefono,
                       envio_departamento AS departamento, envio_ciudad AS ciudad,
                       envio_direccion AS direccion
                FROM pedidos WHERE id = ?
                """, (rs, n) -> new EnvioCopia(
                        rs.getString("destinatario"), rs.getString("telefono"),
                        rs.getString("departamento"), rs.getString("ciudad"),
                        rs.getString("direccion")), pedidoId).stream().findFirst();
    }

    /**
     * Emisión idempotente: la primera llamada crea la factura con numeración propia
     * (FAC-AAAA-NNNNNN); las siguientes devuelven la factura existente sin duplicar.
     */
    public FacturaEmision emitir(Long pedidoId, BigDecimal total) {
        Long id = jdbc.queryForObject("""
                INSERT INTO facturas (pedido_id, numero, total)
                VALUES (?, 'FAC-' || to_char(now(), 'YYYY') || '-'
                           || lpad(nextval('seq_factura_numero')::text, 6, '0'), ?)
                ON CONFLICT (pedido_id) DO UPDATE SET pedido_id = EXCLUDED.pedido_id
                RETURNING id
                """, Long.class, pedidoId, total);
        return jdbc.query("SELECT id, numero, total, emitida_en FROM facturas WHERE id = ?", MAPPER, id)
                .stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("No se pudo leer la factura recién emitida"));
    }
}
