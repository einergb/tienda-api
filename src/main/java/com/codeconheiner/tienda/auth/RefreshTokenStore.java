package com.codeconheiner.tienda.auth;

import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * Puerto de persistencia de refresh tokens. La API guarda solo el hash SHA-256.
 *
 * Una sesión es una <em>familia</em> de tokens: cada inicio de sesión abre una
 * familia ({@code sesionId}) y cada rotación conserva la familia. Si un token ya
 * rotado se vuelve a presentar, se revoca la familia completa (detección de reuso).
 */
public interface RefreshTokenStore {

    /** Token persistido; {@code revocadoEn} y {@code usadoEn} marcan su estado. */
    record Sesion(Long id, Long usuarioId, Long sesionId, OffsetDateTime expiraEn, boolean revocado,
            boolean usado, OffsetDateTime usadoEn, OffsetDateTime revocadoEn) {

        public boolean vigente(OffsetDateTime ahora) {
            return !revocado && !usado && expiraEn.isAfter(ahora);
        }
    }

    /** Abre una familia nueva con su primer token; devuelve el id de la familia. */
    Long crearFamilia(Long usuarioId, String tokenHash, OffsetDateTime expiraEn);

    /** Añade un token nuevo a una familia existente. */
    Long crearEnFamilia(Long usuarioId, Long sesionId, String tokenHash, OffsetDateTime expiraEn);

    /** Busca por hash del token (incluye revocados y usados, para poder detectar el reuso). */
    Optional<Sesion> buscarPorHash(String tokenHash);

    /**
     * Marca el token como usado y encadena el nuevo token de la misma familia.
     * Solo actúa si el token seguía vigente; devuelve 1 si la rotación ocurrió.
     */
    int marcarUsado(String tokenHash, Long reemplazadoPor);

    /** Revoca la familia (logout, cambio de contraseña). */ 
    int revocarFamilia(Long usuarioId, Long sesionId);

    int revocarTodosDeUsuario(Long usuarioId);

    /** Borra tokens caducados o revocados hace más de {@code diasRetencion} días. */
    int purgarAntiguos(int diasRetencion);

    /** Compatibilidad con el código que solo conoce el hash: marca el token como usado. */
    default int revocar(String tokenHash) {
        return marcarUsado(tokenHash, null);
    }
}
