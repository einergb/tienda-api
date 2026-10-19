package com.codeconheiner.tienda.common.ratelimit;

/**
 * Puerto (interfaz de dominio) para limitar la tasa de solicitudes por clave
 * (IP del cliente, usuario, etc.). La política de límites vive fuera del filtro
 * que la consume (principio de inversión de dependencias).
 */
public interface RateLimiter {

    /**
     * Registra una solicitud para la clave indicada.
     *
     * @return true si la solicitud cabe dentro del límite; false si se excedió.
     */
    boolean permitir(String clave);
}
