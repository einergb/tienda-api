package com.codeconheiner.tienda.webhooks;

import com.codeconheiner.tienda.common.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Payload de Wompi (evento {@code transaction.updated}):
 * {@code {"event":"transaction.updated","data":{"id":123,"status":"APPROVED",
 * "reference":"PED-0001-ab12cd34","amount_in_cents":1238000}}}.
 */
@Component
public class WompiEventoParser implements EventoWebhookParser {

    private final ObjectMapper mapper;

    public WompiEventoParser(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public String pasarela() {
        return "WOMPI";
    }

    @Override
    public Optional<WebhookEvento> parsear(String cuerpo) {
        JsonNode raiz;
        try {
            raiz = mapper.readTree(cuerpo);
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Payload de webhook inválido");
        }
        String evento = raiz.path("event").asText("");
        if (!evento.startsWith("transaction.")) {
            return Optional.empty();
        }
        JsonNode data = raiz.path("data");
        String estado = data.path("status").asText("");
        String referencia = data.path("reference").asText(null);
        String id = data.path("id").asText(null);
        if (referencia == null || id == null || estado.isBlank()) {
            return Optional.empty();
        }
        boolean aprobado = "APPROVED".equalsIgnoreCase(estado);
        BigDecimal monto = data.has("amount_in_cents")
                ? BigDecimal.valueOf(data.path("amount_in_cents").asLong()).movePointLeft(2)
                : null;
        return Optional.of(new WebhookEvento("WOMPI", id + ":" + estado, referencia, aprobado, monto));
    }
}
