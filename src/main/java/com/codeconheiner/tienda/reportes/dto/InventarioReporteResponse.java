package com.codeconheiner.tienda.reportes.dto;

import com.codeconheiner.tienda.inventario.dto.StockBajoResponse;
import java.math.BigDecimal;
import java.util.List;

/** Estado del inventario activo: unidades, valor y productos bajo el umbral mínimo. */
public record InventarioReporteResponse(
        int productos,
        int unidades,
        BigDecimal valorInventario,
        List<StockBajoResponse> bajoUmbral) {}
