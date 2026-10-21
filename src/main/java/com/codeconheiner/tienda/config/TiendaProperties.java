package com.codeconheiner.tienda.config;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Parametros de la tienda (prefijo "tienda" en application.yml). */
@ConfigurationProperties(prefix = "tienda")
public record TiendaProperties(
        String nombre,
        String moneda,
        BigDecimal iva,
        BigDecimal envioGratisDesde,
        long costoEnvioBase,
        int stockMinimoAlerta,
        int reservaStockMinutos,
        Jwt jwt,
        Admin admin,
        Cors cors,
        RateLimit rateLimit,
        Pagos pagos,
        Captcha captcha,
        Mail mail) {

    public record Jwt(String secret, int expiracionMinutos) {}

    public record Admin(String email, String password) {}

    public record Cors(List<String> origenesPermitidos) {}

    /** Limitación de tasa por IP (ventana deslizante en memoria). */
    public record RateLimit(int peticionesPorMinuto, int peticionesAuthPorMinuto, int ventanaSegundos) {}

    /**
     * Integración con pasarelas de pago. En modo "simulado" (por defecto) no se llama a
     * ninguna API externa: se devuelven sesiones ficticias para probar el flujo completo.
     */
    public record Pagos(String modo, Wompi wompi, Epayco epayco) {

        /** Modo simulado sin credenciales: utilizable en pruebas y entornos sin pasarela. */
        public static Pagos simulados() {
            return new Pagos("simulado", new Wompi(null, null, null, null, null),
                    new Epayco(null, null, null, null));
        }

        public record Wompi(String apiUrl, String publicKey, String privateKey, String integritySecret,
                String redirectUrl) {}

        public record Epayco(String apiUrl, String apifyUrl, String publicKey, String privateKey) {}

        public boolean simulado() {
            return modo == null || "simulado".equalsIgnoreCase(modo);
        }
    }

    public record Captcha(String secreto, String url) {
    }

    public record Mail(String from, String baseUrl) {
    }
}
