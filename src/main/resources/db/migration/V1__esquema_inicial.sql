-- ============================================================
-- Esquema inicial - plantilla e-commerce (PostgreSQL)
-- Montos en COP (sin decimales). Cupones tipo PORCENTAJE usan 2 decimales.
-- ============================================================

CREATE TABLE roles (
    id      SMALLSERIAL PRIMARY KEY,
    nombre  VARCHAR(30) NOT NULL UNIQUE
);

CREATE TABLE usuarios (
    id             BIGSERIAL PRIMARY KEY,
    nombre         VARCHAR(120) NOT NULL,
    email          VARCHAR(160) NOT NULL,
    password_hash  VARCHAR(100) NOT NULL,
    telefono       VARCHAR(30),
    rol_id         SMALLINT NOT NULL REFERENCES roles(id),
    activo         BOOLEAN NOT NULL DEFAULT TRUE,
    creado_en      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ux_usuarios_email ON usuarios (LOWER(email));

CREATE TABLE direcciones (
    id            BIGSERIAL PRIMARY KEY,
    usuario_id    BIGINT NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    alias         VARCHAR(50),
    destinatario  VARCHAR(120) NOT NULL,
    telefono      VARCHAR(30) NOT NULL,
    departamento  VARCHAR(80) NOT NULL,
    ciudad        VARCHAR(80) NOT NULL,
    direccion     VARCHAR(200) NOT NULL,
    indicaciones  VARCHAR(300),
    es_principal  BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX ix_direcciones_usuario ON direcciones(usuario_id);

CREATE TABLE categorias (
    id                  BIGSERIAL PRIMARY KEY,
    nombre              VARCHAR(80) NOT NULL UNIQUE,
    slug                VARCHAR(100) NOT NULL UNIQUE,
    descripcion         VARCHAR(300),
    categoria_padre_id  BIGINT REFERENCES categorias(id),
    activa              BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE marcas (
    id      BIGSERIAL PRIMARY KEY,
    nombre  VARCHAR(80) NOT NULL UNIQUE,
    activa  BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE productos (
    id                BIGSERIAL PRIMARY KEY,
    sku               VARCHAR(50) NOT NULL UNIQUE,
    nombre            VARCHAR(200) NOT NULL,
    slug              VARCHAR(260) NOT NULL UNIQUE,
    descripcion       TEXT,
    precio            NUMERIC(12,0) NOT NULL CHECK (precio >= 0),
    categoria_id      BIGINT NOT NULL REFERENCES categorias(id),
    marca_id          BIGINT REFERENCES marcas(id),
    garantia_meses    INT NOT NULL DEFAULT 12 CHECK (garantia_meses >= 0),
    activo            BOOLEAN NOT NULL DEFAULT TRUE,
    creado_en         TIMESTAMPTZ NOT NULL DEFAULT now(),
    actualizado_en    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_productos_categoria ON productos(categoria_id);
CREATE INDEX ix_productos_marca ON productos(marca_id);

CREATE TABLE producto_imagenes (
    id           BIGSERIAL PRIMARY KEY,
    producto_id  BIGINT NOT NULL REFERENCES productos(id) ON DELETE CASCADE,
    url          VARCHAR(500) NOT NULL,
    orden        INT NOT NULL DEFAULT 0,
    es_principal BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX ix_producto_imagenes_producto ON producto_imagenes(producto_id);

-- Especificaciones variables de productos tecnologicos (RAM, almacenamiento, etc.)
CREATE TABLE producto_atributos (
    id           BIGSERIAL PRIMARY KEY,
    producto_id  BIGINT NOT NULL REFERENCES productos(id) ON DELETE CASCADE,
    clave        VARCHAR(80) NOT NULL,
    valor        VARCHAR(200) NOT NULL,
    UNIQUE (producto_id, clave)
);

CREATE TABLE inventario (
    producto_id       BIGINT PRIMARY KEY REFERENCES productos(id) ON DELETE CASCADE,
    stock             INT NOT NULL DEFAULT 0 CHECK (stock >= 0),
    stock_reservado   INT NOT NULL DEFAULT 0 CHECK (stock_reservado >= 0),
    stock_minimo      INT NOT NULL DEFAULT 5 CHECK (stock_minimo >= 0),
    CHECK (stock_reservado <= stock)
);

CREATE TABLE carritos (
    id              BIGSERIAL PRIMARY KEY,
    usuario_id      BIGINT NOT NULL UNIQUE REFERENCES usuarios(id) ON DELETE CASCADE,
    actualizado_en  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE carrito_items (
    id           BIGSERIAL PRIMARY KEY,
    carrito_id   BIGINT NOT NULL REFERENCES carritos(id) ON DELETE CASCADE,
    producto_id  BIGINT NOT NULL REFERENCES productos(id),
    cantidad     INT NOT NULL CHECK (cantidad > 0),
    UNIQUE (carrito_id, producto_id)
);

CREATE TABLE cupones (
    id               BIGSERIAL PRIMARY KEY,
    codigo           VARCHAR(40) NOT NULL UNIQUE,
    tipo             VARCHAR(12) NOT NULL CHECK (tipo IN ('PORCENTAJE', 'MONTO')),
    valor            NUMERIC(12,2) NOT NULL CHECK (valor > 0),
    monto_minimo     NUMERIC(12,0) NOT NULL DEFAULT 0,
    usos_maximos     INT,
    usos_actuales    INT NOT NULL DEFAULT 0,
    vigente_desde    TIMESTAMPTZ NOT NULL DEFAULT now(),
    vigente_hasta    TIMESTAMPTZ,
    activo           BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE pedidos (
    id                 BIGSERIAL PRIMARY KEY,
    numero             VARCHAR(20) NOT NULL UNIQUE,
    usuario_id         BIGINT NOT NULL REFERENCES usuarios(id),
    estado             VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE'
        CHECK (estado IN ('PENDIENTE','PAGADO','EN_PREPARACION','ENVIADO','ENTREGADO','CANCELADO','DEVUELTO')),
    subtotal           NUMERIC(12,0) NOT NULL,
    descuento          NUMERIC(12,0) NOT NULL DEFAULT 0,
    iva                NUMERIC(12,0) NOT NULL DEFAULT 0,
    costo_envio        NUMERIC(12,0) NOT NULL DEFAULT 0,
    total              NUMERIC(12,0) NOT NULL,
    cupon_id           BIGINT REFERENCES cupones(id),
    -- Copia de la direccion al momento de comprar
    envio_destinatario VARCHAR(120) NOT NULL,
    envio_telefono     VARCHAR(30) NOT NULL,
    envio_departamento VARCHAR(80) NOT NULL,
    envio_ciudad       VARCHAR(80) NOT NULL,
    envio_direccion    VARCHAR(200) NOT NULL,
    reserva_expira_en  TIMESTAMPTZ,
    creado_en          TIMESTAMPTZ NOT NULL DEFAULT now(),
    actualizado_en     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_pedidos_usuario ON pedidos(usuario_id);
CREATE INDEX ix_pedidos_estado ON pedidos(estado);

-- Se guarda el precio y nombre del momento de la compra
CREATE TABLE pedido_items (
    id               BIGSERIAL PRIMARY KEY,
    pedido_id        BIGINT NOT NULL REFERENCES pedidos(id) ON DELETE CASCADE,
    producto_id      BIGINT NOT NULL REFERENCES productos(id),
    sku              VARCHAR(50) NOT NULL,
    nombre_producto  VARCHAR(200) NOT NULL,
    precio_unitario  NUMERIC(12,0) NOT NULL,
    cantidad         INT NOT NULL CHECK (cantidad > 0),
    subtotal         NUMERIC(12,0) NOT NULL
);
CREATE INDEX ix_pedido_items_pedido ON pedido_items(pedido_id);

CREATE TABLE pagos (
    id              BIGSERIAL PRIMARY KEY,
    pedido_id       BIGINT NOT NULL REFERENCES pedidos(id),
    pasarela        VARCHAR(30) NOT NULL,
    referencia      VARCHAR(100) NOT NULL UNIQUE,
    estado          VARCHAR(15) NOT NULL DEFAULT 'PENDIENTE'
        CHECK (estado IN ('PENDIENTE','APROBADO','RECHAZADO','EXPIRADO','REEMBOLSADO')),
    monto           NUMERIC(12,0) NOT NULL,
    payload         JSONB,
    creado_en       TIMESTAMPTZ NOT NULL DEFAULT now(),
    actualizado_en  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_pagos_pedido ON pagos(pedido_id);

CREATE TABLE envios (
    id              BIGSERIAL PRIMARY KEY,
    pedido_id       BIGINT NOT NULL UNIQUE REFERENCES pedidos(id),
    transportadora  VARCHAR(80),
    guia            VARCHAR(80),
    estado          VARCHAR(15) NOT NULL DEFAULT 'PREPARANDO'
        CHECK (estado IN ('PREPARANDO','EN_CAMINO','ENTREGADO','DEVUELTO')),
    enviado_en      TIMESTAMPTZ,
    entregado_en    TIMESTAMPTZ
);

CREATE TABLE resenas (
    id           BIGSERIAL PRIMARY KEY,
    producto_id  BIGINT NOT NULL REFERENCES productos(id) ON DELETE CASCADE,
    usuario_id   BIGINT NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    calificacion SMALLINT NOT NULL CHECK (calificacion BETWEEN 1 AND 5),
    comentario   VARCHAR(1000),
    creado_en    TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (producto_id, usuario_id)
);
