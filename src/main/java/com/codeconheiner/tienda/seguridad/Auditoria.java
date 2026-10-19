package com.codeconheiner.tienda.seguridad;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Registro de operaciones sensibles en la tabla {@code auditoria} (V6).
 *
 * La auditoría nunca debe tumbar la petición de negocio: si falla la escritura
 * se registra en el log y se continúa.
 */
@Component
public class Auditoria {

    private static final Logger log = LoggerFactory.getLogger(Auditoria.class);

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public Auditoria(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public void registrar(Long usuarioId, String accion, String entidad, String entidadId,
            Map<String, ?> detalle) {
        try {
            jdbc.update("""
                    INSERT INTO auditoria (usuario_id, accion, entidad, entidad_id, detalle, ip)
                    VALUES (?, ?, ?, ?, ?::jsonb, ?)
                    """, usuarioId, accion, entidad, entidadId,
                    detalle == null ? null : json.writeValueAsString(detalle), ipCliente());
        } catch (Exception e) {
            log.warn("No se pudo registrar la auditoría {}: {}", accion, e.getMessage());
        }
    }

    /**
     * Registra una acción atribuida al usuario autenticado. El principal del JWT es
     * el correo, por lo que el id se resuelve en la propia sentencia.
     */
    public void registrarActual(String accion, String entidad, String entidadId, Map<String, ?> detalle) {
        String email = emailAutenticado();
        if (email == null) {
            registrar(null, accion, entidad, entidadId, detalle);
            return;
        }
        try {
            jdbc.update("""
                    INSERT INTO auditoria (usuario_id, accion, entidad, entidad_id, detalle, ip)
                    VALUES ((SELECT id FROM usuarios WHERE LOWER(email) = LOWER(?)),
                            ?, ?, ?, ?::jsonb, ?)
                    """, email, accion, entidad, entidadId,
                    detalle == null ? null : json.writeValueAsString(detalle), ipCliente());
        } catch (Exception e) {
            log.warn("No se pudo registrar la auditoría {}: {}", accion, e.getMessage());
        }
    }

    /** Correo del usuario autenticado, o {@code null} si la petición es anónima. */
    public static String emailAutenticado() {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getName() == null
                || "anonymousUser".equals(auth.getName())) {
            return null;
        }
        return auth.getName();
    }

    /** IP del cliente detrás de proxy (X-Forwarded-For) o la conexión directa. */
    private static String ipCliente() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            HttpServletRequest request = attrs.getRequest();
            String encadenada = request.getHeader("X-Forwarded-For");
            if (encadenada != null && !encadenada.isBlank()) {
                int coma = encadenada.indexOf(',');
                return (coma > 0 ? encadenada.substring(0, coma) : encadenada).trim();
            }
            return request.getRemoteAddr();
        }
        return null;
    }
}
