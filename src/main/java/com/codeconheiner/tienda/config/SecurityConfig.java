package com.codeconheiner.tienda.config;

import com.codeconheiner.tienda.auth.JwtAuthFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, JwtAuthFilter jwtFilter, ObjectMapper objectMapper)
            throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(Customizer.withDefaults())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a -> a
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/catalogo/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/inventario/productos/*/stock").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/resenas/producto/*").permitAll()
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/carrito/**").authenticated()
                .requestMatchers("/api/direcciones/**").authenticated()
                .requestMatchers("/api/cupones/**").authenticated()
                .requestMatchers("/api/resenas/**").authenticated()
                .requestMatchers("/api/pedidos/**").authenticated()
                .requestMatchers("/api/pagos/**").authenticated()
                .requestMatchers("/api/envios/**").authenticated()
                .anyRequest().authenticated())
            .exceptionHandling(e -> e
                .authenticationEntryPoint((request, response, exception) -> escribirProblema(
                        response, objectMapper, HttpStatus.UNAUTHORIZED,
                        "Autenticación requerida: inicia sesión o proporciona un token válido."))
                .accessDeniedHandler((request, response, exception) -> escribirProblema(
                        response, objectMapper, HttpStatus.FORBIDDEN,
                        "No tienes permisos para realizar esta operación.")))
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    private static void escribirProblema(HttpServletResponse response, ObjectMapper objectMapper,
            HttpStatus status, String detalle) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), ProblemDetail.forStatusAndDetail(status, detalle));
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(TiendaProperties props) {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOrigins(props.cors().origenesPermitidos());
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        cfg.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cfg);
        return source;
    }
}
