package com.codeconheiner.tienda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Duration;
import java.sql.SQLException;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PostgresIntegrationTest {

    private static final String ADMIN_EMAIL = "integration-admin@tienda.test";
    private static final String ADMIN_PASSWORD = "integration-admin-password";
    private static final String JWT_SECRET = "integration-tests-jwt-secret-at-least-32-bytes";

    @Container
        @SuppressWarnings("resource")
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("tienda_integration")
            .withUsername("integration")
            .withPassword("integration");

    @DynamicPropertySource
    static void propiedadesDePrueba(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.cache.type", () -> "redis");
        registry.add("spring.data.redis.host", () -> "127.0.0.1");
        registry.add("spring.data.redis.port", () -> 1);
        registry.add("spring.data.redis.connect-timeout", () -> "2s");
        registry.add("spring.data.redis.timeout", () -> "2s");
        registry.add("tienda.jwt.secret", () -> JWT_SECRET);
        registry.add("tienda.admin.email", () -> ADMIN_EMAIL);
        registry.add("tienda.admin.password", () -> ADMIN_PASSWORD);
    }

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void registroPersisteHashYPermiteLogin() throws Exception {
        String email = emailUnico();
        String password = "cliente-password-123";
        ResponseEntity<JsonNode> registro = postJson("/api/auth/registro",
                Map.of("nombre", "Cliente Integración", "email", email, "password", password), null);

        assertThat(registro.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(body(registro).path("rol").asText()).isEqualTo("CLIENTE");
        assertThat(body(registro).path("tipo").asText()).isEqualTo("Bearer");

        String hash = jdbc.queryForObject("SELECT password_hash FROM usuarios WHERE email = ?", String.class, email);
        assertThat(hash).isNotEqualTo(password);
        assertThat(passwordEncoder.matches(password, hash)).isTrue();
        assertThat(jdbc.queryForObject("""
                SELECT r.nombre
                FROM usuarios u JOIN roles r ON r.id = u.rol_id
                WHERE u.email = ?
                """, String.class, email)).isEqualTo("CLIENTE");

        ResponseEntity<JsonNode> login = postJson("/api/auth/login",
                Map.of("email", email, "password", password), null);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(body(login).path("token").asText()).isNotBlank();
    }

    @Test
    void authDevuelveErroresEsperadosYNoDuplicaCorreo() throws Exception {
        ResponseEntity<JsonNode> invalido = postJson("/api/auth/registro",
                Map.of("nombre", "", "email", "correo-invalido", "password", "123"), null);
        assertThat(invalido.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(body(invalido).path("detail").asText()).isEqualTo("Los datos enviados no son válidos.");
        assertThat(body(invalido).path("errores").path("email").asText())
                .isEqualTo("El correo no tiene un formato válido");

        ResponseEntity<String> jsonMalFormado = exchangeRaw(HttpMethod.POST, "/api/auth/registro", "{", null);
        assertThat(jsonMalFormado.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(jsonMalFormado.getBody()).contains("El cuerpo de la solicitud no contiene JSON válido.");

        String email = emailUnico();
        Map<String, String> registro = Map.of(
                "nombre", "Cliente Duplicado", "email", email, "password", "cliente-password-123");
        assertThat(postJson("/api/auth/registro", registro, null).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        ResponseEntity<JsonNode> duplicado = postJson("/api/auth/registro", registro, null);
        assertThat(duplicado.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(body(duplicado).path("detail").asText()).isEqualTo("El correo ya está registrado.");

        ResponseEntity<JsonNode> credenciales = postJson("/api/auth/login",
                Map.of("email", email, "password", "clave-incorrecta"), null);
        assertThat(credenciales.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(body(credenciales).path("detail").asText()).isEqualTo("Correo o contraseña incorrectos.");
    }

    @Test
    void permisosYCatalogoFuncionanContraPostgres() throws Exception {
        assertThat(get("/api/catalogo/tienda").getStatusCode()).isEqualTo(HttpStatus.OK);
        ResponseEntity<JsonNode> categorias = get("/api/catalogo/categorias");
        assertThat(categorias.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(body(categorias).isArray()).isTrue();
        assertThat(body(categorias).size()).isEqualTo(5);

        ResponseEntity<JsonNode> sinToken = postJson("/api/admin/productos", Map.of(), null);
        assertThat(sinToken.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(body(sinToken).path("detail").asText())
                .isEqualTo("Autenticación requerida: inicia sesión o proporciona un token válido.");

        ResponseEntity<JsonNode> tokenInvalido = postJson("/api/admin/productos", Map.of(), "token-invalido");
        assertThat(tokenInvalido.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        String clienteEmail = emailUnico();
        String clienteToken = registrarYObtenerToken(clienteEmail);
        ResponseEntity<JsonNode> sinRol = postJson("/api/admin/productos", Map.of(), clienteToken);
        assertThat(sinRol.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(body(sinRol).path("detail").asText())
                .isEqualTo("No tienes permisos para realizar esta operación.");

        ResponseEntity<JsonNode> productoInvalido = postJson("/api/admin/productos",
                Map.of("sku", "", "nombre", "Teclado", "precio", -1, "categoriaId", 1, "stock", 1),
                tokenAdmin());
        assertThat(productoInvalido.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(body(productoInvalido).path("errores").path("precio").asText())
                .isEqualTo("El precio debe ser mayor o igual a 0");
    }

    @Test
    void redisInaccesibleNoAfectaCatalogoNiHealth() throws Exception {
        String adminToken = tokenAdmin();
        String sufijo = UUID.randomUUID().toString().substring(0, 8);
        String sku = "IT-REDIS-" + sufijo;
        ResponseEntity<JsonNode> creado = postJson("/api/admin/productos",
                producto(sku, "Producto Redis " + sufijo, 1000, 3), adminToken);
        assertThat(creado.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        long productoId = body(creado).path("id").asLong();

        long inicio = System.nanoTime();
        ResponseEntity<JsonNode> detalle = get("/api/catalogo/productos/" + productoId);
        long duracionMs = Duration.ofNanos(System.nanoTime() - inicio).toMillis();

        assertThat(detalle.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(body(detalle).path("id").asLong()).isEqualTo(productoId);
        assertThat(duracionMs).isLessThan(1000);

        ResponseEntity<JsonNode> health = get("/actuator/health");
        assertThat(health.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(body(health).path("status").asText()).isEqualTo("UP");
        assertThat(body(health).path("components").has("redis")).isFalse();
    }

    @Test
    void adminPuedeCrearActualizarYDesactivarProductoEnPostgres() throws Exception {
        String adminToken = tokenAdmin();
        String sufijo = UUID.randomUUID().toString().substring(0, 8);
        String sku = "IT-" + sufijo;
        String nombre = "ProductoIntegracion" + sufijo;
        Map<String, Object> producto = producto(sku, nombre, 1000, 3);

        ResponseEntity<JsonNode> creado = postJson("/api/admin/productos", producto, adminToken);
        assertThat(creado.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        long id = body(creado).path("id").asLong();
        assertThat(body(creado).path("activo").asBoolean()).isTrue();
        assertThat(body(creado).path("stockDisponible").asInt()).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT stock FROM inventario WHERE producto_id = ?", Integer.class, id))
                .isEqualTo(3);

        ResponseEntity<JsonNode> listado = get("/api/catalogo/productos?q=" + nombre);
        assertThat(listado.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(contieneSku(body(listado), sku)).isTrue();

        ResponseEntity<JsonNode> duplicado = postJson("/api/admin/productos", producto, adminToken);
        assertThat(duplicado.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(body(duplicado).path("detail").asText())
                .isEqualTo("La operación viola una restricción de datos (duplicado o referencia inexistente).");

        Map<String, Object> actualizacion = producto(sku, nombre + " actualizado", 1200, 4);
        ResponseEntity<JsonNode> actualizado = exchangeJson(HttpMethod.PUT, "/api/admin/productos/" + id,
                actualizacion, adminToken);
        assertThat(actualizado.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(body(actualizado).path("stockDisponible").asInt()).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT stock FROM inventario WHERE producto_id = ?", Integer.class, id))
                .isEqualTo(4);

        ResponseEntity<JsonNode> desactivado = exchangeJson(HttpMethod.DELETE, "/api/admin/productos/" + id,
                null, adminToken);
        assertThat(desactivado.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(jdbc.queryForObject("SELECT activo FROM productos WHERE id = ?", Boolean.class, id)).isFalse();
        assertThat(get("/api/catalogo/productos/" + id).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void triggerPostgresRechazaCarritoQueSuperaStockConP0001() throws Exception {
        String email = emailUnico();
        registrarYObtenerToken(email);
        Long usuarioId = jdbc.queryForObject("SELECT id FROM usuarios WHERE email = ?", Long.class, email);
        String adminToken = tokenAdmin();
        String sku = "STOCK-" + UUID.randomUUID().toString().substring(0, 8);
        ResponseEntity<JsonNode> creado = postJson("/api/admin/productos", producto(sku, "Producto stock", 1000, 1),
                adminToken);
        assertThat(creado.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        long productoId = body(creado).path("id").asLong();
        Long carritoId = jdbc.queryForObject("INSERT INTO carritos (usuario_id) VALUES (?) RETURNING id",
                Long.class, usuarioId);

        DataAccessException error = catchThrowableOfType(
                () -> jdbc.update("INSERT INTO carrito_items (carrito_id, producto_id, cantidad) VALUES (?, ?, ?)",
                        carritoId, productoId, 2),
                DataAccessException.class);

        assertThat(error).isNotNull();
        Throwable causa = org.springframework.core.NestedExceptionUtils.getMostSpecificCause(Objects.requireNonNull(error));
        assertThat(causa).isInstanceOf(SQLException.class);
        assertThat(((SQLException) causa).getSQLState()).isEqualTo("P0001");
        assertThat(jdbc.queryForObject("SELECT stock_reservado FROM inventario WHERE producto_id = ?",
                Integer.class, productoId)).isZero();
    }

    private String registrarYObtenerToken(String email) throws Exception {
        ResponseEntity<JsonNode> response = postJson("/api/auth/registro",
                Map.of("nombre", "Cliente Integración", "email", email, "password", "cliente-password-123"), null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return body(response).path("token").asText();
    }

    private String tokenAdmin() throws Exception {
        ResponseEntity<JsonNode> response = postJson("/api/auth/login",
                Map.of("email", ADMIN_EMAIL, "password", ADMIN_PASSWORD), null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return body(response).path("token").asText();
    }

    private ResponseEntity<JsonNode> get(String path) {
        return http.exchange(url(path), HttpMethod.GET, HttpEntity.EMPTY, JsonNode.class);
    }

    private ResponseEntity<JsonNode> postJson(String path, Object body, String token) throws Exception {
        return exchangeJson(HttpMethod.POST, path, body, token);
    }

    private ResponseEntity<JsonNode> exchangeJson(HttpMethod method, String path, Object body, String token)
            throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        String json = body == null ? null : objectMapper.writeValueAsString(body);
        return http.exchange(url(path), method, new HttpEntity<>(json, headers), JsonNode.class);
    }

    private ResponseEntity<String> exchangeRaw(HttpMethod method, String path, String body, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return http.exchange(url(path), method, new HttpEntity<>(body, headers), String.class);
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private static String emailUnico() {
        return "it-" + UUID.randomUUID() + "@tienda.test";
    }

    private static Map<String, Object> producto(String sku, String nombre, int precio, int stock) {
        return Map.of("sku", sku, "nombre", nombre, "precio", BigDecimal.valueOf(precio),
                "categoriaId", 1, "stock", stock);
    }

    private static boolean contieneSku(JsonNode productos, String sku) {
        for (JsonNode producto : productos) {
            if (sku.equals(producto.path("sku").asText())) {
                return true;
            }
        }
        return false;
    }

        private static JsonNode body(ResponseEntity<JsonNode> response) {
                return Objects.requireNonNull(response.getBody(), "Expected a JSON response body");
        }
}