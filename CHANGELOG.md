# Changelog

## No publicado

### Autenticación y errores HTTP

- Se mantiene una respuesta genérica para credenciales incorrectas, sin revelar si el correo está registrado.
- Los errores de autenticación y autorización responden con `ProblemDetail`: HTTP `401` para autenticación requerida y HTTP `403` para permisos insuficientes.
- Se añadieron mensajes de validación en español para login y registro, además de una respuesta clara para cuerpos JSON mal formados.
- Se mejoraron los mensajes de conflicto y de datos inválidos.

### Documentación

- Se amplió el README con el árbol del proyecto y un diagrama UML Mermaid de las clases principales.
- Se identificaron los paquetes reservados que aún no tienen lógica de negocio.

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
- Los endpoints públicos de tienda, categorías y productos respondieron HTTP `200`.
- El login con credenciales inválidas, el acceso administrativo sin token y el token inválido respondieron HTTP `401` con `ProblemDetail`.
- El registro con datos inválidos y JSON mal formado respondió HTTP `400` con mensajes en español.
- La consulta de un producto inexistente respondió HTTP `404` con `ProblemDetail`.

### Verificado localmente

- `mvn test` compiló el proyecto con `--release 21` y ejecutó 4 pruebas unitarias, todas aprobadas.

### Pendiente de verificación

- Verificar HTTP `403` con un token válido de cliente y probar las operaciones autenticadas con credenciales de prueba.
