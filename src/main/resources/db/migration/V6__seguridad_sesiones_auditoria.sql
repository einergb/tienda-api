-- ============================================================
-- V6 - Endurecimiento de sesiones y persistencia.
--
-- 1) Refresh tokens por familia: cada inicio de sesión abre una familia
--    (sesion_id); al rotar se conserva la familia, de modo que reutilizar
--    un token ya rotado se puede detectar y revocar la cadena completa.
-- 2) auditoria: traza de operaciones sensibles (login, cambios de roles,
--    transiciones de pedido, emisiones de factura, webhooks...).
-- 3) Índices que faltaban para las consultas reales de la API.
-- 4) Un índice único parcial impide dos direcciones principales por usuario
--    (refuerza el trigger de V3).
--
-- La coherencia de importes de pedidos ya está garantizada por ck_pedidos_total
-- (V3), por lo que V6 no la duplica.
-- ============================================================

-- ---------- 1. Familias de refresh tokens ----------
-- La familia se identifica con sesion_id: el primer token de una sesión usa su
-- propio id como familia (se asigna con un trigger BEFORE INSERT).
ALTER TABLE refresh_tokens ADD COLUMN sesion_id BIGINT;
ALTER TABLE refresh_tokens ADD COLUMN reemplazado_por BIGINT;
ALTER TABLE refresh_tokens ADD COLUMN usado_en TIMESTAMPTZ;
ALTER TABLE refresh_tokens ADD COLUMN revocado_en TIMESTAMPTZ;

-- Familia automática: si no se indica, la fila inaugura su propia sesión.
CREATE OR REPLACE FUNCTION fn_refresh_token_familia() RETURNS TRIGGER AS $$
BEGIN
    IF NEW.sesion_id IS NULL THEN
        NEW.sesion_id := NEW.id;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- En un INSERT el id ya está disponible cuando se ejecutan los triggers BEFORE.
CREATE TRIGGER trg_refresh_token_familia
    BEFORE INSERT ON refresh_tokens
    FOR EACH ROW EXECUTE FUNCTION fn_refresh_token_familia();

-- Los tokens emitidos antes de esta migración no tienen familia: cada uno
-- se convierte en su propia familia para poder seguir operando.
UPDATE refresh_tokens SET sesion_id = id WHERE sesion_id IS NULL;
ALTER TABLE refresh_tokens ALTER COLUMN sesion_id SET NOT NULL;

CREATE INDEX ix_refresh_tokens_sesion ON refresh_tokens(sesion_id);
CREATE INDEX ix_refresh_tokens_expiracion ON refresh_tokens(expira_en);

-- ---------- 2. Auditoría ----------
CREATE TABLE auditoria (
    id          BIGSERIAL PRIMARY KEY,
    usuario_id  BIGINT REFERENCES usuarios(id) ON DELETE SET NULL,
    accion      VARCHAR(60) NOT NULL,
    entidad     VARCHAR(60),
    entidad_id  VARCHAR(60),
    detalle     JSONB,
    ip          VARCHAR(64),
    creado_en   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_auditoria_creado ON auditoria(creado_en DESC);
CREATE INDEX ix_auditoria_usuario ON auditoria(usuario_id);
CREATE INDEX ix_auditoria_accion ON auditoria(accion);

-- ---------- 3. Índices que faltaban ----------
-- Búsquedas públicas del catálogo: solo se listan productos activos.
CREATE INDEX ix_productos_activo ON productos(categoria_id) WHERE activo;
-- Cupones vigentes por código (validación pública).
CREATE INDEX ix_cupones_vigencia ON cupones(vigente_hasta) WHERE activo;
-- Historial de pedidos del cliente, ya ordenado por fecha.
CREATE INDEX ix_pedidos_usuario_creado ON pedidos(usuario_id, creado_en DESC);
-- Pagos por fecha (resúmenes de ventas y conciliación con la pasarela).
CREATE INDEX ix_pagos_creado ON pagos(creado_en);
-- Inventario por stock (consulta administrativa de stock bajo).
CREATE INDEX ix_inventario_stock ON inventario(stock);

-- ---------- 4. Una sola dirección principal por usuario ----------
-- El trigger fn_direccion_unica_principal (V3) ya lo garantiza en la API; este
-- índice parcial lo refuerza a nivel de integridad (defensa en profundidad).
CREATE UNIQUE INDEX ux_direcciones_principal
    ON direcciones(usuario_id) WHERE es_principal;
