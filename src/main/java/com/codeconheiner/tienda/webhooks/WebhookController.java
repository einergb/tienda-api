package com.codeconheiner.tienda.webhooks;

import com.codeconheiner.tienda.common.ApiException;
import com.codeconheiner.tienda.pagos.pasarela.FirmaWebhookRegistry;
import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Notificaciones server-to-server de las pasarelas. Cada webhook se valida con la firma
 * propia de la pasarela (checksum Wompi / x-signature ePayco); las notificaciones
 * duplicadas se ignoran gracias a {@code webhook_eventos} (V5).
 */
@RestController
@RequestMapping("/api/webhooks")
public class WebhookController {

    private final FirmaWebhookRegistry firmas;
    private final Map<String, EventoWebhookParser> parsers;
    private final WebhookPagoService service;

    public WebhookController(FirmaWebhookRegistry firmas, List<EventoWebhookParser> parsers,
            WebhookPagoService service) {
        this.firmas = firmas;
        this.parsers = parsers.stream()
                .collect(Collectors.toMap(p -> p.pasarela().toLowerCase(Locale.ROOT), Function.identity()));
        this.service = service;
    }

    @PostMapping("/wompi")
    public ResponseEntity<Map<String, String>> wompi(@RequestBody String cuerpo,
            HttpServletRequest request) {
        return procesar("WOMPI", cuerpo, request);
    }

    @PostMapping("/epayco")
    public ResponseEntity<Map<String, String>> epayco(@RequestBody String cuerpo,
            HttpServletRequest request) {
        return procesar("EPAYCO", cuerpo, request);
    }

    private ResponseEntity<Map<String, String>> procesar(String pasarela, String cuerpo,
            HttpServletRequest request) {
        if (!firmas.obtener(pasarela).firmaValida(encabezados(request), cuerpo)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Firma del webhook inválida");
        }
        EventoWebhookParser parser = parsers.get(pasarela.toLowerCase(Locale.ROOT));
        String estado;
        try {
            Optional<WebhookEvento> evento = parser == null ? Optional.empty() : parser.parsear(cuerpo);
            estado = evento.map(service::procesar).orElse("ignorado");
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            // Payload ilegible: se acepta para que la pasarela no reintente indefinidamente.
            estado = "ignorado";
        }
        return ResponseEntity.ok(Map.of("estado", estado));
    }

    private static Map<String, String> encabezados(HttpServletRequest request) {
        Map<String, String> mapa = new HashMap<>();
        request.getHeaderNames().asIterator()
                .forEachRemaining(nombre -> mapa.put(nombre, request.getHeader(nombre)));
        return mapa;
    }
}
