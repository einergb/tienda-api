package com.codeconheiner.tienda.catalogo.storage;

import com.codeconheiner.tienda.common.ApiException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
@ConditionalOnProperty(name = "tienda.storage.tipo", havingValue = "LOCAL")
public class LocalImageStorage implements ImageStorage {

    private final Path uploadDir;
    private final String publicBaseUrl;

    public LocalImageStorage(@Value("${tienda.storage.local.dir:uploads}") String dir,
            @Value("${tienda.storage.local.public-base-url:/uploads}") String publicBaseUrl) {
        this.uploadDir = Paths.get(dir);
        this.publicBaseUrl = publicBaseUrl.endsWith("/") ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1)
                : publicBaseUrl;
        try {
            Files.createDirectories(this.uploadDir);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo crear el directorio de uploads", e);
        }
    }

    @Override
    public String upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "El archivo está vacío");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Solo se permiten archivos de imagen");
        }
        String original = file.getOriginalFilename();
        String extension = "";
        if (original != null && original.contains(".")) {
            extension = original.substring(original.lastIndexOf('.'));
        }
        String filename = UUID.randomUUID() + extension;
        try {
            Files.copy(file.getInputStream(), uploadDir.resolve(filename), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Error al guardar la imagen");
        }
        return publicBaseUrl + "/" + filename;
    }

    @Override
    public boolean supportsUpload() {
        return true;
    }
}
