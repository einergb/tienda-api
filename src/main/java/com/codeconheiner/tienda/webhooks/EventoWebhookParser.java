package com.codeconheiner.tienda.webhooks;

import java.util.Optional;

/**
 * Convierte el payload crudo de una pasarela en un {@link WebhookEvento} normalizado.
 * Una interfaz por pasarela (segregación de interfaces); agregar una nueva pasarela es
 * agregar una clase, no modificar el controlador (abierto al cambio).
 */
public interface EventoWebhookParser {

    /** Pasarela dueña del formato de payload (mismo nombre que {@code PasarelaPago}). */
    String pasarela();

    /**
     * @return el evento normalizado, o vacío si el payload no es una notificación que
     *         afecte el estado del pago (p. ej. eventos informativos).
     */
    Optional<WebhookEvento> parsear(String cuerpo);
}
