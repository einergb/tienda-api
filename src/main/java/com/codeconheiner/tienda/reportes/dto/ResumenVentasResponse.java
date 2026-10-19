package com.codeconheiner.tienda.reportes.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/** Resumen de ventas del rango consultado, para el panel administrativo. */
public record ResumenVentasResponse(
        OffsetDateTime desde,
        OffsetDateTime hasta,
        int pedidos,
        int pedidosPagados,
        BigDecimal ingresos,
        BigDecimal ticketPromedio,
        Map<String, Integer> porEstado,
        List<TopProducto> topProductos,
        List<VentaDiaria> ventasPorDia) {

    public record TopProducto(Long productoId, String nombre, int unidades, BigDecimal ingresos) {}

    public record VentaDiaria(String dia, int pedidos, BigDecimal ingresos) {}
}
