package com.codeconheiner.tienda.pagos.pasarela;

import com.codeconheiner.tienda.common.Hashes;
import com.codeconheiner.tienda.config.TiendaProperties;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Checksum de Wompi: la cabecera {@code X-Event-Checksum} debe ser
 * {@code SHA-256(payload + secreto-de-integridad)} en hexadecimal.
 */
@Component
public class WompiFirmaWebhook implements VerificadorFirmaWebhook {

    private final String secreto;

    public WompiFirmaWebhook(TiendaProperties props) {
        this.secreto = props.pagos().wompi().integritySecret();
    }

    @Override
    public String pasarela() {
        return "WOMPI";
    }

    @Override
    public boolean firmaValida(Map<String, String> headers, String cuerpo) {
        String recibida = FirmaWebhookRegistry.encabezado(headers, "x-event-checksum");
        if (recibida == null || secreto == null || secreto.isBlank()) {
            return false;
        }
        return Hashes.igual(recibida.trim().toLowerCase(Locale.ROOT),
                Hashes.sha256Hex(cuerpo + secreto));
    }
}
