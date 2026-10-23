# tienda-api

API REST para una tienda de comercio electrónico, construida con Spring Boot, Java y PostgreSQL. Incluye autenticación JWT, catálogo, carrito, pedidos, pagos, inventario, facturación y administración.

> **Infraestructura:** Redis proporciona caché opcional del catálogo y RustFS almacena imágenes mediante la API compatible con AWS S3. Ambos servicios son externos: `docker-compose.yml` despliega solo la API.

## Contenido

- [Tecnologías](#tecnologías)
- [Funcionalidades](#funcionalidades)
- [Arquitectura](#arquitectura)
- [Requisitos](#requisitos)
- [Configuración](#configuración)
- [Ejecución local](#ejecución-local)
- [Despliegue con Docker](#despliegue-con-docker)
- [API](#api)
- [Integración con frontend](#integración-con-frontend)
- [Autenticación y permisos](#autenticación-y-permisos)
- [Pagos y webhooks](#pagos-y-webhooks)
- [Base de datos](#base-de-datos)
- [Pruebas](#pruebas)
- [Seguridad y limitaciones](#seguridad-y-limitaciones)
- [Redis y almacenamiento S3-compatible](#redis-y-almacenamiento-s3-compatible)
- [Solución de problemas](#solución-de-problemas)

## Tecnologías

- Java 21 y Spring Boot 3.3.5.
- Spring MVC, Spring Security, validación y Actuator.
- JDBC con `JdbcTemplate` y PostgreSQL 16.
- Flyway para versionar el esquema de base de datos.
- JWT para autenticación y BCrypt para contraseñas.
- JUnit, Spring Boot Test y Testcontainers para pruebas.
- Maven para compilación y gestión de dependencias.

## Funcionalidades

- Registro, login, JWT y refresh tokens rotativos con revocación de sesiones.
- Catálogo público con categorías, búsqueda, paginación y productos con stock.
- Administración de productos, carga de imágenes a RustFS (S3-compatible), galería y atributos.
- Carrito, direcciones, cupones y checkout transaccional.
- Reserva y expiración automática de stock; reglas de negocio protegidas por triggers de PostgreSQL.
- Pedidos, pagos, estados, envíos, reseñas verificadas y facturas idempotentes.
- Integración de pagos con Wompi y ePayco, incluyendo validación y deduplicación de webhooks.
- Reportes administrativos de ventas e inventario.
- Limitación de solicitudes por IP y respuestas de error `ProblemDetail`.

## Arquitectura

La aplicación está organizada por dominios bajo `com.codeconheiner.tienda`:

| Paquete | Responsabilidad |
| --- | --- |
| `auth` | Registro, login, JWT y ciclo de vida de refresh tokens. |
| `catalogo` | Catálogo público y administración de productos, imágenes y atributos. |
| `carrito`, `direcciones`, `cupones` | Compras previas al checkout y sus reglas. |
| `pedidos`, `inventario`, `envios` | Checkout, stock, estados y logística. |
| `pagos`, `webhooks` | Pasarelas, sesiones de pago y notificaciones firmadas. |
| `facturas`, `reportes`, `resenas` | Documentos, consultas administrativas y reseñas. |
| `usuarios` | Persistencia y consulta de usuarios y roles. |
| `config`, `seguridad` | Configuración, filtros y tareas programadas. |
| `common` | Manejo de errores, hashes y limitación de solicitudes. |

Los controladores reciben solicitudes, los servicios coordinan reglas de aplicación y los repositorios ejecutan SQL con `JdbcTemplate`. Las lecturas públicas del catálogo pueden usar Redis; las imágenes se almacenan en RustFS mediante la API compatible con AWS S3. La API utiliza Problem Details para mantener respuestas de error consistentes.

### Diagrama de componentes

```mermaid
flowchart LR
  Cliente[Frontend / cliente HTTP]
  subgraph API[Spring Boot API]
    Filtros[Seguridad JWT y rate limit]
    Controladores[Controladores REST]
    Servicios[Servicios de dominio]
    Repositorios[Repositorios JDBC]
    Cache[CacheConfig y CacheCatalogo]
    Storage[StorageService]
    Pagos[Adaptadores de pago]
    Webhooks[Verificación y procesamiento de webhooks]
    Tareas[Tareas programadas]
  end
  DB[(PostgreSQL)]
  Redis[(Redis)]
  RustFS[(RustFS S3-compatible)]
  Wompi[Wompi]
  Epayco[ePayco]

  Cliente --> Filtros --> Controladores --> Servicios --> Repositorios --> DB
  Controladores --> Storage
  Storage -->|AWS SDK S3, path-style| RustFS
  Controladores -. "@Cacheable / @CacheEvict" .-> Cache
  Servicios --> Cache
  Tareas --> Cache
  Cache --> Redis
  Servicios --> Pagos
  Pagos --> Wompi
  Pagos --> Epayco
  Wompi -->|Notificación firmada| Webhooks
  Epayco -->|Notificación firmada| Webhooks
  Webhooks --> Servicios
  Tareas --> Repositorios
```

### UML de clases: pagos extensibles

El registro resuelve cada implementación por nombre. Los contratos separados permiten agregar una pasarela sin acoplar `PagoService` a un proveedor concreto.

```mermaid
classDiagram
  class PagoService {
    +crearConPasarela(usuarioId, request)
  }
  class PasarelaRegistry {
    +obtener(nombre) PasarelaPago
  }
  class PasarelaPago {
    <<interface>>
    +nombre() String
    +crearSesion(transaccion) SesionPago
  }
  class WompiPasarela
  class EpaycoPasarela
  class WebhookController
  class FirmaWebhookRegistry {
    +obtener(pasarela) VerificadorFirmaWebhook
  }
  class VerificadorFirmaWebhook {
    <<interface>>
    +pasarela() String
    +firmaValida(headers, cuerpo) boolean
  }
  class WompiFirmaWebhook
  class EpaycoFirmaWebhook
  class EventoWebhookParser {
    <<interface>>
    +pasarela() String
    +parsear(cuerpo) Optional~WebhookEvento~
  }
  class WompiEventoParser
  class EpaycoEventoParser
  class WebhookPagoService
  class PagoRepository
  class WebhookEventosRepository

  PagoService --> PasarelaRegistry
  PasarelaRegistry o-- PasarelaPago : registra por nombre
  PasarelaPago <|.. WompiPasarela
  PasarelaPago <|.. EpaycoPasarela
  WebhookController --> FirmaWebhookRegistry
  FirmaWebhookRegistry o-- VerificadorFirmaWebhook : registra por pasarela
  VerificadorFirmaWebhook <|.. WompiFirmaWebhook
  VerificadorFirmaWebhook <|.. EpaycoFirmaWebhook
  WebhookController --> EventoWebhookParser : selecciona por pasarela
  EventoWebhookParser <|.. WompiEventoParser
  EventoWebhookParser <|.. EpaycoEventoParser
  WebhookController --> WebhookPagoService
  WebhookPagoService --> WebhookEventosRepository : deduplicación
  WebhookPagoService --> PagoRepository : estado del pago
  PagoService --> PagoRepository : crea intentos
```

### UML de clases: caché y almacenamiento

`CACHE_TYPE=redis` selecciona el gestor Redis; con `none`, la caché no se activa. El almacenamiento usa AWS SDK S3 contra RustFS, con bucket configurable y URL pública independiente del endpoint de escritura.

```mermaid
classDiagram
  class CatalogoController
  class AdminProductoController
  class ProductoService
  class CacheCatalogo {
    +invalidarProducto(id)
    +invalidarProductos(ids)
    +invalidarTodoElCatalogo()
  }
  class CacheConfig
  class RedisCacheManager
  class CacheManager
  class TiendaProperties
  class StorageConfig
  class StorageService {
    +subir(file, productoId) String
    +urlPublica(key) String
    +eliminar(key)
  }
  class S3Client {
    endpointOverride
    pathStyleAccessEnabled
  }
  class RustFS
  class Bucket

  CatalogoController ..> CacheConfig : @Cacheable
  AdminProductoController ..> CacheConfig : @CacheEvict
  CacheConfig --> RedisCacheManager : CACHE_TYPE=redis
  RedisCacheManager ..|> CacheManager
  CacheCatalogo --> CacheManager : evict / clear
  ProductoService --> CacheCatalogo : cambios de catálogo
  AdminProductoController --> ProductoService
  ProductoService --> StorageService : imágenes
  AdminProductoController --> StorageService : imágenes
  StorageConfig --> S3Client : endpoint y credenciales
  StorageService --> S3Client : PutObject / DeleteObject
  StorageService --> TiendaProperties : bucket y URL pública
  S3Client --> RustFS : API AWS S3
  RustFS *-- Bucket : contiene
```

### UML de secuencia: carga de imagen

PostgreSQL conserva `imagen_key`, no los bytes. La URL pública se calcula por separado a partir de `STORAGE_PUBLIC_URL`, el bucket y la clave del objeto.

```mermaid
sequenceDiagram
  autonumber
  actor Admin
  participant C as AdminProductoController
  participant P as ProductoService
  participant R as ProductoRepository
  participant S as StorageService
  participant SDK as AWS S3 SDK
  participant F as RustFS bucket
  participant DB as PostgreSQL

  Admin->>C: POST /api/admin/productos/{id}/imagen (multipart)
  C->>P: subirImagen(id, file)
  P->>R: verificar producto y leer imagen_key anterior
  R->>DB: SELECT producto, imagen_key
  DB-->>R: producto e imagen_key
  P->>S: subir(file, id)
  S->>S: validar tipo, tamaño y contenido
  S->>SDK: PutObject(bucket, productos/{id}/{uuid}.ext)
  SDK->>F: PUT objeto (path-style)
  F-->>SDK: objeto creado
  SDK-->>S: clave del objeto
  S-->>P: clave nueva
  P->>R: actualizar imagen_key
  R->>DB: UPDATE productos
  opt Existe clave anterior
    P->>S: eliminar(clave anterior)
    S->>SDK: DeleteObject(bucket, clave anterior)
    SDK->>F: DELETE objeto
  end
  P->>S: urlPublica(clave nueva)
  S-->>P: URL pública
  P-->>C: ProductoResponse con imagenUrl
  Note over C: Invalida producto y catalogo-listado
  C-->>Admin: 201 Created
```

### UML de secuencia: confirmación de pago

```mermaid
sequenceDiagram
  autonumber
  participant P as Pasarela
  participant C as WebhookController
  participant F as FirmaWebhookRegistry
  participant V as Verificador de firma
  participant N as Parser de evento
  participant S as WebhookPagoService
  participant E as WebhookEventosRepository
  participant R as PagoRepository
  participant DB as PostgreSQL

  P->>C: POST webhook (cuerpo + firma)
  C->>F: obtener(pasarela)
  F-->>C: verificador
  C->>V: firmaValida(encabezados, cuerpo)
  alt Firma inválida
    V-->>C: false
    C-->>P: 401 Firma inválida
  else Firma válida
    V-->>C: true
    C->>N: parsear(cuerpo)
    N-->>C: WebhookEvento normalizado
    C->>S: procesar(evento)
    S->>E: registrarSiNuevo(pasarela, eventoId)
    E->>DB: INSERT ... ON CONFLICT DO NOTHING
    DB-->>E: evento nuevo o duplicado
    alt Evento duplicado
      E-->>S: false
      S-->>C: duplicado
    else Evento nuevo
      S->>R: buscarPorReferencia(referencia)
      R->>DB: SELECT pago
      DB-->>R: pago y monto guardados
      S->>S: validar monto y estado
      S->>R: actualizarEstado(APROBADO o RECHAZADO)
      R->>DB: UPDATE pagos
      Note over DB: Trigger de V3 actualiza pedido y stock al aprobar
      S-->>C: procesado
      C-->>P: 200 estado procesado
    end
  end
```

### UML de estados: pedido

```mermaid
stateDiagram-v2
  [*] --> PENDIENTE: checkout / reserva stock
  PENDIENTE --> PAGADO: pago aprobado
  PAGADO --> EN_PREPARACION: avance administrativo
  EN_PREPARACION --> ENVIADO: despacho
  ENVIADO --> ENTREGADO: entrega confirmada
  ENTREGADO --> DEVUELTO: devolución aceptada
```

## Requisitos

- JDK 21.
- Maven 3.9 o compatible; o Docker para construir y ejecutar la imagen.
- PostgreSQL 16 accesible para la aplicación.
- Docker Compose si se usará el despliegue del homelab.

## Configuración

Para desarrollo local, crea la configuración de entorno a partir de la plantilla:

```bash
cp .env.example .env
```

Configura al menos la conexión de PostgreSQL y un secreto JWT de 32 bytes o más. Puedes generar uno con:

```bash
openssl rand -base64 48
```

No publiques `.env` ni compartas sus valores. `ADMIN_PASSWORD` es opcional: al iniciar la aplicación se crea el administrador inicial solo si la contraseña está definida y `ADMIN_EMAIL` aún no existe. No cambia la contraseña de una cuenta existente.

| Variable | Valor predeterminado | Descripción |
| --- | --- | --- |
| `DB_URL` | `jdbc:postgresql://localhost:5432/tienda` | URL JDBC cuando la aplicación se ejecuta fuera de Compose. |
| `DB_NAME` | `tienda` | Nombre de base usado por el `docker-compose.yml` de la API. |
| `DB_USER` | `tienda` | Usuario PostgreSQL. |
| `DB_PASSWORD` | `tienda` en `application.yml`; Compose la exige | Contraseña PostgreSQL. Usa un valor seguro. |
| `JWT_SECRET` | Vacío | Obligatorio; la aplicación falla al iniciar si falta o tiene menos de 32 bytes. |
| `ADMIN_EMAIL` | `admin@tienda.local` | Correo para el administrador inicial. |
| `ADMIN_PASSWORD` | Vacío | Si se define, permite crear el administrador inicial si no existe. |
| `CORS_ORIGINS` | `http://localhost:5173` | Orígenes CORS permitidos. |
| `TIENDA_NOMBRE` | `TechStore` | Nombre público de la tienda. |
| `CACHE_TYPE` | `none` | `none` (sin Redis, desarrollo) o `redis` (producción). |
| `CACHE_TTL_PRODUCTOS` | `60s` | TTL para detalle de producto. |
| `CACHE_TTL_LISTADOS` | `30s` | TTL para listado de catálogo. |
| `CACHE_TTL_CATEGORIAS` | `1h` | TTL para categorías. |
| `CACHE_TTL_TIENDA` | `1h` | TTL para datos de tienda. |
| `REDIS_HOST/PORT/PASSWORD` | `localhost/6379/` | Conexión Redis cuando `CACHE_TYPE=redis`. |
| `REDIS_CONNECT_TIMEOUT` | `500ms` | Tiempo máximo para conectar con Redis; al agotarse la caché cae a PostgreSQL. |
| `REDIS_TIMEOUT` | `500ms` | Timeout de comandos Redis. |
| `STORAGE_ENDPOINT` | `http://192.168.0.150:9000` | Endpoint de la API S3 de RustFS, accesible desde la API. |
| `STORAGE_ACCESS_KEY` | Vacía | Access key del usuario de RustFS con permisos sobre el bucket. |
| `STORAGE_SECRET_KEY` | Vacía | Secret key del usuario de RustFS. |
| `STORAGE_BUCKET` | `tienda-productos-dev` | Bucket de objetos; debe existir y ser accesible con esas credenciales. Compose usa `tienda-productos` si no se configura. |
| `STORAGE_PUBLIC_URL` | `http://192.168.0.150:9000` | Base URL que pueden alcanzar los navegadores para descargar imágenes; puede ser un proxy/CDN distinto del endpoint S3. |
| `STORAGE_PUBLIC_URL_INCLUYE_BUCKET` | `true` | Si es `true`, la URL pública se construye como `{base}/{bucket}/{clave}`; si es `false`, como `{base}/{clave}`. |
| `RATE_LIMIT_MAXIMO` | `300` | Solicitudes generales por IP y ventana. |
| `RATE_LIMIT_AUTH_MAXIMO` | `10` | Solicitudes de login, registro y refresh por IP y ventana. |
| `PAGOS_MODO` | `simulado` | `simulado` o `real`. El modo real utiliza las URLs configuradas. |
| `WOMPI_API_URL` | `https://api.sandbox.wompi.co/v1` | URL de API Wompi. |
| `WOMPI_PUBLIC_KEY` / `WOMPI_PRIVATE_KEY` | Vacías | Credenciales Wompi. |
| `WOMPI_INTEGRITY_SECRET` | Vacío | Secreto para verificar la firma de webhooks Wompi. |
| `WOMPI_REDIRECT_URL` | `http://localhost:5173/pago/exitoso` | URL de retorno del checkout Wompi. |
| `EPAYCO_API_URL` | `https://api.secure.epayco.co` | URL de API ePayco. |
| `EPAYCO_APIIFY_URL` | `https://apify.epayco.co` | URL auxiliar configurada para ePayco. |
| `EPAYCO_PUBLIC_KEY` / `EPAYCO_PRIVATE_KEY` | Vacías | Credenciales ePayco; la clave privada verifica el webhook actual. |

Con `PAGOS_MODO=simulado`, la API no llama servicios externos. Configura las variables de pagos según la pasarela que utilices.

## Ejecución local

Con PostgreSQL disponible y las variables definidas en el entorno:

```bash
mvn spring-boot:run
```

La aplicación escucha en `http://localhost:8080`. Flyway valida y aplica las migraciones durante el arranque.

Para compilar sin ejecutar pruebas:

```bash
mvn -B -q package -DskipTests
```

## Despliegue con Docker

El Compose de este repositorio levanta la API, no PostgreSQL, Redis ni RustFS. La API se conecta a `web_db:5432` y a la red Docker externa `homelab_net`; configura `REDIS_HOST` y `STORAGE_ENDPOINT` con nombres/IP alcanzables desde esa red. `STORAGE_ENDPOINT` apunta a la API S3 de RustFS, mientras `STORAGE_PUBLIC_URL` debe ser accesible desde los clientes web. Redis y RustFS deben estar desplegados por separado.

1. Verifica que `web_db`, Redis y RustFS estén activos y conectados a `homelab_net`.
2. Crea `.env` a partir de `.env.example`; configura los datos de PostgreSQL, `JWT_SECRET`, `REDIS_HOST` y las variables `STORAGE_*`. No reutilices credenciales de ejemplo.
3. Crea previamente en RustFS el bucket indicado por `STORAGE_BUCKET` y concede a las claves configuradas permisos para leer, subir y borrar objetos en ese bucket.
4. Asegúrate de que la dirección `192.168.0.150` de `ports` en `docker-compose.yml` corresponda al host. Cámbiala si el servidor usa otra IP.
5. Desde la carpeta del proyecto ejecuta:

```bash
docker compose up -d --build
docker compose ps
docker compose logs -f api
```

URLs con la configuración actual:

- API: `http://192.168.0.150:8080`
- Swagger UI: `http://192.168.0.150:8080/swagger-ui.html`
- OpenAPI: `http://192.168.0.150:8080/v3/api-docs`
- Salud: `http://192.168.0.150:8080/actuator/health`

Dentro de Docker se usa `web_db:5432`; el puerto publicado de PostgreSQL en el host no es el puerto entre contenedores. La red externa se declara en `docker-compose.yml` y debe existir antes de levantar la API. El Compose no crea el bucket ni los servicios Redis/RustFS.

## API

La documentación interactiva está en Swagger UI. Resumen de rutas principales:

| Método | Ruta | Acceso | Uso |
| --- | --- | --- | --- |
| `POST` | `/api/auth/registro` | Público | Registra un cliente. |
| `POST` | `/api/auth/login` | Público | Inicia sesión y devuelve JWT y refresh token. |
| `POST` | `/api/auth/refresh` | Público | Rota el refresh token. |
| `POST` | `/api/auth/logout` | Autenticado | Revoca el refresh token. |
| `PUT` | `/api/auth/password` | Autenticado | Cambia la contraseña e invalida sesiones anteriores. |
| `GET` | `/api/catalogo/tienda` | Público | Configuración visible de la tienda. |
| `GET` | `/api/catalogo/categorias` | Público | Categorías activas. |
| `GET` | `/api/catalogo/productos` | Público | Productos activos con stock; acepta `categoriaId`, `q`, `page`, `size`. |
| `GET` | `/api/catalogo/productos/{id}` | Público | Detalle con imágenes y atributos; requiere producto activo y con stock. |
| `POST` | `/api/admin/productos` | `ADMIN` | Crea producto e inventario inicial. |
| `PUT` | `/api/admin/productos/{id}` | `ADMIN` | Actualiza datos del producto e inventario. |
| `DELETE` | `/api/admin/productos/{id}` | `ADMIN` | Desactiva lógicamente el producto. |
| `POST` | `/api/admin/productos/{id}/imagen` | `ADMIN` | Sube la imagen principal (`multipart/form-data`, campo `file`). Guarda clave en `productos.imagen_key` y sincroniza galería. |
| `DELETE` | `/api/admin/productos/{id}/imagen` | `ADMIN` | Elimina imagen principal y objeto en S3. |
| `POST` | `/api/admin/productos/imagenes` | `ADMIN` | Sube archivo (`multipart/form-data`, `archivo`) con prefijo `productos/sin-asignar` o acepta `imagenUrl` (http/https). |
| `POST` | `/api/admin/productos/{id}/imagenes` | `ADMIN` | Agrega entrada a galería por JSON (`url`, `orden`, `esPrincipal`). |
| `POST` | `/api/admin/productos/{id}/imagenes/upload` | `ADMIN` | Sube archivo a galería (`multipart/form-data`, `archivo`) con StorageService. |
| `DELETE` | `/api/admin/productos/{id}/imagenes/{imagenId}` | `ADMIN` | Elimina registro de galería; si es URL propia borra objeto S3 (best effort). |
| `POST` / `DELETE` | `/api/admin/productos/{id}/atributos[/{clave}]` | `ADMIN` | Gestiona atributos clave/valor. |
| `GET` / `POST` / `PUT` / `DELETE` | `/api/carrito` y `/api/carrito/items` | Autenticado | Consulta y modifica el carrito propio. |
| `GET` / `POST` / `PUT` / `DELETE` | `/api/direcciones` | Autenticado | Gestiona direcciones propias. |
| `POST` | `/api/pedidos` | Autenticado | Checkout, reserva stock y vacía el carrito. |
| `GET` | `/api/pedidos` y `/api/pedidos/{id}` | Autenticado | Consulta pedidos propios. |
| `GET` | `/api/pedidos/{id}/factura` | Autenticado | Emite o consulta la factura propia de un pedido pagado. |
| `GET` / `POST` | `/api/pagos/pedido/{pedidoId}`, `/api/pagos` | Autenticado | Consulta pagos o registra un intento. |
| `POST` | `/api/pagos/sesion` | Autenticado | Crea o reutiliza una sesión de pasarela. |
| `GET` | `/api/inventario/productos/{productoId}/stock` | Público | Consulta stock. |
| `GET` | `/api/cupones/validar?codigo=&monto=` | Público | Valida un cupón. |
| `GET` | `/api/resenas/producto/{productoId}` | Público | Consulta reseñas de un producto. |
| `POST` | `/api/resenas` | Autenticado | Crea una reseña; requiere compra entregada. |
| `GET` | `/api/envios/pedido/{pedidoId}` | Autenticado | Consulta el envío asociado a un pedido. |
| `POST` | `/api/webhooks/wompi`, `/api/webhooks/epayco` | Público, firmado | Recibe confirmaciones de pago de las pasarelas. |
| `GET` | `/actuator/health` | Público | Salud de la aplicación. |

Rutas administrativas adicionales, todas con rol `ADMIN`:

| Grupo | Rutas |
| --- | --- |
| Pedidos | `/api/admin/pedidos`, `/api/admin/pedidos/{id}`, `/api/admin/pedidos/{id}/estado`, `/api/admin/pedidos/{id}/factura` |
| Cupones | `/api/admin/cupones`, `/api/admin/cupones/{id}` |
| Envíos | `/api/admin/envios`, `/api/admin/envios/{id}` |
| Inventario | `/api/admin/inventario/stock-bajo` |
| Reportes | `/api/admin/reportes/ventas?desde=&hasta=`, `/api/admin/reportes/inventario` |

### Ejemplo: autenticación y catálogo

El login responde con los campos `token`, `tipo`, `expiraEnSegundos`, `nombre`, `rol` y `refreshToken`. Envía el token de acceso en la cabecera `Authorization`:

```bash
curl -sS -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@tienda.local","password":"TU_CONTRASEÑA"}'

curl -i http://localhost:8080/api/catalogo/productos
```

Para administrar productos, usa `Authorization: Bearer <token>`. La cuenta debe tener el rol `ADMIN`.

### Imágenes de productos

- **Imagen principal**: `productos.imagen_key` es la fuente de verdad. `POST /api/admin/productos/{id}/imagen` sube con `StorageService`, actualiza la clave, sincroniza `producto_imagenes` (pone otras como no principales, reemplaza fila de la principal anterior, inserta nueva con `es_principal=true`, `orden=0`) y borra el objeto anterior (best effort).
- **Galería**: `POST /api/admin/productos/{id}/imagenes/upload` sube archivo con `StorageService`. `POST /api/admin/productos/{id}/imagenes` acepta JSON con `url`. `POST /api/admin/productos/imagenes` sube archivo con prefijo `productos/sin-asignar` o acepta `imagenUrl` http/https válida.
- **Eliminación**: `DELETE /api/admin/productos/{id}/imagen` borra objeto y limpia galería/clave. `DELETE /api/admin/productos/{id}/imagenes/{imagenId}` borra la fila; si la URL es propia (`STORAGE_PUBLIC_URL`) intenta borrar el objeto S3 (best effort).
- **Almacenamiento**: RustFS implementa la API compatible con AWS S3; la aplicación usa el SDK de AWS con endpoint override y acceso path-style (`/{bucket}/{clave}`). El bucket se configura con `STORAGE_BUCKET` y debe existir antes de subir. Las claves se generan con UUID, nunca con el nombre original. Los objetos se sirven con `Cache-Control: public, max-age=31536000, immutable`; al borrar uno, un CDN o caché intermedia podría conservarlo hasta que expire o se purgue. `STORAGE_PUBLIC_URL_INCLUYE_BUCKET` controla si la URL pública incluye el bucket.
- **Nota**: puede existir una fila de galería con placeholder tipo `https://<HOST_DE_IMAGENES>/...` (producto 1 en producción). No se borra automáticamente por código/migración; requiere limpieza manual.

El catálogo público solo muestra productos activos con stock disponible. El `PUT` actualiza los datos e inventario, pero no reactiva productos; actualmente la API no expone un endpoint de reactivación.

## Integración con frontend

El frontend necesita la URL base de la API, un origen permitido por CORS y enviar el JWT en las solicitudes protegidas. La API es stateless: no usa cookies ni sesión de servidor.

### URL base y CORS

- Desarrollo local: `http://localhost:8080`.
- Despliegue actual en la red local: `http://192.168.0.150:8080` (ajusta la dirección si cambia el host).
- OpenAPI: `/v3/api-docs`; Swagger UI: `/swagger-ui.html`.

Configura la URL en el frontend; por ejemplo, con Vite:

```dotenv
VITE_API_URL=http://localhost:8080
```

Por defecto, CORS permite únicamente `http://localhost:5173`. Si el frontend usa otro origen, incluye el origen exacto en el `.env` del backend, sin barra final:

```dotenv
CORS_ORIGINS=http://localhost:5173,http://localhost:3000
```

Se permiten los métodos `GET`, `POST`, `PUT`, `PATCH`, `DELETE` y `OPTIONS`, y las cabeceras `Authorization` y `Content-Type`. Si el frontend envía cabeceras personalizadas, también deben añadirse en `SecurityConfig`.

### Login, JWT y refresh

Login: `POST /api/auth/login`.

```http
Content-Type: application/json
```

```json
{
  "email": "cliente@correo.com",
  "password": "clave-segura"
}
```

La respuesta incluye `token`, `tipo`, `expiraEnSegundos`, `nombre`, `rol` y `refreshToken`. Envía el token de acceso en cada solicitud protegida:

```http
Authorization: Bearer <token>
```

Refresh: `POST /api/auth/refresh`, con el refresh token en el cuerpo:

```json
{
  "refreshToken": "abc123..."
}
```

Cada refresh rota el token: reemplaza el refresh token anterior por el nuevo recibido. No envíes varios refresh simultáneos usando el mismo token; el reuso puede revocar la familia de sesión.

El frontend debería mantener el token de acceso en memoria y proteger el refresh token. Ante un `401`, puede intentar refrescar una sola vez y repetir la solicitud; si el refresh falla, debe cerrar la sesión. El endpoint `POST /api/auth/logout` revoca la sesión usando el refresh token enviado en el cuerpo; `/api/auth/**` es público y no requiere JWT.

### Registro

`POST /api/auth/registro` devuelve `202 Accepted`. El usuario debe confirmar su correo antes de poder iniciar sesión. El cuerpo esperado es:

```json
{
  "primerNombre": "Ana",
  "segundoNombre": null,
  "primerApellido": "Pérez",
  "segundoApellido": null,
  "nombre": "Ana Pérez",
  "telefono": "3001234567",
  "email": "ana@correo.com",
  "emailConfirmacion": "ana@correo.com",
  "password": "clave-segura-1",
  "captchaToken": "token-recaptcha",
  "aceptaTerminos": true,
  "aceptaTratamientoDatos": true,
  "aceptaMarketing": false
}
```

`CAPTCHA_SECRETO` configura la verificación de reCAPTCHA. **Limitación de seguridad actual:** el backend acepta literalmente `captchaToken: "test"` en cualquier entorno y, si `CAPTCHA_SECRETO` está vacío, acepta cualquier token no vacío sin verificarlo. No despliegues con ese bypass; configura el secreto y revisa el bypass antes de exponer el registro en producción.

### Rutas y permisos

No requieren token:

| Método | Ruta | Uso |
| --- | --- | --- |
| `GET` | `/api/catalogo/tienda` | Información de la tienda. |
| `GET` | `/api/catalogo/categorias` | Categorías. |
| `GET` | `/api/catalogo/productos?categoriaId=&q=&page=0&size=12` | Listado y búsqueda. |
| `GET` | `/api/catalogo/productos/{id}` | Detalle de producto. |
| `GET` | `/api/resenas/producto/{productoId}` | Reseñas del producto. |
| `GET` | `/api/cupones/validar?codigo=&monto=` | Validación de cupón. |

El catálogo público solo devuelve productos activos con `stockDisponible > 0`; el detalle de un producto sin stock responde `404`.

Requieren JWT válido las rutas `/api/carrito/**`, `/api/direcciones/**`, `/api/pedidos/**`, `/api/pagos/**`, `/api/resenas/**` (excepto la consulta pública de reseñas indicada arriba) y `/api/envios/**`. `/api/admin/**` requiere además el rol `ADMIN`. La validación `GET /api/cupones/validar` es pública; la administración de cupones está bajo `/api/admin/cupones/**`.

### Errores y límites

Los errores siguen el formato `ProblemDetail`. Una respuesta de autenticación puede verse así:

```json
{
  "type": "about:blank",
  "title": "Unauthorized",
  "status": 401,
  "detail": "Autenticación requerida: inicia sesión o proporciona un token válido.",
  "instance": "/api/carrito"
}
```

Los errores de validación incluyen un mapa `errores` por campo:

```json
{
  "status": 400,
  "detail": "Los datos enviados no son válidos.",
  "errores": {
    "email": "El correo no tiene un formato válido"
  }
}
```

| Estado | Significado |
| --- | --- |
| `400` | Datos inválidos o JSON mal formado. |
| `401` | Falta el token o no es válido. |
| `403` | El usuario no tiene el rol o permiso requerido. |
| `404` | Recurso no encontrado o producto sin stock. |
| `409` | Conflicto, por ejemplo correo duplicado. |
| `422` | Regla de negocio no satisfecha. |
| `429` | Límite de solicitudes; respeta la cabecera `Retry-After`. |
| `500` | Error interno. |

Los límites por IP son 10 solicitudes por minuto para login, registro y refresh, y 300 por minuto para el resto de `/api/**`.

### Pagos

El frontend crea una sesión de pago; no recibe webhooks. Envía `POST /api/pagos/sesion` con un JWT válido y una clave de idempotencia que debe reutilizarse al reintentar la misma operación:

```http
Authorization: Bearer <token>
Content-Type: application/json
```

```json
{
  "pedidoId": 123,
  "pasarela": "WOMPI",
  "claveIdempotencia": "uuid-del-front"
}
```

La API devuelve los datos de la sesión y la URL para continuar el pago. La pasarela comunica el resultado al backend mediante `/api/webhooks/wompi` o `/api/webhooks/epayco`; el backend valida la firma y actualiza el pago.

## Autenticación y permisos

- Las rutas públicas no requieren token.
- `/api/admin/**` requiere un JWT válido con rol `ADMIN`.
- Carrito, pedidos, pagos, direcciones y operaciones propias requieren autenticación.
- En solicitudes protegidas utiliza `Authorization: Bearer <token>`.
- El JWT se firma con `JWT_SECRET`; la expiración de acceso se configura en `tienda.jwt.expiracion-minutos` (120 minutos por defecto).
- Los refresh tokens se almacenan como hash, se rotan al refrescar y tienen una vigencia de siete días.
- Las contraseñas de usuario se almacenan con BCrypt.

Errores frecuentes: `400` validación o JSON inválido; `401` autenticación ausente/inválida; `403` rol insuficiente; `404` recurso inexistente/no disponible; `409` conflicto o restricción; `422` regla de negocio de PostgreSQL; `429` límite de solicitudes superado; `500` error inesperado. Los errores se responden como `ProblemDetail`, con mensajes en español.

## Pagos y webhooks

El flujo de pago es:

1. El cliente crea el pedido con `POST /api/pedidos`; el stock se reserva y el pedido queda pendiente.
2. Solicita `POST /api/pagos/sesion` con el pedido, la pasarela (`WOMPI` o `EPAYCO`) y, cuando corresponda, la clave de idempotencia.
3. La API devuelve los datos de la sesión y la URL de pago para el frontend.
4. La pasarela notifica el resultado a `/api/webhooks/wompi` o `/api/webhooks/epayco`.
5. La API valida la firma y el pago, deduplica eventos repetidos y actualiza el pedido. Un pago aprobado marca el pedido como pagado y descuenta el stock reservado.

En modo `simulado` no se invocan APIs externas. En modo `real` se utilizan las URLs y credenciales configuradas; el ejemplo de Wompi apunta al sandbox. Los secretos de pasarela deben configurarse únicamente en el servidor.

## Base de datos

Flyway administra el esquema en `src/main/resources/db/migration`:

| Migración | Contenido |
| --- | --- |
| V1 | Esquema inicial de usuarios, catálogo, inventario, carrito, pedidos y dominios relacionados. |
| V2 | Roles `ADMIN` y `CLIENTE`, y categorías iniciales. |
| V3 | Triggers y funciones para stock, pedidos, cupones, direcciones y reglas de negocio. |
| V4 | Persistencia de refresh tokens. |
| V5 | Facturas, eventos de webhook e idempotencia de pagos. |
| V6 | Familias de refresh tokens, auditoría e índices adicionales. |

En Compose, la API se conecta al PostgreSQL existente `web_db`, en la base definida por `DB_NAME`. No uses la base de producción para pruebas automatizadas que limpian fixtures. Las pruebas PostgreSQL locales apuntan específicamente a `localhost:5432/tienda_db`; `PostgresIntegrationTest` crea una base temporal independiente con Testcontainers.

## Pruebas

Pruebas unitarias y MVC sin PostgreSQL externo:

```bash
mvn -Dtest='AuthServiceTest,ApiEndpointsTest,ModulosEndpointsTest,RateLimitingTest,VentanaDeslizanteRateLimiterTest,PasarelasTest' test
```

Pruebas SQL y flujo HTTP contra PostgreSQL auxiliar local. Requieren `tienda_db` en `localhost:5432`, usuario `tienda_user` y contraseña `tienda_pass`:

```bash
mvn -Dtest='RepositoriosPostgresTest,FlujoCompletoPostgresTest' test
```

Pruebas contra PostgreSQL temporal gestionado por Testcontainers:

```bash
mvn -Dtest=PostgresIntegrationTest test
```

Testcontainers requiere que Docker sea accesible para el proceso Maven; si no lo es, esta clase está configurada para omitirse. Para ejecutar toda la suite:

```bash
mvn test
```

Las pruebas `RepositoriosPostgresTest` y `FlujoCompletoPostgresTest` limpian sus propios datos de prueba. Usa una base auxiliar desechable, nunca la base de producción.

## Seguridad y limitaciones

- Configura secretos únicos y robustos; no incluyas `.env`, JWT, contraseñas ni claves de pasarela en Git o en logs compartidos.
- El administrador inicial se crea solo si no existe. Cambiar `ADMIN_PASSWORD` no modifica una cuenta previamente creada.
- El rate limiter usa una ventana deslizante en memoria por instancia; no comparte contadores entre réplicas y no está respaldado por Redis.
- Las imágenes principales guardan en PostgreSQL la clave del objeto S3, no sus bytes. Las cargas de producto y galería se validan y almacenan en RustFS. La galería también permite URLs externas.
- Los productos se desactivan de forma lógica. No existe todavía una ruta de API para reactivarlos.
- Las reglas críticas de negocio también se aplican en PostgreSQL; los errores SQLSTATE `P0001` se convierten en HTTP `422`.
- La caché Redis aplica solo a lecturas públicas de catálogo. Si Redis falla, la API continúa consultando PostgreSQL; el health de Redis está excluido de `/actuator/health` y sus probes.
- Stock y reservas se invalidan después de las operaciones Java de checkout, estados de pedido, aprobación de pagos, devolución de envío y expiración de reservas. Escrituras directas en PostgreSQL u otros clientes que disparen los triggers no notifican a esta instancia; los TTL predeterminados son 60 s para detalles y 30 s para listados para acotar esa posible obsolescencia.
- Las categorías solo tienen lectura pública, sin rutas administrativas de creación o activación. `ProductoResponse` no incluye promedio ni conteo de reseñas; esos cambios no requieren invalidar el detalle.

## Redis y almacenamiento S3-compatible

Redis se usa opcionalmente para la caché del catálogo (`CACHE_TYPE=redis`). El almacenamiento de imágenes utiliza RustFS mediante el protocolo S3 de AWS; no se requiere MinIO, aunque otro servicio compatible puede configurarse cambiando el endpoint y las credenciales. Estos servicios no se levantan desde el Compose de la API.

### Redis para caché

La caché cubre tienda, categorías, listado y detalle público. El TTL es configurable por entorno; cambios de catálogo hechos desde la API invalidan las entradas afectadas. PostgreSQL no publica invalidaciones para escrituras externas, por lo que deben considerarse los TTL de respaldo documentados en Seguridad y limitaciones. Una caída de Redis no desactiva la API ni cambia el estado de health. La conexión usa timeouts de 500 ms (`REDIS_CONNECT_TIMEOUT`/`REDIS_TIMEOUT`), rechaza comandos sin conexión y evita reintentar contra Redis durante unos segundos tras un fallo, de modo que las peticiones siguen sirviéndose desde PostgreSQL sin esperas largas. Redis se excluye del health de Actuator.

### RustFS y buckets

Configura `STORAGE_ENDPOINT` con la URL interna de la API S3 de RustFS y `STORAGE_PUBLIC_URL` con la dirección pública desde la que el navegador obtendrá las imágenes. Pueden ser distintas cuando hay proxy inverso o CDN. Configura `STORAGE_BUCKET` con un bucket ya creado y credenciales con permisos limitados a ese bucket. La API no crea buckets ni expone las claves de acceso. Las cargas admiten JPEG, PNG y WebP de hasta 5 MiB; las claves usan UUID y prefijos como `productos/{id}/`.

## Solución de problemas

| Síntoma | Comprobación |
| --- | --- |
| La API no conecta a PostgreSQL en Docker | Verifica que `web_db` esté activo, en `homelab_net`, y que `DB_NAME`, `DB_USER` y `DB_PASSWORD` coincidan. Entre contenedores usa `web_db:5432`. |
| La API falla al iniciar por JWT | Define `JWT_SECRET` con al menos 32 bytes. |
| Login ADMIN da `401` | Usa el correo y contraseña actuales de la cuenta. `ADMIN_PASSWORD` solo crea la cuenta si no existía; no la restablece. |
| Ruta ADMIN da `403` | El JWT debe pertenecer a un usuario con rol `ADMIN`. |
| Catálogo vacío | El catálogo oculta productos inactivos o sin stock disponible. |
| La imagen no aparece | El endpoint actual guarda URLs, no carga archivos; comprueba que la URL sea real y accesible para el navegador. |
| Pruebas PostgreSQL no conectan | Comprueba PostgreSQL auxiliar en `localhost:5432/tienda_db`; no apuntes las pruebas a producción. |
| Testcontainers no ejecuta | Comprueba que Maven tenga acceso al daemon Docker. |
