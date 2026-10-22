# AGENTS.md - tienda-api

Este archivo proporciona orientación específica del repositorio para agentes de OpenCode, con el fin de evitar errores y trabajar de manera eficiente. Enfócate en hechos no obvios y verificables.

## Stack tecnológico
- Spring Boot 3.3.5 (parent), Java 21, Maven
- JDBC (JdbcTemplate), PostgreSQL, Flyway migrations (classpath:db/migration)
- Spring Security + JWT (jjwt 0.12.6)
- Validation, Actuator, springdoc-openapi 2.6.0
- Tests: Spring Boot Test + Security Test (WebMvcTest with in-memory fakes)

## Estructura y límites de la aplicación
- Base package: `com.codeconheiner.tienda`
- Main: `TiendaApplication` (uses `@ConfigurationPropertiesScan`)
- Imágenes: `productos.imagen_key` es fuente de verdad (V9). Galería `producto_imagenes`. Almacenamiento S3-compatible vía `StorageService` (`subirArchivo`, `urlPublica`, `derivarClaveDesdeUrlPublica`), `Cache-Control` immutable, `STORAGE_PUBLIC_URL_INCLUYE_BUCKET`. AdminBootstrap crea admin ACTIVO/VERIFICADO si no existe (método `crearAdmin`).
- Caché: solo lecturas públicas con `CACHE_TYPE` (`none` por defecto). Caches `tienda-info`, `categorias`, `catalogo-listado` (clave por filtros/página), `producto`. Nunca cachea `/api/admin/**`, carrito, checkout/pedidos, stock para decisiones de reserva/compra (los flujos usan repositorios/BD). Invalidación en escrituras admin/imagenes/atributos y al expirar reservas. Serialización JSON, prefijo `tienda::`, `CacheErrorHandler` pasa a BD si Redis falla.
- Domains:
  - `auth/`: AuthController (registro/login/refresh/logout/password), AuthService, JwtService, JwtAuthFilter, `RefreshTokenStore` (puerto) + RefreshTokenRepository; DTOs for registro/login/token/refresh. Las sesiones son **familias** de refresh tokens (`sesion_id`): rotación encadenada por `reemplazado_por`, detección de reuso (token ya usado ⇒ revoca la familia) y `purgarAntiguos`. El puerto permite sustituir la persistencia sin tocar `AuthService` (DIP).
  - `seguridad/`: `Auditoria` (escribe en la tabla `auditoria`, nunca tumba la petición), `SeguridadEncabezadosFilter` (X-Content-Type-Options, X-Frame-Options, Referrer-Policy, Permissions-Policy, CSP y HSTS solo bajo HTTPS).
  - `catalogo/`: public catalog + admin product mgmt (AdminProductoController under `/api/admin/productos`, incl. `/imagenes` y `/atributos`), ProductoService (logical delete via `activo=false`), repositories
  - `usuarios/`: Usuario entity + UsuarioRepository (joins roles)
  - `common/`: ApiException + GlobalExceptionHandler (RFC 7807 ProblemDetail). Handles validation (400 + `errores` map), unreadable JSON (400), ApiException, DataIntegrityViolationException (409, salvo regla P0001), SQLSTATE P0001 (422), generic 500
  - `config/`: SecurityConfig, TiendaProperties, AdminBootstrap, `ValidacionConfiguracion` (fail-fast de JWT/pagos/rate-limit al arrancar), TareasSeguridad (@Component + @EnableScheduling: `expirar_reservas()` cada 60 s y purga de refresh tokens cada hora), RateLimitFilter + RateLimitConfig
  - `common/ratelimit/`: puerto `RateLimiter` + `VentanaDeslizanteRateLimiter` (ventana deslizante en memoria por clave/IP); `common/Hashes` (SHA-256 hex)
  - `direcciones/`, `cupones/`, `resenas/`: módulos completos (controller + service + repository + DTOs). En direcciones el repositorio desmarca la principal anterior antes de insertar/actualizar (lo exige el índice único parcial `ux_direcciones_principal` de V6); el trigger de V3 queda como red de seguridad.
  - `pedidos/`: PedidoController (checkout en `crear` + `GET /{id}/factura`), AdminPedidoController (transiciones de estado + factura admin), PedidoService (orquesta), CalculadoraTotales (@Component con el cálculo de subtotal/descuento/IVA/envío/total, SRP), PedidoRepository. `PedidoService` y `FacturaService` auditan creación, cambios de estado y emisión.
  - `pagos/`: PagoController (`POST /api/pagos`, `POST /api/pagos/sesion`), PagoService (registro simple + orquestación con pasarela), PagoRepository (`crear` 6-arg con idempotency_key, `buscarPorIdempotencyKey`, `buscarPorReferencia`, `guardarSesion`, `actualizarEstado`), PagoDetalle (record público interno)
  - `pagos/pasarela/`: `PasarelaPago` (interfaz) + WompiPasarela/EpaycoPasarela (modo simulado si `pagos.modo != real`), `PasarelaRegistry` (List injection = OCP; pasarela desconocida → 400 "Pasarela no soportada"), `VerificadorFirmaWebhook` + WompiFirmaWebhook/EpaycoFirmaWebhook + FirmaWebhookRegistry, `CrearTransaccion`/`SesionPago`
  - `facturas/`: FacturaService, FacturaRepository (emisión idempotente `ON CONFLICT (pedido_id) DO UPDATE`), FacturaEmision y EnvioCopia (records públicos), dto/FacturaResponse
  - `reportes/`: ReporteController (`/api/admin/reportes/ventas|inventario`), ReporteService (ReporteRepository + InventarioRepository), ReporteRepository (resumen + `TotalesInventario` nested record)
  - `webhooks/`: WebhookController (`/api/webhooks/wompi|epayco`, valida firma → 401 "Firma del webhook inválida"), WebhookPagoService (registra cada pago procesado en `auditoria`), WebhookEventosRepository (`INSERT ... ON CONFLICT DO NOTHING RETURNING` = dedupe), EventoWebhookParser + WompiEventoParser/EpaycoEventoParser (JSON ilegible → 400 "Payload de webhook inválido")
  - `envios/`: EnvioController + AdminEnvioController (CRUD admin)
  - `carrito/`, `inventario/`: CarritoController/CarritoService e InventarioController + AdminInventarioController (`/stock-bajo` sobre `v_stock_bajo`)
- Flyway: enabled, locations `db/migration`, `baseline-on-migrate: true`, `baseline-version: 3`. V1 schema, V2 seeds (roles ADMIN/CLIENTE + 5 categorías), V3 triggers/functions (RAISE EXCEPTION con SQLSTATE P0001), V4 `refresh_tokens` (hash SHA-256, rotación, 7 días de expiración), V5 `facturas` + `webhook_eventos` + `pagos.idempotency_key`/`sesion_id`, V6 familias de refresh tokens + `auditoria` + índices + `ux_direcciones_principal`.

## Puntos de entrada y enrutamiento
- Public: `/api/auth/**`, `/api/webhooks/**`, `/actuator/health/**`, `/actuator/info`, Swagger (`/v3/api-docs/**`, `/swagger-ui/**`, `/swagger-ui.html`), `GET /api/catalogo/**`, `GET /api/resenas/producto/**`, `GET /api/cupones/validar`, `GET /api/inventario/productos/*/stock`
- Admin-only: `/api/admin/**` requires role `ADMIN` (hasRole). SecurityConfig returns ProblemDetail on 401/403 with exact Spanish messages matching tests.
- Authenticated: `/api/carrito/**`, `/api/direcciones/**`, `/api/pedidos/**`, `/api/pagos/**`, `/api/resenas/**`, `/api/envios/pedido/**` (cualquier rol autenticado).
- Rate limiting: `RateLimitFilter` (registrado en `RateLimitConfig`) aplica ventana deslizante por IP a `/api/**`; general 300/min, auth (login/registro/refresh) 10/min; `/api/webhooks/**` excluido a propósito (`shouldNotFilter`). 429 = ProblemDetail + `Retry-After`.
- Catalog rule: public product detail only returned if `activo` AND `stockDisponible > 0` (see ProductoService.detallePublico). Logical delete = `desactivar()` sets `activo=false`.

## Construcción, pruebas y ejecución
- Build: `mvn -B -q package -DskipTests` (Docker uses this)
- Test all: `mvn test`
- Run single test class: `mvn test -Dtest=ApiEndpointsTest`
- Run single test method: `mvn test -Dtest=ApiEndpointsTest#registroYLoginDevuelvenToken`
- Integration tests vs local PG (`localhost:5432/tienda_db`, `tienda_user`/`tienda_pass`, no Docker needed): `mvn test -Dtest=RepositoriosPostgresTest,FlujoCompletoPostgresTest` (Flyway aplica V1–V6; limpian fixtures `it-%`/`IT-%`, `webhook_eventos` con `evento_id LIKE 'IT-%'` y `auditoria` con `accion LIKE 'IT-%'`). Ambos suben `tienda.rate-limit.*` por `DynamicPropertySource` (el E2E encadena muchas peticiones desde la misma IP) y definen los secretos de firma `e2e-*`.
- `PostgresIntegrationTest` usa Testcontainers con `disabledWithoutDocker = true`: sin Docker se omite (5 tests), no falla.
- Run app locally: `mvn spring-boot:run` (requires DB + env). Or via Docker compose.
- Dev quick check: build + tests. Tests use @WebMvcTest with in-memory beans (see ApiEndpointsTest.TestBeans), so no external DB needed.

## Variables de entorno y configuración
- application.yml reads from env vars:
  - `DB_URL` (default `jdbc:postgresql://localhost:5432/tienda`)
  - `DB_USER` (default `tienda`), `DB_PASSWORD` (default `tienda`)
  - `JWT_SECRET` (required) - jjwt uses HMAC-SHA; must be >= 32 bytes. `application.yml` lo deja vacío (`${JWT_SECRET:}`) a propósito: `ValidacionConfiguracion.validarSecretoJwt`/`JwtService` fallan con un mensaje español claro al arrancar si falta o es corto. `.env.example` shows `openssl rand -base64 48` to generate.
  - `ADMIN_EMAIL` (default `admin@tienda.local`), `ADMIN_PASSWORD` (empty by default) - AdminBootstrap creates admin if password non-blank and user doesn't exist.
  - `CORS_ORIGINS` (default `http://localhost:5173`), `TIENDA_NOMBRE` (default `TechStore`)
- tienda props (TiendaProperties, record de 12 componentes — todos los `new TiendaProperties(...)` deben llevarlos): nombre, moneda, iva, envioGratisDesde, costoEnvioBase, stockMinimoAlerta, reservaStockMinutos, jwt{secret, expiracionMinutos}, admin{email,password}, cors{origenesPermitidos}, rateLimit{peticionesPorMinuto,peticionesAuthPorMinuto,ventanaSegundos}, pagos{modo,wompi{apiUrl,publicKey,privateKey,integritySecret,redirectUrl},epayco{apiUrl,apifyUrl,publicKey,privateKey}}. `costoEnvioBase` (application.yml `costo-envio-base`, 10000) se usa en el checkout cuando el subtotal no alcanza `envioGratisDesde`.
- `TiendaProperties.Pagos` NO tiene constructor sin argumentos: para pruebas usar la fábrica `TiendaProperties.Pagos.simulados()`. Un segundo constructor en el record rompía el binding de Spring Boot (dejaba `pagos()` en `null` y tumbaba el contexto), así que no añadir sobrecargas al record.
- Env vars de las funciones nuevas: `RATE_LIMIT_MAXIMO` (300), `RATE_LIMIT_AUTH_MAXIMO` (10), `PAGOS_MODO` (`simulado`|`real`), `WOMPI_API_URL`, `WOMPI_PUBLIC_KEY`, `WOMPI_PRIVATE_KEY`, `WOMPI_INTEGRITY_SECRET`, `WOMPI_REDIRECT_URL`, `EPAYCO_API_URL`, `EPAYCO_APIIFY_URL`, `EPAYCO_PUBLIC_KEY`, `EPAYCO_PRIVATE_KEY`. `tienda.rate-limit.ventana-segundos` (60) solo está en `application.yml`.

## Docker / despliegue
- Multi-stage Dockerfile: Maven build (go-offline) then JRE image, non-root user `app`, exposes 8080, HEALTHCHECK hits `/actuator/health`.
- docker-compose.yml: service `api`, container `tienda-api`, binds `192.168.0.150:8080:8080`, uses external network `homelab_net`, connects to `web_db:5432` (DB_URL set accordingly). Requires `.env` with DB_* and JWT_SECRET.
- DB host in containers: `web_db` on `homelab_net`; external host access on 5433 (per README). API uses internal network.

## Especificidades de pruebas (importante conocer)
- WebMvc slice tests mock infrastructure: ApiEndpointsTest provides in-memory UsuarioRepository, in-memory ProductoService, JwtService, TiendaProperties. Keep this pattern if extending MVC tests. `@WebMvcTest` (sin lista de controllers) carga TODOS los controllers: cada controller nuevo necesita su bean/fake en `TestBeans`. `ModulosEndpointsTest` sí fija la lista de controllers explícitamente.
- `ApiEndpointsTest.InMemoryUsuarioRepository.buscarPorEmail` solo devuelve usuarios creados vía registro: tests con `@WithMockUser` que resuelven el usuario por email (direcciones, pagos) deben registrar el usuario primero o usar un fake que devuelva un id fijo. `@BeforeEach` limpia usuarios y productos entre tests.
- Tests assert exact Spanish ProblemDetail messages (match GlobalExceptionHandler/SecurityConfig): e.g. 400 validation "Los datos enviados no son válidos." with `errores` by field; 400 malformed JSON "El cuerpo de la solicitud no contiene JSON válido."; 401 auth missing "Autenticación requerida: inicia sesión o proporciona un token válido."; 403 "No tienes permisos para realizar esta operación."; 404 "Producto no encontrado"; 409 duplicate "El correo ya está registrado."; 422 con el mensaje exacto del `RAISE EXCEPTION` de V3 (p. ej. "Transicion de estado no permitida: ENTREGADO -> PAGADO"). DataIntegrityViolationException sin regla P0001 → 409 con mensaje genérico de restricción.
- Mensajes exactos de las funciones nuevas que los tests asertan: 429 `RateLimitFilter.MENSAJE` "Demasiadas solicitudes desde tu conexión. Intenta de nuevo más tarde." con cabecera `Retry-After`; 400 "Pasarela no soportada"; 400 "Payload de webhook inválido"; 400 "Webhook de pasarela no soportada"; 401 "Firma del webhook inválida"; 409 "El pedido no admite pagos en el estado actual"; 409 "La factura solo se emite para pedidos pagados"; 400 "El rango de fechas no es válido"; 404 "Pedido no encontrado". El cuerpo del webhook responde `{"estado":"procesado|duplicado|ya-procesado|ignorado"}`.
- `ControladorPruebaRateLimit` (test) es un `@RestController` en el paquete base: cualquier `@WebMvcTest` sin lista de controllers lo carga, así que su ruta debe ser única y pública (`GET /api/catalogo/prueba-rate-limit`); no usar rutas ya mapeadas por `CatalogoController`.
- Firmas de webhook en tests: `SHA-256(cuerpo + secreto)` con `Hashes.sha256Hex` sobre el cuerpo exacto enviado. Sin secreto configurado, toda firma se rechaza (seguro por defecto). Secreto vacío ⇒ webhooks siempre 401.
- AuthService normalizes email (lowercase, trimmed) and name trimmed; stores password with BCrypt; login checks activo and password match, returns generic 401 for bad creds. Refresh tokens se guardan como hash SHA-256 hex (64 chars, columna VARCHAR(64)). `RefreshTokenStore` (puerto) expone `crearFamilia`/`crearEnFamilia`/`buscarPorHash`/`marcarUsado`/`revocarFamilia`/`revocarTodosDeUsuario`/`purgarAntiguos`; los tests con fakes in-memory implementan el puerto, no la clase.
- `Auditoria.registrarActual` resuelve el `usuario_id` por el correo del principal (el JWT no expone el id numérico). Registrar auditoría nunca debe lanzar: cualquier fallo se registra en el log y la petición continúa.
- Tests need JWT secret >=32 bytes in test props (see test code).

## Particularidades de base de datos y migraciones
- V1: uses NUMERIC(12,0) for COP amounts (no decimals). Cupones PORCENTAJE uses NUMERIC(12,2). `pedidos` tiene CHECK `total = subtotal - descuento + iva + costo_envio`.
- V3 adds business logic in PL/pgSQL (triggers, functions) - e.g. `reservar_stock`, `fn_carrito_item_validar` valida stock reservado; `fn_pedido_antes_insert` asigna numero, reserva_expira_en y valida/aplica cupones; `fn_pedido_cambio_estado` valida la máquina de estados (PENDIENTE→PAGADO→EN_PREPARACION→ENVIADO→ENTREGADO→DEVUELTO) y descuenta stock al PAGADO; `fn_direccion_unica_principal`; `fn_resena_compra_verificada` exige pedido ENTREGADO.
- `RAISE EXCEPTION` (SQLSTATE P0001) → GlobalExceptionHandler responde **422** con el mensaje del trigger. Detalle no obvio: el mensaje del driver termina con `\n  Where: PL/pgSQL function ...` (con sangría), por eso la limpieza usa la regex `\R\s*Where:` y no un `indexOf("\nWhere:")`.
- V4: tabla `refresh_tokens` (usuario_id FK cascade, hash único, expirado_en, revocado). AuthService rota al refrescar y revoca al hacer logout o cambiar contraseña.
- V5: `facturas` (pedido_id UNIQUE FK `ON DELETE CASCADE`, numero `FAC-AAAA-NNNNNN` con `seq_factura_numero`), `webhook_eventos` (UNIQUE `(pasarela, evento_id)`) y `pagos.idempotency_key` (índice único parcial sobre valores no nulos) + `pagos.sesion_id`. La emisión de factura usa `INSERT ... ON CONFLICT (pedido_id) DO UPDATE SET numero = facturas.numero` (idempotente). No hay FK en `webhook_eventos`: los tests deben borrar sus filas `IT-%` explícitamente.
- Seed roles: ADMIN, CLIENTE. Seed categories: Computadores, Celulares, Audio, Gaming, Accesorios.
- Slugs generated from nombre+sku (normalize, remove diacritics, alphanumeric-dash).
- TRAMPA de Java: los text blocks recortan espacios finales de línea; `SELECT """ + COLUMNAS + """` genera `SELECTid...` sin espacio. Armar SQL concatenado con strings normales (`"SELECT " + COLUMNAS + " FROM ..."`) o usar `\s`.

## Comandos a preferir (exactos, no obvios)
- Quick verification: `mvn test` (no external services). WebMvc tests don't require DB (PostgresIntegrationTest se omite sin Docker).
- Focused: `mvn test -Dtest=ApiEndpointsTest,AuthServiceTest`
- Focused nuevos módulos: `mvn test -Dtest='ModulosEndpointsTest,RateLimitingTest,VentanaDeslizanteRateLimiterTest,PasarelasTest'`
- Integration con PG local: `mvn test -Dtest=RepositoriosPostgresTest,FlujoCompletoPostgresTest`
- Build jar: `mvn package` (skipping tests optional as above)
- Run with local DB: ensure PostgreSQL running, set env (or .env), then `mvn spring-boot:run`.
- OJO con la caché incremental de Maven: tras cambios amplios de código de prueba, `rm -rf target/test-classes` antes de `mvn test-compile` para no ocultar errores de compilación.

## Convenciones y consideraciones importantes
- Logical delete only (productos desactivados kept). Public catalog never returns inactive or zero-stock items.
- Email uniqueness case-insensitive (DB unique index on LOWER(email), repo checks LOWER).
- ProblemDetail everywhere for errors (no custom error bodies except ProblemDetail + optional `errores`).
- Stock model: `inventario.stock - inventario.stock_reservado = stock_disponible`. Public queries filter `stock_disponible > 0`.
- Admin bootstrap is silent if ADMIN_PASSWORD empty (logs info). If exists, skips.
- Security: stateless sessions, JWT filter before UsernamePasswordAuthenticationFilter, CORS configured from props.
- SOLID: `CalculadoraTotales` (pedidos) aísla el cálculo de importes; las pasarelas implementan `PasarelaPago` y se resuelven por `PasarelaRegistry`; las firmas de webhook implementan `VerificadorFirmaWebhook`; los parsers implementan `EventoWebhookParser`. Al añadir una pasarela solo hay que implementar esas interfaces y registrarlas como bean (no modificar `PagoService`).
- El rate limiter es en memoria por instancia (no distribuido): con varias réplicas cada una cuenta por separado. Si se necesita límite global, cambiarlo detrás del puerto `RateLimiter`.
- When editing tests or code, preserve Spanish messages exactly as they appear (tests assert them).
- README claims `web_db` on external network `homelab_net` - matches compose; don't change network assumptions without reason.
