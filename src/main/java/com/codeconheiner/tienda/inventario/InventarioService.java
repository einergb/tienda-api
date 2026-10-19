package com.codeconheiner.tienda.inventario;

import com.codeconheiner.tienda.common.ApiException;
import com.codeconheiner.tienda.inventario.dto.StockBajoResponse;
import com.codeconheiner.tienda.inventario.dto.StockInfoResponse;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class InventarioService {

    private final InventarioRepository repo;

    public InventarioService(InventarioRepository repo) {
        this.repo = repo;
    }

    public StockInfoResponse obtenerStock(Long productoId) {
        return repo.obtenerStock(productoId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Producto no encontrado"));
    }

    public List<StockBajoResponse> listarStockBajo() {
        return repo.listarStockBajo();
    }
}
