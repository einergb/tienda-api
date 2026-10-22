package com.codeconheiner.tienda.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.codeconheiner.tienda.catalogo.CategoriaResponse;
import com.codeconheiner.tienda.catalogo.ImagenProductoResponse;
import com.codeconheiner.tienda.catalogo.ProductoResponse;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;

/**
 * Ida y vuelta de los valores de cada cache por el serializador que usa Redis.
 *
 * <p>Los DTO son records ({@code final}): si el serializador depende del tipado por defecto de
 * Jackson no escribe {@code @class} y la lectura falla con "missing type id property '@class'".
 * Esta prueba usa exactamente los serializadores de {@link CacheConfig}, sin necesitar Redis.
 */
class CacheConfigSerializacionTest {

    private static final TiendaProperties.CacheConfigProperties TTL =
            new TiendaProperties.CacheConfigProperties("60s", "30s", "1h", "1h");

    @Test
    void elDetalleDelProductoConservaTodosLosCampos() {
        ProductoResponse producto = new ProductoResponse(1L, "SKU-1", "Producto 1", "producto-1", "desc",
                new BigDecimal("1000.00"), 1L, "Computadores", 2L, "Marca", 12, 7, true,
                List.of(new ImagenProductoResponse(5L, "https://cdn/5.png", 0, true)),
                Map.of("color", "negro"), "productos/1.png", "https://cdn/1.png");

        Object leido = idaYVuelta(CacheCatalogo.PRODUCTO, producto);

        // imagenKey es @JsonIgnore (no se expone ni se cachea); imagenUrl sí viaja y se conserva.
        assertThat(leido).usingRecursiveComparison().ignoringFields("imagenKey").isEqualTo(producto);
        assertThat(((ProductoResponse) leido).imagenUrl()).isEqualTo("https://cdn/1.png");
    }

    @Test
    void elListadoSeLeeAunqueElServicioDevuelvaUnaListaInmutable() {
        ProductoResponse uno = producto(1L);
        ProductoResponse dos = producto(2L);

        Object leido = idaYVuelta(CacheCatalogo.LISTADO, List.of(uno, dos));

        assertThat(leido).isEqualTo(List.of(uno, dos));
    }

    @Test
    void lasCategoriasSeLeenComoListaDeCategorias() {
        List<CategoriaResponse> categorias = List.of(
                new CategoriaResponse(1L, "Computadores", "computadores", null),
                new CategoriaResponse(2L, "Celulares", "celulares", 1L));

        Object leido = idaYVuelta(CacheCatalogo.CATEGORIAS, categorias);

        assertThat(leido).isEqualTo(categorias);
    }

    @Test
    void laInfoDeLaTiendaConservaLosValores() {
        Map<String, Object> info = Map.of(
                "nombre", "TechStore",
                "moneda", "COP",
                "iva", new BigDecimal("0.19"),
                "envioGratisDesde", new BigDecimal("200000"));

        @SuppressWarnings("unchecked")
        Map<String, Object> leido = (Map<String, Object>) idaYVuelta(CacheCatalogo.TIENDA, info);

        assertThat(leido).containsEntry("nombre", "TechStore").containsEntry("moneda", "COP");
        assertThat(((Number) leido.get("iva")).doubleValue()).isEqualTo(0.19);
        assertThat(((Number) leido.get("envioGratisDesde")).longValue()).isEqualTo(200_000L);
    }

    @Test
    void cadaCacheTieneSuTtlConfigurado() {
        Map<String, RedisCacheConfiguration> configs = CacheConfig.configuraciones(TTL);

        assertThat(configs.get(CacheCatalogo.PRODUCTO).getTtl()).isEqualTo(Duration.ofSeconds(60));
        assertThat(configs.get(CacheCatalogo.LISTADO).getTtl()).isEqualTo(Duration.ofSeconds(30));
        assertThat(configs.get(CacheCatalogo.CATEGORIAS).getTtl()).isEqualTo(Duration.ofHours(1));
        assertThat(configs.get(CacheCatalogo.TIENDA).getTtl()).isEqualTo(Duration.ofHours(1));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object idaYVuelta(String cache, Object valor) {
        SerializationPair par = CacheConfig.configuraciones(TTL).get(cache).getValueSerializationPair();
        ByteBuffer bytes = (ByteBuffer) par.write(valor);
        return par.read(bytes);
    }

    private static ProductoResponse producto(long id) {
        return new ProductoResponse(id, "SKU-" + id, "Producto " + id, "producto-" + id, "desc",
                new BigDecimal("1000"), 1L, "Computadores", null, null, 12, 10, true, List.of(), Map.of());
    }
}
