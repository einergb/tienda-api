package com.codeconheiner.tienda.auth.mail;

import com.codeconheiner.tienda.config.TiendaProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final TiendaProperties props;

    public EmailService(TiendaProperties props) {
        this.props = props;
    }

    public void enviarConfirmacion(String email, String token) {
        String baseUrl = props.mail() == null || props.mail().baseUrl() == null
                ? "http://localhost:8080"
                : props.mail().baseUrl();
        String link = baseUrl + "/api/auth/confirmar-email?token=" + token;
        String from = props.mail() == null || props.mail().from() == null ? "no-reply@tienda.local" : props.mail().from();
        log.info("[EMAIL] from={} to={} subject='Confirmación de correo' link={}", from, email,
                "Confirmación de correo", link);
        // En entorno actual no hay SMTP configurado; se registra para permitir reintentos/seguimiento
    }
}
