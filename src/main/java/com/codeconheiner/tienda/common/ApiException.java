package com.codeconheiner.tienda.common;

import org.springframework.http.HttpStatus;

/** Error de negocio con el codigo HTTP que debe devolverse. */
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
