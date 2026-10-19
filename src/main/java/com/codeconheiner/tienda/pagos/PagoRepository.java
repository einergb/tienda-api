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

    public PagoResponse crear(Long pedidoId, String pasarela, String referencia, String estado, BigDecimal monto) {
        return crear(pedidoId, pasarela, referencia, estado, monto, null);
    }

    /**
     * Registra el intento de pago con el monto total del pedido. Los triggers de V3 validan
     * el monto al aprobarlo y marcan el pedido como PAGADO. La clave de idempotencia se
     * guarda para reutilizarla en los reintentos contra la pasarela.
     */
    public PagoResponse crear(Long pedidoId, String pasarela, String referencia, String estado,
            BigDecimal monto, String idempotencyKey) {
        Long id = jdbc.queryForObject("""
                INSERT INTO pagos (pedido_id, pasarela, referencia, estado, monto, idempotency_key)
                VALUES (?, ?, ?, ?, ?, ?)
                RETURNING id
                """, Long.class, pedidoId, pasarela, referencia, estado, monto, idempotencyKey);
        return jdbc.query("SELECT " + COLUMNAS + " FROM pagos WHERE id = ?", MAPPER, id).stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("No se pudo leer el pago recién creado"));
    }

    private static final String COLUMNAS_DETALLE =
            "id, pedido_id, pasarela, referencia, estado, monto, sesion_id, idempotency_key";

    private static final RowMapper<PagoDetalle> DETALLE = (rs, n) -> new PagoDetalle(
            rs.getLong("id"),
            rs.getLong("pedido_id"),
            rs.getString("pasarela"),
            rs.getString("referencia"),
            rs.getString("estado"),
            rs.getBigDecimal("monto"),
            rs.getString("sesion_id"),
            rs.getString("idempotency_key"));

    /** Reintento seguro: localiza el intento previo por su clave de idempotencia. */
    public java.util.Optional<PagoDetalle> buscarPorIdempotencyKey(String clave) {
        return jdbc.query("SELECT " + COLUMNAS_DETALLE + " FROM pagos WHERE idempotency_key = ?",
                        DETALLE, clave).stream().findFirst();
    }

    /** Los webhooks identifican el pago por la referencia enviada a la pasarela. */
    public java.util.Optional<PagoDetalle> buscarPorReferencia(String referencia) {
        return jdbc.query("SELECT " + COLUMNAS_DETALLE + " FROM pagos WHERE referencia = ?",
                        DETALLE, referencia).stream().findFirst();
    }

    /** Guarda la sesión/transacción devuelta por la pasarela. */
    public void guardarSesion(Long pagoId, String sesionId) {
        jdbc.update("UPDATE pagos SET sesion_id = ? WHERE id = ?", sesionId, pagoId);
    }

    /**
     * Actualiza el estado del intento. Al pasar a APROBADO, el trigger de V3
     * {@code trg_pago_aprobado_update} marca el pedido como PAGADO y descuenta stock.
     *
     * @return filas afectadas (0 si el pago no existe).
     */
    public int actualizarEstado(Long pagoId, String estado) {
        return jdbc.update("UPDATE pagos SET estado = ? WHERE id = ?", estado, pagoId);
    }
}
