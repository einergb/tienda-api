package com.codeconheiner.tienda.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Ejecuta periodicamente la funcion V3 expirar_reservas(): cancela pedidos PENDIENTES
 * cuya reserva expiro y libera el stock reservado.
 */
@Component
@EnableScheduling
public class TareaExpiracionReservas {

    private static final Logger log = LoggerFactory.getLogger(TareaExpiracionReservas.class);

    private final JdbcTemplate jdbc;

    public TareaExpiracionReservas(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Scheduled(fixedDelay = 60_000)
    public void expirarReservas() {
        try {
            Integer cancelados = jdbc.queryForObject("SELECT expirar_reservas()", Integer.class);
            if (cancelados != null && cancelados > 0) {
                log.info("Se cancelaron {} pedidos con reserva expirada", cancelados);
            }
        } catch (DataAccessException e) {
            log.warn("No se pudo ejecutar expirar_reservas(): {}", e.getMessage());
        }
    }
}
