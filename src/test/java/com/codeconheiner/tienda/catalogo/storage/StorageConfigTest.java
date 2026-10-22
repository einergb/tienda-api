package com.codeconheiner.tienda.catalogo.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.codeconheiner.tienda.config.TiendaProperties;
import java.util.List;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.S3Client;

class StorageConfigTest {

    @Test
    void creaElClienteS3ConCredencialesEstaticas() {
        StorageConfig config = new StorageConfig();

        S3Client cliente = config.s3Client(props("clave", "secreto"));

        assertThat(cliente).isNotNull();
        assertThat(cliente.serviceClientConfiguration()).isNotNull();
        cliente.close();
    }

    @Test
    void arrancaSinCredencialesConfiguradas() {
        StorageConfig config = new StorageConfig();

        assertThatCode(() -> {
            S3Client cliente = config.s3Client(props("", ""));
            cliente.close();
        }).doesNotThrowAnyException();
    }

    private TiendaProperties props(String accessKey, String secretKey) {
        return new TiendaProperties("Tienda", "COP", java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO, 10000,
                5, 15, new TiendaProperties.Jwt("test-only-secret-with-at-least-32-bytes", 30),
                new TiendaProperties.Admin("admin@example.com", ""),
                new TiendaProperties.Cors(List.of("http://localhost:5173")),
                new TiendaProperties.RateLimit(300, 10, 60), TiendaProperties.Pagos.simulados(),
                new TiendaProperties.Captcha("test-secret", null),
                new TiendaProperties.Mail("no-reply@test.local", "http://localhost:8080"),
                new TiendaProperties.Storage("http://localhost:9000", accessKey, secretKey, "bucket",
                        "http://localhost:9000", Boolean.TRUE));
    }
}
