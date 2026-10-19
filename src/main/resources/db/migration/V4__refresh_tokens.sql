-- ============================================================
-- V4 - Sesiones: refresh tokens para refresco y cierre de sesión.
-- La API guarda solo el hash SHA-256 del token; el valor en claro
-- se entrega al cliente una sola vez. La rotación se hace en la API
-- (revocar el anterior y emitir uno nuevo).
-- ============================================================

CREATE TABLE refresh_tokens (
    id          BIGSERIAL PRIMARY KEY,
    usuario_id  BIGINT NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    token_hash  VARCHAR(64) NOT NULL UNIQUE,
    expira_en   TIMESTAMPTZ NOT NULL,
    revocado    BOOLEAN NOT NULL DEFAULT FALSE,
    creado_en   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_refresh_tokens_usuario ON refresh_tokens(usuario_id);
