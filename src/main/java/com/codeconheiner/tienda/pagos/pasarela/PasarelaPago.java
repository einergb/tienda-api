package com.codeconheiner.tienda.pagos.pasarela;

/**
 * Puerto de integración con una pasarela de pago (patrón orquestador: el backend crea la
 * transacción/sesión y la pasarela cobra al usuario y notifica por webhook).
 *
 * <p>Cada pasarela es una clase independiente; agregar una nueva no modifica el código
 * existente (abierto al cambio, cerrado a la modificación). La firma de los webhooks se
 * valida en {@link VerificadorFirmaWebhook}, separada por responsabilidad.</p>
 */
public interface PasarelaPago {

    /** Nombre normalizado usado por la API (p. ej. "WOMPI", "EPAYCO"). */
    String nombre();

    /**
     * Crea la transacción o sesión de pago. Debe enviar la cabecera
     * {@code idempotency-key} con {@link CrearTransaccion#idempotencyKey()}.
     */
    SesionPago crearSesion(CrearTransaccion transaccion);
}
