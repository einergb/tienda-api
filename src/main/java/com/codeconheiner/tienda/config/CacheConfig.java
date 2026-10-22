package com.codeconheiner.tienda.config;

import com.codeconheiner.tienda.catalogo.CategoriaResponse;
import com.codeconheiner.tienda.catalogo.ProductoResponse;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
@ConditionalOnProperty(name = "spring.cache.type", havingValue = "redis")
@EnableCaching
public class CacheConfig implements CachingConfigurer {

    private static final Logger log = LoggerFactory.getLogger(CacheConfig.class);

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory, TiendaProperties props) {
        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(configuracionBase())
                .withInitialCacheConfigurations(configuraciones(props.cache()))
                .build();
    }

    /**
     * Prefijo de claves ({@code tienda::}), claves en texto plano y sin valores nulos.
     *
     * <p>El serializador de valores por defecto es el de JDK: cada cache conoce el tipo de lo que
     * guarda y lo registra en {@link #configuraciones}. Si se añade una cache nueva hay que
     * añadirla ahí.
     */
    static RedisCacheConfiguration configuracionBase() {
        return RedisCacheConfiguration.defaultCacheConfig()
                .prefixCacheNameWith("tienda::")
                .serializeKeysWith(SerializationPair.fromSerializer(new StringRedisSerializer()))
                .disableCachingNullValues();
    }

    /**
     * Una configuración por cache, cada una con el tipo concreto de su valor y su TTL.
     *
     * <p>No sirve {@code GenericJackson2JsonRedisSerializer} con tipado por defecto: los DTO de
     * la aplicación son {@code record} y por tanto {@code final}, de modo que
     * {@code DefaultTyping.NON_FINAL} no escribe la propiedad {@code @class} en el JSON y la
     * lectura acaba en {@code missing type id property '@class'}. Con un serializador tipado el
     * JSON es plano y se lee con el tipo declarado, sin metadatos de clase.
     */
    static Map<String, RedisCacheConfiguration> configuraciones(TiendaProperties.CacheConfigProperties cache) {
        ObjectMapper mapper = mapperJson();
        TypeFactory tipos = mapper.getTypeFactory();
        RedisCacheConfiguration base = configuracionBase();

        JavaType producto = tipos.constructType(ProductoResponse.class);
        JavaType listadoProductos = tipos.constructCollectionType(List.class, ProductoResponse.class);
        JavaType listadoCategorias = tipos.constructCollectionType(List.class, CategoriaResponse.class);
        JavaType infoTienda = tipos.constructMapType(Map.class, String.class, Object.class);

        Map<String, RedisCacheConfiguration> configs = new HashMap<>();
        configs.put(CacheCatalogo.PRODUCTO, base
                .serializeValuesWith(json(mapper, producto))
                .entryTtl(ttl(cache.ttlProductos(), Duration.ofMinutes(5))));
        configs.put(CacheCatalogo.LISTADO, base
                .serializeValuesWith(json(mapper, listadoProductos))
                .entryTtl(ttl(cache.ttlListados(), Duration.ofSeconds(60))));
        configs.put(CacheCatalogo.CATEGORIAS, base
                .serializeValuesWith(json(mapper, listadoCategorias))
                .entryTtl(ttl(cache.ttlCategorias(), Duration.ofHours(1))));
        configs.put(CacheCatalogo.TIENDA, base
                .serializeValuesWith(json(mapper, infoTienda))
                .entryTtl(ttl(cache.ttlTienda(), Duration.ofHours(1))));
        return configs;
    }

    private static ObjectMapper mapperJson() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    private static SerializationPair<?> json(ObjectMapper mapper, JavaType tipo) {
        return SerializationPair.fromSerializer(new Jackson2JsonRedisSerializer<>(mapper, tipo));
    }

    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
                log.warn("Cache GET falló para cache={} key={}: {}", cache.getName(), key, exception.toString());
            }

            @Override
            public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
                log.warn("Cache PUT falló para cache={} key={}: {}", cache.getName(), key, exception.toString());
            }

            @Override
            public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
                log.warn("Cache EVICT falló para cache={} key={}: {}", cache.getName(), key, exception.toString());
            }

            @Override
            public void handleCacheClearError(RuntimeException exception, Cache cache) {
                log.warn("Cache CLEAR falló: {}", exception.toString());
            }
        };
    }

    private static Duration ttl(String value, Duration fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            if (value.endsWith("ms")) {
                return Duration.ofMillis(Long.parseLong(value.substring(0, value.length() - 2)));
            }
            if (value.endsWith("s")) {
                return Duration.ofSeconds(Long.parseLong(value.substring(0, value.length() - 1)));
            }
            if (value.endsWith("m")) {
                return Duration.ofMinutes(Long.parseLong(value.substring(0, value.length() - 1)));
            }
            if (value.endsWith("h")) {
                return Duration.ofHours(Long.parseLong(value.substring(0, value.length() - 1)));
            }
            if (value.endsWith("d")) {
                return Duration.ofDays(Long.parseLong(value.substring(0, value.length() - 1)));
            }
        } catch (Exception ignored) {
        }
        return fallback;
    }
}
