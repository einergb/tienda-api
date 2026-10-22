package com.codeconheiner.tienda.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codeconheiner.tienda.config.TiendaProperties.Pagos;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class ValidacionConfiguracionTest {

    private static final String SECRETO_FUERTE = "test-secret-with-at-least-32-characters";

    private TiendaProperties props(String secreto, String modoPagos) {
        return new TiendaProperties(
                "Tienda", "COP", BigDecimal.ZERO, BigDecimal.ZERO, 10000, 5, 15,
                new TiendaProperties.Jwt(secreto, 30),
                new TiendaProperties.Admin("admin@example.com", ""),
                new TiendaProperties.Cors(List.of("http://localhost:5173")),
                new TiendaProperties.RateLimit(300, 10, 60),
                new TiendaProperties.Pagos(modoPagos,
                        new TiendaProperties.Pagos.Wompi(null, null, null, null, null),
                        new TiendaProperties.Pagos.Epayco(null, null, null, null)),
                new TiendaProperties.Captcha("test-secret", null),
                new TiendaProperties.Mail("no-reply@test.local", "http://localhost:8080"),
                new TiendaProperties.Storage("http://localhost:9000", "key", "secret", "bucket", "http://localhost:9000", Boolean.TRUE),
                new TiendaProperties.CacheConfigProperties("5m", "60s", "1h", "1h"));
    }

    @Test
    void aceptaConfiguracionDeEjemplo() {
        new ValidacionConfiguracion(props(SECRETO_FUERTE, "simulado")).validar();
    }

    @Test
    void rechazaSecretoCortoConMensajeEspanol() {
        assertThatThrownBy(() -> new ValidacionConfiguracion(props("corto", "simulado")).validar())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET demasiado corto");
    }

    @Test
    void rechazaSecretoAusente() {
        assertThatThrownBy(() -> new ValidacionConfiguracion(props("   ", "simulado")).validar())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Falta JWT_SECRET");
    }

    @Test
    void rechazaModoDePagoDesconocido() {
        assertThatThrownBy(() -> new ValidacionConfiguracion(props(SECRETO_FUERTE, "produccion")).validar())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("tienda.pagos.modo");
    }

    @Test
    void rechazaRateLimitConValoresNoPositivos() {
        TiendaProperties props = new TiendaProperties(
                "Tienda", "COP", BigDecimal.ZERO, BigDecimal.ZERO, 10000, 5, 15,
                new TiendaProperties.Jwt(SECRETO_FUERTE, 30),
                new TiendaProperties.Admin("admin@example.com", ""),
                new TiendaProperties.Cors(List.of("http://localhost:5173")),
                new TiendaProperties.RateLimit(0, 10, 60),
                Pagos.simulados(),
                new TiendaProperties.Captcha("test-secret", null),
                new TiendaProperties.Mail("no-reply@test.local", "http://localhost:8080"),
                new TiendaProperties.Storage("http://localhost:9000", "key", "secret", "bucket", "http://localhost:9000", Boolean.TRUE),
                new TiendaProperties.CacheConfigProperties("5m", "60s", "1h", "1h"));

        assertThatThrownBy(() -> new ValidacionConfiguracion(props).validar())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("tienda.rate-limit");
    }

    @Test
    void pagosSimuladosNoExigeCredenciales() {
        // modo simulado: no se piden claves de pasarela, pero el modo debe ser válido
        new ValidacionConfiguracion(props(SECRETO_FUERTE, "simulado")).validar();
        assertThat(Pagos.simulados().modo()).isEqualTo("simulado");
    }
}
