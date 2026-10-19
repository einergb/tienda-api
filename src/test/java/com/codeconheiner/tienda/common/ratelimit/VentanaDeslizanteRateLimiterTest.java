package com.codeconheiner.tienda.common.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class VentanaDeslizanteRateLimiterTest {

    @Test
    void admiteHastaElMaximoYRechazaElExceso() {
        RelojFalso reloj = new RelojFalso(Instant.parse("2026-01-01T00:00:00Z"));
        RateLimiter limiter = new VentanaDeslizanteRateLimiter(3, 60, reloj);

        assertThat(limiter.permitir("1.1.1.1")).isTrue();
        assertThat(limiter.permitir("1.1.1.1")).isTrue();
        assertThat(limiter.permitir("1.1.1.1")).isTrue();
        assertThat(limiter.permitir("1.1.1.1")).isFalse();
    }

    @Test
    void liberaElAccesoCuandoLaVentanaSeDesliza() {
        RelojFalso reloj = new RelojFalso(Instant.parse("2026-01-01T00:00:00Z"));
        RateLimiter limiter = new VentanaDeslizanteRateLimiter(2, 60, reloj);

        assertThat(limiter.permitir("2.2.2.2")).isTrue();
        assertThat(limiter.permitir("2.2.2.2")).isTrue();
        assertThat(limiter.permitir("2.2.2.2")).isFalse();

        // un segundo antes de la ventana sigue bloqueado
        reloj.avanzar(59);
        assertThat(limiter.permitir("2.2.2.2")).isFalse();

        // al cumplirse 60 s, las marcas viejas se descartan
        reloj.avanzar(1);
        assertThat(limiter.permitir("2.2.2.2")).isTrue();
    }

    @Test
    void lasClavesSonIndependientes() {
        RateLimiter limiter = new VentanaDeslizanteRateLimiter(1, 60,
                new RelojFalso(Instant.parse("2026-01-01T00:00:00Z")));

        assertThat(limiter.permitir("3.3.3.3")).isTrue();
        assertThat(limiter.permitir("3.3.3.3")).isFalse();
        assertThat(limiter.permitir("9.9.9.9")).isTrue();
    }

    /** Reloj controlable para simular el paso del tiempo sin esperar. */
    private static final class RelojFalso extends Clock {

        private Instant ahora;

        private RelojFalso(Instant inicio) {
            this.ahora = inicio;
        }

        void avanzar(long segundos) {
            ahora = ahora.plusSeconds(segundos);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return ahora;
        }
    }
}
