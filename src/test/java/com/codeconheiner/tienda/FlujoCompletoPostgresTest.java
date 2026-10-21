package com.codeconheiner.tienda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Flujo completo por HTTP contra PostgreSQL real: registro, catálogo admin, carrito,
 * direcciones, checkout, pago aprobado, transiciones de estado, reseñas y sesiones.
 * Validación clave: los RAISE EXCEPTION (SQLSTATE P0001) de V3 responden HTTP 422
 * con el mensaje en español del trigger.
 */
@SpringBootTest
@AutoConfigureMockMvc
class FlujoCompletoPostgresTest {

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:postgresql://localhost:5432/tienda_db");
        registry.add("spring.datasource.username", () -> "tienda_user");
        registry.add("spring.datasource.password", () -> "tienda_pass");
        registry.add("tienda.jwt.secret", () -> "flujo-completo-tests-jwt-secret-at-least-32-bytes");
        registry.add("tienda.admin.email", () -> "admin@tienda.local");
        registry.add("tienda.admin.password", () -> "");
        // los webhooks de prueba firman con este secreto (SHA-256(payload + secreto))
        registry.add("tienda.pagos.wompi.integrity-secret", () -> "e2e-wompi-secreto");
        registry.add("tienda.pagos.epayco.private-key", () -> "e2e-epayco-llave");
        // holgura para que el rate limit por IP no interrumpa la secuencia del E2E
        registry.add("tienda.rate-limit.peticiones-por-minuto", () -> "5000");
        registry.add("tienda.rate-limit.peticiones-auth-por-minuto", () -> "500");
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private PasswordEncoder encoder;

    private String sufijo;
    private String sku;
    private String emailAdmin;
    private String emailCliente;

    @BeforeEach
    void preparar() {
        sufijo = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        sku = "IT-" + sufijo;
        emailAdmin = "it-e2e-admin-" + sufijo + "@tienda.test";
        emailCliente = "it-e2e-" + sufijo + "@tienda.test";
        limpiar("it-e2e-%");
    }

    @AfterEach
    void limpiarFixtures() {
        limpiar("it-e2e-%");
    }

    private void limpiar(String patronEmail) {
        String pedidosDePrueba = """
                SELECT p.id FROM pedidos p JOIN usuarios u ON u.id = p.usuario_id
                WHERE u.email LIKE '""" + patronEmail + "'";
        jdbc.update("DELETE FROM envios WHERE pedido_id IN (" + pedidosDePrueba + ")");
        jdbc.update("DELETE FROM pagos WHERE pedido_id IN (" + pedidosDePrueba + ")");
        jdbc.update("DELETE FROM pedidos WHERE id IN (" + pedidosDePrueba + ")");
        jdbc.update("DELETE FROM carritos WHERE usuario_id IN "
                + "(SELECT id FROM usuarios WHERE email LIKE '" + patronEmail + "')");
        // los usuarios borran en cascada direcciones, resenas y refresh_tokens
        jdbc.update("DELETE FROM usuarios WHERE email LIKE '" + patronEmail + "'");
        jdbc.update("DELETE FROM productos WHERE sku LIKE 'IT-%' AND nombre LIKE 'Producto IT %'");
    }

    @Test
    void flujoCompletoValidaReglasDeNegocioEn422() throws Exception {
        // ---------- usuarios ----------
        try {
            jdbc.update("""
                    INSERT INTO usuarios (nombre, email, password_hash, rol_id, activo, email_verificado, estado)
                    VALUES ('Admin Integracion', ?, ?, (SELECT id FROM roles WHERE nombre = 'ADMIN'), TRUE, TRUE, 'ACTIVO')
                    """, emailAdmin, encoder.encode("admin-clave-segura"));
        } catch (Exception ignored) {
            jdbc.update("UPDATE usuarios SET activo = TRUE, email_verificado = TRUE, estado = 'ACTIVO', password_hash = ? WHERE LOWER(email) = LOWER(?)",
                    encoder.encode("admin-clave-segura"), emailAdmin);
        }
        String adminToken = token(emailAdmin, "admin-clave-segura");

        mvc.perform(post("/api/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"primerNombre":"Cliente","primerApellido":"IT","nombre":"Cliente","telefono":"3001234567","email":"%s","emailConfirmacion":"%s","password":"clave-segura-1","captchaToken":"test","aceptaTerminos":true,"aceptaTratamientoDatos":true}
                                """.formatted(emailCliente, emailCliente)))
                .andExpect(status().isAccepted());
        jdbc.update("UPDATE usuarios SET activo = TRUE, email_verificado = TRUE, estado = 'ACTIVO' WHERE LOWER(email) = LOWER(?)",
                emailCliente);
        String clienteToken = token(emailCliente, "clave-segura-1");
        String refreshToken = json(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + emailCliente + "\",\"password\":\"clave-segura-1\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).path("refreshToken").asText();

        // el refresh token rota y el logout invalida la sesión
        JsonNode refresco = json(mvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(refresco.path("refreshToken").asText()).isNotEqualTo(refreshToken);
        String refreshTokenRotado = refresco.path("refreshToken").asText();

        mvc.perform(post("/api/auth/logout").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshTokenRotado + "\"}"))
                .andExpect(status().isNoContent());
        // El logout revoca la familia completa: el token rotado deja de servir.
        mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshTokenRotado + "\"}"))
                .andExpect(status().isUnauthorized());

        // ---------- producto (admin) ----------
        JsonNode producto = json(mvc.perform(post("/api/admin/productos")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku":"%s","nombre":"Producto IT %s","precio":1000,"categoriaId":1,
                                 "stock":10,"stockMinimo":8}
                                """.formatted(sku, sufijo)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        long productoId = producto.path("id").asLong();

        // el detalle público muestra imágenes/atributos (aunque aún no haya ninguno)
        mvc.perform(get("/api/catalogo/productos/" + productoId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stockDisponible").value(10))
                .andExpect(jsonPath("$.imagenes").isArray())
                .andExpect(jsonPath("$.atributos").isMap());

        // admin agrega atributo e imagen
        mvc.perform(post("/api/admin/productos/" + productoId + "/atributos")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clave\":\"RAM\",\"valor\":\"16GB\"}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/admin/productos/" + productoId + "/imagenes")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://cdn.example.com/" + sufijo + ".png\",\"esPrincipal\":true}"))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/catalogo/productos/" + productoId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.atributos.RAM").value("16GB"))
                .andExpect(jsonPath("$.imagenes.length()").value(1));

        // ---------- carrito, dirección y checkout ----------
        mvc.perform(post("/api/carrito/items").header("Authorization", "Bearer " + clienteToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productoId\":" + productoId + ",\"cantidad\":2}"))
                .andExpect(status().isCreated());

        JsonNode direccion = json(mvc.perform(post("/api/direcciones")
                        .header("Authorization", "Bearer " + clienteToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"destinatario":"Cliente Uno","telefono":"3001112222","departamento":"Bogota",
                                 "ciudad":"Bogota","direccion":"Carrera 7 #1-2","esPrincipal":true}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());

        JsonNode pedido = json(mvc.perform(post("/api/pedidos")
                        .header("Authorization", "Bearer " + clienteToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"direccionId\":" + direccion.path("id").asLong() + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                .andReturn().getResponse().getContentAsString());
        long pedidoId = pedido.path("id").asLong();
        assertThat(pedido.path("numero").asText()).startsWith("PED-");
        assertThat(pedido.path("total").decimalValue()).isEqualByComparingTo("12380");

        // el carrito quedó vacío y el stock reservado subió
        mvc.perform(get("/api/carrito").header("Authorization", "Bearer " + clienteToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());
        assertThat(jdbc.queryForObject("SELECT stock_reservado FROM inventario WHERE producto_id = ?",
                Integer.class, productoId)).isEqualTo(2);

        // ---------- reseña ANTES de recibir: el trigger responde 422 ----------
        mvc.perform(post("/api/resenas").header("Authorization", "Bearer " + clienteToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productoId\":" + productoId + ",\"calificacion\":5}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail")
                        .value("Solo puedes resenar productos que compraste y ya recibiste"));

        // ---------- pago aprobado ----------
        mvc.perform(post("/api/pagos").header("Authorization", "Bearer " + clienteToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"pedidoId":%d,"pasarela":"wompi","referencia":"REF-%s","estado":"APROBADO"}
                                """.formatted(pedidoId, sufijo)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("APROBADO"));

        mvc.perform(get("/api/pedidos/" + pedidoId).header("Authorization", "Bearer " + clienteToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("PAGADO"));
        // el stock descontado (2 de 10) y sin reserva
        assertThat(jdbc.queryForObject(
                "SELECT stock FROM inventario WHERE producto_id = ?", Integer.class, productoId))
                .isEqualTo(8);
        assertThat(jdbc.queryForObject(
                "SELECT stock_reservado FROM inventario WHERE producto_id = ?", Integer.class, productoId))
                .isZero();

        // ---------- transiciones de estado (admin) ----------
        String[] cadena = {"EN_PREPARACION", "ENVIADO", "ENTREGADO"};
        for (String estado : cadena) {
            mvc.perform(put("/api/admin/pedidos/" + pedidoId + "/estado")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"estado\":\"" + estado + "\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.estado").value(estado));
        }

        // transición inválida: el trigger responde 422 con el mensaje del trigger
        mvc.perform(put("/api/admin/pedidos/" + pedidoId + "/estado")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"PAGADO\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail")
                        .value("Transicion de estado no permitida: ENTREGADO -> PAGADO"));

        // ---------- reseña con pedido ENTREGADO ----------
        mvc.perform(post("/api/resenas").header("Authorization", "Bearer " + clienteToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productoId\":" + productoId + ",\"calificacion\":5,"
                                + "\"comentario\":\"Todo bien\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.calificacion").value(5));

        // lectura pública de reseñas
        mvc.perform(get("/api/resenas/producto/" + productoId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].comentario").value("Todo bien"));

        // stock bajo visible para el admin (8 disponibles <= mínimo 8)
        String bajos = mvc.perform(get("/api/admin/inventario/stock-bajo")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        boolean enUmbral = false;
        for (JsonNode fila : mapper.readTree(bajos)) {
            enUmbral |= fila.path("productoId").asLong() == productoId;
        }
        assertThat(enUmbral).isTrue();

        Long adminIdCheck = jdbc.queryForObject("SELECT id FROM usuarios WHERE LOWER(email) = LOWER(?)", Long.class, emailAdmin);
        assertThat(adminIdCheck).isPositive();
    }

    /**
     * Pasarela, webhooks y facturas de punta a punta: sesión de checkout simulada con
     * idempotencia saliente, webhook firmado que marca el pedido como PAGADO (trigger de
     * V3), deduplicación de notificaciones y emisión idempotente de la factura.
     */
    @Test
    void pasarelaWebhookYFacturaFuncionanContraPostgres() throws Exception {
        try {
            jdbc.update("""
                    INSERT INTO usuarios (nombre, email, password_hash, rol_id, activo, email_verificado, estado)
                    VALUES ('Admin Integracion', ?, ?, (SELECT id FROM roles WHERE nombre = 'ADMIN'), TRUE, TRUE, 'ACTIVO')
                    """, emailAdmin, encoder.encode("admin-clave-segura"));
        } catch (Exception ignored) {
            jdbc.update("UPDATE usuarios SET activo = TRUE, email_verificado = TRUE, estado = 'ACTIVO', password_hash = ? WHERE LOWER(email) = LOWER(?)",
                    encoder.encode("admin-clave-segura"), emailAdmin);
        }
        String adminToken = token(emailAdmin, "admin-clave-segura");

        mvc.perform(post("/api/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"primerNombre":"Cliente","primerApellido":"IT","nombre":"Cliente","telefono":"3001234567","email":"%s","emailConfirmacion":"%s","password":"clave-segura-1","captchaToken":"test","aceptaTerminos":true,"aceptaTratamientoDatos":true}
                                """.formatted(emailCliente, emailCliente)))
                .andExpect(status().isAccepted());
        jdbc.update("UPDATE usuarios SET activo = TRUE, email_verificado = TRUE, estado = 'ACTIVO' WHERE LOWER(email) = LOWER(?)",
                emailCliente);
        String clienteToken = token(emailCliente, "clave-segura-1");

        JsonNode producto = json(mvc.perform(post("/api/admin/productos")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku":"%s","nombre":"Producto IT %s","precio":1000,"categoriaId":1,
                                 "stock":10,"stockMinimo":8}
                                """.formatted(sku, sufijo)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        long productoId = producto.path("id").asLong();

        mvc.perform(post("/api/carrito/items").header("Authorization", "Bearer " + clienteToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productoId\":" + productoId + ",\"cantidad\":2}"))
                .andExpect(status().isCreated());
        JsonNode direccion = json(mvc.perform(post("/api/direcciones")
                        .header("Authorization", "Bearer " + clienteToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"destinatario":"Cliente Uno","telefono":"3001112222","departamento":"Bogota",
                                 "ciudad":"Bogota","direccion":"Carrera 7 #1-2","esPrincipal":true}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        JsonNode pedido = json(mvc.perform(post("/api/pedidos")
                        .header("Authorization", "Bearer " + clienteToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"direccionId\":" + direccion.path("id").asLong() + "}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        long pedidoId = pedido.path("id").asLong();
        String numeroPedido = pedido.path("numero").asText();

        // ---------- sesión en la pasarela (modo simulado) con idempotencia saliente ----------
        String clave = UUID.randomUUID().toString();
        String cuerpoSesion = """
                {"pedidoId":%d,"pasarela":"WOMPI","claveIdempotencia":"%s"}
                """.formatted(pedidoId, clave);

        JsonNode sesion = json(mvc.perform(post("/api/pagos/sesion")
                        .header("Authorization", "Bearer " + clienteToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoSesion))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.pasarela").value("WOMPI"))
                .andReturn().getResponse().getContentAsString());
        String referencia = sesion.path("referencia").asText();
        assertThat(referencia).startsWith(numeroPedido + "-");
        assertThat(sesion.path("sesionId").asText()).isEqualTo("SIM-" + referencia);

        // el reintento con la misma clave reutiliza el intento (no duplica el cobro)
        JsonNode reintento = json(mvc.perform(post("/api/pagos/sesion")
                        .header("Authorization", "Bearer " + clienteToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoSesion))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        assertThat(reintento.path("pagoId").asLong()).isEqualTo(sesion.path("pagoId").asLong());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pagos WHERE pedido_id = ?",
                Long.class, pedidoId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT idempotency_key FROM pagos WHERE pedido_id = ?",
                String.class, pedidoId)).isEqualTo(clave);
        assertThat(jdbc.queryForObject("SELECT sesion_id FROM pagos WHERE pedido_id = ?",
                String.class, pedidoId)).isEqualTo("SIM-" + referencia);

        // ---------- webhook de Wompi firmado: marca el pedido como PAGADO ----------
        String cuerpoWebhook = """
                {"event":"transaction.updated","data":{"id":"tx-e2e-%s","status":"APPROVED",
                 "reference":"%s","amount_in_cents":1238000}}
                """.formatted(sufijo, referencia);
        String checksum = com.codeconheiner.tienda.common.Hashes.sha256Hex(cuerpoWebhook + "e2e-wompi-secreto");

        mvc.perform(post("/api/webhooks/wompi")
                        .header("X-Event-Checksum", checksum)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoWebhook))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("procesado"));

        assertThat(jdbc.queryForObject("SELECT estado FROM pedidos WHERE id = ?",
                String.class, pedidoId)).isEqualTo("PAGADO");
        assertThat(jdbc.queryForObject("SELECT stock FROM inventario WHERE producto_id = ?",
                Integer.class, productoId)).isEqualTo(8);
        assertThat(jdbc.queryForObject("SELECT stock_reservado FROM inventario WHERE producto_id = ?",
                Integer.class, productoId)).isZero();

        // la misma notificación reenviada se ignora
        mvc.perform(post("/api/webhooks/wompi")
                        .header("X-Event-Checksum", checksum)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoWebhook))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("duplicado"));

        // una firma inválida no toca nada
        mvc.perform(post("/api/webhooks/wompi")
                        .header("X-Event-Checksum", "firma-falsa")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoWebhook))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Firma del webhook inválida"));

        // ---------- factura idempotente ----------
        JsonNode factura = json(mvc.perform(get("/api/pedidos/" + pedidoId + "/factura")
                        .header("Authorization", "Bearer " + clienteToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        String numeroFactura = factura.path("numero").asText();
        assertThat(numeroFactura).matches("FAC-\\d{4}-\\d{6}");
        assertThat(factura.path("total").decimalValue()).isEqualByComparingTo("12380");

        JsonNode facturaRepetida = json(mvc.perform(get("/api/pedidos/" + pedidoId + "/factura")
                        .header("Authorization", "Bearer " + clienteToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(facturaRepetida.path("numero").asText()).isEqualTo(numeroFactura);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM facturas WHERE pedido_id = ?",
                Long.class, pedidoId)).isEqualTo(1);

        // ---------- reportes del admin ----------
        mvc.perform(get("/api/admin/reportes/ventas")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("desde", java.time.OffsetDateTime.now().minusDays(1).toString())
                        .param("hasta", java.time.OffsetDateTime.now().plusDays(1).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pedidos").value(1))
                .andExpect(jsonPath("$.pedidosPagados").value(1))
                .andExpect(jsonPath("$.porEstado.PAGADO").value(1));

        mvc.perform(get("/api/admin/reportes/inventario")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unidades").value(8))
                .andExpect(jsonPath("$.bajoUmbral").isArray());
    }

    private String token(String email, String password) throws Exception {
        return json(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).path("token").asText();
    }

    private static JsonNode json(String cuerpo) throws Exception {
        return new ObjectMapper().readTree(cuerpo);
    }
}