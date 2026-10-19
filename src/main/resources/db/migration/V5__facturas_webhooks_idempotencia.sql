-- V5: facturas por pedido, eventos de webhook (idempotencia entrante) e
-- idempotencia saliente de pagos para reintentos seguros contra pasarelas.

-- ---------- Facturas ----------
-- Una factura por pedido; la numeración es propia e independiente del numero de pedido.
CREATE SEQUENCE seq_factura_numero START 1;

CREATE TABLE facturas (
    id         BIGSERIAL PRIMARY KEY,
    pedido_id  BIGINT NOT NULL UNIQUE REFERENCES pedidos(id) ON DELETE CASCADE,
    numero     VARCHAR(30) NOT NULL UNIQUE,
    total      NUMERIC(12,0) NOT NULL,
    emitida_en TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ---------- Webhooks de pasarelas ----------
-- (pasarela, evento_id) únicos: las notificaciones duplicadas se ignoran.
CREATE TABLE webhook_eventos (
    id           BIGSERIAL PRIMARY KEY,
    pasarela     VARCHAR(30) NOT NULL,
    evento_id    VARCHAR(160) NOT NULL,
    procesado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (pasarela, evento_id)
);

-- ---------- Idempotencia saliente de pagos ----------
-- La misma clave reutilizada en un reintento asegura que la pasarela no procese dos
-- veces la misma transacción (cabecera idempotency-key de Wompi y ePayco).
ALTER TABLE pagos ADD COLUMN idempotency_key VARCHAR(64);
CREATE UNIQUE INDEX ux_pagos_idempotency_key ON pagos(idempotency_key) WHERE idempotency_key IS NOT NULL;

-- Identificador de la sesión/transacción devuelto por la pasarela.
ALTER TABLE pagos ADD COLUMN sesion_id VARCHAR(120);
