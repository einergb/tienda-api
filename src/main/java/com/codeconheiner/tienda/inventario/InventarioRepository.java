package com.codeconheiner.tienda.inventario;

import com.codeconheiner.tienda.inventario.dto.StockBajoResponse;
import com.codeconheiner.tienda.inventario.dto.StockInfoResponse;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class InventarioRepository {

    private static final RowMapper<StockInfoResponse> MAPPER = (rs, n) -> new StockInfoResponse(
            rs.getLong("producto_id"),
            rs.getInt("stock"),
            rs.getInt("stock_reservado"),
            rs.getInt("stock_disponible"),
            rs.getInt("stock_minimo"));

    private final JdbcTemplate jdbc;

    public InventarioRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<StockInfoResponse> obtenerStock(Long productoId) {
        return jdbc.query("""
                SELECT producto_id, stock, stock_reservado,
                       (stock - stock_reservado) AS stock_disponible, stock_minimo
                FROM inventario WHERE producto_id = ?
                """, MAPPER, productoId).stream().findFirst();
    }

    /** Productos activos en el umbral de alerta (vista v_stock_bajo de V3). */
    public List<StockBajoResponse> listarStockBajo() {
        return jdbc.query("""
                SELECT id, sku, nombre, stock, stock_reservado, stock_minimo
                FROM v_stock_bajo ORDER BY (stock - stock_reservado), id
                """, (rs, n) -> new StockBajoResponse(
                rs.getLong("id"),
                rs.getString("sku"),
                rs.getString("nombre"),
                rs.getInt("stock"),
                rs.getInt("stock_reservado"),
                rs.getInt("stock_minimo")));
    }
}
