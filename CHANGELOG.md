# Changelog

## No publicado

### Despliegue

- Docker Compose inicia solo la API; no crea un segundo contenedor PostgreSQL.
- La API se publica en `192.168.0.150:8080` y se conecta al contenedor PostgreSQL existente mediante `web_db:5432` en la red externa `homelab_net`.
- La conexión de la API utiliza `DB_NAME`, `DB_USER` y `DB_PASSWORD` desde el entorno de Compose.
- Flyway adopta en la versión `3` los esquemas no vacíos que aún no tienen historial de migraciones, según el estado existente de la base de datos `web`.
- Se renombró la migración V3 a `V3__reglas_negocio.sql` para que Flyway la reconozca en bases nuevas.

### Verificado en homelab

- La API se reconstruyó y reinició correctamente mediante Docker Compose.
- Flyway validó las tres migraciones y creó el historial mediante un baseline en la versión `3`, sin ejecutar nuevamente las migraciones anteriores sobre el esquema existente.
- El endpoint `http://192.168.0.150:8080/actuator/health` respondió HTTP `200` con estado `UP`.

### Pendiente de verificación

- Ejecutar las pruebas automatizadas con Maven.
- Validar los endpoints funcionales de la API desde un equipo con conectividad al homelab.
