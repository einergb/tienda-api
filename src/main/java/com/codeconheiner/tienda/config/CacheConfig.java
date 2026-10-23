package com.codeconheiner.tienda.config;

import com.codeconheiner.tienda.catalogo.CategoriaResponse;
import com.codeconheiner.tienda.catalogo.ProductoResponse;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.lettuce.core.ClientOptions;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.redis.LettuceClientConfigurationBuilderCustomizer;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.cache.CacheStatistics;
import org.springframework.data.redis.cache.CacheStatisticsCollector;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.lang.Nullable;

@Configuration
@ConditionalOnProperty(name = "spring.cache.type", havingValue = "redis")
@EnableCaching
public class CacheConfig implements CachingConfigurer {

    private static final Logger log = LoggerFactory.getLogger(CacheConfig.class);

    /** Ventana mínima entre warnings de caché para no inundar el log si Redis está caído. */
    private static final Duration INTERVALO_WARNING = Duration.ofMinutes(1);

    /** Tras un fallo de conexión se evita tocar Redis durante este tiempo. */
    private static final Duration INTERVALO_SIN_REDIS = Duration.ofSeconds(5);

    /**
     * Con Redis inaccesible, Lettuce encola los comandos hasta agotar el timeout. Rechazarlos
     * en cuanto no hay conexión hace que la caché falle rápido y la petición siga contra la BD.
     */
    @Bean
    public LettuceClientConfigurationBuilderCustomizer lettuceRechazaComandosSinConexion() {
        return builder -> builder.clientOptions(ClientOptions.builder()
                .disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
                .build());
    }

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory, TiendaProperties props) {
        RedisCacheWriter writer = new RedisCacheWriterConCortacircuitos(
                RedisCacheWriter.nonLockingRedisCacheWriter(connectionFactory), INTERVALO_SIN_REDIS);
        return RedisCacheManager.builder(writer)
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
        return new CacheErrorHandlerConLimite(INTERVALO_WARNING);
    }

    /**
     * Escritor de Redis que abre un pequeño cortacircuitos al primer fallo de conexión. Sin esto
     * la lectura falla tras el timeout y la escritura vuelve a esperar otro timeout, duplicando
     * la latencia de cada petición mientras Redis siga inaccesible.
     */
    static final class RedisCacheWriterConCortacircuitos implements RedisCacheWriter {

        private final RedisCacheWriter delegate;
        private final long intervaloNanos;
        private final LongSupplier reloj;
        private final AtomicLong ultimoFallo = new AtomicLong(Long.MIN_VALUE);

        RedisCacheWriterConCortacircuitos(RedisCacheWriter delegate, Duration intervalo) {
            this(delegate, intervalo, System::nanoTime);
        }

        RedisCacheWriterConCortacircuitos(RedisCacheWriter delegate, Duration intervalo, LongSupplier reloj) {
            this.delegate = delegate;
            this.intervaloNanos = intervalo.toNanos();
            this.reloj = reloj;
        }

        @Override
        @Nullable
        public byte[] get(String name, byte[] key) {
            return conCache(() -> delegate.get(name, key), null);
        }

        @Override
        @Nullable
        public byte[] get(String name, byte[] key, @Nullable Duration ttl) {
            return conCache(() -> delegate.get(name, key, ttl), null);
        }

        @Override
        public boolean supportsAsyncRetrieve() {
            return delegate.supportsAsyncRetrieve();
        }

        @Override
        public CompletableFuture<byte[]> retrieve(String name, byte[] key, @Nullable Duration ttl) {
            if (!disponible()) {
                return CompletableFuture.completedFuture(null);
            }
            try {
                return delegate.retrieve(name, key, ttl)
                        .whenComplete((valor, error) -> falloSiAplica(error));
            } catch (DataAccessException ex) {
                marcarFallo();
                throw ex;
            }
        }

        @Override
        public void put(String name, byte[] key, byte[] value, @Nullable Duration ttl) {
            sinRetorno(() -> delegate.put(name, key, value, ttl));
        }

        @Override
        public CompletableFuture<Void> store(String name, byte[] key, byte[] value, @Nullable Duration ttl) {
            if (!disponible()) {
                return CompletableFuture.completedFuture(null);
            }
            try {
                return delegate.store(name, key, value, ttl)
                        .whenComplete((valor, error) -> falloSiAplica(error));
            } catch (DataAccessException ex) {
                marcarFallo();
                throw ex;
            }
        }

        @Override
        @Nullable
        public byte[] putIfAbsent(String name, byte[] key, byte[] value, @Nullable Duration ttl) {
            return conCache(() -> delegate.putIfAbsent(name, key, value, ttl), null);
        }

        @Override
        public void remove(String name, byte[] key) {
            sinRetorno(() -> delegate.remove(name, key));
        }

        @Override
        public void clean(String name, byte[] pattern) {
            sinRetorno(() -> delegate.clean(name, pattern));
        }

        @Override
        public void clearStatistics(String name) {
            delegate.clearStatistics(name);
        }

        @Override
        public RedisCacheWriter withStatisticsCollector(CacheStatisticsCollector cacheStatisticsCollector) {
            return new RedisCacheWriterConCortacircuitos(
                    delegate.withStatisticsCollector(cacheStatisticsCollector),
                    Duration.ofNanos(intervaloNanos), reloj);
        }

        @Override
        public CacheStatistics getCacheStatistics(String cacheName) {
            return delegate.getCacheStatistics(cacheName);
        }

        private boolean disponible() {
            long ultimo = ultimoFallo.get();
            return ultimo == Long.MIN_VALUE || reloj.getAsLong() - ultimo >= intervaloNanos;
        }

        private void marcarFallo() {
            ultimoFallo.set(reloj.getAsLong());
        }

        private void falloSiAplica(@Nullable Throwable error) {
            if (error != null) {
                marcarFallo();
            }
        }

        @Nullable
        private <T> T conCache(Supplier<T> operacion, @Nullable T valorSiNoDisponible) {
            if (!disponible()) {
                return valorSiNoDisponible;
            }
            try {
                return operacion.get();
            } catch (DataAccessException ex) {
                marcarFallo();
                throw ex;
            }
        }

        private void sinRetorno(Runnable operacion) {
            if (!disponible()) {
                return;
            }
            try {
                operacion.run();
            } catch (DataAccessException ex) {
                marcarFallo();
                throw ex;
            }
        }
    }

    /**
     * Registra el primer fallo de caché como warning y el resto en DEBUG durante un intervalo.
     * Así una caída de Redis no genera dos warnings por petición.
     */
    static final class CacheErrorHandlerConLimite implements CacheErrorHandler {

        private final long intervaloNanos;
        private final LongSupplier reloj;
        private final AtomicLong ultimoWarning = new AtomicLong(Long.MIN_VALUE);

        CacheErrorHandlerConLimite(Duration intervalo) {
            this(intervalo, System::nanoTime);
        }

        CacheErrorHandlerConLimite(Duration intervalo, LongSupplier reloj) {
            this.intervaloNanos = intervalo.toNanos();
            this.reloj = reloj;
        }

        @Override
        public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
            registrar("GET", cache, key, exception);
        }

        @Override
        public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
            registrar("PUT", cache, key, exception);
        }

        @Override
        public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
            registrar("EVICT", cache, key, exception);
        }

        @Override
        public void handleCacheClearError(RuntimeException exception, Cache cache) {
            registrar("CLEAR", cache, null, exception);
        }

        private void registrar(String operacion, Cache cache, Object key, RuntimeException exception) {
            long ahora = reloj.getAsLong();
            long ultimo = ultimoWarning.get();
            boolean tocaWarning = ultimo == Long.MIN_VALUE || ahora - ultimo >= intervaloNanos;
            if (tocaWarning && ultimoWarning.compareAndSet(ultimo, ahora)) {
                log.warn("Cache {} falló para cache={} key={}: {} (fallos repetidos en DEBUG durante {}s)",
                        operacion, cache.getName(), key, exception.toString(), INTERVALO_WARNING.toSeconds());
            } else {
                log.debug("Cache {} falló para cache={} key={}: {}",
                        operacion, cache.getName(), key, exception.toString());
            }
        }
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
