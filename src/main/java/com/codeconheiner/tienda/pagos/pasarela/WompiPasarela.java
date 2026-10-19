package com.codeconheiner.tienda.pagos.pasarela;

import com.codeconheiner.tienda.common.ApiException;
import com.codeconheiner.tienda.config.TiendaProperties;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Wompi (enfoque API directa): el backend crea la transacción contra
 * {@code POST {api-url}/transactions} con la cabecera {@code idempotency-key}; el pago
 * completo lo realiza el usuario con el checkout.js de Wompi o vía PSE/NEQUI, y Wompi
 * notifica a {@code POST /api/webhooks/wompi} con el checksum {@code X-Event-Checksum}.
 *
 * <p>Modo simulado (por defecto): no hace llamadas HTTP; devuelve una sesión ficticia.</p>
 */
@Component
public class WompiPasarela implements PasarelaPago {

    private static final Logger log = LoggerFactory.getLogger(WompiPasarela.class);

    private final TiendaProperties.Pagos.Wompi cfg;
    private final boolean simulado;
    private final RestClient http = RestClient.create();

    public WompiPasarela(TiendaProperties props) {
        this.cfg = props.pagos().wompi();
        this.simulado = props.pagos().simulado();
    }

    @Override
    public String nombre() {
        return "WOMPI";
    }

    @Override
    public SesionPago crearSesion(CrearTransaccion transaccion) {
        if (simulado) {
            log.info("[simulado] Wompi crearia transaccion referencia={} monto={}",
                    transaccion.referencia(), transaccion.monto());
            return new SesionPago(nombre(), transaccion.referencia(),
                    "SIM-" + transaccion.referencia(), null);
        }
        try {
            Map<String, Object> cuerpo = new LinkedHashMap<>();
            cuerpo.put("amount_in_cents", aCentavos(transaccion.monto()));
            cuerpo.put("currency", transaccion.moneda());
            cuerpo.put("reference", transaccion.referencia());
            if (transaccion.email() != null) {
                cuerpo.put("customer_email", transaccion.email());
            }
            cuerpo.put("redirect_url", cfg.redirectUrl());

            JsonNode respuesta = http.post()
                    .uri(cfg.apiUrl() + "/transactions")
                    .header("Authorization", "Bearer " + cfg.privateKey())
                    .header("Idempotency-Key", transaccion.idempotencyKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(cuerpo)
                    .retrieve()
                    .body(JsonNode.class);
            String sesionId = respuesta == null ? null
                    : respuesta.path("data").path("id").asText(null);
            return new SesionPago(nombre(), transaccion.referencia(), sesionId, cfg.redirectUrl());
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Fallo al crear la transaccion en Wompi", e);
            throw new ApiException(HttpStatus.BAD_GATEWAY, "La pasarela Wompi no está disponible");
        }
    }

    static long aCentavos(BigDecimal monto) {
        return monto.movePointRight(2).longValueExact();
    }
}
