package com.codeconheiner.tienda.catalogo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.codeconheiner.tienda.config.CacheCatalogo;
import com.codeconheiner.tienda.config.CacheConfig;
import com.codeconheiner.tienda.config.TiendaProperties;
import com.codeconheiner.tienda.pedidos.PedidoRepository;
import com.codeconheiner.tienda.pedidos.PedidoService;
import com.codeconheiner.tienda.pedidos.dto.PedidoItemResponse;
import com.codeconheiner.tienda.pedidos.dto.PedidoResponse;
import com.codeconheiner.tienda.seguridad.Auditoria;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Collection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.web.multipart.MultipartFile;

/**
 * Comportamiento de la cache del catalogo con un gestor en memoria: sirve para comprobar que
 * las claves separan las peticiones que devuelven resultados distintos y que las entradas se
 * invalidan cuando el producto cambia.
 */
@SpringJUnitConfig(CatalogoCacheTest.Config.class)
class CatalogoCacheTest {

    @Autowired
    private CatalogoController controller;

    @Autowired
    private ProductoServiceFalso productos;

    @Autowired
    private AdminProductoController adminProductos;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private CacheCatalogo cacheCatalogo;

    @BeforeEach
    void limpiar() {
        ((CacheManagerPrueba) cacheManager).fallar = false;
        productos.limpiar();
        cacheManager.getCache("producto").clear();
        cacheManager.getCache("catalogo-listado").clear();
        cacheManager.getCache("categorias").clear();
        cacheManager.getCache("tienda-info").clear();
    }

    @Test
    void segundaLecturaDelProductoNoConsultaLaBase() {
        productos.guardar(producto(1L, "SKU-1"));

        controller.producto(1L);
        controller.producto(1L);

        assertThat(productos.llamadasDetalle()).isEqualTo(1);
    }

    @Test
    void actualizarProductoInvalidaDetalleYListados() {
        productos.guardar(producto(1L, "SKU-1"));
        controller.producto(1L);
        controller.productos(null, null, 0, 12);

        adminProductos.actualizar(1L, new ProductoRequest("SKU-1", "Actualizado", "desc",
                new BigDecimal("1200"), 1L, null, 12, 10, 5));
        controller.producto(1L);
        controller.productos(null, null, 0, 12);

        assertThat(productos.llamadasDetalle()).isEqualTo(2);
        assertThat(productos.llamadasListado()).isEqualTo(2);
    }

    @Test
    void subirYBorrarImagenInvalidanElDetalle() {
        productos.guardar(producto(1L, "SKU-1"));
        controller.producto(1L);

        adminProductos.subirImagenProducto(1L, new MockMultipartFile("file", "imagen.png", "image/png",
                new byte[] {1}));
        controller.producto(1L);
        adminProductos.eliminarImagenProducto(1L);
        controller.producto(1L);

        assertThat(productos.llamadasDetalle()).isEqualTo(3);
    }

    @Test
    void cambioDeStockDesdeJavaInvalidaDetalleYListados() {
        productos.guardar(producto(1L, "SKU-1"));
        controller.producto(1L);
        controller.productos(null, null, 0, 12);

        PedidoRepository pedidos = mock(PedidoRepository.class);
        when(pedidos.obtenerPorIdAdmin(7L)).thenReturn(Optional.of(pedido("PENDIENTE")),
            Optional.of(pedido("CANCELADO")));
        when(pedidos.cambiarEstado(7L, "CANCELADO")).thenReturn(1);
        PedidoService servicioPedidos = new PedidoService(pedidos, null, null, null,
            mock(Auditoria.class), cacheCatalogo);

        servicioPedidos.cambiarEstado(7L, "CANCELADO");
        controller.producto(1L);
        controller.productos(null, null, 0, 12);

        assertThat(productos.llamadasDetalle()).isEqualTo(2);
        assertThat(productos.llamadasListado()).isEqualTo(2);
    }

    @Test
    void falloDeCacheNoImpideLeerDesdeElServicio() {
        productos.guardar(producto(1L, "SKU-1"));
        ((CacheManagerPrueba) cacheManager).fallar = true;

        assertThat(controller.producto(1L).id()).isEqualTo(1L);

        assertThat(productos.llamadasDetalle()).isEqualTo(1);
    }

    @Test
    void cacheTypeNoneNoIntervieneEnLasLecturas() {
        new ApplicationContextRunner()
                .withUserConfiguration(CacheConfig.class, CacheDisabledConfig.class)
                .withPropertyValues("spring.cache.type=none")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(CacheManager.class);
                    ProductoServiceFalso servicio = context.getBean(ProductoServiceFalso.class);
                    CatalogoController catalogo = context.getBean(CatalogoController.class);
                    servicio.guardar(producto(1L, "SKU-1"));

                    catalogo.producto(1L);
                    catalogo.producto(1L);

                    assertThat(servicio.llamadasDetalle()).isEqualTo(2);
                });
    }

    private static ProductoResponse producto(long id, String sku) {
        return new ProductoResponse(id, sku, "Producto " + id, sku.toLowerCase(java.util.Locale.ROOT), "desc",
                new BigDecimal("1000"), 1L, "Computadores", null, null, 12, 10, true, List.of(), Map.of());
    }

    private static TiendaProperties propiedadesPrueba() {
        return new TiendaProperties(
                "Tienda de prueba", "COP", new BigDecimal("0.19"), new BigDecimal("200000"), 10000, 5, 15,
                new TiendaProperties.Jwt("cache-tests-secret-at-least-32-bytes", 120),
                new TiendaProperties.Admin("admin@example.com", ""),
                new TiendaProperties.Cors(List.of("http://localhost:5173")),
                new TiendaProperties.RateLimit(300, 10, 60),
                TiendaProperties.Pagos.simulados(),
                new TiendaProperties.Captcha("test-secret", null),
                new TiendaProperties.Mail("no-reply@test.local", "http://localhost:8080"),
                new TiendaProperties.Storage("http://localhost:9000", "key", "secret", "bucket",
                        "http://localhost:9000", Boolean.TRUE),
                new TiendaProperties.CacheConfigProperties("60s", "30s", "1h", "1h"));
    }

    private static PedidoResponse pedido(String estado) {
        return new PedidoResponse(7L, "PED-7", estado, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, null, null,
                List.of(new PedidoItemResponse(1L, 1L, "SKU-1", "Producto 1", BigDecimal.ONE, 1,
                        BigDecimal.ONE)));
    }

    @Configuration
    static class CacheDisabledConfig {

        @Bean
        TiendaProperties disabledCacheProperties() {
            return propiedadesPrueba();
        }

        @Bean
        CategoriaRepository disabledCacheCategoriaRepository() {
            return new CategoriaRepository((JdbcTemplate) null);
        }

        @Bean
        ProductoServiceFalso disabledCacheProductoService(TiendaProperties disabledCacheProperties) {
            return new ProductoServiceFalso(disabledCacheProperties);
        }

        @Bean
        CatalogoController disabledCacheCatalogo(ProductoServiceFalso disabledCacheProductoService,
                CategoriaRepository disabledCacheCategoriaRepository, TiendaProperties disabledCacheProperties) {
            return new CatalogoController(disabledCacheProductoService, disabledCacheCategoriaRepository,
                    disabledCacheProperties);
        }
    }

    @Test
    void listadosConDistintosFiltrosNoCompartenEntrada() {
        productos.guardar(producto(1L, "SKU-1"));
        productos.guardar(producto(2L, "SKU-2"));

        controller.productos(null, null, 0, 12);
        controller.productos(2L, null, 0, 12);
        controller.productos(null, null, 1, 12);
        controller.productos(null, null, 0, 24);

        assertThat(productos.llamadasListado()).isEqualTo(4);
        assertThat(entradasListado()).hasSize(4);
    }

    @Test
    void listadoIgualSeSirveDesdeLaCache() {
        productos.guardar(producto(1L, "SKU-1"));

        controller.productos(null, null, 0, 12);
        controller.productos(null, null, 0, 12);

        assertThat(productos.llamadasListado()).isEqualTo(1);
        assertThat(entradasListado()).hasSize(1);
    }

    @Test
    void listadoConBusquedaDeTextoNoSeCachea() {
        productos.guardar(producto(1L, "SKU-1"));

        controller.productos(null, "laptop", 0, 12);
        controller.productos(null, "laptop", 0, 12);

        assertThat(productos.llamadasListado()).isEqualTo(2);
        assertThat(entradasListado()).isEmpty();
    }

    @Test
    void claveDelListadoIncluyeTodosLosParametrosQueCambianElResultado() {
        String base = CatalogoController.claveListado(1L, null, 0, 12);

        assertThat(base).isNotEqualTo(CatalogoController.claveListado(2L, null, 0, 12));
        assertThat(base).isNotEqualTo(CatalogoController.claveListado(1L, "laptop", 0, 12));
        assertThat(base).isNotEqualTo(CatalogoController.claveListado(1L, null, 1, 12));
        assertThat(base).isNotEqualTo(CatalogoController.claveListado(1L, null, 0, 24));
        // pagina y tamaño se normalizan igual que en la consulta
        assertThat(CatalogoController.claveListado(1L, null, -1, 12))
                .isEqualTo(CatalogoController.claveListado(1L, null, 0, 12));
        assertThat(CatalogoController.claveListado(1L, null, 0, 500))
                .isEqualTo(CatalogoController.claveListado(1L, null, 0, 50));
    }

    @SuppressWarnings("unchecked")
    private Map<Object, Object> entradasListado() {
        Cache cache = cacheManager.getCache("catalogo-listado");
        return (Map<Object, Object>) cache.getNativeCache();
    }

    @Configuration
    @EnableCaching
    static class Config implements CachingConfigurer {

        @Bean
        public CacheManager cacheManager() {
            return new CacheManagerPrueba();
        }

        @Override
        public CacheErrorHandler errorHandler() {
            return new CacheConfig().errorHandler();
        }

        @Bean
        TiendaProperties tiendaProperties() {
            return propiedadesPrueba();
        }

        @Bean
        CategoriaRepository categoriaRepository() {
            return new CategoriaRepository((JdbcTemplate) null) {
                @Override
                public List<CategoriaResponse> listarActivas() {
                    return List.of(new CategoriaResponse(1L, "Computadores", "computadores", null));
                }
            };
        }

        @Bean
        ProductoServiceFalso productoService(TiendaProperties properties) {
            return new ProductoServiceFalso(properties);
        }

        @Bean
        CatalogoController catalogoController(ProductoService productos, CategoriaRepository categorias,
                TiendaProperties properties) {
            return new CatalogoController(productos, categorias, properties);
        }

        @Bean
        AdminProductoController adminProductoController(ProductoService productos, TiendaProperties properties) {
            return new AdminProductoController(productos,
                    new com.codeconheiner.tienda.catalogo.storage.StorageService(
                            (software.amazon.awssdk.services.s3.S3Client) null, properties));
        }

        @Bean
        CacheCatalogo cacheCatalogo(CacheManager cacheManager) {
            return new CacheCatalogo(cacheManager);
        }

    }

    static class CacheManagerPrueba implements CacheManager {

        private final Map<String, CachePrueba> caches = new ConcurrentHashMap<>();
        private volatile boolean fallar;

        @Override
        public Cache getCache(String name) {
            return caches.computeIfAbsent(name, key -> new CachePrueba(key, this));
        }

        @Override
        public Collection<String> getCacheNames() {
            return caches.keySet();
        }
    }

    static class CachePrueba implements Cache {

        private final Cache delegate;
        private final CacheManagerPrueba manager;

        CachePrueba(String name, CacheManagerPrueba manager) {
            this.delegate = new org.springframework.cache.concurrent.ConcurrentMapCacheManager(name).getCache(name);
            this.manager = manager;
        }

        private void comprobarFallo() {
            if (manager != null && manager.fallar) {
                throw new IllegalStateException("cache no disponible");
            }
        }

        @Override
        public String getName() {
            return delegate.getName();
        }

        @Override
        public Object getNativeCache() {
            return delegate.getNativeCache();
        }

        @Override
        public ValueWrapper get(Object key) {
            comprobarFallo();
            return delegate.get(key);
        }

        @Override
        public <T> T get(Object key, Class<T> type) {
            comprobarFallo();
            return delegate.get(key, type);
        }

        @Override
        public <T> T get(Object key, java.util.concurrent.Callable<T> valueLoader) {
            comprobarFallo();
            return delegate.get(key, valueLoader);
        }

        @Override
        public void put(Object key, Object value) {
            comprobarFallo();
            delegate.put(key, value);
        }

        @Override
        public void evict(Object key) {
            comprobarFallo();
            delegate.evict(key);
        }

        @Override
        public void clear() {
            comprobarFallo();
            delegate.clear();
        }
    }

    /** ProductoService en memoria que cuenta las consultas (simula el acceso a Postgres). */
    static class ProductoServiceFalso extends ProductoService {

        private final Map<Long, ProductoResponse> productos = new LinkedHashMap<>();
        private int llamadasListado;
        private int llamadasDetalle;

        ProductoServiceFalso(TiendaProperties properties) {
            super(new ProductoRepository((JdbcTemplate) null), properties,
                    new com.codeconheiner.tienda.catalogo.storage.StorageService(
                            (software.amazon.awssdk.services.s3.S3Client) null, properties) {
                        @Override
                        public String subir(MultipartFile file, Long productoId) {
                            return "productos/" + productoId + "/clave-de-prueba.png";
                        }

                        @Override
                        public String subirArchivo(MultipartFile file, String prefijo) {
                            return prefijo + "/clave-de-prueba.png";
                        }
                    });
        }

        @Override
        public List<ProductoResponse> listarPublico(Long categoriaId, String q, int page, int size) {
            llamadasListado++;
            return productos.values().stream()
                    .filter(p -> p.activo())
                    .filter(p -> categoriaId == null || categoriaId.equals(p.categoriaId()))
                    .filter(p -> q == null || q.isBlank() || p.nombre().toLowerCase().contains(q.toLowerCase()))
                    .toList();
        }

        @Override
        public ProductoResponse detallePublico(long id) {
            llamadasDetalle++;
            return java.util.Optional.ofNullable(productos.get(id))
                    .filter(p -> p.activo())
                    .filter(p -> p.stockDisponible() > 0)
                    .orElseThrow(() -> new com.codeconheiner.tienda.common.ApiException(
                            org.springframework.http.HttpStatus.NOT_FOUND, "Producto no encontrado"));
        }

        void guardar(ProductoResponse producto) {
            productos.put(producto.id(), producto);
        }

        @Override
        public ProductoResponse actualizar(long id, ProductoRequest request) {
            ProductoResponse actualizado = new ProductoResponse(id, request.sku(), request.nombre(),
                    request.sku().toLowerCase(java.util.Locale.ROOT), request.descripcion(), request.precio(),
                    request.categoriaId(), "Computadores", request.marcaId(), null, request.garantiaMeses(),
                    request.stock(), true, List.of(), Map.of());
            guardar(actualizado);
            return actualizado;
        }

        @Override
        public ProductoResponse subirImagen(long id, MultipartFile archivo) {
            return productos.get(id);
        }

        @Override
        public void eliminarImagen(long id) {
        }

        void limpiar() {
            productos.clear();
            llamadasListado = 0;
            llamadasDetalle = 0;
        }

        int llamadasListado() {
            return llamadasListado;
        }

        int llamadasDetalle() {
            return llamadasDetalle;
        }
    }
}
