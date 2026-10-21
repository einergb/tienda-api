package com.codeconheiner.tienda.catalogo.storage;

import org.springframework.web.multipart.MultipartFile;

public interface ImageStorage {

    String upload(MultipartFile file);

    boolean supportsUpload();

}