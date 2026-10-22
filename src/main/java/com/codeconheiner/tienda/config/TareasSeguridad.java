package com.codeconheiner.tienda.config;

import com.codeconheiner.tienda.auth.RefreshTokenStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Limpieza periódica de sesiones y reservas de stock.
 *
 * - Expira reservas de pedidos PENDIENTES vencidas (función de V3).
 * - Purga refresh tokens caducados y familias revocadas hace tiempo.
 */
@Component
@EnableScheduling
public class TareasSeguridad {

    private static final Logger log = LoggerFactory.getLogger(TareasSeguridad.class);

    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    private final RefreshTokenStore refreshTokens;
    private final CacheCatalogo cache;

    public TareasSeguridad(org.springframework.jdbc.core.JdbcTemplate jdbc, RefreshTokenStore refreshTokens,
            CacheCatalogo cache) {
        this.jdbc = jdbc;
        this.refreshTokens = refreshTokens;
        this.cache = cache;
    }

    /** Cada 60 segundos: expira reservas vencidas. */
    @Scheduled(fixedDelay = 60_000)
    public void expirarReservas() {
        try {
            Integer cancelados = jdbc.queryForObject("SELECT expirar_reservas()", Integer.class);
            if (cancelados != null && cancelados > 0) {
                log.info("Se cancelaron {} pedidos con reserva expirada", cancelados);
                cache.invalidarTodoElCatalogo();
            }
        } catch (DataAccessException e) {
            log.warn("No se pudo ejecutar expirar_reservas(): {}", e.getMessage());
        }
    }

    /** Cada hora: purga tokens caducados/revocados antiguos (retención 30 días). */
    @Scheduled(fixedDelay = 3_600_000)
    public void purgarTokens() {
        try {
            int borrados = refreshTokens.purgarAntiguos(30);
            if (borrados > 0) {
                log.info("Se purgaron {} refresh tokens antiguos", borrados);
            }
        } catch (DataAccessException e) {
            log.warn("No se pudo purgar refresh tokens: {}", e.getMessage());
        }
    }
}
