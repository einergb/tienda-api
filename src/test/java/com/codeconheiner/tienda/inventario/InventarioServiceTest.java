package com.codeconheiner.tienda.inventario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.codeconheiner.tienda.common.ApiException;
import com.codeconheiner.tienda.inventario.dto.StockInfoResponse;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class InventarioServiceTest {

    private final FakeInventarioRepository repo = new FakeInventarioRepository();
    private final InventarioService service = new InventarioService(repo);

    @Test
    void obtenerStockDevuelveInformacionDelProducto() {
        repo.agregar(1L, new StockInfoResponse(1L, 10, 2, 8, 5));

        StockInfoResponse respuesta = service.obtenerStock(1L);

        assertThat(respuesta.productoId()).isEqualTo(1L);
        assertThat(respuesta.stock()).isEqualTo(10);
        assertThat(respuesta.stockReservado()).isEqualTo(2);
        assertThat(respuesta.stockDisponible()).isEqualTo(8);
        assertThat(respuesta.stockMinimo()).isEqualTo(5);
    }

    @Test
    void obtenerStockInexistenteDevuelve404() {
        ApiException error = catchThrowableOfType(
                () -> service.obtenerStock(999L), ApiException.class);

        assertThat(error).isNotNull();
        assertThat(error.getStatus().value()).isEqualTo(404);
        assertThat(error).hasMessage("Producto no encontrado");
    }

    private static class FakeInventarioRepository extends InventarioRepository {

        private final Map<Long, StockInfoResponse> datos = new HashMap<>();

        FakeInventarioRepository() {
            super((JdbcTemplate) null);
        }

        void agregar(long productoId, StockInfoResponse stock) {
            datos.put(productoId, stock);
        }

        @Override
        public Optional<StockInfoResponse> obtenerStock(Long productoId) {
            return Optional.ofNullable(datos.get(productoId));
        }
    }
}
