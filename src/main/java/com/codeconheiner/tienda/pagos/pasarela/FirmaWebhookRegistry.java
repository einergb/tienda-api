package com.codeconheiner.tienda.pagos.pasarela;

import com.codeconheiner.tienda.common.ApiException;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Resuelve el verificador de firma por pasarela; si no hay, rechaza con 400. */
@Component
public class FirmaWebhookRegistry {

    private final Map<String, VerificadorFirmaWebhook> porPasarela;

    public FirmaWebhookRegistry(java.util.List<VerificadorFirmaWebhook> verificadores) {
        this.porPasarela = verificadores.stream()
                .collect(Collectors.toMap(v -> v.pasarela().trim().toLowerCase(Locale.ROOT),
                        Function.identity()));
    }

    public VerificadorFirmaWebhook obtener(String pasarela) {
        VerificadorFirmaWebhook verificador =
                porPasarela.get(pasarela == null ? "" : pasarela.trim().toLowerCase(Locale.ROOT));
        if (verificador == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Webhook de pasarela no soportada");
        }
        return verificador;
    }

    /** Lee una cabecera sin importar mayúsculas/minúsculas. */
    public static String encabezado(Map<String, String> headers, String nombre) {
        for (Map.Entry<String, String> e : headers.entrySet()) {
            if (e.getKey() != null && e.getKey().equalsIgnoreCase(nombre)) {
                return e.getValue();
            }
        }
        return null;
    }
}
