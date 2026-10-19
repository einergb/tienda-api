package com.codeconheiner.tienda.pedidos;

import com.codeconheiner.tienda.direcciones.dto.DireccionResponse;
import com.codeconheiner.tienda.pedidos.dto.PedidoItemResponse;
import com.codeconheiner.tienda.pedidos.dto.PedidoResponse;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class PedidoRepository {

    private static final RowMapper<PedidoItemResponse> ITEM_MAPPER = (rs, n) -> new PedidoItemResponse(
            rs.getLong("id"),
            rs.getLong("producto_id"),
            rs.getString("sku"),
            rs.getString("nombre_producto"),
            rs.getBigDecimal("precio_unitario"),
            rs.getInt("cantidad"),
            rs.getBigDecimal("subtotal"));

    private static final String COLUMNAS =
            "id, numero, estado, subtotal, descuento, iva, costo_envio, total, reserva_expira_en, creado_en";

    /** Mapper base: los items se hidratan despues con obtenerItems (evita N+1 dentro del mapper). */
    private static final RowMapper<PedidoResponse> MAPPER = (rs, n) -> new PedidoResponse(
            rs.getLong("id"),
            rs.getString("numero"),
            rs.getString("estado"),
            rs.getBigDecimal("subtotal"),
            rs.getBigDecimal("descuento"),
            rs.getBigDecimal("iva"),
            rs.getBigDecimal("costo_envio"),
            rs.getBigDecimal("total"),
            rs.getObject("reserva_expira_en", OffsetDateTime.class),
            rs.getObject("creado_en", OffsetDateTime.class),
            List.of());

    private final JdbcTemplate jdbc;

    public PedidoRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<PedidoResponse> listarPorUsuario(Long usuarioId) {
        return conItems(jdbc.query("SELECT " + COLUMNAS + " FROM pedidos WHERE usuario_id = ? ORDER BY id DESC",
                MAPPER, usuarioId));
    }

    public Optional<PedidoResponse> obtenerPorId(Long usuarioId, Long pedidoId) {
        return conItems(jdbc.query("SELECT " + COLUMNAS + " FROM pedidos WHERE id = ? AND usuario_id = ?",
                MAPPER, pedidoId, usuarioId)).stream().findFirst();
    }

    /** Listado administrativo; estado opcional para filtrar. */
    public List<PedidoResponse> listarTodos(String estado) {
        if (estado == null || estado.isBlank()) {
            return conItems(jdbc.query("SELECT " + COLUMNAS + " FROM pedidos ORDER BY id DESC LIMIT 500", MAPPER));
        }
        return conItems(jdbc.query("SELECT " + COLUMNAS + " FROM pedidos WHERE estado = ? ORDER BY id DESC LIMIT 500",
                MAPPER, estado.trim().toUpperCase()));
    }

    /** Vista administrativa: no filtra por usuario. */
    public Optional<PedidoResponse> obtenerPorIdAdmin(Long pedidoId) {
        return conItems(jdbc.query("SELECT " + COLUMNAS + " FROM pedidos WHERE id = ?", MAPPER, pedidoId))
                .stream().findFirst();
    }

    /** Cambia el estado; la maquina de estados y el efecto sobre inventario/cupones los aplica V3. */
    public int cambiarEstado(Long pedidoId, String estado) {
        return jdbc.update("UPDATE pedidos SET estado = ? WHERE id = ?", estado, pedidoId);
    }

    /**
     * Crea el pedido con los totales calculados en la API. El trigger de V3 genera el numero,
     * fija la expiracion de la reserva y valida/aplica el cupon.
     */
    public Long crear(Long usuarioId, DireccionResponse dir, BigDecimal subtotal, BigDecimal descuento,
            BigDecimal iva, BigDecimal costoEnvio, BigDecimal total, Long cuponId) {
        return jdbc.queryForObject("""
                INSERT INTO pedidos (numero, usuario_id, estado, subtotal, descuento, iva, costo_envio, total,
                                     cupon_id, envio_destinatario, envio_telefono, envio_departamento,
                                     envio_ciudad, envio_direccion)
                VALUES (NULL, ?, 'PENDIENTE', ?, ?, ?, ?, ?, ?,
                        ?, ?, ?, ?, ?)
                RETURNING id
                """, Long.class, usuarioId, subtotal, descuento, iva, costoEnvio, total, cuponId,
                dir.destinatario(), dir.telefono(), dir.departamento(), dir.ciudad(), dir.direccion());
    }

    /** El trigger de V3 completa sku/nombre/precio/subtotal y reserva el stock. */
    public void agregarItem(Long pedidoId, Long productoId, Integer cantidad) {
        jdbc.update("INSERT INTO pedido_items (pedido_id, producto_id, cantidad) VALUES (?, ?, ?)",
                pedidoId, productoId, cantidad);
    }

    public List<PedidoItemResponse> obtenerItems(Long pedidoId) {
        return jdbc.query("""
                SELECT id, producto_id, sku, nombre_producto, precio_unitario, cantidad, subtotal
                FROM pedido_items WHERE pedido_id = ?
                ORDER BY id
                """, ITEM_MAPPER, pedidoId);
    }

    private List<PedidoResponse> conItems(List<PedidoResponse> pedidos) {
        return pedidos.stream()
                .map(p -> new PedidoResponse(p.id(), p.numero(), p.estado(), p.subtotal(), p.descuento(), p.iva(),
                        p.costoEnvio(), p.total(), p.reservaExpiraEn(), p.creadoEn(), obtenerItems(p.id())))
                .toList();
    }
}
