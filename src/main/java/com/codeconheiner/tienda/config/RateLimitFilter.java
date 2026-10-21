package com.codeconheiner.tienda.config;

import com.codeconheiner.tienda.common.ratelimit.RateLimiter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Aplica limitación de tasa por IP del cliente sobre los endpoints /api/**.
 * Las rutas de webhook quedan fuera del filtro: las pasarelas reintentan y deben
 * poder notificar aunque haya picos de tráfico.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    public static final String MENSAJE =
            "Demasiadas solicitudes desde tu conexión. Intenta de nuevo más tarde.";

    /** Rutas de autenticación con límite más estricto (fuerza bruta). */
    private static final String[] RUTAS_AUTH = {
        "/api/auth/login", "/api/auth/registro", "/api/auth/refresh"
    };

    private final RateLimiter general;
    private final RateLimiter auth;
    private final ObjectMapper objectMapper;
    private final int ventanaSegundos;

    public RateLimitFilter(RateLimiter general, RateLimiter auth, ObjectMapper objectMapper,
            int ventanaSegundos) {
        this.general = general;
        this.auth = auth;
        this.objectMapper = objectMapper;
        this.ventanaSegundos = ventanaSegundos;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String ruta = request.getRequestURI();
        return !ruta.startsWith("/api/") || ruta.startsWith("/api/webhooks/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        RateLimiter limitador = esRutaAuth(request.getRequestURI()) ? auth : general;
        if (!limitador.permitir(IpResolver.resolve(request))) {
            escribirLimite(response);
            return;
        }
        chain.doFilter(request, response);
    }

    private static boolean esRutaAuth(String ruta) {
        for (String propia : RUTAS_AUTH) {
            if (ruta.equals(propia)) {
                return true;
            }
        }
        return false;
    }



    private void escribirLimite(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", String.valueOf(ventanaSegundos));
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(),
                ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, MENSAJE));
    }
}
