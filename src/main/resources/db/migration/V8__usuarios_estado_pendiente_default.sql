ALTER TABLE usuarios ALTER COLUMN email_verificado SET DEFAULT FALSE;
ALTER TABLE usuarios ALTER COLUMN estado SET DEFAULT 'PENDIENTE';

-- Para usuarios existentes, mantenemos que estén activos/verificados para no romper login existente
UPDATE usuarios SET email_verificado = COALESCE(email_verificado, TRUE), estado = COALESCE(NULLIF(estado, 'PENDIENTE'), 'ACTIVO');
UPDATE usuarios SET email_verificado = TRUE, estado = 'ACTIVO';
