package com.codeconheiner.tienda.common;

import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** Respuestas de error uniformes (RFC 7807 / ProblemDetail). */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final String MSG_RESTRICCION =
        "La operación viola una restricción de datos (duplicado o referencia inexistente).";
    private static final String MSG_REGLA_GENERICA =
        "La operación no cumple una regla de negocio.";
    /** El driver PG adjunta "\n  Where: PL/pgSQL function ..." (la sangría varía según la ruta). */
    private static final java.util.regex.Pattern PATRON_DONDE =
        java.util.regex.Pattern.compile("\\R\\s*Where:");

    @ExceptionHandler(ApiException.class)
    ProblemDetail handleApi(ApiException e) {
        return ProblemDetail.forStatusAndDetail(e.getStatus(), e.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail handleIntegrity(DataIntegrityViolationException e) {
        // Los RAISE EXCEPTION de V3 usan SQLSTATE P0001: son reglas de negocio y responden 422.
        if (esReglaDeNegocio(e)) {
            String mensaje = mensajeReglaDeNegocio(e);
            log.warn("Regla de negocio violada: {}", mensaje);
            return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, mensaje);
        }
        log.warn("Violación de integridad: {}", e.getMostSpecificCause().getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, MSG_RESTRICCION);
    }

    @ExceptionHandler(DataAccessException.class)
    ProblemDetail handleDataAccess(DataAccessException e) {
        if (esReglaDeNegocio(e)) {
            String mensaje = mensajeReglaDeNegocio(e);
            log.warn("Regla de negocio violada: {}", mensaje);
            return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, mensaje);
        }
        log.error("Error de acceso a datos", e);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno del servidor");
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleOther(Exception e) {
        log.error("Error no controlado", e);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno del servidor");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> errores = new LinkedHashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            errores.putIfAbsent(fe.getField(), fe.getDefaultMessage());
        }
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Los datos enviados no son válidos.");
        pd.setProperty("errores", errores);
        return ResponseEntity.badRequest().body(pd);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status,
                "El cuerpo de la solicitud no contiene JSON válido.");
        return handleExceptionInternal(ex, pd, headers, status, request);
    }

    /** Detecta SQLSTATE P0001 (RAISE EXCEPTION) en la cadena de causas. */
    private static boolean esReglaDeNegocio(Throwable e) {
        for (Throwable causa = e; causa != null; causa = causa.getCause()) {
            if (causa instanceof SQLException sql && sql.getSQLState() != null
                    && sql.getSQLState().startsWith("P0")) {
                return true;
            }
            if (causa.getCause() == causa) {
                break;
            }
        }
        return false;
    }

    /** Extrae el mensaje en espanol del RAISE EXCEPTION sin el prefijo "ERROR:" ni el "Where:" del driver. */
    private static String mensajeReglaDeNegocio(Throwable e) {
        for (Throwable causa = e; causa != null; causa = causa.getCause()) {
            if (causa instanceof SQLException sql && sql.getSQLState() != null
                    && sql.getSQLState().startsWith("P0")) {
                String mensaje = sql.getMessage() == null ? "" : sql.getMessage();
                mensaje = mensaje.replaceFirst("^ERROR:\\s*", "");
                java.util.regex.Matcher donde = PATRON_DONDE.matcher(mensaje);
                if (donde.find()) {
                    mensaje = mensaje.substring(0, donde.start());
                }
                mensaje = mensaje.trim();
                return mensaje.isEmpty() ? MSG_REGLA_GENERICA : mensaje;
            }
            if (causa.getCause() == causa) {
                break;
            }
        }
        return MSG_REGLA_GENERICA;
    }
}
