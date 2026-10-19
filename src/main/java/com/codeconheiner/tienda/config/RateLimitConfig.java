package com.codeconheiner.tienda.config;

import com.codeconheiner.tienda.common.ratelimit.RateLimiter;
import com.codeconheiner.tienda.common.ratelimit.VentanaDeslizanteRateLimiter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/** Ensambla la limitación de tasa a partir de la configuración de la tienda. */
@Configuration
public class RateLimitConfig {

    @Bean
    RateLimiter rateLimiterGeneral(TiendaProperties props) {
        return new VentanaDeslizanteRateLimiter(
                props.rateLimit().peticionesPorMinuto(), props.rateLimit().ventanaSegundos());
    }

    @Bean
    RateLimiter rateLimiterAuth(TiendaProperties props) {
        return new VentanaDeslizanteRateLimiter(
                props.rateLimit().peticionesAuthPorMinuto(), props.rateLimit().ventanaSegundos());
    }

    /** Se registra antes que la cadena de seguridad para rechazar el exceso de entrada. */
    @Bean
    FilterRegistrationBean<RateLimitFilter> rateLimitFilter(RateLimiter rateLimiterGeneral,
            RateLimiter rateLimiterAuth, ObjectMapper objectMapper, TiendaProperties props) {
        RateLimitFilter filtro = new RateLimitFilter(rateLimiterGeneral, rateLimiterAuth,
                objectMapper, props.rateLimit().ventanaSegundos());
        FilterRegistrationBean<RateLimitFilter> registro = new FilterRegistrationBean<>(filtro);
        registro.addUrlPatterns("/api/*");
        registro.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registro;
    }
}
