# Changelog

## No publicado

### Lógica de negocio completa

- Se implementó el checkout (`POST /api/pedidos`): valida carrito y dirección, aplica cupones, calcula subtotal, descuento, IVA, envío y total en la API e inserta el pedido con reserva de stock en una sola transacción (los triggers de V3 confirman `numero`, expiración de reserva y totales).
- Se implementó la creación de pagos (`POST /api/pagos`) con `estado` opcional (`PENDIENTE`/`APROBADO`/`RECHAZADO`); un pago aprobado dispara el trigger que marca el pedido como `PAGADO` y descuenta el stock reservado.
- Se añadió el ciclo de vida administrativo de pedidos: listado con filtro por estado, detalle y transiciones (`PUT /api/admin/pedidos/{id}/estado`) validadas por la máquina de estados de V3.
- Se creó el módulo de direcciones (`/api/direcciones`) con CRUD por usuario; el trigger `fn_direccion_unica_principal` garantiza una sola dirección principal.
- Se creó el módulo de cupones con CRUD administrativo (`/api/admin/cupones`) y validación pública (`GET /api/cupones/validar`).
- Se creó el módulo de envíos administrativos (`/api/admin/envios`) con listado, detalle, creación y actualización.
- Se creó el módulo de reseñas (`/api/resenas`) con lectura pública y escritura propia; `fn_resena_compra_verificada` exige un pedido `ENTREGADO`.
- Se añadió el listado administrativo de stock bajo (`GET /api/admin/inventario/stock-bajo`) sobre la vista `v_stock_bajo`.
- El detalle del catálogo ahora incluye imágenes y atributos, con endpoints administrativos para gestionarlos (`/imagenes`, `/atributos`).
- Se añadió la expiración automática de reservas: `TareaExpiracionReservas` ejecuta `expirar_reservas()` cada 60 s y cancela pedidos `PENDIENTE` vencidos liberando stock.
- `TiendaProperties` incorpora `costoEnvioBase` (por defecto `10000`), que reemplaza el valor de envío por defecto que estaba fijo en el código.

### Sesiones y autenticación

- Nueva migración `V4__refresh_tokens.sql`: tabla de refresh tokens guardados como hash SHA-256, con expiración de 7 días.
- `POST /api/auth/refresh` rota el refresh token (el anterior queda revocado) y `POST /api/auth/logout` lo invalida (`204`).
- `PUT /api/auth/password` cambia la contraseña validando la actual y revoca todas las sesiones del usuario.
- Las respuestas de login, registro y refresh incluyen ahora `refreshToken` además del `token` JWT.

### Errores HTTP

- Los `RAISE EXCEPTION` de V3 (SQLSTATE `P0001`) responden HTTP `422` con el mensaje en español del trigger, sin el prefijo `ERROR:` ni el detalle `Where:` del driver. Antes se respondía `409` o `500` según la traducción del driver.
- Se mantiene `409` para las restricciones de datos reales (duplicados y referencias).

### Autenticación y errores HTTP

- Se mantiene una respuesta genérica para credenciales incorrectas, sin revelar si el correo está registrado.
- Los errores de autenticación y autorización responden con `ProblemDetail`: HTTP `401` para autenticación requerida y HTTP `403` para permisos insuficientes.
- Se añadieron mensajes de validación en español para login y registro, además de una respuesta clara para cuerpos JSON mal formados.
- Se añadieron mensajes en español para los campos validados de productos.
- Se añadieron pruebas de integración con PostgreSQL efímera para persistencia, auth, productos y trigger de stock con Testcontainers.
- Se actualizó Testcontainers a `1.21.3` para usar un cliente Docker compatible con el daemon del homelab.
- Surefire fija Docker API `1.40` para las pruebas de integración y evita la negociación predeterminada `1.32`.
- Se mejoraron los mensajes de conflicto y de datos inválidos.

### Pruebas

- `RepositoriosPostgresTest` pasó contra PostgreSQL local en `localhost:5432/tienda_db`: 19 pruebas reales, 0 fallos; cubre CRUD de nuevos repositorios, checkout con cupón, expiración de reservas y reglas `P0001`.
- Nuevo `FlujoCompletoPostgresTest`: flujo completo por HTTP (registro, refresh/logout, producto admin con imágenes y atributos, carrito, dirección, checkout, reseña rechazada con `422`, pago aprobado, transiciones de estado y reseña final) contra la BD real.
- La suite completa quedó en 86 pruebas con 0 fallos (5 omitidas sin Docker: `PostgresIntegrationTest`).
- Las pruebas de endpoints cubren validación de productos (`400`), integridad de datos (`409`), reglas de negocio (`422`) y error inesperado (`500` genérico).

### Documentación

- Se amplió el README con el árbol del proyecto y un diagrama UML Mermaid de las clases principales.
- README y CHANGELOG actualizados: se documentan los nuevos módulos, la migración V4 y el mapeo `422`; se retiraron las afirmaciones de endpoints no implementados (`501`).

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

- Antes de añadir los módulos y los tests de repositorios locales, `mvn clean test` pasó en homelab: 22 pruebas, 0 fallos y 0 omitidas.
- `mvn -Dtest=RepositoriosPostgresTest test` pasó contra PostgreSQL 16.15 en `localhost:5432/tienda_db`: 9 pruebas SQL reales, 0 fallos y 0 omitidas; Flyway validó V1–V3.
- `RepositoriosPostgresTest` verifica que usa `tienda_db` y limpia solo fixtures de prueba identificados por prefijos/IDs propios.
- En Fedora, la última ejecución de `mvn test` ejecutó 58 pruebas con 0 fallos; 5 de Testcontainers se omitieron porque ese entorno no tiene Docker.
- Las pruebas de endpoints cubren validación de productos (`400`), integridad de datos (`409`) y error inesperado (`500` genérico).

