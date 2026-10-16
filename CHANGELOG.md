# Changelog

## No publicado

### Autenticación y errores HTTP

- Se mantiene una respuesta genérica para credenciales incorrectas, sin revelar si el correo está registrado.
- Los errores de autenticación y autorización responden con `ProblemDetail`: HTTP `401` para autenticación requerida y HTTP `403` para permisos insuficientes.
- Se añadieron mensajes de validación en español para login y registro, además de una respuesta clara para cuerpos JSON mal formados.
- Se añadieron mensajes en español para los campos validados de productos.
- Se añadieron pruebas de integración con PostgreSQL efímera para persistencia, auth, productos y trigger de stock con Testcontainers.
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
- El registro de un usuario de prueba respondió HTTP `201` y el login posterior HTTP `200`.
- El JWT de ese usuario con rol `CLIENTE` fue rechazado en la ruta administrativa con HTTP `403` y `ProblemDetail`.
- El login con rol `ADMIN` permitió crear el producto `PRUEBA-001` y la API respondió HTTP `201`.
- El token `ADMIN` permitió desactivar lógicamente el producto de prueba; la API respondió HTTP `204`.
- El token `ADMIN` permitió actualizar el producto de prueba; la API respondió HTTP `200` y el producto permaneció desactivado.

### Verificado localmente

- `mvn test` compiló con `--release 21`: 17 pruebas unitarias/MVC aprobadas y 5 pruebas PostgreSQL omitidas porque no hay Docker disponible en este entorno.
- Las pruebas de integración con PostgreSQL compilan; deben ejecutarse en un host con Docker y reportar `Skipped: 0` para verificar los criterios de base de datos.
- Las pruebas de endpoints cubren validación de productos (`400`), integridad de datos (`409`) y error inesperado (`500` genérico).

