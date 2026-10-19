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
        Cors cors) {

    public record Jwt(String secret, int expiracionMinutos) {}

    public record Admin(String email, String password) {}

    public record Cors(List<String> origenesPermitidos) {}
}
