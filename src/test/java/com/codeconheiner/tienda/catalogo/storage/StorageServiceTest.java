package com.codeconheiner.tienda.catalogo.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.codeconheiner.tienda.common.ApiException;
import com.codeconheiner.tienda.config.TiendaProperties;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;

class StorageServiceTest {

    @Test
    void rechazaTipoNoPermitido() {
        StorageService service = new StorageService(mock(S3Client.class), props());
        MockMultipartFile file = new MockMultipartFile("file", "a.gif", "image/gif", new byte[] {1, 2, 3, 4});

        ApiException error = org.assertj.core.api.Assertions.catchThrowableOfType(
                () -> service.subir(file, 1L), ApiException.class);

        assertThat(error).isNotNull();
        assertThat(error.getStatus().value()).isEqualTo(400);
        assertThat(error).hasMessage("Tipo de archivo no permitido");
    }

    @Test
    void rechazaArchivoMuyGrande() {
        StorageService service = new StorageService(mock(S3Client.class), props());
        byte[] grande = new byte[5 * 1024 * 1024 + 1];
        MockMultipartFile file = new MockMultipartFile("file", "a.jpg", "image/jpeg", grande);

        ApiException error = org.assertj.core.api.Assertions.catchThrowableOfType(
                () -> service.subir(file, 1L), ApiException.class);

        assertThat(error).isNotNull();
        assertThat(error.getStatus().value()).isEqualTo(400);
        assertThat(error).hasMessage("Archivo muy grande");
    }

    @Test
    void rechazaArchivoQueNoEsImagen() {
        StorageService service = new StorageService(mock(S3Client.class), props());
        // content-type de imagen pero el contenido es texto
        MockMultipartFile file = new MockMultipartFile("file", "a.jpg", "image/jpeg", "texto plano".getBytes());

        ApiException error = org.assertj.core.api.Assertions.catchThrowableOfType(
                () -> service.subir(file, 1L), ApiException.class);

        assertThat(error).isNotNull();
        assertThat(error.getStatus().value()).isEqualTo(400);
        assertThat(error).hasMessage("Archivo no es una imagen válida");
    }

    @Test
    void subeCorrectamenteConClaveBienFormada() {
        S3Client s3 = mock(S3Client.class);
        StorageService service = new StorageService(s3, props());
        MockMultipartFile file = new MockMultipartFile("file", "foto original.png", "image/png", png());

        String key = service.subir(file, 5L);

        assertThat(key).startsWith("productos/5/");
        assertThat(key).endsWith(".png");
        // la clave no debe contener el nombre original del archivo
        assertThat(key).doesNotContain("original");
        verify(s3).putObject(any(software.amazon.awssdk.services.s3.model.PutObjectRequest.class),
                any(RequestBody.class));
    }

    @Test
    void aceptaWebpValidandoLaFirmaRIFF() {
        S3Client s3 = mock(S3Client.class);
        StorageService service = new StorageService(s3, props());
        byte[] webp = new byte[16];
        System.arraycopy("RIFF".getBytes(), 0, webp, 0, 4);
        System.arraycopy("WEBP".getBytes(), 0, webp, 8, 4);
        MockMultipartFile file = new MockMultipartFile("file", "foto.webp", "image/webp", webp);

        String key = service.subir(file, 7L);

        assertThat(key).startsWith("productos/7/");
        assertThat(key).endsWith(".webp");
    }

    @Test
    void urlPublicaCombinaBaseBucketYClave() {
        StorageService service = new StorageService(mock(S3Client.class), props());

        assertThat(service.urlPublica("productos/1/a.png"))
                .isEqualTo("http://localhost:9000/bucket/productos/1/a.png");
        assertThat(service.urlPublica(null)).isNull();
    }

    private TiendaProperties props() {
        return new TiendaProperties("Tienda", "COP", java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO, 10000,
                5, 15, new TiendaProperties.Jwt("test-only-secret-with-at-least-32-bytes", 30),
                new TiendaProperties.Admin("admin@example.com", ""), new TiendaProperties.Cors(List.of("http://localhost:5173")),
                new TiendaProperties.RateLimit(300, 10, 60), com.codeconheiner.tienda.config.TiendaProperties.Pagos.simulados(),
                new TiendaProperties.Captcha("test-secret", null),
                new TiendaProperties.Mail("no-reply@test.local", "http://localhost:8080"),
                new TiendaProperties.Storage("http://localhost:9000", "key", "secret", "bucket", "http://localhost:9000", Boolean.TRUE),
                new TiendaProperties.CacheConfigProperties("5m", "60s", "1h", "1h"));
    }

    /** PNG valido de 1x1 generado en memoria. */
    private static byte[] png() {
        try {
            BufferedImage imagen = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(imagen, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
