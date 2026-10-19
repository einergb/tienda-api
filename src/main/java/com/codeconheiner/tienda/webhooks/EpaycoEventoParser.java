package com.codeconheiner.tienda.webhooks;

import com.codeconheiner.tienda.common.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Payload de ePayco (confirmación IPN del Smart Checkout):
 * {@code {"type":"charge","data":{"x_transaction_id":"ref1","x_invoice":"PED-0001-ab12cd34",
 * "x_cod_response":"1","x_amount":"123800.00"}}}. {@code x_cod_response = "1"} significa
 * aprobado.
 */
@Component
public class EpaycoEventoParser implements EventoWebhookParser {

    private final ObjectMapper mapper;

    public EpaycoEventoParser(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public String pasarela() {
        return "EPAYCO";
    }

    @Override
    public Optional<WebhookEvento> parsear(String cuerpo) {
        JsonNode raiz;
        try {
            raiz = mapper.readTree(cuerpo);
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Payload de webhook inválido");
        }
        JsonNode data = raiz.path("data");
        String id = data.path("x_transaction_id").asText(null);
        String referencia = data.path("x_invoice").asText(null);
        if (referencia == null) {
            referencia = data.path("x_id_factura").asText(null);
        }
        String codigo = data.path("x_cod_response").asText("");
        if (id == null || referencia == null || codigo.isBlank()) {
            return Optional.empty();
        }
        boolean aprobado = "1".equals(codigo);
        BigDecimal monto = data.path("x_amount").isNumber() ? data.path("x_amount").decimalValue() : null;
        return Optional.of(new WebhookEvento("EPAYCO", id + ":" + codigo, referencia, aprobado, monto));
    }
}
