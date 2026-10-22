package com.codeconheiner.tienda;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codeconheiner.tienda.auth.JwtAuthFilter;
import com.codeconheiner.tienda.auth.JwtService;
import com.codeconheiner.tienda.common.ratelimit.RateLimiter;
import com.codeconheiner.tienda.common.ratelimit.VentanaDeslizanteRateLimiter;
import com.codeconheiner.tienda.config.RateLimitFilter;
import com.codeconheiner.tienda.config.SecurityConfig;
import com.codeconheiner.tienda.config.TiendaProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/** Comportamiento del filtro de limitación de tasa con límites bajos (3 por ventana). */
@WebMvcTest(controllers = ControladorPruebaRateLimit.class)
@Import(SecurityConfig.class)
class RateLimitingTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void excedeElLimiteYResponde429ConProblemDetailYRetryAfter() throws Exception {
        for (int i = 0; i < 3; i++) {
            mvc.perform(get("/api/catalogo/prueba-rate-limit")).andExpect(status().isOk());
        }

        mvc.perform(get("/api/catalogo/prueba-rate-limit"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "60"))
                .andExpect(jsonPath("$.detail").value(RateLimitFilter.MENSAJE));
    }

    @Test
    void lasRutasDeAuthUsanElLimiteEstrictoYFueraDeApiNoSeLimita() throws Exception {
        // En este slice no hay AuthController (404), pero el filtro consume el límite antes
        // de llegar al dispatcher: las 3 primeras pasan el filtro y la cuarta se bloquea.
        for (int i = 0; i < 3; i++) {
            mvc.perform(post("/api/auth/login").contentType("application/json")
                            .content("{\"email\":\"x@y.co\",\"password\":\"12345678\"}"))
                    .andExpect(status().isNotFound());
        }

        mvc.perform(post("/api/auth/login").contentType("application/json")
                        .content("{\"email\":\"x@y.co\",\"password\":\"12345678\"}"))
                .andExpect(status().isTooManyRequests());

        // rutas fuera de /api/** quedan fuera del filtro (404 del mapeo, nunca 429)
        mvc.perform(get("/actuator/health")).andExpect(status().isNotFound());
    }

    /** Controlador mínimo para ejercitar el filtro sin arrastrar beans del catálogo: ver
     * {@link ControladorPruebaRateLimit}. */

    @TestConfiguration
    static class LimitesDePrueba {

        @Bean
        TiendaProperties tiendaProperties() {
            return new TiendaProperties("Tienda", "COP", new BigDecimal("0.19"), BigDecimal.ZERO, 10000,
                    5, 15,
                    new TiendaProperties.Jwt("endpoint-tests-secret-at-least-32-bytes", 120),
                    new TiendaProperties.Admin("admin@example.com", ""),
                    new TiendaProperties.Cors(List.of("http://localhost:5173")),
                    new TiendaProperties.RateLimit(3, 3, 60),
                    TiendaProperties.Pagos.simulados(),
                    new TiendaProperties.Captcha("test-secret", null),
                    new TiendaProperties.Mail("no-reply@test.local", "http://localhost:8080"),
                    new TiendaProperties.Storage("http://localhost:9000", "key", "secret", "bucket", "http://localhost:9000"));
        }

        @Bean
        JwtService jwtService(TiendaProperties properties) {
            return new JwtService(properties);
        }

        @Bean
        JwtAuthFilter jwtAuthFilter(JwtService jwtService) {
            return new JwtAuthFilter(jwtService);
        }

        @Bean
        RateLimiter rateLimiterGeneral() {
            return new VentanaDeslizanteRateLimiter(3, 60);
        }

        @Bean
        RateLimiter rateLimiterAuth() {
            return new VentanaDeslizanteRateLimiter(3, 60);
        }

        @Bean
        RateLimitFilter rateLimitFilter(RateLimiter rateLimiterGeneral, RateLimiter rateLimiterAuth,
                ObjectMapper objectMapper) {
            return new RateLimitFilter(rateLimiterGeneral, rateLimiterAuth, objectMapper, 60);
        }
    }
}
