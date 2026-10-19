package com.codeconheiner.tienda.pagos.pasarela;

import com.codeconheiner.tienda.common.Hashes;
import com.codeconheiner.tienda.config.TiendaProperties;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Firma de ePayco: la cabecera {@code x-signature} debe ser
 * {@code SHA-256(payload + llave-privada)} en hexadecimal (recomendación oficial de ePayco
 * para validar que la notificación salió de ellos).
 */
@Component
public class EpaycoFirmaWebhook implements VerificadorFirmaWebhook {

    private final String llavePrivada;

    public EpaycoFirmaWebhook(TiendaProperties props) {
        this.llavePrivada = props.pagos().epayco().privateKey();
    }

    @Override
    public String pasarela() {
        return "EPAYCO";
    }

    @Override
    public boolean firmaValida(Map<String, String> headers, String cuerpo) {
        String recibida = FirmaWebhookRegistry.encabezado(headers, "x-signature");
        if (recibida == null || llavePrivada == null || llavePrivada.isBlank()) {
            return false;
        }
        return Hashes.igual(recibida.trim().toLowerCase(Locale.ROOT),
                Hashes.sha256Hex(cuerpo + llavePrivada));
    }
}
