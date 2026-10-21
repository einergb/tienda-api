package com.codeconheiner.tienda.auth.verification;

import jakarta.servlet.http.HttpServletRequest;
import java.time.OffsetDateTime;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ConsentimientoRepository {

    private final JdbcTemplate jdbc;

    public ConsentimientoRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void guardar(Long usuarioId, String tipoDocumento, String version, HttpServletRequest request) {
        String ip = request == null ? null : request.getRemoteAddr();
        String ua = request == null ? null : request.getHeader("User-Agent");
        if (ua != null && ua.length() > 255) {
            ua = ua.substring(0, 255);
        }
        jdbc.update("""
                INSERT INTO consentimientos (usuario_id, tipo_documento, version, fecha_hora, ip, user_agent)
                VALUES (?, ?, ?, ?, ?, ?)
                """, usuarioId, tipoDocumento, version, OffsetDateTime.now(), ip, ua);
    }
}
