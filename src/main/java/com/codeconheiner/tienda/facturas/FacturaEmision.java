package com.codeconheiner.tienda.facturas;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** Fila de la tabla facturas; la numeración la genera la base de datos. */
public record FacturaEmision(Long id, String numero, BigDecimal total, OffsetDateTime emitidaEn) {}
