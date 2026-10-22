package com.codeconheiner.tienda.catalogo.storage;

import com.codeconheiner.tienda.common.ApiException;
import com.codeconheiner.tienda.config.TiendaProperties;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * Guarda las imagenes de los productos en un almacenamiento S3-compatible (RustFS) y devuelve la
 * clave del objeto. En Postgres solo se guarda esa clave.
 */
@Service
public class StorageService {

    private static final Logger log = LoggerFactory.getLogger(StorageService.class);
    private static final Set<String> TIPOS_PERMITIDOS = Set.of("image/jpeg", "image/png", "image/webp");
    private static final long MAX_SIZE = 5L * 1024 * 1024;

    private final S3Client s3Client;
    private final TiendaProperties props;

    public StorageService(S3Client s3Client, TiendaProperties props) {
        this.s3Client = s3Client;
        this.props = props;
    }

    /**
     * Valida y sube la imagen. La clave generada es "productos/{idProducto}/{uuid}.{extension}":
     * nunca se usa el nombre original del archivo.
     *
     * @return la clave del objeto subido
     */
    public String subir(MultipartFile file, Long productoId) {
        String prefijo = "productos/" + productoId;
        return subirArchivo(file, prefijo);
    }

    /**
     * Método general para subir archivos con validaciones reutilizables.
     * Genera la clave "{prefijo}/{UUID}.{ext}" sin usar el nombre original.
     */
    public String subirArchivo(MultipartFile file, String prefijo) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Archivo inválido");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Archivo muy grande");
        }
        String contentType = file.getContentType();
        if (contentType == null || !TIPOS_PERMITIDOS.contains(contentType)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Tipo de archivo no permitido");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Archivo inválido");
        }
        // No basta con el content-type: se comprueba que el contenido sea realmente una imagen.
        if (!esImagenValida(bytes, contentType)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Archivo no es una imagen válida");
        }
        String prefijoLimpio = prefijo == null ? "" : prefijo.replaceAll("^/+", "").replaceAll("/+$", "");
        String key = (prefijoLimpio.isBlank() ? "" : prefijoLimpio + "/")
                + UUID.randomUUID() + "." + extension(contentType);
        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucket())
                    .key(key)
                    .contentType(contentType)
                    .cacheControl("public, max-age=31536000, immutable")
                    .build();
            s3Client.putObject(request, RequestBody.fromBytes(bytes));
        } catch (Exception e) {
            log.error("No se pudo subir la imagen {}", key, e);
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo guardar la imagen");
        }
        return key;
    }

    /** Borra el objeto del almacenamiento. Si falla solo queda un aviso: no tumba la peticion. */
    public void eliminar(String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        try {
            DeleteObjectRequest request = DeleteObjectRequest.builder().bucket(bucket()).key(key).build();
            s3Client.deleteObject(request);
        } catch (Exception e) {
            log.warn("No se pudo eliminar la imagen {}", key, e);
        }
    }

    /** URL publica del objeto. Null si no hay clave. */
    public String urlPublica(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        String base = publicBaseUrl();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (publicUrlIncluyeBucket()) {
            return base + "/" + bucket() + "/" + key;
        }
        return base + "/" + key;
    }

    /** Deriva la clave desde una URL pública propia. Devuelve Optional vacío si no es nuestra. */
    public Optional<String> derivarClaveDesdeUrlPublica(String url) {
        if (url == null || url.isBlank()) {
            return Optional.empty();
        }
        String base = publicBaseUrl();
        if (base != null && base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (base == null || !url.startsWith(base + "/")) {
            return Optional.empty();
        }
        String resto = url.substring((base + "/").length());
        if (publicUrlIncluyeBucket()) {
            String b = bucket();
            if (b != null && !b.isBlank() && resto.startsWith(b + "/")) {
                String clave = resto.substring((b + "/").length());
                return clave.isBlank() ? Optional.empty() : Optional.of(clave);
            }
            return Optional.empty();
        }
        // Si no incluye bucket, el resto debe ser la clave tal cual (puede incluir prefijos como productos/...)
        if (resto.isBlank()) {
            return Optional.empty();
        }
        // Evitar confundir URLs que aún incluyan el bucket cuando el flag está false
        String b2 = bucket();
        if (b2 != null && !b2.isBlank() && resto.startsWith(b2 + "/")) {
            return Optional.empty();
        }
        return Optional.of(resto);
    }

    private boolean publicUrlIncluyeBucket() {
        TiendaProperties.Storage storage = props.storage();
        if (storage == null || storage.publicUrlIncluyeBucket() == null) {
            return true;
        }
        return storage.publicUrlIncluyeBucket();
    }

    private String bucket() {
        TiendaProperties.Storage storage = props.storage();
        return storage == null || storage.bucket() == null || storage.bucket().isBlank()
                ? "tienda-productos-dev"
                : storage.bucket();
    }

    private String publicBaseUrl() {
        TiendaProperties.Storage storage = props.storage();
        return storage == null || storage.publicBaseUrl() == null || storage.publicBaseUrl().isBlank()
                ? "http://192.168.0.150:9000"
                : storage.publicBaseUrl();
    }

    /**
     * JPEG y PNG se validan decodificando la imagen con ImageIO. WebP no tiene lector en ImageIO
     * (hace falta un plugin), asi que se valida con la firma RIFF....WEBP.
     */
    private boolean esImagenValida(byte[] bytes, String contentType) {
        if (bytes == null || bytes.length == 0) {
            return false;
        }
        if ("image/webp".equals(contentType)) {
            return esWebp(bytes);
        }
        try {
            BufferedImage imagen = ImageIO.read(new ByteArrayInputStream(bytes));
            return imagen != null && imagen.getWidth() > 0 && imagen.getHeight() > 0;
        } catch (IOException e) {
            return false;
        }
    }

    /** Firma WebP: "RIFF" + 4 bytes de tamaño + "WEBP". */
    private boolean esWebp(byte[] bytes) {
        return bytes.length >= 12
                && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P';
    }

    private String extension(String contentType) {
        if ("image/png".equals(contentType)) {
            return "png";
        }
        if ("image/webp".equals(contentType)) {
            return "webp";
        }
        return "jpg";
    }
}
