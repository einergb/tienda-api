package com.codeconheiner.tienda.catalogo.storage;

import com.codeconheiner.tienda.common.ApiException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
@ConditionalOnProperty(name = "tienda.storage.tipo", havingValue = "URL", matchIfMissing = true)
public class UrlOnlyImageStorage implements ImageStorage {

    @Override
    public String upload(MultipartFile file) {
        throw new ApiException(HttpStatus.BAD_REQUEST, "La subida de archivos no está habilitada. Usa imagenUrl.");
    }

    @Override
    public boolean supportsUpload() {
        return false;
    }
}
