package com.codeconheiner.tienda.webhooks;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class WebhookEventosRepository {

    private final JdbcTemplate jdbc;

    public WebhookEventosRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Registra el evento la primera vez que se ve. La restricción UNIQUE
     * (pasarela, evento_id) de V5 garantiza que las notificaciones duplicadas que las
     * pasarelas reenvían se ignoren (idempotencia entrante).
     *
     * @return true si el evento es nuevo; false si ya se había procesado.
     */
    public boolean registrarSiNuevo(String pasarela, String eventoId) {
        List<Long> ids = jdbc.query("""
                INSERT INTO webhook_eventos (pasarela, evento_id) VALUES (?, ?)
                ON CONFLICT (pasarela, evento_id) DO NOTHING
                RETURNING id
                """, (rs, n) -> rs.getLong("id"), pasarela, eventoId);
        return !ids.isEmpty();
    }
}
