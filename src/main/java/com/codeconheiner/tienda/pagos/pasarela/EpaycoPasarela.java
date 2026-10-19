package com.codeconheiner.tienda.pagos.pasarela;

import com.codeconheiner.tienda.common.ApiException;
import com.codeconheiner.tienda.config.TiendaProperties;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * ePayco (enfoque Smart Checkout): el backend se autentica en {@code POST {apify-url}/login}
 * con Basic Auth (llave pública:llave privada) para obtener un token, y con él crea una
 * sesión de checkout que el frontend renderiza con su librería JavaScript. ePayco cobra y
 * notifica a {@code POST /api/webhooks/epayco} con la cabecera {@code x-signature}.
 *
 * <p>Modo simulado (por defecto): no hace llamadas HTTP; devuelve una sesión ficticia.</p>
 */
@Component
public class EpaycoPasarela implements PasarelaPago {

    private static final Logger log = LoggerFactory.getLogger(EpaycoPasarela.class);

    private final TiendaProperties.Pagos.Epayco cfg;
    private final boolean simulado;
    private final RestClient http = RestClient.create();

    public EpaycoPasarela(TiendaProperties props) {
        this.cfg = props.pagos().epayco();
        this.simulado = props.pagos().simulado();
    }

    @Override
    public String nombre() {
        return "EPAYCO";
    }

    @Override
    public SesionPago crearSesion(CrearTransaccion transaccion) {
        if (simulado) {
            log.info("[simulado] Epayco crearia sesion de checkout referencia={} monto={}",
                    transaccion.referencia(), transaccion.monto());
            return new SesionPago(nombre(), transaccion.referencia(),
                    "SIM-" + transaccion.referencia(), null);
        }
        try {
            String token = autenticar();

            Map<String, Object> cuerpo = new LinkedHashMap<>();
            cuerpo.put("amount", transaccion.monto());
            cuerpo.put("currency", transaccion.moneda());
            cuerpo.put("description", "Pedido " + transaccion.referencia());
            cuerpo.put("invoice", transaccion.referencia());
            if (transaccion.email() != null) {
                cuerpo.put("email", transaccion.email());
            }

            // El nombre exacto del endpoint de sesiones lo define la guía vigente de ePayco.
            JsonNode respuesta = http.post()
                    .uri(cfg.apiUrl() + "/payment/create")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .header("Idempotency-Key", transaccion.idempotencyKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(cuerpo)
                    .retrieve()
                    .body(JsonNode.class);
            String sesionId = respuesta == null ? null
                    : respuesta.path("data").path("sessionId").asText(null);
            String urlPago = respuesta == null ? null
                    : respuesta.path("data").path("paymentUrl").asText(null);
            return new SesionPago(nombre(), transaccion.referencia(), sesionId, urlPago);
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Fallo al crear la sesion de checkout en ePayco", e);
            throw new ApiException(HttpStatus.BAD_GATEWAY, "La pasarela ePayco no está disponible");
        }
    }

    private String autenticar() {
        String credenciales = Base64.getEncoder()
                .encodeToString((cfg.publicKey() + ":" + cfg.privateKey())
                        .getBytes(StandardCharsets.UTF_8));
        JsonNode respuesta = http.post()
                .uri(cfg.apifyUrl() + "/login")
                .header(HttpHeaders.AUTHORIZATION, "Basic " + credenciales)
                .retrieve()
                .body(JsonNode.class);
        String token = respuesta == null ? null : respuesta.path("token").asText(null);
        if (token == null) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "La pasarela ePayco no está disponible");
        }
        return token;
    }
}
