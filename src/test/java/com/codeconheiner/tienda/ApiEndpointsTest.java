package com.codeconheiner.tienda;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import com.codeconheiner.tienda.pedidos.PedidoRepository;
import com.codeconheiner.tienda.pedidos.PedidoService;
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
                {"nombre":"Ana","email":"ana@example.com","password":"clave-segura"}
                """;

        mvc.perform(post("/api/auth/registro").contentType("application/json").content(registro))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipo").value("Bearer"))
                .andExpect(jsonPath("$.rol").value("CLIENTE"))
                .andExpect(jsonPath("$.refreshToken").isNotEmpty());

        mvc.perform(post("/api/auth/login").contentType("application/json")
                        .content("{\"email\":\"ana@example.com\",\"password\":\"clave-segura\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void refreshRotationTokenYLogoutLoInvalida() throws Exception {
        String registro = """
                {"nombre":"Ana","email":"ana@example.com","password":"clave-segura"}
                """;
        String refresh = mvc.perform(post("/api/auth/registro").contentType("application/json").content(registro))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String refreshToken = extraer(refresh, "refreshToken");

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
                {"nombre":"Ana","email":"ana@example.com","password":"clave-segura"}
                """;
        String body = mvc.perform(post("/api/auth/registro").contentType("application/json").content(registro))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String token = extraer(body, "token");
        String refreshToken = extraer(body, "refreshToken");

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
                        .content("{\"nombre\":\"\",\"email\":\"correo-invalido\",\"password\":\"123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Los datos enviados no son válidos."))
                .andExpect(jsonPath("$.errores.nombre").value("El nombre es obligatorio"))
                .andExpect(jsonPath("$.errores.email").value("El correo no tiene un formato válido"))
                .andExpect(jsonPath("$.errores.password").value("La contraseña debe tener entre 8 y 72 caracteres"));
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
                {"nombre":"Ana","email":"ana@example.com","password":"clave-segura"}
                """;
        mvc.perform(post("/api/auth/registro").contentType("application/json").content(registro))
                .andExpect(status().isCreated());

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
                                {"nombre":"Ana","email":"ana@example.com","password":"clave-segura"}
                                """))
                .andExpect(status().isCreated());

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
                    new TiendaProperties.Cors(List.of("http://localhost:5173")));
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
        RefreshTokenRepository refreshTokenRepository() {
            return new InMemoryRefreshTokenRepository();
        }

        @Bean
        AuthService authService(InMemoryUsuarioRepository usuarios, RefreshTokenRepository refreshTokenRepository,
                JwtService jwtService, TiendaProperties properties,
                org.springframework.security.crypto.password.PasswordEncoder encoder) {
            return new AuthService(usuarios, refreshTokenRepository, encoder, jwtService, properties);
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
        PedidoService pedidoService(PedidoRepository pedidoRepository, CarritoRepository carritoRepository,
                DireccionService direccionService, CuponService cuponService, TiendaProperties properties) {
            return new PedidoService(pedidoRepository, carritoRepository, direccionService, cuponService, properties);
        }

        @Bean
        PagoRepository pagoRepository() {
            return new PagoRepository(null);
        }

        @Bean
        PagoService pagoService(PagoRepository pagoRepository, PedidoRepository pedidoRepository) {
            return new PagoService(pagoRepository, pedidoRepository);
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
            Usuario usuario = new Usuario((long) usuarios.size() + 1, nombre, email, passwordHash, rol, true);
            usuarios.put(email.toLowerCase(Locale.ROOT), usuario);
            return usuario;
        }

        @Override
        public Optional<Usuario> buscarPorId(Long id) {
            return usuarios.values().stream().filter(u -> u.id().equals(id)).findFirst();
        }

        @Override
        public void actualizarPassword(Long id, String passwordHash) {
            usuarios.replaceAll((email, u) -> u.id().equals(id)
                    ? new Usuario(u.id(), u.nombre(), u.email(), passwordHash, u.rol(), u.activo()) : u);
        }

        void limpiar() {
            usuarios.clear();
        }
    }

    static class InMemoryRefreshTokenRepository extends RefreshTokenRepository {

        private final Map<String, RefreshTokenRepository.RefreshToken> tokens = new HashMap<>();
        private long siguiente = 1;

        InMemoryRefreshTokenRepository() {
            super((JdbcTemplate) null);
        }

        @Override
        public Long crear(Long usuarioId, String tokenHash, java.time.OffsetDateTime expiraEn) {
            long id = siguiente++;
            tokens.put(tokenHash, new RefreshTokenRepository.RefreshToken(id, usuarioId, expiraEn, false));
            return id;
        }

        @Override
        public Optional<RefreshTokenRepository.RefreshToken> buscarPorHash(String tokenHash) {
            return Optional.ofNullable(tokens.get(tokenHash));
        }

        @Override
        public int revocar(String tokenHash) {
            RefreshTokenRepository.RefreshToken token = tokens.get(tokenHash);
            if (token == null || token.revocado()) {
                return 0;
            }
            tokens.put(tokenHash, new RefreshTokenRepository.RefreshToken(token.id(), token.usuarioId(),
                    token.expiraEn(), true));
            return 1;
        }

        @Override
        public int revocarTodosDeUsuario(Long usuarioId) {
            int n = 0;
            for (Map.Entry<String, RefreshTokenRepository.RefreshToken> e : tokens.entrySet()) {
                RefreshTokenRepository.RefreshToken t = e.getValue();
                if (t.usuarioId().equals(usuarioId) && !t.revocado()) {
                    e.setValue(new RefreshTokenRepository.RefreshToken(t.id(), t.usuarioId(), t.expiraEn(), true));
                    n++;
                }
            }
            return n;
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
            super(new ProductoRepository((JdbcTemplate) null), properties);
        }

        @Override
        public List<ProductoResponse> listarPublico(Long categoriaId, String q, int page, int size) {
            return productos.values().stream().filter(producto -> producto.activo()).toList();
        }

        @Override
        public ProductoResponse detallePublico(long id) {
            return Optional.ofNullable(productos.get(id))
                    .filter(producto -> producto.activo())
                    .filter(producto -> producto.stockDisponible() > 0)
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