package com.codeconheiner.tienda.catalogo.storage;

import com.codeconheiner.tienda.config.TiendaProperties;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

/** Cliente S3-compatible (RustFS) con acceso por ruta: http://host:9000/{bucket}/{clave}. */
@Configuration
public class StorageConfig {

    private static final Logger log = LoggerFactory.getLogger(StorageConfig.class);

    @Bean
    public S3Client s3Client(TiendaProperties props) {
        TiendaProperties.Storage storage = props.storage();
        String endpoint = storage == null || storage.endpoint() == null || storage.endpoint().isBlank()
                ? "http://192.168.0.150:9000"
                : storage.endpoint();
        String accessKey = storage == null ? "" : nullSafe(storage.accessKey());
        String secretKey = storage == null ? "" : nullSafe(storage.secretKey());
        return S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.US_EAST_1)
                .credentialsProvider(credenciales(accessKey, secretKey))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();
    }

    /**
     * Credenciales estaticas del bucket. Sin access-key/secret-key la app arranca igual (para no
     * tumbar el arranque) pero las subidas fallan con 403 del almacen.
     */
    private AwsCredentialsProvider credenciales(String accessKey, String secretKey) {
        if (accessKey.isBlank() || secretKey.isBlank()) {
            log.warn("Almacenamiento S3 sin credenciales configuradas (STORAGE_ACCESS_KEY/STORAGE_SECRET_KEY)");
            return AnonymousCredentialsProvider.create();
        }
        return StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey));
    }

    private static String nullSafe(String valor) {
        return valor == null ? "" : valor;
    }
}
