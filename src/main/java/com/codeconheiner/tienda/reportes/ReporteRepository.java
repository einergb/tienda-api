package com.codeconheiner.tienda.reportes;

import com.codeconheiner.tienda.reportes.dto.ResumenVentasResponse;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Consultas agregadas de negocio (solo lectura) para los reportes administrativos. */
@Repository
public class ReporteRepository {

    /** Totales del inventario activo: cantidad de productos, unidades y valor. */
    public record TotalesInventario(int productos, int unidades, BigDecimal valor) {}

    private record Totales(int pedidos, BigDecimal ingresos) {}

    private final JdbcTemplate jdbc;

    public ReporteRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public ResumenVentasResponse resumen(OffsetDateTime desde, OffsetDateTime hasta) {
        Totales totales = jdbc.queryForObject("""
                SELECT COUNT(*)::int AS pedidos, COALESCE(SUM(total), 0) AS ingresos
                FROM pedidos WHERE creado_en >= ? AND creado_en <= ?
                """, (rs, n) -> new Totales(rs.getInt("pedidos"), rs.getBigDecimal("ingresos")),
                desde, hasta);
        int pagados = jdbc.queryForObject("""
                SELECT COUNT(*)::int FROM pedidos
                WHERE creado_en >= ? AND creado_en <= ?
                  AND estado IN ('PAGADO','EN_PREPARACION','ENVIADO','ENTREGADO')
                """, Integer.class, desde, hasta);

        Map<String, Integer> porEstado = new LinkedHashMap<>();
        jdbc.query("""
                SELECT estado, COUNT(*)::int AS cantidad FROM pedidos
                WHERE creado_en >= ? AND creado_en <= ? GROUP BY estado ORDER BY estado
                """, (rs, n) -> porEstado.put(rs.getString("estado"), rs.getInt("cantidad")),
                desde, hasta);

        List<ResumenVentasResponse.TopProducto> top = jdbc.query("""
                SELECT it.producto_id, p.nombre, SUM(it.cantidad)::int AS unidades,
                       SUM(it.subtotal) AS ingresos
                FROM pedido_items it
                JOIN pedidos pe ON pe.id = it.pedido_id
                JOIN productos p ON p.id = it.producto_id
                WHERE pe.creado_en >= ? AND pe.creado_en <= ? AND pe.estado <> 'CANCELADO'
                GROUP BY it.producto_id, p.nombre
                ORDER BY ingresos DESC, unidades DESC
                LIMIT 5
                """, (rs, n) -> new ResumenVentasResponse.TopProducto(
                        rs.getLong("producto_id"), rs.getString("nombre"),
                        rs.getInt("unidades"), rs.getBigDecimal("ingresos")),
                desde, hasta);

        List<ResumenVentasResponse.VentaDiaria> porDia = jdbc.query("""
                SELECT to_char(date_trunc('day', creado_en), 'YYYY-MM-DD') AS dia,
                       COUNT(*)::int AS pedidos, COALESCE(SUM(total), 0) AS ingresos
                FROM pedidos
                WHERE creado_en >= ? AND creado_en <= ? AND estado <> 'CANCELADO'
                GROUP BY 1 ORDER BY 1
                """, (rs, n) -> new ResumenVentasResponse.VentaDiaria(
                        rs.getString("dia"), rs.getInt("pedidos"), rs.getBigDecimal("ingresos")),
                desde, hasta);

        BigDecimal ingresos = totales == null || totales.ingresos() == null
                ? BigDecimal.ZERO : totales.ingresos();
        int totalPedidos = totales == null ? 0 : totales.pedidos();
        BigDecimal ticket = totalPedidos == 0 ? BigDecimal.ZERO
                : ingresos.divide(BigDecimal.valueOf(totalPedidos), 0, java.math.RoundingMode.HALF_UP);
        return new ResumenVentasResponse(desde, hasta, totalPedidos, pagados,
                ingresos, ticket, Map.copyOf(porEstado), top, porDia);
    }

    /** Valor del inventario activo (stock físico × precio) para el reporte administrativo. */
    public TotalesInventario totalesInventario() {
        TotalesInventario totales = jdbc.queryForObject("""
                SELECT COUNT(*)::int AS productos, COALESCE(SUM(i.stock), 0)::int AS unidades,
                       COALESCE(SUM(i.stock * p.precio), 0) AS valor
                FROM inventario i JOIN productos p ON p.id = i.producto_id
                WHERE p.activo
                """, (rs, n) -> new TotalesInventario(rs.getInt("productos"), rs.getInt("unidades"),
                        rs.getBigDecimal("valor")));
        return totales == null ? new TotalesInventario(0, 0, BigDecimal.ZERO) : totales;
    }
}
