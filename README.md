# tienda-api

API backend de tienda construida con Spring Boot 3, Java 21, Spring Security, JDBC y PostgreSQL. Este documento describe exclusivamente las rutas y comportamientos que están implementados actualmente y sirve como referencia para conectar un frontend.

## Alcance actual

La API implementa:

- Registro de clientes y login con JWT.
- Lectura pública de la configuración visible de la tienda, las categorías activas y el catálogo de productos activos con stock.
- Creación, actualización y desactivación lógica de productos para usuarios con rol `ADMIN`.
- CORS configurable, documentación OpenAPI/Swagger y endpoint de salud de Actuator.

El esquema SQL también contiene tablas para direcciones, imágenes, atributos, carritos, cupones, pedidos, pagos, envíos y reseñas. Sin embargo, esos módulos no tienen controladores ni operaciones API implementadas en este momento. La presencia de esas tablas no significa que el frontend pueda operar esas funciones.

## Ejecutar la API

### Con Docker Compose

Se necesita Docker con Docker Compose. Copia el archivo de ejemplo, establece los secretos y levanta los servicios:

```bash
cp .env.example .env
```

Completa al menos `DB_PASSWORD` y `JWT_SECRET` en `.env`. `JWT_SECRET` debe tener al menos 32 caracteres. Para crear el administrador inicial, define también `ADMIN_PASSWORD`.

```bash
docker compose up -d --build
```

Compose inicia PostgreSQL y espera a que esté saludable antes de iniciar la API. Flyway aplica las migraciones del esquema y los datos iniciales. Si `ADMIN_PASSWORD` no está vacío y el correo configurado todavía no existe, el arranque crea un usuario `ADMIN`; si el usuario ya existe, no lo reemplaza.

Direcciones locales:

- API: `http://localhost:8080`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI: `http://localhost:8080/v3/api-docs`
- Salud: `http://localhost:8080/actuator/health`

### Variables de entorno

| Variable | Uso | Valor predeterminado o comportamiento |
| --- | --- | --- |
| `DB_NAME` | Nombre de la base PostgreSQL en Compose y URL de conexión de la API. | `tienda` |
| `DB_USER` | Usuario PostgreSQL. | `tienda` |
| `DB_PASSWORD` | Contraseña PostgreSQL. | Obligatoria para Compose. |
| `DB_URL` | URL JDBC usada por la API. | `jdbc:postgresql://localhost:5432/tienda`; Compose la establece para usar el servicio `db`. |
| `JWT_SECRET` | Clave para firmar y validar tokens JWT. | Debe configurarse; usa al menos 32 caracteres. |
| `ADMIN_EMAIL` | Correo del administrador inicial. | `admin@tienda.local` |
| `ADMIN_PASSWORD` | Contraseña del administrador inicial. | Vacía: no crea el administrador. |
| `CORS_ORIGINS` | Origen u orígenes de frontend autorizados por CORS. | `http://localhost:5173` |
| `TIENDA_NOMBRE` | Nombre devuelto por el endpoint de configuración. | `TechStore` |

La configuración adicional de tienda está en `src/main/resources/application.yml`: moneda `COP`, IVA `0.19`, envío gratis desde `200000`, stock mínimo de alerta `5`, reserva de stock de `15` minutos y expiración JWT de `120` minutos. De estos valores, el endpoint público de tienda devuelve nombre, moneda, IVA y umbral de envío gratis. El código actual no implementa la operación de compra ni cálculos de carrito, impuestos o envío.

## Conexión desde el frontend

### URL base y formato

En el entorno local la URL base es:

```text
http://localhost:8080
```

Las rutas de la API usan JSON. Para solicitudes con cuerpo, envía `Content-Type: application/json`. Las rutas protegidas reciben el JWT en el encabezado:

```http
Authorization: Bearer <token>
```

La API no usa sesión de navegador para la autenticación; la configuración de seguridad es stateless. Configura `CORS_ORIGINS` para que incluya el origen exacto desde el que sirve el frontend. Por defecto se permite `http://localhost:5173`. La configuración permite los métodos `GET`, `POST`, `PUT`, `PATCH`, `DELETE`, `OPTIONS` y los encabezados `Authorization` y `Content-Type`.

### Ejemplo de solicitud desde JavaScript

El siguiente ejemplo muestra el formato de conexión; `API_BASE` debe apuntar al servidor real del entorno y `token` debe ser el valor recibido en el login o registro.

```js
const API_BASE = "http://localhost:8080";

async function solicitar(ruta, { token, ...opciones } = {}) {
  const headers = { ...opciones.headers };

  if (opciones.body !== undefined) {
    headers["Content-Type"] = "application/json";
  }
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }

  const respuesta = await fetch(`${API_BASE}${ruta}`, {
    ...opciones,
    headers,
  });

  if (respuesta.status === 204) return null;

  const contenido = await respuesta.json().catch(() => null);
  if (!respuesta.ok) {
    throw Object.assign(new Error(contenido?.detail ?? respuesta.statusText), {
      status: respuesta.status,
      contenido,
    });
  }
  return contenido;
}
```

Lectura pública del catálogo:

```js
const productos = await solicitar("/api/catalogo/productos");
const categorias = await solicitar("/api/catalogo/categorias");
const tienda = await solicitar("/api/catalogo/tienda");
```

Login y uso del token en una operación administrativa:

```js
const sesion = await solicitar("/api/auth/login", {
  method: "POST",
  body: JSON.stringify({ email, password }),
});

const productoCreado = await solicitar("/api/admin/productos", {
  method: "POST",
  token: sesion.token,
  body: JSON.stringify({
    sku: "LAP-001",
    nombre: "Portatil 15 pulgadas",
    precio: 2500000,
    categoriaId: 1,
    stock: 10,
  }),
});
```

Pasa `sesion.token` sin modificarlo al parámetro `token`: la función `solicitar` construye `Authorization: Bearer <token>`. No pases el texto completo `Bearer ...` como valor de `token`, porque produciría un encabezado duplicado. Esta API no define ni controla dónde persiste el navegador el token; esa decisión pertenece al frontend. Actualmente no hay endpoints de refresh ni logout ni revocación de tokens: un JWT emitido sigue siendo válido hasta su expiración. El helper contempla `204` y respuestas de error sin cuerpo, como las que puede devolver Spring Security.

## Autenticación y permisos

### Registrar un cliente

`POST /api/auth/registro` es público y responde `201 Created` con un token. El registro siempre asigna el rol `CLIENTE`.

Solicitud:

```json
{
  "nombre": "Nombre del cliente",
  "email": "cliente@example.com",
  "password": "una-clave-de-8-o-mas"
}
```

Validaciones del cuerpo:

- `nombre`: obligatorio, no puede quedar en blanco, máximo 120 caracteres.
- `email`: obligatorio, debe tener formato de correo y máximo 160 caracteres.
- `password`: obligatorio, entre 8 y 72 caracteres.

Antes de guardar, el servicio elimina espacios al principio y al final de nombre y correo, y convierte el correo a minúsculas. Si el correo ya está registrado, responde `409 Conflict` con el detalle `El correo ya esta registrado`.

### Iniciar sesión

`POST /api/auth/login` es público.

Solicitud:

```json
{
  "email": "cliente@example.com",
  "password": "una-clave-de-8-o-mas"
}
```

Ambos campos son obligatorios y no pueden estar en blanco. Un login válido responde `200 OK`. Antes de buscar, el servicio recorta espacios al principio y al final del correo y lo convierte a minúsculas con una normalización independiente de la configuración regional. El repositorio compara correos sin distinguir mayúsculas/minúsculas. Si no coincide el correo y contraseña o la cuenta está inactiva, responde `401 Unauthorized` con el detalle `Credenciales invalidas`.

### Respuesta de registro y login

Ambos endpoints devuelven la misma estructura:

```json
{
  "token": "<JWT emitido por la API>",
  "tipo": "Bearer",
  "expiraEnSegundos": 7200,
  "nombre": "Nombre del usuario",
  "rol": "CLIENTE"
}
```

Los valores entre `<...>` son marcadores ilustrativos, no datos de una cuenta. `expiraEnSegundos` se calcula a partir de `tienda.jwt.expiracion-minutos` (por defecto, 120 minutos). Envía el valor de `token` en cada llamada protegida con el esquema indicado por `tipo`.

### Reglas de acceso

| Rutas | Acceso |
| --- | --- |
| `/api/auth/**` | Público. |
| `GET /api/catalogo/**` | Público. |
| `/api/admin/**` | Requiere token válido con rol `ADMIN`. |
| Cualquier otra ruta | Requiere autenticación; no hay más endpoints de aplicación implementados actualmente. |
| `/actuator/health/**`, `/actuator/info`, `/v3/api-docs/**`, `/swagger-ui/**`, `/swagger-ui.html` | Públicos según la configuración de seguridad. |

La API responde `401 Unauthorized` cuando una ruta protegida requiere autenticación y no se presenta una autenticación válida. Las rutas administrativas exigen `ADMIN`; un usuario registrado mediante el endpoint público recibe `CLIENTE` y no obtiene permisos administrativos. El token contiene el correo como sujeto y los claims `uid` y `rol`.

## Rutas del catálogo público

### Obtener configuración visible de la tienda

`GET /api/catalogo/tienda`

Respuesta:

```json
{
  "nombre": "TechStore",
  "moneda": "COP",
  "iva": 0.19,
  "envioGratisDesde": 200000
}
```

Los valores se leen de la configuración de la aplicación. Esta ruta solo expone esos datos; no calcula totales, impuestos ni costos de envío.

### Listar categorías

`GET /api/catalogo/categorias`

Devuelve un arreglo JSON de categorías activas, ordenadas por nombre. Cada elemento tiene esta forma:

```json
{
  "id": 1,
  "nombre": "Computadores",
  "slug": "computadores",
  "categoriaPadreId": null
}
```

La migración inicial crea las categorías `Computadores`, `Celulares`, `Audio`, `Gaming` y `Accesorios`. El ID de una categoría se debe tomar de la respuesta, no asumirlo en el frontend.

### Listar productos

`GET /api/catalogo/productos`

Parámetros opcionales:

| Parámetro | Uso | Comportamiento |
| --- | --- | --- |
| `categoriaId` | Filtra por ID de categoría. | Si se omite, no filtra por categoría. |
| `q` | Busca texto en nombre o descripción. | La búsqueda usa coincidencia parcial sin distinguir mayúsculas/minúsculas en PostgreSQL (`ILIKE`). |
| `page` | Número de página, comenzando en cero. | Predeterminado `0`; valores negativos se convierten en `0`. |
| `size` | Cantidad solicitada por página. | Predeterminado `12`; se limita al rango de `1` a `50`. |

Ejemplo:

```http
GET /api/catalogo/productos?categoriaId=1&q=portatil&page=0&size=12
```

La respuesta es un arreglo JSON directo, sin objeto envolvente ni total de resultados. El orden es por ID descendente. Solo incluye productos activos cuyo stock disponible sea mayor que cero. El stock disponible devuelto es `stock - stock reservado`.

Cada producto contiene:

```json
{
  "id": 1,
  "sku": "LAP-001",
  "nombre": "Portatil 15 pulgadas",
  "slug": "portatil-15-pulgadas-lap-001",
  "descripcion": null,
  "precio": 2500000,
  "categoriaId": 1,
  "categoria": "Computadores",
  "marcaId": null,
  "marca": null,
  "garantiaMeses": 12,
  "stockDisponible": 10,
  "activo": true
}
```

El ejemplo muestra la estructura; los valores dependen de los datos de la base. El precio se almacena como número entero en COP según el esquema SQL. `descripcion`, `marcaId` y `marca` pueden ser `null`.

### Consultar detalle de producto

`GET /api/catalogo/productos/{id}`

Devuelve un objeto de producto con la estructura anterior. Si el producto no existe, está inactivo o no tiene stock disponible, responde `404 Not Found` con el detalle `Producto no encontrado`. Por tanto, el detalle público aplica la misma condición de visibilidad por stock que el listado.

## Rutas administrativas de productos

Todas las rutas de esta sección requieren `Authorization: Bearer <token>` de un usuario `ADMIN`. Los cuerpos de creación y actualización usan el mismo DTO.

### Crear producto

`POST /api/admin/productos` responde `201 Created` con el producto creado.

```json
{
  "sku": "LAP-001",
  "nombre": "Portatil 15 pulgadas",
  "descripcion": "Descripción del producto",
  "precio": 2500000,
  "categoriaId": 1,
  "marcaId": null,
  "garantiaMeses": 12,
  "stock": 10,
  "stockMinimo": 5
}
```

Campos y restricciones:

| Campo | Requerido | Restricción o valor aplicado |
| --- | --- | --- |
| `sku` | Sí | No puede estar en blanco; máximo 50 caracteres. Debe respetar la unicidad impuesta por la base de datos. |
| `nombre` | Sí | No puede estar en blanco; máximo 200 caracteres. |
| `descripcion` | No | Texto opcional. |
| `precio` | Sí | Número no negativo. La base de datos lo guarda en COP sin decimales. |
| `categoriaId` | Sí | Debe referenciar una categoría existente; la clave foránea la valida en la base de datos. |
| `marcaId` | No | Puede ser `null`; si se informa, debe referenciar una marca existente. |
| `garantiaMeses` | No | Número no negativo; si se omite o es `null`, se usa `12`. |
| `stock` | Sí | Número entero no negativo. |
| `stockMinimo` | No | Número entero no negativo; si se omite o es `null`, se usa `tienda.stock-minimo-alerta` (por defecto `5`). |

La API genera `slug` a partir del nombre y SKU. El producto se crea activo. El registro de inventario se crea junto con el producto.

### Actualizar producto

`PUT /api/admin/productos/{id}` recibe el mismo cuerpo completo que la creación y responde `200 OK` con el producto actualizado. También actualiza stock y stock mínimo con los valores enviados; no es una actualización parcial. Si el producto no existe, responde `404 Not Found` con el detalle `Producto no encontrado`.

### Desactivar producto

`DELETE /api/admin/productos/{id}` responde `204 No Content` si la desactivación se realiza. Es un borrado lógico: el registro se conserva y se cambia `activo` a `false`. Si el ID no existe, responde `404 Not Found` con el detalle `Producto no encontrado`.

## Validaciones y errores

Los errores de validación de cuerpos anotados con `@Valid` responden `400 Bad Request` usando `ProblemDetail`. Incluyen `detail: "Datos invalidos"` y una propiedad `errores` que relaciona los nombres de los campos con el primer mensaje de validación para cada campo. La forma relevante para el frontend es:

```json
{
  "status": 400,
  "detail": "Datos invalidos",
  "errores": {
    "email": "..."
  }
}
```

El texto de validación (`...`) lo genera Jakarta Validation y puede depender de la configuración de mensajes. `ProblemDetail` puede incluir además sus campos estándar.

Errores de negocio y datos conocidos:

| HTTP | Caso | Detalle definido por la API |
| --- | --- | --- |
| `400` | Cuerpo que no pasa validación. | `Datos invalidos`, más el mapa `errores`. |
| `401` | Credenciales de login incorrectas o usuario inactivo. | `Credenciales invalidas`. |
| `401` | Ruta protegida sin autenticación válida. | La configuración establece el estado HTTP; no define un cuerpo propio para este caso. |
| `404` | Producto inexistente, inactivo o sin stock en la consulta pública; o ID inexistente en una operación administrativa. | `Producto no encontrado`. |
| `409` | Correo duplicado al registrar. | `El correo ya esta registrado`. |
| `409` | Restricción de integridad de datos, por ejemplo un duplicado o una referencia inexistente. | `La operacion viola una restriccion de datos (duplicado o referencia inexistente)`. |
| `500` | Error no controlado. | `Error interno del servidor`. |

Los errores gestionados por `GlobalExceptionHandler` usan `ProblemDetail` (formato RFC 7807). No asumas que todas las respuestas de seguridad, en particular `401`, tienen ese mismo cuerpo.

## Resumen de endpoints disponibles

| Método | Ruta | Acceso | Respuesta de éxito |
| --- | --- | --- | --- |
| `POST` | `/api/auth/registro` | Público | `201`, token y datos del usuario. |
| `POST` | `/api/auth/login` | Público | `200`, token y datos del usuario. |
| `GET` | `/api/catalogo/tienda` | Público | `200`, configuración visible de tienda. |
| `GET` | `/api/catalogo/categorias` | Público | `200`, arreglo de categorías activas. |
| `GET` | `/api/catalogo/productos` | Público | `200`, arreglo paginado de productos visibles. |
| `GET` | `/api/catalogo/productos/{id}` | Público | `200`, producto visible. |
| `POST` | `/api/admin/productos` | `ADMIN` | `201`, producto creado. |
| `PUT` | `/api/admin/productos/{id}` | `ADMIN` | `200`, producto actualizado. |
| `DELETE` | `/api/admin/productos/{id}` | `ADMIN` | `204`, sin cuerpo. |

## Funcionalidades que aún no expone la API

No hay endpoints implementados para carrito, direcciones, checkout, creación o consulta de pedidos, cupones, pagos, envíos, imágenes de producto, atributos, reseñas ni administración de categorías o marcas. Tampoco hay endpoint para consultar el perfil o la sesión del usuario autenticado. No conectes pantallas del frontend a estas funciones esperando que el backend actual las atienda.

## Estructura relevante

```text
src/main/java/com/codeconheiner/tienda/
  auth/       Registro, login, JWT y filtro de autenticación
  catalogo/   Lectura pública del catálogo y administración de productos
  common/     Excepciones y respuestas de error
  config/     Configuración de tienda, seguridad y administrador inicial
  usuarios/   Acceso y modelo de usuario
  carrito/    Paquete reservado, sin endpoints implementados
  inventario/ Paquete reservado, sin endpoints implementados
  pedidos/    Paquete reservado, sin endpoints implementados
  pagos/      Paquete reservado, sin endpoints implementados
  envios/     Paquete reservado, sin endpoints implementados
src/main/resources/db/migration/
  V1__esquema_inicial.sql
  V2__datos_semilla.sql
```
