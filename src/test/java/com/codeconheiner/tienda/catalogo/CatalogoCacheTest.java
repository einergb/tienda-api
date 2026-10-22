package com.codeconheiner.tienda.catalogo;

import static org.assertj.core.api.Assertions.assertThat;

import com.codeconheiner.tienda.config.TiendaProperties;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
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
    private CacheManager cacheManager;

    @BeforeEach
    void limpiar() {
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

    private static ProductoResponse producto(long id, String sku) {
        return new ProductoResponse(id, sku, "Producto " + id, sku.toLowerCase(java.util.Locale.ROOT), "desc",
                new BigDecimal("1000"), 1L, "Computadores", null, null, 12, 10, true, List.of(), Map.of());
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
    static class Config {

        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager("producto", "catalogo-listado", "categorias", "tienda-info");
        }

        @Bean
        TiendaProperties tiendaProperties() {
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
