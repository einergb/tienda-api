CREATE TABLE usuarios_verificacion (
    id                BIGSERIAL PRIMARY KEY,
    usuario_id        BIGINT NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    token_hash        VARCHAR(64) NOT NULL UNIQUE,
    expira_en         TIMESTAMPTZ NOT NULL,
    usado_en          TIMESTAMPTZ,
    creado_en         TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_usuarios_verificacion_usuario ON usuarios_verificacion(usuario_id);
CREATE INDEX idx_usuarios_verificacion_expira ON usuarios_verificacion(expira_en);

CREATE TABLE consentimientos (
    id                BIGSERIAL PRIMARY KEY,
    usuario_id        BIGINT NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    tipo_documento    VARCHAR(40) NOT NULL,
    version           VARCHAR(20) NOT NULL,
    fecha_hora        TIMESTAMPTZ NOT NULL DEFAULT now(),
    ip                VARCHAR(45),
    user_agent        VARCHAR(255)
);
CREATE INDEX idx_consentimientos_usuario ON consentimientos(usuario_id);

ALTER TABLE usuarios ADD COLUMN IF NOT EXISTS primer_nombre VARCHAR(60);
ALTER TABLE usuarios ADD COLUMN IF NOT EXISTS segundo_nombre VARCHAR(60);
ALTER TABLE usuarios ADD COLUMN IF NOT EXISTS primer_apellido VARCHAR(60);
ALTER TABLE usuarios ADD COLUMN IF NOT EXISTS segundo_apellido VARCHAR(60);
ALTER TABLE usuarios ADD COLUMN IF NOT EXISTS email_verificado BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE usuarios ADD COLUMN IF NOT EXISTS estado VARCHAR(20) NOT NULL DEFAULT 'ACTIVO';

UPDATE usuarios SET primer_nombre = nombre WHERE primer_nombre IS NULL;
UPDATE usuarios SET email_verificado = COALESCE(email_verificado, TRUE);
UPDATE usuarios SET estado = COALESCE(estado, 'ACTIVO');

ALTER TABLE usuarios ALTER COLUMN email_verificado SET DEFAULT TRUE;
ALTER TABLE usuarios ALTER COLUMN estado SET DEFAULT 'ACTIVO';
