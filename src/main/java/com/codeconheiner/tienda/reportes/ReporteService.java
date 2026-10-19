package com.codeconheiner.tienda.reportes;

import com.codeconheiner.tienda.common.ApiException;
import com.codeconheiner.tienda.inventario.InventarioRepository;
import com.codeconheiner.tienda.reportes.dto.InventarioReporteResponse;
import com.codeconheiner.tienda.reportes.dto.ResumenVentasResponse;
import java.time.OffsetDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** Reportes administrativos: ventas por rango de fechas y estado del inventario. */
@Service
public class ReporteService {

    private static final int DIAS_POR_DEFECTO = 30;

    private final ReporteRepository repo;
    private final InventarioRepository inventario;

    public ReporteService(ReporteRepository repo, InventarioRepository inventario) {
        this.repo = repo;
        this.inventario = inventario;
    }

    public ResumenVentasResponse ventas(OffsetDateTime desde, OffsetDateTime hasta) {
        OffsetDateTime fin = hasta == null ? OffsetDateTime.now() : hasta;
        OffsetDateTime inicio = desde == null ? fin.minusDays(DIAS_POR_DEFECTO) : desde;
        if (inicio.isAfter(fin)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "El rango de fechas no es válido");
        }
        return repo.resumen(inicio, fin);
    }

    /** Estado del inventario activo: unidades, valor y productos bajo el umbral mínimo. */
    public InventarioReporteResponse inventario() {
        ReporteRepository.TotalesInventario totales = repo.totalesInventario();
        return new InventarioReporteResponse(totales.productos(), totales.unidades(),
                totales.valor(), inventario.listarStockBajo());
    }
}
