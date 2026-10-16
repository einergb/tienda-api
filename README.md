# tienda-api

Plantilla backend e-commerce (Spring Boot 3 + Java 21 + JDBC + PostgreSQL + Flyway), lista para Docker. El Compose de este proyecto levanta solo la API y se conecta al contenedor PostgreSQL existente `web_db`.

## Arrancar con Docker

```bash
cp .env.example .env
# Configura DB_NAME, DB_USER y DB_PASSWORD con los valores de tu PostgreSQL existente.
# Configura JWT_SECRET (min. 32 caracteres) y, si aplica, ADMIN_PASSWORD.
docker compose up -d --build
```

- API: http://192.168.0.150:8080
- Swagger: http://192.168.0.150:8080/swagger-ui.html
- Salud: http://192.168.0.150:8080/actuator/health
- PostgreSQL desde el host: `192.168.0.150:5433` (publicado por el Compose del contenedor de base de datos).

Antes de iniciar la API, el contenedor PostgreSQL debe estar activo, llamarse `web_db` y estar conectado a la red externa Docker `homelab_net`. La API conecta a `web_db:5432` dentro de esa red; `5433` es el puerto publicado para clientes que se conectan desde el host y no se usa entre contenedores. Flyway aplica las migraciones al arrancar la API. Si defines `ADMIN_PASSWORD`, se crea el usuario administrador (`ADMIN_EMAIL`) si todavía no existe.

## Probar

```bash
# Login admin
curl -s -X POST localhost:8080/api/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"admin@tienda.local","password":"TU_ADMIN_PASSWORD"}'

# Crear producto (usa el token del login)
curl -s -X POST localhost:8080/api/admin/productos -H "Authorization: Bearer TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"sku":"LAP-001","nombre":"Portatil 15 pulgadas","precio":2500000,"categoriaId":1,"stock":10}'

# Catalogo publico
curl -s localhost:8080/api/catalogo/productos
```

## Estructura del proyecto

```text
tienda-api/
├── CHANGELOG.md
├── Dockerfile
├── docker-compose.yml
├── pom.xml
├── README.md
└── src/main/
  ├── java/com/codeconheiner/tienda/
  │   ├── TiendaApplication.java
  │   ├── auth/
  │   │   ├── AuthController.java
  │   │   ├── AuthService.java
  │   │   ├── JwtAuthFilter.java
  │   │   ├── JwtService.java
  │   │   └── dto/ (LoginRequest, RegistroRequest, TokenResponse)
  │   ├── catalogo/
  │   │   ├── AdminProductoController.java
  │   │   ├── CatalogoController.java
  │   │   ├── CategoriaRepository.java
  │   │   ├── CategoriaResponse.java
  │   │   ├── ProductoRepository.java
  │   │   ├── ProductoRequest.java
  │   │   ├── ProductoResponse.java
  │   │   └── ProductoService.java
  │   ├── common/ (ApiException, GlobalExceptionHandler)
  │   ├── config/ (AdminBootstrap, SecurityConfig, TiendaProperties)
  │   ├── usuarios/ (Usuario, UsuarioRepository)
  │   ├── carrito/     (paquete reservado)
  │   ├── envios/      (paquete reservado)
  │   ├── inventario/  (paquete reservado)
  │   ├── pagos/       (paquete reservado)
  │   └── pedidos/     (paquete reservado)
  └── resources/
    ├── application.yml
    └── db/migration/
      ├── V1__esquema_inicial.sql
      ├── V2__datos_semilla.sql
      └── V3__reglas_negocio.sql
```

Los paquetes marcados como reservados todavía no contienen lógica de negocio. La autenticación, el catálogo, la configuración y el acceso a usuarios sí están implementados.

## UML de clases

```mermaid
classDiagram
  class TiendaApplication
  class AuthController
  class AuthService
  class JwtService
  class JwtAuthFilter
  class SecurityConfig
  class OncePerRequestFilter
  class AdminBootstrap
  class TiendaProperties
  class LoginRequest
  class RegistroRequest
  class TokenResponse
  class Usuario
  class UsuarioRepository
  class CatalogoController
  class AdminProductoController
  class ProductoService
  class ProductoRepository
  class CategoriaRepository
  class ProductoRequest
  class ProductoResponse
  class CategoriaResponse
  class GlobalExceptionHandler
  class ApiException
  class JdbcTemplate
  class ResponseEntityExceptionHandler

  AuthController --> AuthService : registro y login
  AuthController ..> LoginRequest
  AuthController ..> RegistroRequest
  AuthController ..> TokenResponse
  AuthService --> UsuarioRepository
  AuthService --> JwtService
  AuthService --> TiendaProperties
  AuthService ..> Usuario
  AuthService ..> TokenResponse
  UsuarioRepository --> JdbcTemplate
  UsuarioRepository ..> Usuario

  SecurityConfig --> JwtAuthFilter : registra filtro
  JwtAuthFilter --|> OncePerRequestFilter
  JwtAuthFilter --> JwtService : valida token
  JwtService --> TiendaProperties
  JwtService ..> Usuario : genera token
  AdminBootstrap --> UsuarioRepository
  AdminBootstrap --> TiendaProperties

  CatalogoController --> ProductoService
  CatalogoController --> CategoriaRepository
  CatalogoController --> TiendaProperties
  CatalogoController ..> CategoriaResponse
  CatalogoController ..> ProductoResponse
  AdminProductoController --> ProductoService
  AdminProductoController ..> ProductoRequest
  AdminProductoController ..> ProductoResponse
  ProductoService --> ProductoRepository
  ProductoService --> TiendaProperties
  ProductoService ..> ProductoRequest
  ProductoService ..> ProductoResponse
  ProductoRepository --> JdbcTemplate
  ProductoRepository ..> ProductoResponse
  CategoriaRepository --> JdbcTemplate
  CategoriaRepository ..> CategoriaResponse

  GlobalExceptionHandler --|> ResponseEntityExceptionHandler
  GlobalExceptionHandler ..> ApiException : transforma en ProblemDetail
```

## Reutilizar la plantilla

Cambia solo `tienda.*` en `application.yml` (o las variables del `.env`), las categorias semilla en `V2` y el frontend.
