package com.codeconheiner.tienda.config;

import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Comprobaciones de configuración al arrancar (fail-fast).
 *
 * Prefiere dejar la aplicación inoperativa a arrancar con secretos débiles o
 * valores imposibles: los problemas se detectan en el primer arranque, no en
 * el primer login.
 */
@Component
public class ValidacionConfiguracion {

    private static final int LONGITUD_MINIMA_SECRETO = 32;

    private final TiendaProperties props;

    public ValidacionConfiguracion(TiendaProperties props) {
        this.props = props;
    }

    @PostConstruct
    void validar() {
        validarSecretoJwt(props.jwt().secret());
        validarPagos();
        validarRateLimit();
    }

    /**
     * Regla compartida con {@code JwtService}: el secreto debe existir y medir al
     * menos 32 caracteres (HMAC-SHA de 256 bits).
     */
    public static void validarSecretoJwt(String secreto) {
        if (secreto == null || secreto.isBlank()) {
            throw new IllegalStateException(
                    "Falta JWT_SECRET: define un secreto de al menos 32 caracteres (openssl rand -base64 48).");
        }
        if (secreto.getBytes(StandardCharsets.UTF_8).length < LONGITUD_MINIMA_SECRETO) {
            throw new IllegalStateException("JWT_SECRET demasiado corto: debe tener al menos "
                    + LONGITUD_MINIMA_SECRETO + " caracteres.");
        }
    }

    /** En modo real la pasarela elegida necesita sus credenciales. */
    private void validarPagos() {
        String modo = props.pagos().modo();
        if (modo == null || modo.isBlank()) {
            throw new IllegalStateException("tienda.pagos.modo debe ser 'simulado' o 'real'.");
        }
        String normalizado = modo.trim().toLowerCase(Locale.ROOT);
        if (!normalizado.equals("simulado") && !normalizado.equals("real")) {
            throw new IllegalStateException("tienda.pagos.modo debe ser 'simulado' o 'real', no '" + modo + "'.");
        }
    }

    private void validarRateLimit() {
        var rl = props.rateLimit();
        if (rl.peticionesPorMinuto() <= 0 || rl.peticionesAuthPorMinuto() <= 0 || rl.ventanaSegundos() <= 0) {
            throw new IllegalStateException(
                    "tienda.rate-limit debe usar valores mayores que cero (peticiones, auth y ventana).");
        }
    }
}
