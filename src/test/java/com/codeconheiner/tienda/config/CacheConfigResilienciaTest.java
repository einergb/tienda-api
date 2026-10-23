package com.codeconheiner.tienda.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.lettuce.core.ClientOptions;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;

/** Fallo rápido de la caché cuando Redis no está disponible. */
class CacheConfigResilienciaTest {

    @Test
    void lettuceRechazaComandosSinConexionEnVezDeEncolarlos() {
        CacheConfig config = new CacheConfig();
        LettuceClientConfiguration.LettuceClientConfigurationBuilder builder = LettuceClientConfiguration.builder();

        config.lettuceRechazaComandosSinConexion().customize(builder);

        assertThat(builder.build().getClientOptions()).hasValueSatisfying(options -> assertThat(
                options.getDisconnectedBehavior()).isEqualTo(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS));
    }

    @Test
    void trasUnFalloNoSeVuelveATocarRedisDuranteLaVentana() {
        RedisCacheWriter delegate = mock(RedisCacheWriter.class);
        RedisConnectionFailureException caido = new RedisConnectionFailureException("Redis no disponible");
        when(delegate.get(anyString(), any())).thenThrow(caido);
        when(delegate.get(anyString(), any(), any())).thenThrow(caido);
        AtomicLong reloj = new AtomicLong(1_000);
        CacheConfig.RedisCacheWriterConCortacircuitos writer =
                new CacheConfig.RedisCacheWriterConCortacircuitos(delegate, Duration.ofSeconds(5), reloj::get);

        assertThatThrownBy(() -> writer.get("producto", new byte[] {1})).isSameAs(caido);

        // Dentro de la ventana no se intenta conectar ni se propaga el error: la petición va a BD.
        assertThat(writer.get("producto", new byte[] {1})).isNull();
        writer.put("producto", new byte[] {1}, new byte[] {2}, Duration.ofSeconds(60));
        verify(delegate, times(1)).get(anyString(), any());

        reloj.addAndGet(Duration.ofSeconds(5).toNanos());

        assertThatThrownBy(() -> writer.get("producto", new byte[] {1})).isSameAs(caido);
        verify(delegate, times(2)).get(anyString(), any());
    }
}
