package com.codeconheiner.tienda.carrito;

import com.codeconheiner.tienda.carrito.dto.CarritoItemResponse;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class CarritoRepository {

    private static final RowMapper<CarritoItemResponse> ITEM_MAPPER = (rs, n) -> new CarritoItemResponse(
            rs.getLong("id"),
            rs.getLong("producto_id"),
            rs.getString("sku"),
            rs.getString("nombre_producto"),
            rs.getBigDecimal("precio_unitario"),
            rs.getInt("cantidad"),
            rs.getBigDecimal("subtotal"),
            rs.getInt("stock_disponible"));

    private final JdbcTemplate jdbc;

    public CarritoRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Long> obtenerIdCarritoPorUsuario(Long usuarioId) {
        return jdbc.query("SELECT id FROM carritos WHERE usuario_id = ?",
                (rs, n) -> rs.getLong("id"),
                usuarioId).stream().findFirst();
    }

    public Long crearCarrito(Long usuarioId) {
        return jdbc.queryForObject("INSERT INTO carritos (usuario_id) VALUES (?) RETURNING id",
                Long.class, usuarioId);
    }

    public List<CarritoItemResponse> listarItems(Long carritoId) {
        return jdbc.query("""
                SELECT ci.id, ci.producto_id, p.sku, p.nombre AS nombre_producto, p.precio AS precio_unitario,
                       ci.cantidad, (p.precio * ci.cantidad) AS subtotal,
                       (i.stock - i.stock_reservado) AS stock_disponible
                FROM carrito_items ci
                JOIN productos p ON p.id = ci.producto_id
                JOIN inventario i ON i.producto_id = p.id
                WHERE ci.carrito_id = ?
                ORDER BY ci.id
                """, ITEM_MAPPER, carritoId);
    }

    public void agregarItem(Long carritoId, Long productoId, Integer cantidad) {
        jdbc.update("""
                INSERT INTO carrito_items (carrito_id, producto_id, cantidad)
                VALUES (?, ?, ?)
                ON CONFLICT (carrito_id, producto_id)
                DO UPDATE SET cantidad = carrito_items.cantidad + EXCLUDED.cantidad
                """, carritoId, productoId, cantidad);
    }

    public void actualizarCantidad(Long carritoId, Long productoId, Integer cantidad) {
        jdbc.update("UPDATE carrito_items SET cantidad = ? WHERE carrito_id = ? AND producto_id = ?",
                cantidad, carritoId, productoId);
    }

    public void eliminarItem(Long carritoId, Long productoId) {
        jdbc.update("DELETE FROM carrito_items WHERE carrito_id = ? AND producto_id = ?",
                carritoId, productoId);
    }

    public void vaciarCarrito(Long carritoId) {
        jdbc.update("DELETE FROM carrito_items WHERE carrito_id = ?", carritoId);
    }

    public Optional<BigDecimal> obtenerSubtotal(Long carritoId) {
        return jdbc.query("""
                SELECT COALESCE(SUM(p.precio * ci.cantidad), 0) AS subtotal
                FROM carrito_items ci
                JOIN productos p ON p.id = ci.producto_id
                WHERE ci.carrito_id = ?
                """, (rs, n) -> rs.getBigDecimal("subtotal"), carritoId).stream().findFirst();
    }
}
