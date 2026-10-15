-- ============================================================
-- V3 - Reglas de negocio en la BD: funciones, triggers y vistas
-- Los errores de regla usan RAISE EXCEPTION (SQLSTATE P0001); la API
-- los traduce a HTTP 422 con el mensaje en espanol.
-- ============================================================

-- ---------- Restricciones adicionales ----------
ALTER TABLE pedido_items
    ADD CONSTRAINT ux_pedido_items_producto UNIQUE (pedido_id, producto_id),
    ADD CONSTRAINT ck_pedido_items_subtotal CHECK (subtotal = precio_unitario * cantidad);

ALTER TABLE pedidos
    ADD CONSTRAINT ck_pedidos_total
    CHECK (descuento >= 0 AND descuento <= subtotal
           AND total >= 0
           AND total = subtotal - descuento + iva + costo_envio);

CREATE SEQUENCE seq_pedido_numero START 1;

-- ---------- Utilidades ----------
CREATE FUNCTION fn_set_actualizado_en() RETURNS trigger AS $$
BEGIN
    NEW.actualizado_en := now();
    RETURN NEW;
END $$ LANGUAGE plpgsql;

CREATE TRIGGER trg_productos_actualizado BEFORE UPDATE ON productos
    FOR EACH ROW EXECUTE FUNCTION fn_set_actualizado_en();
CREATE TRIGGER trg_pedidos_actualizado BEFORE UPDATE ON pedidos
    FOR EACH ROW EXECUTE FUNCTION fn_set_actualizado_en();
CREATE TRIGGER trg_pagos_actualizado BEFORE UPDATE ON pagos
    FOR EACH ROW EXECUTE FUNCTION fn_set_actualizado_en();

-- ---------- Inventario: reserva atomica ----------
-- Devuelve TRUE si pudo reservar (producto activo y stock disponible suficiente).
CREATE FUNCTION reservar_stock(p_producto BIGINT, p_cantidad INT) RETURNS BOOLEAN AS $$
DECLARE
    filas INT;
BEGIN
    UPDATE inventario i
       SET stock_reservado = i.stock_reservado + p_cantidad
      FROM productos p
     WHERE i.producto_id = p_producto
       AND p.id = i.producto_id
       AND p.activo
       AND (i.stock - i.stock_reservado) >= p_cantidad;
    GET DIAGNOSTICS filas = ROW_COUNT;
    RETURN filas = 1;
END $$ LANGUAGE plpgsql;

-- ---------- Carrito: no superar el stock disponible ----------
CREATE FUNCTION fn_carrito_item_validar() RETURNS trigger AS $$
DECLARE
    disponible INT;
BEGIN
    SELECT (i.stock - i.stock_reservado) INTO disponible
      FROM inventario i JOIN productos p ON p.id = i.producto_id
     WHERE i.producto_id = NEW.producto_id AND p.activo;
    IF disponible IS NULL THEN
        RAISE EXCEPTION 'El producto % no esta disponible', NEW.producto_id;
    END IF;
    IF NEW.cantidad > disponible THEN
        RAISE EXCEPTION 'La cantidad solicitada (%) supera el stock disponible (%)', NEW.cantidad, disponible;
    END IF;
    UPDATE carritos SET actualizado_en = now() WHERE id = NEW.carrito_id;
    RETURN NEW;
END $$ LANGUAGE plpgsql;

CREATE TRIGGER trg_carrito_item_validar BEFORE INSERT OR UPDATE OF cantidad ON carrito_items
    FOR EACH ROW EXECUTE FUNCTION fn_carrito_item_validar();

-- ---------- Direcciones: una sola principal por usuario ----------
CREATE FUNCTION fn_direccion_unica_principal() RETURNS trigger AS $$
BEGIN
    UPDATE direcciones SET es_principal = FALSE
     WHERE usuario_id = NEW.usuario_id AND id <> NEW.id AND es_principal;
    RETURN NULL;
END $$ LANGUAGE plpgsql;

CREATE TRIGGER trg_direccion_unica_principal AFTER INSERT OR UPDATE OF es_principal ON direcciones
    FOR EACH ROW WHEN (NEW.es_principal) EXECUTE FUNCTION fn_direccion_unica_principal();

-- ---------- Pedidos: numero, expiracion de reserva y cupon ----------
CREATE FUNCTION fn_pedido_antes_insert() RETURNS trigger AS $$
DECLARE
    c cupones%ROWTYPE;
    esperado NUMERIC;
BEGIN
    IF NEW.numero IS NULL OR NEW.numero = '' THEN
        NEW.numero := 'PED-' || to_char(now(), 'YYYY') || '-' || lpad(nextval('seq_pedido_numero')::text, 6, '0');
    END IF;
    IF NEW.estado = 'PENDIENTE' AND NEW.reserva_expira_en IS NULL THEN
        NEW.reserva_expira_en := now() + interval '15 minutes';
    END IF;

    IF NEW.cupon_id IS NULL THEN
        IF NEW.descuento <> 0 THEN
            RAISE EXCEPTION 'No se puede aplicar descuento sin cupon';
        END IF;
        RETURN NEW;
    END IF;

    SELECT * INTO c FROM cupones WHERE id = NEW.cupon_id FOR UPDATE;
    IF NOT FOUND OR NOT c.activo THEN
        RAISE EXCEPTION 'El cupon no existe o esta inactivo';
    END IF;
    IF now() < c.vigente_desde OR (c.vigente_hasta IS NOT NULL AND now() > c.vigente_hasta) THEN
        RAISE EXCEPTION 'El cupon % esta fuera de vigencia', c.codigo;
    END IF;
    IF c.usos_maximos IS NOT NULL AND c.usos_actuales >= c.usos_maximos THEN
        RAISE EXCEPTION 'El cupon % ya alcanzo su limite de usos', c.codigo;
    END IF;
    IF NEW.subtotal < c.monto_minimo THEN
        RAISE EXCEPTION 'El cupon % requiere una compra minima de %', c.codigo, c.monto_minimo;
    END IF;

    IF c.tipo = 'PORCENTAJE' THEN
        esperado := round(NEW.subtotal * c.valor / 100);
    ELSE
        esperado := least(c.valor, NEW.subtotal);
    END IF;
    IF NEW.descuento <> esperado THEN
        RAISE EXCEPTION 'El descuento (%) no coincide con el cupon % (esperado %)', NEW.descuento, c.codigo, esperado;
    END IF;

    UPDATE cupones SET usos_actuales = usos_actuales + 1 WHERE id = c.id;
    RETURN NEW;
END $$ LANGUAGE plpgsql;

CREATE TRIGGER trg_pedido_antes_insert BEFORE INSERT ON pedidos
    FOR EACH ROW EXECUTE FUNCTION fn_pedido_antes_insert();

-- Al confirmar la transaccion: el pedido debe tener items y su subtotal debe cuadrar.
CREATE FUNCTION fn_pedido_verificar_items() RETURNS trigger AS $$
DECLARE
    suma NUMERIC;
BEGIN
    SELECT SUM(subtotal) INTO suma FROM pedido_items WHERE pedido_id = NEW.id;
    IF suma IS NULL THEN
        RAISE EXCEPTION 'El pedido % no tiene items', NEW.numero;
    END IF;
    IF suma <> NEW.subtotal THEN
        RAISE EXCEPTION 'El subtotal del pedido % (%) no coincide con la suma de sus items (%)',
            NEW.numero, NEW.subtotal, suma;
    END IF;
    RETURN NULL;
END $$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER trg_pedido_verificar_items AFTER INSERT ON pedidos
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION fn_pedido_verificar_items();

-- ---------- Items de pedido: precio desde la BD + reserva de stock ----------
CREATE FUNCTION fn_pedido_item_antes_insert() RETURNS trigger AS $$
DECLARE
    p productos%ROWTYPE;
BEGIN
    SELECT * INTO p FROM productos WHERE id = NEW.producto_id;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'El producto % no existe', NEW.producto_id;
    END IF;
    NEW.sku := p.sku;
    NEW.nombre_producto := p.nombre;
    NEW.precio_unitario := p.precio;
    NEW.subtotal := p.precio * NEW.cantidad;
    RETURN NEW;
END $$ LANGUAGE plpgsql;

CREATE TRIGGER trg_pedido_item_antes_insert BEFORE INSERT ON pedido_items
    FOR EACH ROW EXECUTE FUNCTION fn_pedido_item_antes_insert();

CREATE FUNCTION fn_pedido_item_reservar() RETURNS trigger AS $$
BEGIN
    IF NOT reservar_stock(NEW.producto_id, NEW.cantidad) THEN
        RAISE EXCEPTION 'Stock insuficiente o producto inactivo: % (%)', NEW.nombre_producto, NEW.sku;
    END IF;
    RETURN NULL;
END $$ LANGUAGE plpgsql;

CREATE TRIGGER trg_pedido_item_reservar AFTER INSERT ON pedido_items
    FOR EACH ROW EXECUTE FUNCTION fn_pedido_item_reservar();

-- ---------- Pedidos: maquina de estados + efecto sobre el inventario ----------
--  PENDIENTE -> PAGADO       : descuenta stock y libera la reserva
--  PENDIENTE -> CANCELADO    : libera la reserva
--  PAGADO/EN_PREPARACION -> CANCELADO, cualquier -> DEVUELTO : devuelve stock
CREATE FUNCTION fn_pedido_cambio_estado() RETURNS trigger AS $$
DECLARE
    permitido BOOLEAN;
BEGIN
    permitido := CASE OLD.estado
        WHEN 'PENDIENTE'      THEN NEW.estado IN ('PAGADO', 'CANCELADO')
        WHEN 'PAGADO'         THEN NEW.estado IN ('EN_PREPARACION', 'CANCELADO')
        WHEN 'EN_PREPARACION' THEN NEW.estado IN ('ENVIADO', 'CANCELADO')
        WHEN 'ENVIADO'        THEN NEW.estado IN ('ENTREGADO', 'DEVUELTO')
        WHEN 'ENTREGADO'      THEN NEW.estado = 'DEVUELTO'
        ELSE FALSE
    END;
    IF NOT permitido THEN
        RAISE EXCEPTION 'Transicion de estado no permitida: % -> %', OLD.estado, NEW.estado;
    END IF;

    IF OLD.estado = 'PENDIENTE' AND NEW.estado = 'PAGADO' THEN
        UPDATE inventario i
           SET stock = i.stock - it.cantidad,
               stock_reservado = i.stock_reservado - it.cantidad
          FROM pedido_items it
         WHERE it.pedido_id = NEW.id AND i.producto_id = it.producto_id;
    ELSIF OLD.estado = 'PENDIENTE' AND NEW.estado = 'CANCELADO' THEN
        UPDATE inventario i
           SET stock_reservado = i.stock_reservado - it.cantidad
          FROM pedido_items it
         WHERE it.pedido_id = NEW.id AND i.producto_id = it.producto_id;
    ELSIF NEW.estado IN ('CANCELADO', 'DEVUELTO') THEN
        UPDATE inventario i
           SET stock = i.stock + it.cantidad
          FROM pedido_items it
         WHERE it.pedido_id = NEW.id AND i.producto_id = it.producto_id;
    END IF;

    IF NEW.estado = 'CANCELADO' AND NEW.cupon_id IS NOT NULL THEN
        UPDATE cupones SET usos_actuales = greatest(usos_actuales - 1, 0) WHERE id = NEW.cupon_id;
    END IF;
    RETURN NEW;
END $$ LANGUAGE plpgsql;

CREATE TRIGGER trg_pedido_cambio_estado BEFORE UPDATE OF estado ON pedidos
    FOR EACH ROW WHEN (OLD.estado IS DISTINCT FROM NEW.estado)
    EXECUTE FUNCTION fn_pedido_cambio_estado();

-- Cancela pedidos PENDIENTES cuya reserva expiro (liberando stock). Devuelve cuantos cancelo.
-- Llamar periodicamente (p. ej. @Scheduled cada minuto en la API o pg_cron).
CREATE FUNCTION expirar_reservas() RETURNS INT AS $$
DECLARE
    n INT;
BEGIN
    WITH exp AS (
        UPDATE pedidos SET estado = 'CANCELADO'
         WHERE estado = 'PENDIENTE' AND reserva_expira_en IS NOT NULL AND reserva_expira_en < now()
        RETURNING id
    ), pg AS (
        UPDATE pagos SET estado = 'EXPIRADO'
         WHERE estado = 'PENDIENTE' AND pedido_id IN (SELECT id FROM exp)
        RETURNING 1
    )
    SELECT COUNT(*) INTO n FROM exp;
    RETURN n;
END $$ LANGUAGE plpgsql;

-- ---------- Pagos ----------
CREATE FUNCTION fn_pago_validar_monto() RETURNS trigger AS $$
DECLARE
    total_pedido NUMERIC;
BEGIN
    IF NEW.estado = 'APROBADO' THEN
        SELECT total INTO total_pedido FROM pedidos WHERE id = NEW.pedido_id;
        IF NEW.monto <> total_pedido THEN
            RAISE EXCEPTION 'El monto aprobado (%) no coincide con el total del pedido (%)', NEW.monto, total_pedido;
        END IF;
    END IF;
    RETURN NEW;
END $$ LANGUAGE plpgsql;

CREATE TRIGGER trg_pago_validar_monto BEFORE INSERT OR UPDATE OF estado, monto ON pagos
    FOR EACH ROW EXECUTE FUNCTION fn_pago_validar_monto();

-- Pago aprobado => pedido PAGADO (y el trigger de pedidos descuenta el stock)
CREATE FUNCTION fn_pago_aprobado() RETURNS trigger AS $$
BEGIN
    UPDATE pedidos SET estado = 'PAGADO' WHERE id = NEW.pedido_id AND estado = 'PENDIENTE';
    RETURN NULL;
END $$ LANGUAGE plpgsql;

CREATE TRIGGER trg_pago_aprobado_insert AFTER INSERT ON pagos
    FOR EACH ROW WHEN (NEW.estado = 'APROBADO') EXECUTE FUNCTION fn_pago_aprobado();
CREATE TRIGGER trg_pago_aprobado_update AFTER UPDATE OF estado ON pagos
    FOR EACH ROW WHEN (NEW.estado = 'APROBADO' AND OLD.estado IS DISTINCT FROM NEW.estado)
    EXECUTE FUNCTION fn_pago_aprobado();

-- ---------- Envios ----------
CREATE FUNCTION fn_envio_validar() RETURNS trigger AS $$
DECLARE
    estado_pedido VARCHAR;
BEGIN
    IF TG_OP = 'INSERT' THEN
        SELECT estado INTO estado_pedido FROM pedidos WHERE id = NEW.pedido_id;
        IF estado_pedido NOT IN ('PAGADO', 'EN_PREPARACION') THEN
            RAISE EXCEPTION 'Solo se puede crear el envio de un pedido pagado (estado actual: %)', estado_pedido;
        END IF;
    END IF;
    IF NEW.estado = 'EN_CAMINO' THEN
        IF NEW.guia IS NULL OR NEW.transportadora IS NULL THEN
            RAISE EXCEPTION 'Para despachar se requiere transportadora y numero de guia';
        END IF;
        NEW.enviado_en := coalesce(NEW.enviado_en, now());
    ELSIF NEW.estado = 'ENTREGADO' THEN
        NEW.entregado_en := coalesce(NEW.entregado_en, now());
    END IF;
    RETURN NEW;
END $$ LANGUAGE plpgsql;

CREATE TRIGGER trg_envio_validar BEFORE INSERT OR UPDATE OF estado ON envios
    FOR EACH ROW EXECUTE FUNCTION fn_envio_validar();

CREATE FUNCTION fn_envio_propagar_estado() RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        UPDATE pedidos SET estado = 'EN_PREPARACION' WHERE id = NEW.pedido_id AND estado = 'PAGADO';
    ELSIF OLD.estado IS DISTINCT FROM NEW.estado THEN
        IF NEW.estado = 'EN_CAMINO' THEN
            UPDATE pedidos SET estado = 'ENVIADO' WHERE id = NEW.pedido_id AND estado = 'EN_PREPARACION';
        ELSIF NEW.estado = 'ENTREGADO' THEN
            UPDATE pedidos SET estado = 'ENTREGADO' WHERE id = NEW.pedido_id AND estado = 'ENVIADO';
        ELSIF NEW.estado = 'DEVUELTO' THEN
            UPDATE pedidos SET estado = 'DEVUELTO'
             WHERE id = NEW.pedido_id AND estado IN ('ENVIADO', 'ENTREGADO');
        END IF;
    END IF;
    RETURN NULL;
END $$ LANGUAGE plpgsql;

CREATE TRIGGER trg_envio_propagar_estado AFTER INSERT OR UPDATE OF estado ON envios
    FOR EACH ROW EXECUTE FUNCTION fn_envio_propagar_estado();

-- ---------- Resenas: solo compra verificada (pedido ENTREGADO) ----------
CREATE FUNCTION fn_resena_compra_verificada() RETURNS trigger AS $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
          FROM pedido_items it JOIN pedidos pe ON pe.id = it.pedido_id
         WHERE pe.usuario_id = NEW.usuario_id
           AND it.producto_id = NEW.producto_id
           AND pe.estado = 'ENTREGADO') THEN
        RAISE EXCEPTION 'Solo puedes resenar productos que compraste y ya recibiste';
    END IF;
    RETURN NEW;
END $$ LANGUAGE plpgsql;

CREATE TRIGGER trg_resena_compra_verificada BEFORE INSERT ON resenas
    FOR EACH ROW EXECUTE FUNCTION fn_resena_compra_verificada();

-- ---------- Vistas ----------
CREATE VIEW v_catalogo_publico AS
SELECT p.id, p.sku, p.nombre, p.slug, p.precio,
       c.nombre AS categoria, m.nombre AS marca,
       (i.stock - i.stock_reservado) AS stock_disponible
  FROM productos p
  JOIN categorias c ON c.id = p.categoria_id
  LEFT JOIN marcas m ON m.id = p.marca_id
  JOIN inventario i ON i.producto_id = p.id
 WHERE p.activo AND (i.stock - i.stock_reservado) > 0;

CREATE VIEW v_stock_bajo AS
SELECT p.id, p.sku, p.nombre, i.stock, i.stock_reservado, i.stock_minimo
  FROM productos p
  JOIN inventario i ON i.producto_id = p.id
 WHERE p.activo AND (i.stock - i.stock_reservado) <= i.stock_minimo;