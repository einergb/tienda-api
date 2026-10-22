package com.codeconheiner.tienda.pagos.pasarela;

import static org.assertj.core.api.Assertions.assertThat;

import com.codeconheiner.tienda.common.Hashes;
import com.codeconheiner.tienda.config.TiendaProperties;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Verifica la firma de los webhooks de cada pasarela y el modo simulado de las
 * integraciones (que es el que corre por defecto y en las pruebas de integración).
 */
class PasarelasTest {

    private static TiendaProperties props(String secretoWompi, String llaveEpayco) {
        return new TiendaProperties("Tienda", "COP", new BigDecimal("0.19"), BigDecimal.ZERO, 10000, 5, 15,
                new TiendaProperties.Jwt("pasarelas-tests-secret-at-least-32-bytes", 120),
                new TiendaProperties.Admin("admin@example.com", ""),
                new TiendaProperties.Cors(List.of("http://localhost:5173")),
                new TiendaProperties.RateLimit(300, 10, 60),
                new TiendaProperties.Pagos("simulado",
                        new TiendaProperties.Pagos.Wompi(null, null, null, secretoWompi, null),
                        new TiendaProperties.Pagos.Epayco(null, null, null, llaveEpayco)),
                new TiendaProperties.Captcha("test-secret", null),
                new TiendaProperties.Mail("no-reply@test.local", "http://localhost:8080"),
                new TiendaProperties.Storage("http://localhost:9000", "key", "secret", "bucket", "http://localhost:9000", Boolean.TRUE),
                new TiendaProperties.CacheConfigProperties("5m", "60s", "1h", "1h"));
    }

    @Test
    void wompiValidaElChecksumSha256DelPayloadConElSecretoDeIntegridad() {
        VerificadorFirmaWebhook wompi = new WompiFirmaWebhook(props("secreto-wompi", null));
        String cuerpo = "{\"event\":\"transaction.updated\",\"data\":{}}";
        String checksum = Hashes.sha256Hex(cuerpo + "secreto-wompi");

        // mayúsculas/minúsculas de la cabecera no importan
        assertThat(wompi.firmaValida(Map.of("X-Event-Checksum", checksum), cuerpo)).isTrue();
        assertThat(wompi.firmaValida(Map.of("x-event-checksum", checksum.toUpperCase()), cuerpo)).isTrue();

        // otro checksum, otro cuerpo o sin cabecera no pasan
        assertThat(wompi.firmaValida(Map.of("X-Event-Checksum", Hashes.sha256Hex(cuerpo)), cuerpo))
                .isFalse();
        assertThat(wompi.firmaValida(Map.of("X-Event-Checksum", checksum), cuerpo + " ")).isFalse();
        assertThat(wompi.firmaValida(Map.of(), cuerpo)).isFalse();
    }

    @Test
    void wompiRechazaTodaFirmaSinSecretoConfigurado() {
        // seguro por defecto: sin secreto, ningún webhook es válido
        VerificadorFirmaWebhook wompi = new WompiFirmaWebhook(props(null, null));
        String cuerpo = "{}";

        assertThat(wompi.firmaValida(Map.of("X-Event-Checksum", Hashes.sha256Hex(cuerpo)), cuerpo))
                .isFalse();
        assertThat(wompi.firmaValida(Map.of("X-Event-Checksum", ""), cuerpo)).isFalse();
    }

    @Test
    void epaycoValidaXSignatureSha256DelPayloadConLaLlavePrivada() {
        VerificadorFirmaWebhook epayco = new EpaycoFirmaWebhook(props(null, "llave-privada"));
        String cuerpo = "{\"type\":\"charge\",\"data\":{\"x_invoice\":\"PED-1-abc12345\"}}";
        String firma = Hashes.sha256Hex(cuerpo + "llave-privada");

        assertThat(epayco.firmaValida(Map.of("x-signature", firma), cuerpo)).isTrue();
        assertThat(epayco.firmaValida(Map.of("X-Signature", firma), cuerpo)).isTrue();

        assertThat(epayco.firmaValida(Map.of("x-signature", "firma-falsa"), cuerpo)).isFalse();
        assertThat(epayco.firmaValida(Map.of(), cuerpo)).isFalse();
    }

    @Test
    void epaycoRechazaTodaFirmaSinLlavePrivada() {
        VerificadorFirmaWebhook epayco = new EpaycoFirmaWebhook(props(null, null));
        String cuerpo = "{}";

        assertThat(epayco.firmaValida(Map.of("x-signature", Hashes.sha256Hex(cuerpo)), cuerpo))
                .isFalse();
    }

    @Test
    void modoSimuladoDevuelveSesionSinLlamarNingunaApiExterna() {
        TiendaProperties props = props("secreto", "llave");
        PasarelaPago wompi = new WompiPasarela(props);
        PasarelaPago epayco = new EpaycoPasarela(props);
        CrearTransaccion transaccion = new CrearTransaccion("clave-12345678",
                "PED-0001-ab12cd34", new BigDecimal("12380"), "COP", "cliente@example.com", 1L);

        assertThat(wompi.nombre()).isEqualTo("WOMPI");
        assertThat(epayco.nombre()).isEqualTo("EPAYCO");

        SesionPago wompiSesion = wompi.crearSesion(transaccion);
        assertThat(wompiSesion.sesionId()).isEqualTo("SIM-PED-0001-ab12cd34");
        assertThat(wompiSesion.referencia()).isEqualTo("PED-0001-ab12cd34");
        assertThat(wompiSesion.urlPago()).isNull();

        assertThat(epayco.crearSesion(transaccion).sesionId())
                .isEqualTo("SIM-PED-0001-ab12cd34");
    }

    @Test
    void elRegistryResuelveLaPasarelaYRechazaLasNoSoportadas() {
        TiendaProperties props = props("secreto", "llave");
        FirmaWebhookRegistry registry = new FirmaWebhookRegistry(
                List.of(new WompiFirmaWebhook(props), new EpaycoFirmaWebhook(props)));

        assertThat(registry.obtener("WOMPI")).isInstanceOf(WompiFirmaWebhook.class);
        assertThat(registry.obtener("epayco")).isInstanceOf(EpaycoFirmaWebhook.class);

        com.codeconheiner.tienda.common.ApiException e = org.assertj.core.api.Assertions
                .catchThrowableOfType(() -> registry.obtener("PAYU"),
                        com.codeconheiner.tienda.common.ApiException.class);
        assertThat(e.getStatus().value()).isEqualTo(400);
        assertThat(e.getMessage()).isEqualTo("Webhook de pasarela no soportada");
    }
}
