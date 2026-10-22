package com.codeconheiner.tienda.carrito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.codeconheiner.tienda.carrito.dto.CarritoItemRequest;
import com.codeconheiner.tienda.carrito.dto.CarritoItemResponse;
import com.codeconheiner.tienda.carrito.dto.CarritoResponse;
import com.codeconheiner.tienda.common.ApiException;
import com.codeconheiner.tienda.config.TiendaProperties;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

class CarritoServiceTest {

    private final TiendaProperties props = new TiendaProperties(
            "Tienda", "COP", new BigDecimal("0.19"), new BigDecimal("200000"), 10000, 5, 15,
            new TiendaProperties.Jwt("test-only-secret-with-at-least-32-bytes", 30),
            new TiendaProperties.Admin("admin@example.com", ""),
            new TiendaProperties.Cors(List.of("http://localhost:5173")),
            new TiendaProperties.RateLimit(300, 10, 60),
            TiendaProperties.Pagos.simulados(),
            new TiendaProperties.Captcha("test-secret", null),
            new TiendaProperties.Mail("no-reply@test.local", "http://localhost:8080"),
            new TiendaProperties.Storage("http://localhost:9000", "key", "secret", "bucket", "http://localhost:9000", Boolean.TRUE),
            new TiendaProperties.CacheConfigProperties("5m", "60s", "1h", "1h"));

    private final FakeCarritoRepository repo = new FakeCarritoRepository();
    private final CarritoService service = new CarritoService(repo, props);

    @Test
    void obtenerCarritoCreaCarritoVacioYCalculaTotalesEnCero() {
        CarritoResponse respuesta = service.obtenerCarrito(1L);

        assertThat(respuesta.id()).isNotNull();
        assertThat(respuesta.usuarioId()).isEqualTo(1L);
        assertThat(respuesta.items()).isEmpty();
        assertThat(respuesta.subtotal()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(respuesta.costoEnvio()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(respuesta.iva()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(respuesta.total()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void agregarItemRecalculaSubtotalIvaEnvioYTotal() {
        repo.precioProducto(1L, new BigDecimal("1000"));

        service.agregarItem(1L, new CarritoItemRequest(1L, 2));
        CarritoResponse respuesta = service.agregarItem(1L, new CarritoItemRequest(1L, 3));

        assertThat(respuesta.items()).hasSize(1);
        assertThat(respuesta.items().get(0).cantidad()).isEqualTo(5);
        assertThat(respuesta.subtotal()).isEqualByComparingTo(new BigDecimal("5000"));
        // por debajo del umbral de envío gratis se cobra envío
        assertThat(respuesta.costoEnvio()).isEqualByComparingTo(new BigDecimal("10000"));
        assertThat(respuesta.iva()).isEqualByComparingTo(new BigDecimal("950"));
        assertThat(respuesta.total()).isEqualByComparingTo(new BigDecimal("15950"));
    }

    @Test
    void envioEsGratisCuandoElSubtotalAlcanzaElUmbral() {
        repo.precioProducto(1L, new BigDecimal("200000"));

        CarritoResponse respuesta = service.agregarItem(1L, new CarritoItemRequest(1L, 1));

        assertThat(respuesta.costoEnvio()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(respuesta.total()).isEqualByComparingTo(new BigDecimal("238000"));
    }

    @Test
    void agregarItemRechazadoPorStockDevuelve400() {
        repo.explotarAlAgregar();

        ApiException error = catchThrowableOfType(
                () -> service.agregarItem(1L, new CarritoItemRequest(1L, 1)), ApiException.class);

        assertThat(error).isNotNull();
        assertThat(error.getStatus().value()).isEqualTo(400);
        assertThat(error).hasMessage("No se pudo agregar el producto al carrito");
    }

    @Test
    void eliminarItemYVaciarCarritoDejanElCarritoVacio() {
        repo.precioProducto(1L, new BigDecimal("1000"));
        repo.precioProducto(2L, new BigDecimal("2000"));
        service.agregarItem(1L, new CarritoItemRequest(1L, 1));
        service.agregarItem(1L, new CarritoItemRequest(2L, 1));

        service.eliminarItem(1L, 1L);
        assertThat(service.obtenerCarrito(1L).items()).hasSize(1);

        service.vaciarCarrito(1L);
        assertThat(service.obtenerCarrito(1L).items()).isEmpty();
    }

    @Test
    void actualizarItemCambiaLaCantidad() {
        repo.precioProducto(1L, new BigDecimal("1000"));
        service.agregarItem(1L, new CarritoItemRequest(1L, 2));

        CarritoResponse respuesta = service.actualizarItem(1L, 1L, new CarritoItemRequest(1L, 7));

        assertThat(respuesta.items().get(0).cantidad()).isEqualTo(7);
        assertThat(respuesta.subtotal()).isEqualByComparingTo(new BigDecimal("7000"));
    }

    private static class FakeCarritoRepository extends CarritoRepository {

        private final Map<Long, Long> carritos = new HashMap<>();
        private final Map<Long, List<CarritoItemResponse>> items = new HashMap<>();
        private final Map<Long, BigDecimal> precios = new HashMap<>();
        private final AtomicLong secuencia = new AtomicLong(1);
        private boolean explotar;

        FakeCarritoRepository() {
            super((JdbcTemplate) null);
        }

        void precioProducto(long productoId, BigDecimal precio) {
            precios.put(productoId, precio);
        }

        void explotarAlAgregar() {
            explotar = true;
        }

        @Override
        public Optional<Long> obtenerIdCarritoPorUsuario(Long usuarioId) {
            return carritos.entrySet().stream()
                    .filter(e -> e.getValue().equals(usuarioId))
                    .map(Map.Entry::getKey)
                    .findFirst();
        }

        @Override
        public Long crearCarrito(Long usuarioId) {
            long id = secuencia.getAndIncrement();
            carritos.put(id, usuarioId);
            items.put(id, new ArrayList<>());
            return id;
        }

        @Override
        public void agregarItem(Long carritoId, Long productoId, Integer cantidad) {
            if (explotar) {
                throw new DataIntegrityViolationException("stock insuficiente");
            }
            BigDecimal precio = precios.getOrDefault(productoId, BigDecimal.ZERO);
            List<CarritoItemResponse> lista = items.computeIfAbsent(carritoId, k -> new ArrayList<>());
            for (int i = 0; i < lista.size(); i++) {
                CarritoItemResponse actual = lista.get(i);
                if (actual.productoId().equals(productoId)) {
                    int nueva = actual.cantidad() + cantidad;
                    lista.set(i, respuesta(actual.id(), productoId, nueva, precio));
                    return;
                }
            }
            lista.add(respuesta(secuencia.getAndIncrement(), productoId, cantidad, precio));
        }

        @Override
        public List<CarritoItemResponse> listarItems(Long carritoId) {
            return List.copyOf(items.getOrDefault(carritoId, List.of()));
        }

        @Override
        public void actualizarCantidad(Long carritoId, Long productoId, Integer cantidad) {
            List<CarritoItemResponse> lista = items.computeIfAbsent(carritoId, k -> new ArrayList<>());
            for (int i = 0; i < lista.size(); i++) {
                CarritoItemResponse actual = lista.get(i);
                if (actual.productoId().equals(productoId)) {
                    BigDecimal precio = precios.getOrDefault(productoId, BigDecimal.ZERO);
                    lista.set(i, respuesta(actual.id(), productoId, cantidad, precio));
                }
            }
        }

        @Override
        public void eliminarItem(Long carritoId, Long productoId) {
            List<CarritoItemResponse> lista = items.getOrDefault(carritoId, new ArrayList<>());
            lista.removeIf(item -> item.productoId().equals(productoId));
        }

        @Override
        public void vaciarCarrito(Long carritoId) {
            items.getOrDefault(carritoId, new ArrayList<>()).clear();
        }

        private static CarritoItemResponse respuesta(long id, long productoId, int cantidad, BigDecimal precio) {
            return new CarritoItemResponse(id, productoId, "SKU-" + productoId, "Producto " + productoId,
                    precio, cantidad, precio.multiply(BigDecimal.valueOf(cantidad)), 10);
        }
    }
}
