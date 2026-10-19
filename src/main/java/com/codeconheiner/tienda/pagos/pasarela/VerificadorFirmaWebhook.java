package com.codeconheiner.tienda.pagos.pasarela;

import java.util.Map;

/**
 * Valida la autenticidad de las notificaciones server-to-server de una pasarela.
 * Interfaz separada de {@link PasarelaPago} (principio de segregación de interfaces).
 */
public interface VerificadorFirmaWebhook {

    /** Pasarela dueña del webhook (mismo nombre normalizado que {@code PasarelaPago}). */
    String pasarela();

    /** @return true solo si la firma enviada en las cabeceras corresponde al cuerpo. */
    boolean firmaValida(Map<String, String> headers, String cuerpo);
}
