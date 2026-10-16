# AGENTS.md - tienda-api

This file gives OpenCode agents repo-specific guidance to avoid mistakes and work efficiently. Focus on non-obvious, verifiable facts.

## Tech stack
- Spring Boot 3.3.5 (parent), Java 21, Maven
- JDBC (JdbcTemplate), PostgreSQL, Flyway migrations (classpath:db/migration)
- Spring Security + JWT (jjwt 0.12.6)
- Validation, Actuator, springdoc-openapi 2.6.0
- Tests: Spring Boot Test + Security Test (WebMvcTest with in-memory fakes)

## App structure & boundaries
- Base package: `com.codeconheiner.tienda`
- Main: `TiendaApplication` (uses `@ConfigurationPropertiesScan`)
- Domains:
  - `auth/`: AuthController, AuthService, JwtService, JwtAuthFilter; DTOs for registro/login/token
  - `catalogo/`: public catalog + admin product mgmt (AdminProductoController under `/api/admin/productos`), ProductoService (logical delete via `activo=false`), repositories
  - `usuarios/`: Usuario entity + UsuarioRepository (joins roles)
  - `common/`: ApiException + GlobalExceptionHandler (RFC 7807 ProblemDetail). Handles validation (400 + `errores` map), unreadable JSON (400), ApiException, DataIntegrityViolationException (409), generic 500
  - `config/`: SecurityConfig, TiendaProperties, AdminBootstrap
- Scaffolding present (not exposed): `carrito`, `inventario`, `pedidos`, `pagos`, `envios` (schema/migrations exist; README notes these aren't implemented as API yet)
- Flyway: enabled, locations `db/migration`, `baseline-on-migrate: true`, `baseline-version: 3`. V1 schema, V2 seeds (roles ADMIN/CLIENTE + 5 categorías), V3 adds triggers/functions (raises SQL exceptions - handler maps data integrity to 409; business rule errors from V3 SQLSTATE P0001 conceptually -> 422 per comments, but current GlobalExceptionHandler treats most DB rule violations as 409 via DataIntegrityViolationException).

## Entry points & routing
- Public: `/api/auth/**`, `/actuator/health/**`, `/actuator/info`, Swagger (`/v3/api-docs/**`, `/swagger-ui/**`, `/swagger-ui.html`), `GET /api/catalogo/**`
- Admin-only: `/api/admin/**` requires role `ADMIN` (hasRole). SecurityConfig returns ProblemDetail on 401/403 with exact Spanish messages matching tests.
- Catalog rule: public product detail only returned if `activo` AND `stockDisponible > 0` (see ProductoService.detallePublico). Logical delete = `desactivar()` sets `activo=false`.

## Build, test, run
- Build: `mvn -B -q package -DskipTests` (Docker uses this)
- Test all: `mvn test`
- Run single test class: `mvn test -Dtest=ApiEndpointsTest`
- Run single test method: `mvn test -Dtest=ApiEndpointsTest#registroYLoginDevuelvenToken`
- Run app locally: `mvn spring-boot:run` (requires DB + env). Or via Docker compose.
- Dev quick check: build + tests. Tests use @WebMvcTest with in-memory beans (see ApiEndpointsTest.TestBeans), so no external DB needed.

## Env & config
- application.yml reads from env vars:
  - `DB_URL` (default `jdbc:postgresql://localhost:5432/tienda`)
  - `DB_USER` (default `tienda`), `DB_PASSWORD` (default `tienda`)
  - `JWT_SECRET` (required) - jjwt uses HMAC-SHA; must be >= 32 bytes. `.env.example` shows `openssl rand -base64 48` to generate.
  - `ADMIN_EMAIL` (default `admin@tienda.local`), `ADMIN_PASSWORD` (empty by default) - AdminBootstrap creates admin if password non-blank and user doesn't exist.
  - `CORS_ORIGINS` (default `http://localhost:5173`), `TIENDA_NOMBRE` (default `TechStore`)
- tienda props (TiendaProperties): nombre, moneda, iva, envioGratisDesde, stockMinimoAlerta, reservaStockMinutos, jwt{secret, expiracionMinutos}, admin{email,password}, cors{origenesPermitidos}.

## Docker / deployment
- Multi-stage Dockerfile: Maven build (go-offline) then JRE image, non-root user `app`, exposes 8080, HEALTHCHECK hits `/actuator/health`.
- docker-compose.yml: service `api`, container `tienda-api`, binds `192.168.0.150:8080:8080`, uses external network `homelab_net`, connects to `web_db:5432` (DB_URL set accordingly). Requires `.env` with DB_* and JWT_SECRET.
- DB host in containers: `web_db` on `homelab_net`; external host access on 5433 (per README). API uses internal network.

## Testing specifics (must know)
- WebMvc slice tests mock infrastructure: ApiEndpointsTest provides in-memory UsuarioRepository, in-memory ProductoService, JwtService, TiendaProperties. Keep this pattern if extending MVC tests.
- Tests assert exact Spanish ProblemDetail messages (match GlobalExceptionHandler/SecurityConfig): e.g. 400 validation "Los datos enviados no son válidos." with `errores` by field; 400 malformed JSON "El cuerpo de la solicitud no contiene JSON válido."; 401 auth missing "Autenticación requerida: inicia sesión o proporciona un token válido."; 403 "No tienes permisos para realizar esta operación."; 404 "Producto no encontrado"; 409 duplicate "El correo ya está registrado." Also DataIntegrityViolationException maps to 409 with generic constraint message.
- AuthService normalizes email (lowercase, trimmed) and name trimmed; stores password with BCrypt; login checks activo and password match, returns generic 401 for bad creds.
- Tests need JWT secret >=32 bytes in test props (see test code).

## Database & migrations quirks
- V1: uses NUMERIC(12,0) for COP amounts (no decimals). Cupones PORCENTAJE uses NUMERIC(12,2). 
- V3 adds business logic in PL/pgSQL (triggers, functions) - e.g. `reservar_stock`, `fn_carrito_item_validar` validates stock vs reserved and raises exceptions; `fn_pedido_antes_insert` validates coupons, sets numero, reserva expiry. Errors via `RAISE EXCEPTION` may surface as DataIntegrityViolationException depending on execution path - GlobalExceptionHandler logs cause and returns 409 with generic constraint message. This is intentional per migration comment noting future 422 mapping possibility.
- Seed roles: ADMIN, CLIENTE. Seed categories: Computadores, Celulares, Audio, Gaming, Accesorios.
- Slugs generated from nombre+sku (normalize, remove diacritics, alphanumeric-dash).

## Commands to prefer (exact, non-obvious)
- Quick verification: `mvn test` (no external services). WebMvc tests don't require DB.
- Focused: `mvn test -Dtest=ApiEndpointsTest,AuthServiceTest`
- Build jar: `mvn package` (skipping tests optional as above)
- Run with local DB: ensure PostgreSQL running, set env (or .env), then `mvn spring-boot:run`.

## Conventions & gotchas
- Logical delete only (productos desactivados kept). Public catalog never returns inactive or zero-stock items.
- Email uniqueness case-insensitive (DB unique index on LOWER(email), repo checks LOWER).
- ProblemDetail everywhere for errors (no custom error bodies except ProblemDetail + optional `errores`).
- Stock model: `inventario.stock - inventario.stock_reservado = stock_disponible`. Public queries filter `stock_disponible > 0`.
- Admin bootstrap is silent if ADMIN_PASSWORD empty (logs info). If exists, skips.
- Security: stateless sessions, JWT filter before UsernamePasswordAuthenticationFilter, CORS configured from props.
- When editing tests or code, preserve Spanish messages exactly as they appear (tests assert them).
- README claims `web_db` on external network `homelab_net` - matches compose; don't change network assumptions without reason.
