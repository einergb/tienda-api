package com.codeconheiner.tienda;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codeconheiner.tienda.auth.AuthService;
import com.codeconheiner.tienda.auth.JwtService;
import com.codeconheiner.tienda.auth.RefreshTokenRepository;
import com.codeconheiner.tienda.catalogo.CategoriaRepository;
import com.codeconheiner.tienda.catalogo.CategoriaResponse;
import com.codeconheiner.tienda.catalogo.ProductoRepository;
import com.codeconheiner.tienda.catalogo.ProductoRequest;
import com.codeconheiner.tienda.catalogo.ProductoResponse;
import com.codeconheiner.tienda.catalogo.ProductoService;
import com.codeconheiner.tienda.common.ApiException;
import com.codeconheiner.tienda.config.SecurityConfig;
import com.codeconheiner.tienda.config.TiendaProperties;
import com.codeconheiner.tienda.cupones.CuponRepository;
import com.codeconheiner.tienda.cupones.CuponService;
import com.codeconheiner.tienda.cupones.dto.CuponRequest;
import com.codeconheiner.tienda.cupones.dto.CuponResponse;
import com.codeconheiner.tienda.facturas.FacturaRepository;
import com.codeconheiner.tienda.facturas.FacturaService;
import com.codeconheiner.tienda.direcciones.DireccionRepository;
import com.codeconheiner.tienda.direcciones.DireccionService;
import com.codeconheiner.tienda.direcciones.dto.DireccionRequest;
import com.codeconheiner.tienda.direcciones.dto.DireccionResponse;
import com.codeconheiner.tienda.resenas.ResenaRepository;
import com.codeconheiner.tienda.resenas.ResenaService;
import com.codeconheiner.tienda.usuarios.Usuario;
import com.codeconheiner.tienda.usuarios.UsuarioRepository;
import com.codeconheiner.tienda.carrito.CarritoRepository;
import com.codeconheiner.tienda.carrito.CarritoService;
import com.codeconheiner.tienda.inventario.InventarioRepository;
import com.codeconheiner.tienda.inventario.InventarioService;
import com.codeconheiner.tienda.pagos.PagoRepository;
import com.codeconheiner.tienda.pagos.PagoService;
import com.codeconheiner.tienda.pagos.pasarela.EpaycoFirmaWebhook;
import com.codeconheiner.tienda.pagos.pasarela.EpaycoPasarela;
import com.codeconheiner.tienda.pagos.pasarela.FirmaWebhookRegistry;
import com.codeconheiner.tienda.pagos.pasarela.PasarelaRegistry;
import com.codeconheiner.tienda.pagos.pasarela.WompiFirmaWebhook;
import com.codeconheiner.tienda.pagos.pasarela.WompiPasarela;
import com.codeconheiner.tienda.pedidos.CalculadoraTotales;
import com.codeconheiner.tienda.pedidos.PedidoRepository;
import com.codeconheiner.tienda.pedidos.PedidoService;
import com.codeconheiner.tienda.reportes.ReporteRepository;
import com.codeconheiner.tienda.reportes.ReporteService;
import com.codeconheiner.tienda.webhooks.EpaycoEventoParser;
import com.codeconheiner.tienda.webhooks.WebhookEventosRepository;
import com.codeconheiner.tienda.webhooks.WebhookPagoService;
import com.codeconheiner.tienda.webhooks.WompiEventoParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.codeconheiner.tienda.envios.EnvioRepository;
import com.codeconheiner.tienda.envios.EnvioService;
import com.codeconheiner.tienda.usuarios.UsuarioService;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

@WebMvcTest
@Import({SecurityConfig.class, ApiEndpointsTest.TestBeans.class})
class ApiEndpointsTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private InMemoryUsuarioRepository usuarios;

    @Autowired
    private InMemoryProductoService productos;

    @BeforeEach
    void limpiarEstado() {
        usuarios.limpiar();
        productos.limpiar();
    }

    @Test
    void registroYLoginDevuelvenToken() throws Exception {
        String registro = """
                {"primerNombre":"Ana","primerApellido":"Perez","nombre":"Ana Perez","telefono":"3001234567","email":"ana@example.com","emailConfirmacion":"ana@example.com","password":"clave-segura","captchaToken":"test","aceptaTerminos":true,"aceptaTratamientoDatos":true}
                """;

        mvc.perform(post("/api/auth/registro").contentType("application/json").content(registro))
                .andExpect(status().isAccepted());
    }

    @Test
    void refreshRotationTokenYLogoutLoInvalida() throws Exception {
        String registro = """
                {"primerNombre":"Ana","primerApellido":"Perez","nombre":"Ana Perez","telefono":"3001234567","email":"ana@example.com","emailConfirmacion":"ana@example.com","password":"clave-segura","captchaToken":"test","aceptaTerminos":true,"aceptaTratamientoDatos":true}
                """;
        mvc.perform(post("/api/auth/registro").contentType("application/json").content(registro))
                .andExpect(status().isAccepted());
        var u = usuarios.buscarPorEmail("ana@example.com").orElseThrow();
        usuarios.activarYVerificar(u.id());
        String login = mvc.perform(post("/api/auth/login").contentType("application/json")
                        .content("{\"email\":\"ana@example.com\",\"password\":\"clave-segura\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String refreshToken = extraer(login, "refreshToken");

        // el refresh token emite un par nuevo
        String refresco = mvc.perform(post("/api/auth/refresh").contentType("application/json")
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String refreshTokenRotado = extraer(refresco, "refreshToken");

        // el token original ya fue rotado: ya no sirve
        mvc.perform(post("/api/auth/refresh").contentType("application/json")
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Sesión inválida o expirada."));

        // logout invalida el token vigente
        mvc.perform(post("/api/auth/logout").contentType("application/json")
                        .content("{\"refreshToken\":\"" + refreshTokenRotado + "\"}"))
                .andExpect(status().isNoContent());

        mvc.perform(post("/api/auth/refresh").contentType("application/json")
                        .content("{\"refreshToken\":\"" + refreshTokenRotado + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void cambioDePasswordValidaLaActualYCierraSesiones() throws Exception {
        String registro = """
                {"primerNombre":"Ana","primerApellido":"Perez","nombre":"Ana Perez","telefono":"3001234567","email":"ana@example.com","emailConfirmacion":"ana@example.com","password":"clave-segura","captchaToken":"test","aceptaTerminos":true,"aceptaTratamientoDatos":true}
                """;
        mvc.perform(post("/api/auth/registro").contentType("application/json").content(registro))
                .andExpect(status().isAccepted());
        var u = usuarios.buscarPorEmail("ana@example.com").orElseThrow();
        usuarios.activarYVerificar(u.id());
        String login = mvc.perform(post("/api/auth/login").contentType("application/json")
                        .content("{\"email\":\"ana@example.com\",\"password\":\"clave-segura\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = extraer(login, "token");
        String refreshToken = extraer(login, "refreshToken");

        mvc.perform(put("/api/auth/password").header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"passwordActual\":\"incorrecta\",\"passwordNuevo\":\"otra-clave-segura\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("La contraseña actual no es correcta."));

        mvc.perform(put("/api/auth/password").header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"passwordActual\":\"clave-segura\",\"passwordNuevo\":\"otra-clave-segura\"}"))
                .andExpect(status().isNoContent());

        // el login con la contraseña nueva funciona y el refresh token anterior quedó revocado
        mvc.perform(post("/api/auth/login").contentType("application/json")
                        .content("{\"email\":\"ana@example.com\",\"password\":\"otra-clave-segura\"}"))
                .andExpect(status().isOk());

        mvc.perform(post("/api/auth/refresh").contentType("application/json")
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    private static String extraer(String json, String campo) throws Exception {
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        return mapper.readTree(json).path(campo).asText();
    }

    @Test
    void registroInvalidoDevuelve400ConErroresPorCampo() throws Exception {
        mvc.perform(post("/api/auth/registro").contentType("application/json")
                        .content("{\"primerNombre\":\"\",\"primerApellido\":\"\",\"nombre\":\"\",\"email\":\"correo-invalido\",\"emailConfirmacion\":\"correo-invalido\",\"password\":\"123\",\"captchaToken\":\"\",\"aceptaTerminos\":false,\"aceptaTratamientoDatos\":false,\"telefono\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Los datos enviados no son válidos."));
    }

    @Test
    void jsonMalFormadoDevuelve400ConProblemDetail() throws Exception {
        mvc.perform(post("/api/auth/registro").contentType("application/json").content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("El cuerpo de la solicitud no contiene JSON válido."));
    }

    @Test
    void credencialesIncorrectasDevuelven401Generico() throws Exception {
        mvc.perform(post("/api/auth/login").contentType("application/json")
                        .content("{\"email\":\"nadie@example.com\",\"password\":\"incorrecta\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Correo o contraseña incorrectos."));
    }

    @Test
    void correoDuplicadoDevuelve409() throws Exception {
        String registro = """
                {"primerNombre":"Ana","primerApellido":"Perez","nombre":"Ana Perez","telefono":"3001234567","email":"ana@example.com","emailConfirmacion":"ana@example.com","password":"clave-segura","captchaToken":"test","aceptaTerminos":true,"aceptaTratamientoDatos":true}
                """;
        mvc.perform(post("/api/auth/registro").contentType("application/json").content(registro))
                .andExpect(status().isAccepted());

        mvc.perform(post("/api/auth/registro").contentType("application/json").content(registro))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("El correo ya está registrado."));
    }

    @Test
    void catalogoPublicoRespondeCorrectamente() throws Exception {
        mvc.perform(get("/api/catalogo/tienda"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Tienda de prueba"));

        mvc.perform(get("/api/catalogo/categorias"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nombre").value("Computadores"));

        mvc.perform(get("/api/catalogo/productos").param("page", "0").param("size", "12"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void productoInexistenteDevuelve404() throws Exception {
        mvc.perform(get("/api/catalogo/productos/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Producto no encontrado"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void subirImagenDeProductoDevuelve201ConImagenUrlYNoExponeLaClave() throws Exception {
        long id = crearProducto("SKU-IMG-1");

        MockMultipartFile archivo = new MockMultipartFile("file", "foto.png", "image/png", pngDePrueba());
        mvc.perform(multipart("/api/admin/productos/" + id + "/imagen").file(archivo))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.imagenUrl").value("http://localhost:9000/bucket/productos/" + id + "/clave-de-prueba.png"))
                .andExpect(jsonPath("$.imagenKey").doesNotExist());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void subirImagenDeProductoInexistenteDevuelve404() throws Exception {
        MockMultipartFile archivo = new MockMultipartFile("file", "foto.png", "image/png", pngDePrueba());
        mvc.perform(multipart("/api/admin/productos/999999/imagen").file(archivo))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Producto no encontrado"));
    }

    @Test
    @WithMockUser(roles = "CLIENTE")
    void subirImagenComoClienteDevuelve403() throws Exception {
        MockMultipartFile archivo = new MockMultipartFile("file", "foto.png", "image/png", pngDePrueba());
        mvc.perform(multipart("/api/admin/productos/1/imagen").file(archivo))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("No tienes permisos para realizar esta operación."));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void eliminarImagenDeProductoDevuelve204() throws Exception {
        long id = crearProducto("SKU-IMG-3");

        mvc.perform(delete("/api/admin/productos/" + id + "/imagen"))
                .andExpect(status().isNoContent());
    }

    private long crearProducto(String sku) throws Exception {
        String producto = """
                {"sku":"%s","nombre":"Producto con imagen","descripcion":"desc","precio":1000,"categoriaId":1,"stock":5}
                """.formatted(sku);
        String cuerpo = mvc.perform(post("/api/admin/productos").contentType("application/json").content(producto))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return new com.fasterxml.jackson.databind.ObjectMapper().readTree(cuerpo).path("id").asLong();
    }

    /** PNG valido de 1x1 generado en memoria. */
    private static byte[] pngDePrueba() {
        try {
            java.awt.image.BufferedImage imagen = new java.awt.image.BufferedImage(1, 1,
                    java.awt.image.BufferedImage.TYPE_INT_RGB);
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(imagen, "png", out);
            return out.toByteArray();
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void rutaAdminSinTokenDevuelve401() throws Exception {
        mvc.perform(post("/api/admin/productos").contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail")
                        .value("Autenticación requerida: inicia sesión o proporciona un token válido."));
    }

    @Test
    @WithMockUser(roles = "CLIENTE")
    void rutaAdminConRolClienteDevuelve403() throws Exception {
        mvc.perform(post("/api/admin/productos").contentType("application/json").content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("No tienes permisos para realizar esta operación."));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void productoInvalidoDevuelve400ConMensajesEnEspanol() throws Exception {
        String producto = """
                {"sku":"","nombre":"","precio":-1,"categoriaId":null,"garantiaMeses":-1,"stock":-1,"stockMinimo":-1}
                """;

        mvc.perform(post("/api/admin/productos").contentType("application/json").content(producto))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Los datos enviados no son válidos."))
                .andExpect(jsonPath("$.errores.sku").value("El SKU es obligatorio"))
                .andExpect(jsonPath("$.errores.nombre").value("El nombre es obligatorio"))
                .andExpect(jsonPath("$.errores.precio").value("El precio debe ser mayor o igual a 0"))
                .andExpect(jsonPath("$.errores.categoriaId").value("La categoría es obligatoria"))
                .andExpect(jsonPath("$.errores.garantiaMeses").value("La garantía no puede ser negativa"))
                .andExpect(jsonPath("$.errores.stock").value("El stock no puede ser negativo"))
                .andExpect(jsonPath("$.errores.stockMinimo").value("El stock mínimo no puede ser negativo"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void reglaDeNegocioSqlDevuelve422ConMensajeEnEspanol() throws Exception {
        String producto = """
                {"sku":"SQL-P0001","nombre":"Teclado","precio":1000,"categoriaId":1,"stock":3}
                """;

        mvc.perform(post("/api/admin/productos").contentType("application/json").content(producto))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("El cupon PRUEBA esta fuera de vigencia"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void errorInternoDevuelve500SinFiltrarDetalle() throws Exception {
        String producto = """
                {"sku":"FORCE-500","nombre":"Teclado","precio":1000,"categoriaId":1,"stock":3}
                """;

        mvc.perform(post("/api/admin/productos").contentType("application/json").content(producto))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("Error interno del servidor"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void cuponPorcentajeSeCreaYValida() throws Exception {
        String cupon = """
                {"codigo":"VERANO10","tipo":"PORCENTAJE","valor":10}
                """;
        mvc.perform(post("/api/admin/cupones").contentType("application/json").content(cupon))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.codigo").value("VERANO10"))
                .andExpect(jsonPath("$.activo").value(true));

        mvc.perform(get("/api/cupones/validar").param("codigo", "verano10").param("subtotal", "5000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigo").value("VERANO10"))
                .andExpect(jsonPath("$.descuento").value(500));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void cuponConTipoInvalidoDevuelve400() throws Exception {
        String cupon = """
                {"codigo":"MALO","tipo":"OTRO","valor":10}
                """;
        mvc.perform(post("/api/admin/cupones").contentType("application/json").content(cupon))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("El tipo de cupón debe ser PORCENTAJE o MONTO"));
    }

    @Test
    @WithMockUser(username = "ana@example.com")
    void direccionesPermitenCrearListarYEliminar() throws Exception {
        // el usuario debe existir en el repositorio para que el servicio pueda resolverlo
        mvc.perform(post("/api/auth/registro").contentType("application/json")
                        .content("""
                                {"primerNombre":"Ana","primerApellido":"Perez","nombre":"Ana Perez","telefono":"3001234567","email":"ana@example.com","emailConfirmacion":"ana@example.com","password":"clave-segura","captchaToken":"test","aceptaTerminos":true,"aceptaTratamientoDatos":true}
                                """))
                .andExpect(status().isAccepted());
        // activar usuario para tests
        var u = usuarios.buscarPorEmail("ana@example.com").orElseThrow();
        usuarios.activarYVerificar(u.id());

        String direccion = """
                {"destinatario":"Ana Perez","telefono":"3001234567","departamento":"Bogota","ciudad":"Bogota",
                 "direccion":"Calle 1 #2-3","esPrincipal":true}
                """;

        mvc.perform(post("/api/direcciones").contentType("application/json").content(direccion))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.esPrincipal").value(true));

        mvc.perform(get("/api/direcciones"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        mvc.perform(delete("/api/direcciones/1"))
                .andExpect(status().isNoContent());

        mvc.perform(delete("/api/direcciones/1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Dirección no encontrada"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminPuedeCrearActualizarYDesactivarProducto() throws Exception {
        String producto = """
                {"sku":"SKU-1","nombre":"Teclado","precio":1000,"categoriaId":1,"stock":3}
                """;

        mvc.perform(post("/api/admin/productos").contentType("application/json").content(producto))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.activo").value(true));

        String actualizacion = """
                {"sku":"SKU-1","nombre":"Teclado actualizado","precio":1200,"categoriaId":1,"stock":4}
                """;
        mvc.perform(put("/api/admin/productos/1").contentType("application/json").content(actualizacion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Teclado actualizado"))
                .andExpect(jsonPath("$.stockDisponible").value(4));

        mvc.perform(delete("/api/admin/productos/1"))
                .andExpect(status().isNoContent());
    }

    @TestConfiguration
    static class TestBeans {

        @Bean
        TiendaProperties tiendaProperties() {
            return new TiendaProperties(
                    "Tienda de prueba", "COP", new BigDecimal("0.19"), BigDecimal.ZERO, 10000, 5, 15,
                    new TiendaProperties.Jwt("endpoint-tests-secret-at-least-32-bytes", 120),
                    new TiendaProperties.Admin("admin@example.com", ""),
                    new TiendaProperties.Cors(List.of("http://localhost:5173")),
                    new TiendaProperties.RateLimit(300, 10, 60),
                    new TiendaProperties.Pagos("simulado",
                            new TiendaProperties.Pagos.Wompi(null, null, null, "wompi-test-secreto", null),
                            new TiendaProperties.Pagos.Epayco(null, null, null, "epayco-test-llave")),
                    new TiendaProperties.Captcha("test-secret", null),
                    new TiendaProperties.Mail("no-reply@test.local", "http://localhost:8080"),
                    new TiendaProperties.Storage("http://localhost:9000", "key", "secret", "bucket", "http://localhost:9000", Boolean.TRUE));
        }

        @Bean
        com.codeconheiner.tienda.catalogo.storage.StorageService storageService(TiendaProperties properties) {
            return new com.codeconheiner.tienda.catalogo.storage.StorageService(
                    (software.amazon.awssdk.services.s3.S3Client) null, properties) {
                @Override
                public String subirArchivo(org.springframework.web.multipart.MultipartFile file, String prefijo) {
                    String key = (prefijo == null ? "productos/sin-asignar" : prefijo.replaceAll("^/+", "").replaceAll("/+$", ""))
                            + "/clave-de-prueba.png";
                    return key;
                }

                @Override
                public String subir(org.springframework.web.multipart.MultipartFile file, Long productoId) {
                    return "productos/" + productoId + "/clave-de-prueba.png";
                }

                @Override
                public String urlPublica(String key) {
                    if (key == null || key.isBlank()) {
                        return null;
                    }
                    String base = "http://localhost:9000";
                    String bucket = "bucket";
                    if (properties.storage() != null && properties.storage().publicBaseUrl() != null) {
                        base = properties.storage().publicBaseUrl();
                    }
                    if (properties.storage() != null && properties.storage().bucket() != null) {
                        bucket = properties.storage().bucket();
                    }
                    boolean incluyeBucket = properties.storage() == null
                            || properties.storage().publicUrlIncluyeBucket() == null
                            || Boolean.TRUE.equals(properties.storage().publicUrlIncluyeBucket());
                    if (base.endsWith("/")) {
                        base = base.substring(0, base.length() - 1);
                    }
                    if (incluyeBucket) {
                        return base + "/" + bucket + "/" + key;
                    }
                    return base + "/" + key;
                }

                @Override
                public void eliminar(String key) {
                }

                @Override
                public java.util.Optional<String> derivarClaveDesdeUrlPublica(String url) {
                    return java.util.Optional.empty();
                }
            };
        }

        @Bean
        com.codeconheiner.tienda.auth.captcha.CaptchaVerifier captchaVerifier() {
            return (token, ip) -> true;
        }

        @Bean
        com.codeconheiner.tienda.auth.verification.VerificationTokenRepository verificationTokenRepository() {
            return new com.codeconheiner.tienda.auth.verification.VerificationTokenRepository((org.springframework.jdbc.core.JdbcTemplate) null) {
                private final java.util.Map<String, Long> tokens = new java.util.HashMap<>();

                @Override
                public void crear(Long usuarioId, String token, java.time.OffsetDateTime expiraEn) {
                    tokens.put(com.codeconheiner.tienda.common.Hashes.sha256Hex(token), usuarioId);
                }

                @Override
                public java.util.Optional<Long> consumir(String token, java.time.OffsetDateTime ahora) {
                    String hash = com.codeconheiner.tienda.common.Hashes.sha256Hex(token);
                    Long id = tokens.remove(hash);
                    return java.util.Optional.ofNullable(id);
                }

                @Override
                public int contarRecientes(Long usuarioId, java.time.OffsetDateTime desde) {
                    return 0;
                }

                @Override
                public void invalidarTodos(Long usuarioId) {
                    tokens.entrySet().removeIf(e -> e.getValue().equals(usuarioId));
                }
            };
        }

        @Bean
        com.codeconheiner.tienda.auth.verification.ConsentimientoRepository consentimientoRepository() {
            return new com.codeconheiner.tienda.auth.verification.ConsentimientoRepository(
                    (org.springframework.jdbc.core.JdbcTemplate) null) {
                @Override
                public void guardar(Long usuarioId, String tipoDocumento, String version,
                        jakarta.servlet.http.HttpServletRequest request) {
                }
            };
        }

        @Bean
        com.codeconheiner.tienda.auth.mail.EmailService emailService(TiendaProperties properties) {
            return new com.codeconheiner.tienda.auth.mail.EmailService(properties);
        }

        @Bean
        JwtService jwtService(TiendaProperties properties) {
            return new JwtService(properties);
        }

        @Bean
        InMemoryUsuarioRepository usuarios() {
            return new InMemoryUsuarioRepository();
        }

        @Bean
        com.codeconheiner.tienda.auth.RefreshTokenStore refreshTokenRepository() {
            return new InMemoryRefreshTokenRepository();
        }

        @Bean
        AuthService authService(InMemoryUsuarioRepository usuarios,
                com.codeconheiner.tienda.auth.RefreshTokenStore refreshTokenRepository,
                JwtService jwtService, TiendaProperties properties,
                org.springframework.security.crypto.password.PasswordEncoder encoder,
                com.codeconheiner.tienda.seguridad.Auditoria auditoria,
                com.codeconheiner.tienda.auth.captcha.CaptchaVerifier captchaVerifier,
                com.codeconheiner.tienda.auth.verification.VerificationTokenRepository verificationTokenRepository,
                com.codeconheiner.tienda.auth.verification.ConsentimientoRepository consentimientoRepository,
                com.codeconheiner.tienda.auth.mail.EmailService emailService) {
            return new AuthService(usuarios, refreshTokenRepository, encoder, jwtService, properties, auditoria,
                    captchaVerifier, verificationTokenRepository, consentimientoRepository, emailService);
        }

        @Bean
        com.codeconheiner.tienda.seguridad.Auditoria auditoria() {
            return new com.codeconheiner.tienda.seguridad.Auditoria(null, null) {
                @Override
                public void registrar(Long usuarioId, String accion, String entidad, String entidadId,
                        java.util.Map<String, ?> detalle) {
                }

                @Override
                public void registrarActual(String accion, String entidad, String entidadId,
                        java.util.Map<String, ?> detalle) {
                }
            };
        }

        @Bean
        InMemoryProductoService productoService(TiendaProperties properties) {
            return new InMemoryProductoService(properties);
        }

        @Bean
        CategoriaRepository categoriaRepository() {
            return new InMemoryCategoriaRepository();
        }

        @Bean
        UsuarioService usuarioService(InMemoryUsuarioRepository usuarios) {
            return new UsuarioService(usuarios);
        }

        @Bean
        CarritoRepository carritoRepository() {
            return new CarritoRepository(null);
        }

        @Bean
        CarritoService carritoService(CarritoRepository carritoRepository, TiendaProperties properties) {
            return new CarritoService(carritoRepository, properties);
        }

        @Bean
        InventarioRepository inventarioRepository() {
            return new InventarioRepository(null);
        }

        @Bean
        InventarioService inventarioService(InventarioRepository inventarioRepository) {
            return new InventarioService(inventarioRepository);
        }

        @Bean
        PedidoRepository pedidoRepository() {
            return new PedidoRepository(null);
        }

        @Bean
        DireccionRepository direccionRepository() {
            return new InMemoryDireccionRepository();
        }

        @Bean
        DireccionService direccionService(DireccionRepository direccionRepository) {
            return new DireccionService(direccionRepository);
        }

        @Bean
        CuponRepository cuponRepository() {
            return new InMemoryCuponRepository();
        }

        @Bean
        CuponService cuponService(CuponRepository cuponRepository) {
            return new CuponService(cuponRepository);
        }

        @Bean
        ResenaRepository resenaRepository() {
            return new InMemoryResenaRepository();
        }

        @Bean
        ResenaService resenaService(ResenaRepository resenaRepository) {
            return new ResenaService(resenaRepository);
        }

        @Bean
        CalculadoraTotales calculadoraTotales(TiendaProperties properties, CuponService cuponService) {
            return new CalculadoraTotales(properties, cuponService);
        }

        @Bean
        PedidoService pedidoService(PedidoRepository pedidoRepository, CarritoRepository carritoRepository,
                DireccionService direccionService, CalculadoraTotales calculadoraTotales,
                com.codeconheiner.tienda.seguridad.Auditoria auditoria) {
            return new PedidoService(pedidoRepository, carritoRepository, direccionService, calculadoraTotales,
                    auditoria);
        }

        @Bean
        PagoRepository pagoRepository() {
            return new PagoRepository(null);
        }

        @Bean
        PasarelaRegistry pasarelaRegistry(TiendaProperties properties) {
            return new PasarelaRegistry(
                    List.of(new WompiPasarela(properties), new EpaycoPasarela(properties)));
        }

        @Bean
        PagoService pagoService(PagoRepository pagoRepository, PedidoRepository pedidoRepository,
                PasarelaRegistry pasarelaRegistry, UsuarioRepository usuarios, TiendaProperties properties) {
            return new PagoService(pagoRepository, pedidoRepository, pasarelaRegistry, usuarios, properties);
        }

        @Bean
        FacturaRepository facturaRepository() {
            return new FacturaRepository(null);
        }

        @Bean
        FacturaService facturaService(FacturaRepository facturaRepository, PedidoRepository pedidoRepository,
                TiendaProperties properties, com.codeconheiner.tienda.seguridad.Auditoria auditoria) {
            return new FacturaService(facturaRepository, pedidoRepository, properties, auditoria);
        }

        @Bean
        ReporteRepository reporteRepository() {
            return new ReporteRepository(null);
        }

        @Bean
        ReporteService reporteService(ReporteRepository reporteRepository,
                InventarioRepository inventarioRepository) {
            return new ReporteService(reporteRepository, inventarioRepository);
        }

        @Bean
        FirmaWebhookRegistry firmaWebhookRegistry(TiendaProperties properties) {
            return new FirmaWebhookRegistry(
                    List.of(new WompiFirmaWebhook(properties), new EpaycoFirmaWebhook(properties)));
        }

        @Bean
        WompiEventoParser wompiEventoParser(ObjectMapper objectMapper) {
            return new WompiEventoParser(objectMapper);
        }

        @Bean
        EpaycoEventoParser epaycoEventoParser(ObjectMapper objectMapper) {
            return new EpaycoEventoParser(objectMapper);
        }

        @Bean
        WebhookEventosRepository webhookEventosRepository() {
            return new WebhookEventosRepository(null);
        }

        @Bean
        WebhookPagoService webhookPagoService(WebhookEventosRepository eventos, PagoRepository pagos,
                com.codeconheiner.tienda.seguridad.Auditoria auditoria) {
            return new WebhookPagoService(eventos, pagos, auditoria);
        }

        @Bean
        EnvioRepository envioRepository() {
            return new EnvioRepository(null);
        }

        @Bean
        EnvioService envioService(EnvioRepository envioRepository) {
            return new EnvioService(envioRepository);
        }
    }

    static class InMemoryUsuarioRepository extends UsuarioRepository {

        private final Map<String, Usuario> usuarios = new HashMap<>();

        InMemoryUsuarioRepository() {
            super((JdbcTemplate) null);
        }

        @Override
        public Optional<Usuario> buscarPorEmail(String email) {
            return Optional.ofNullable(usuarios.get(email.toLowerCase(Locale.ROOT)));
        }

        @Override
        public boolean existePorEmail(String email) {
            return usuarios.containsKey(email.toLowerCase(Locale.ROOT));
        }

        @Override
        public Usuario crear(String nombre, String email, String passwordHash, String rol) {
            Usuario usuario = new Usuario((long) usuarios.size() + 1, nombre, email, passwordHash, rol, false, false,
                    "PENDIENTE");
            usuarios.put(email.toLowerCase(Locale.ROOT), usuario);
            return usuario;
        }

        @Override
        public Usuario crearCompleto(String nombre, String primerNombre, String segundoNombre, String primerApellido,
                String segundoApellido, String email, String telefono, String passwordHash, String rol) {
            Usuario usuario = new Usuario((long) usuarios.size() + 1, nombre, email, passwordHash, rol, false, false,
                    "PENDIENTE");
            usuarios.put(email.toLowerCase(Locale.ROOT), usuario);
            return usuario;
        }

        @Override
        public int activarYVerificar(Long id) {
            for (Map.Entry<String, Usuario> e : usuarios.entrySet()) {
                if (e.getValue().id().equals(id)) {
                    Usuario u = e.getValue();
                    usuarios.put(e.getKey(), new Usuario(u.id(), u.nombre(), u.email(), u.passwordHash(), u.rol(), true,
                            true, "ACTIVO"));
                    return 1;
                }
            }
            return 0;
        }

        @Override
        public Optional<Usuario> buscarPorId(Long id) {
            return usuarios.values().stream().filter(u -> u.id().equals(id)).findFirst();
        }

        @Override
        public void actualizarPassword(Long id, String passwordHash) {
            usuarios.replaceAll((email, u) -> u.id().equals(id)
                    ? new Usuario(u.id(), u.nombre(), u.email(), passwordHash, u.rol(), u.activo(),
                            u.emailVerificado(), u.estado()) : u);
        }

        void limpiar() {
            usuarios.clear();
        }
    }

    static class InMemoryRefreshTokenRepository implements com.codeconheiner.tienda.auth.RefreshTokenStore {

        private final Map<String, com.codeconheiner.tienda.auth.RefreshTokenStore.Sesion> tokens = new HashMap<>();
        private long siguiente = 1;

        InMemoryRefreshTokenRepository() {
        }

        @Override
        public Long crearFamilia(Long usuarioId, String tokenHash, java.time.OffsetDateTime expiraEn) {
            long id = siguiente++;
            tokens.put(tokenHash, new com.codeconheiner.tienda.auth.RefreshTokenStore.Sesion(id, usuarioId, id,
                    expiraEn, false, false, null, null));
            return id;
        }

        @Override
        public Long crearEnFamilia(Long usuarioId, Long sesionId, String tokenHash, java.time.OffsetDateTime expiraEn) {
            long id = siguiente++;
            tokens.put(tokenHash, new com.codeconheiner.tienda.auth.RefreshTokenStore.Sesion(id, usuarioId, sesionId,
                    expiraEn, false, false, null, null));
            return id;
        }

        @Override
        public Optional<com.codeconheiner.tienda.auth.RefreshTokenStore.Sesion> buscarPorHash(String tokenHash) {
            return Optional.ofNullable(tokens.get(tokenHash));
        }

        @Override
        public int marcarUsado(String tokenHash, Long reemplazadoPor) {
            com.codeconheiner.tienda.auth.RefreshTokenStore.Sesion token = tokens.get(tokenHash);
            if (token == null || token.revocado() || token.usado()) {
                return 0;
            }
            tokens.put(tokenHash, new com.codeconheiner.tienda.auth.RefreshTokenStore.Sesion(token.id(),
                    token.usuarioId(), token.sesionId(), token.expiraEn(), token.revocado(), true,
                    java.time.OffsetDateTime.now(), token.revocadoEn()));
            return 1;
        }

        @Override
        public int revocarFamilia(Long usuarioId, Long sesionId) {
            int n = 0;
            for (Map.Entry<String, com.codeconheiner.tienda.auth.RefreshTokenStore.Sesion> e : tokens.entrySet()) {
                com.codeconheiner.tienda.auth.RefreshTokenStore.Sesion t = e.getValue();
                if (t.usuarioId().equals(usuarioId) && t.sesionId().equals(sesionId) && !t.revocado()) {
                    e.setValue(new com.codeconheiner.tienda.auth.RefreshTokenStore.Sesion(t.id(), t.usuarioId(),
                            t.sesionId(), t.expiraEn(), true, t.usado(), t.usadoEn(),
                            java.time.OffsetDateTime.now()));
                    n++;
                }
            }
            return n;
        }

        @Override
        public int revocarTodosDeUsuario(Long usuarioId) {
            int n = 0;
            for (Map.Entry<String, com.codeconheiner.tienda.auth.RefreshTokenStore.Sesion> e : tokens.entrySet()) {
                com.codeconheiner.tienda.auth.RefreshTokenStore.Sesion t = e.getValue();
                if (t.usuarioId().equals(usuarioId) && !t.revocado()) {
                    e.setValue(new com.codeconheiner.tienda.auth.RefreshTokenStore.Sesion(t.id(), t.usuarioId(),
                            t.sesionId(), t.expiraEn(), true, t.usado(), t.usadoEn(),
                            java.time.OffsetDateTime.now()));
                    n++;
                }
            }
            return n;
        }

        @Override
        public int purgarAntiguos(int diasRetencion) {
            return 0;
        }
    }

    static class InMemoryDireccionRepository extends DireccionRepository {

        private final Map<Long, DireccionResponse> direcciones = new HashMap<>();
        private final Map<Long, Long> duenos = new HashMap<>();
        private long siguiente = 1;

        InMemoryDireccionRepository() {
            super((JdbcTemplate) null);
        }

        @Override
        public java.util.List<DireccionResponse> listarPorUsuario(Long usuarioId) {
            return duenos.entrySet().stream().filter(e -> e.getValue().equals(usuarioId))
                    .map(e -> direcciones.get(e.getKey())).toList();
        }

        @Override
        public Optional<DireccionResponse> buscarPropia(Long id, Long usuarioId) {
            Long dueno = duenos.get(id);
            return dueno != null && dueno.equals(usuarioId) ? Optional.ofNullable(direcciones.get(id))
                    : Optional.empty();
        }

        @Override
        public Long crear(Long usuarioId, DireccionRequest r) {
            long id = siguiente++;
            direcciones.put(id, new DireccionResponse(id, r.alias(), r.destinatario(), r.telefono(),
                    r.departamento(), r.ciudad(), r.direccion(), r.indicaciones(),
                    Boolean.TRUE.equals(r.esPrincipal())));
            duenos.put(id, usuarioId);
            return id;
        }

        @Override
        public int actualizar(Long id, Long usuarioId, DireccionRequest r) {
            if (!buscarPropia(id, usuarioId).isPresent()) {
                return 0;
            }
            direcciones.put(id, new DireccionResponse(id, r.alias(), r.destinatario(), r.telefono(),
                    r.departamento(), r.ciudad(), r.direccion(), r.indicaciones(),
                    Boolean.TRUE.equals(r.esPrincipal())));
            return 1;
        }

        @Override
        public int eliminar(Long id, Long usuarioId) {
            if (!buscarPropia(id, usuarioId).isPresent()) {
                return 0;
            }
            direcciones.remove(id);
            duenos.remove(id);
            return 1;
        }
    }

    static class InMemoryCuponRepository extends CuponRepository {

        private final Map<Long, CuponResponse> cupones = new HashMap<>();
        private long siguiente = 1;

        InMemoryCuponRepository() {
            super((JdbcTemplate) null);
        }

        @Override
        public java.util.List<CuponResponse> listar() {
            return new java.util.ArrayList<>(cupones.values());
        }

        @Override
        public Optional<CuponResponse> buscarPorId(Long id) {
            return Optional.ofNullable(cupones.get(id));
        }

        @Override
        public Optional<CuponResponse> buscarPorCodigo(String codigo) {
            return cupones.values().stream()
                    .filter(c -> c.codigo().equalsIgnoreCase(codigo)).findFirst();
        }

        @Override
        public Long crear(CuponRequest r) {
            long id = siguiente++;
            cupones.put(id, new CuponResponse(id, r.codigo(), r.tipo(), r.valor(),
                    r.montoMinimo() == null ? java.math.BigDecimal.ZERO : r.montoMinimo(), r.usosMaximos(), 0,
                    r.vigenteDesde() == null ? java.time.OffsetDateTime.now().minusMinutes(1) : r.vigenteDesde(),
                    r.vigenteHasta(), r.activo() == null || r.activo()));
            return id;
        }

        @Override
        public int actualizar(Long id, CuponRequest r) {
            if (!cupones.containsKey(id)) {
                return 0;
            }
            CuponResponse anterior = cupones.get(id);
            cupones.put(id, new CuponResponse(id, r.codigo(), r.tipo(), r.valor(),
                    r.montoMinimo() == null ? java.math.BigDecimal.ZERO : r.montoMinimo(), r.usosMaximos(),
                    anterior.usosActuales(),
                    r.vigenteDesde() == null ? java.time.OffsetDateTime.now().minusMinutes(1) : r.vigenteDesde(),
                    r.vigenteHasta(), r.activo() == null || r.activo()));
            return 1;
        }
    }

    static class InMemoryResenaRepository extends ResenaRepository {

        InMemoryResenaRepository() {
            super((JdbcTemplate) null);
        }

        @Override
        public java.util.List<com.codeconheiner.tienda.resenas.dto.ResenaResponse> listarPorProducto(
                Long productoId) {
            return List.of();
        }

        @Override
        public Long crear(Long usuarioId, com.codeconheiner.tienda.resenas.dto.ResenaRequest r) {
            return 1L;
        }

        @Override
        public Optional<com.codeconheiner.tienda.resenas.dto.ResenaResponse> buscarPropia(Long id, Long usuarioId) {
            return Optional.empty();
        }
    }

    static class InMemoryCategoriaRepository extends CategoriaRepository {

        InMemoryCategoriaRepository() {
            super((JdbcTemplate) null);
        }

        @Override
        public List<CategoriaResponse> listarActivas() {
            return List.of(new CategoriaResponse(1L, "Computadores", "computadores", null));
        }
    }

    static class InMemoryProductoService extends ProductoService {

        private final Map<Long, ProductoResponse> productos = new HashMap<>();
        private long siguienteId = 1;

        InMemoryProductoService(TiendaProperties properties) {
            super(new ProductoRepository((JdbcTemplate) null), properties,
                    new com.codeconheiner.tienda.catalogo.storage.StorageService(
                            (software.amazon.awssdk.services.s3.S3Client) null, properties) {
                        @Override
                        public String subirArchivo(org.springframework.web.multipart.MultipartFile file, String prefijo) {
                            String p = prefijo == null ? "productos/sin-asignar" : prefijo.replaceAll("^/+", "").replaceAll("/+$", "");
                            return p + "/clave-de-prueba.png";
                        }

                        @Override
                        public String subir(org.springframework.web.multipart.MultipartFile file, Long productoId) {
                            return "productos/" + productoId + "/clave-de-prueba.png";
                        }

                        @Override
                        public String urlPublica(String key) {
                            if (key == null || key.isBlank()) {
                                return null;
                            }
                            return "http://localhost:9000/bucket/" + key;
                        }

                        @Override
                        public void eliminar(String key) {
                        }

                        @Override
                        public java.util.Optional<String> derivarClaveDesdeUrlPublica(String url) {
                            if (url == null) {
                                return java.util.Optional.empty();
                            }
                            String base = "http://localhost:9000/bucket/";
                            if (url.startsWith(base)) {
                                return java.util.Optional.of(url.substring(base.length()));
                            }
                            return java.util.Optional.empty();
                        }
                    });
        }

        @Override
        public List<ProductoResponse> listarPublico(Long categoriaId, String q, int page, int size) {
            return productos.values().stream().filter(producto -> producto.activo()).map(p -> new ProductoResponse(
                    p.id(), p.sku(), p.nombre(), p.slug(), p.descripcion(), p.precio(), p.categoriaId(), p.categoria(),
                    p.marcaId(), p.marca(), p.garantiaMeses(), p.stockDisponible(), p.activo(), p.imagenes(),
                    p.atributos())).toList();
        }

        @Override
        public ProductoResponse detallePublico(long id) {
            return Optional.ofNullable(productos.get(id))
                    .filter(producto -> producto.activo())
                    .filter(producto -> producto.stockDisponible() > 0)
                    .map(p -> new ProductoResponse(p.id(), p.sku(), p.nombre(), p.slug(), p.descripcion(), p.precio(),
                            p.categoriaId(), p.categoria(), p.marcaId(), p.marca(), p.garantiaMeses(),
                            p.stockDisponible(), p.activo(), p.imagenes(), p.atributos()))
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Producto no encontrado"));
        }

        @Override
        public ProductoResponse crear(ProductoRequest request) {
            if ("SQL-P0001".equals(request.sku())) {
                throw new DataIntegrityViolationException("Regla de negocio",
                        new SQLException("ERROR: El cupon PRUEBA esta fuera de vigencia\nWhere: fn_pedido_antes_insert",
                                "P0001"));
            }
            if ("FORCE-500".equals(request.sku())) {
                throw new IllegalStateException("detalle interno confidencial");
            }
            long id = siguienteId++;
            ProductoResponse producto = respuesta(id, request, true);
            productos.put(id, producto);
            return producto;
        }

        @Override
        public ProductoResponse actualizar(long id, ProductoRequest request) {
            ProductoResponse actual = productos.get(id);
            if (actual == null) {
                throw new ApiException(HttpStatus.NOT_FOUND, "Producto no encontrado");
            }
            ProductoResponse actualizado = respuesta(id, request, actual.activo());
            productos.put(id, actualizado);
            return actualizado;
        }

        @Override
        public void desactivar(long id) {
            ProductoResponse actual = productos.get(id);
            if (actual == null) {
                throw new ApiException(HttpStatus.NOT_FOUND, "Producto no encontrado");
            }
            productos.put(id, new ProductoResponse(actual.id(), actual.sku(), actual.nombre(), actual.slug(),
                    actual.descripcion(), actual.precio(), actual.categoriaId(), actual.categoria(), actual.marcaId(),
                    actual.marca(), actual.garantiaMeses(), actual.stockDisponible(), false, actual.imagenes(),
                    actual.atributos()));
        }

        @Override
        public List<ProductoResponse> listarTodos() {
            return productos.values().stream().toList();
        }

        @Override
        public ProductoResponse reactivar(long id) {
            ProductoResponse actual = productos.get(id);
            if (actual == null) {
                throw new ApiException(HttpStatus.NOT_FOUND, "Producto no encontrado");
            }
            ProductoResponse reactivado = new ProductoResponse(actual.id(), actual.sku(), actual.nombre(), actual.slug(),
                    actual.descripcion(), actual.precio(), actual.categoriaId(), actual.categoria(), actual.marcaId(),
                    actual.marca(), actual.garantiaMeses(), actual.stockDisponible(), true, actual.imagenes(),
                    actual.atributos());
            productos.put(id, reactivado);
            return reactivado;
        }

        @Override
        public ProductoResponse subirImagen(long id, MultipartFile file) {
            ProductoResponse actual = productos.get(id);
            if (actual == null) {
                throw new ApiException(HttpStatus.NOT_FOUND, "Producto no encontrado");
            }
            String key = "productos/" + id + "/clave-de-prueba.png";
            ProductoResponse conImagen = new ProductoResponse(actual.id(), actual.sku(), actual.nombre(), actual.slug(),
                    actual.descripcion(), actual.precio(), actual.categoriaId(), actual.categoria(), actual.marcaId(),
                    actual.marca(), actual.garantiaMeses(), actual.stockDisponible(), actual.activo(), actual.imagenes(),
                    actual.atributos(), key, "http://localhost:9000/bucket/" + key);
            productos.put(id, conImagen);
            return conImagen;
        }

        @Override
        public String subirImagenGaleria(long id, MultipartFile archivo) {
            ProductoResponse actual = productos.get(id);
            if (actual == null) {
                throw new ApiException(HttpStatus.NOT_FOUND, "Producto no encontrado");
            }
            return "http://localhost:9000/bucket/productos/" + id + "/galeria-prueba.png";
        }

        @Override
        public void eliminarImagen(long id) {
            ProductoResponse actual = productos.get(id);
            if (actual == null) {
                throw new ApiException(HttpStatus.NOT_FOUND, "Producto no encontrado");
            }
            productos.put(id, new ProductoResponse(actual.id(), actual.sku(), actual.nombre(), actual.slug(),
                    actual.descripcion(), actual.precio(), actual.categoriaId(), actual.categoria(), actual.marcaId(),
                    actual.marca(), actual.garantiaMeses(), actual.stockDisponible(), actual.activo(), actual.imagenes(),
                    actual.atributos()));
        }

        void limpiar() {
            productos.clear();
            siguienteId = 1;
        }

        private static ProductoResponse respuesta(long id, ProductoRequest request, boolean activo) {
            return new ProductoResponse(id, request.sku(), request.nombre(), request.sku().toLowerCase(Locale.ROOT),
                    request.descripcion(), request.precio(), request.categoriaId(), "Computadores", request.marcaId(),
                    null, request.garantiaMeses() == null ? 12 : request.garantiaMeses(), request.stock(), activo,
                    List.of(), Map.of());
        }
    }
}