# tienda-api

Plantilla backend e-commerce (Spring Boot 3 + Java 21 + JDBC + PostgreSQL + Flyway), lista para Docker.

## Arrancar con Docker

```bash
cp .env.example .env
# Edita .env: DB_PASSWORD, JWT_SECRET (min. 32 caracteres) y ADMIN_PASSWORD
docker compose up -d --build
```

- API: http://localhost:8080
- Swagger: http://localhost:8080/swagger-ui.html
- Salud: http://localhost:8080/actuator/health

Flyway crea el esquema y los datos semilla al primer arranque. Si defines `ADMIN_PASSWORD`, se crea el usuario administrador (`ADMIN_EMAIL`).

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

## Estructura

```
config/      propiedades de la tienda, seguridad (JWT), administrador inicial
common/      ApiException y manejo global de errores (ProblemDetail)
auth/        registro, login, JWT
usuarios/    acceso a datos de usuarios
catalogo/    productos, categorias (publico + /api/admin/productos)
inventario/ carrito/ pedidos/ pagos/ envios/   (paquetes reservados)
db/migration V1 esquema, V2 datos semilla
```

## Reutilizar la plantilla

Cambia solo `tienda.*` en `application.yml` (o las variables del `.env`), las categorias semilla en `V2` y el frontend.
