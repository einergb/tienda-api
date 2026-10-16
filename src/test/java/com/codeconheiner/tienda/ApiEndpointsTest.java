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
import com.codeconheiner.tienda.catalogo.CategoriaRepository;
import com.codeconheiner.tienda.catalogo.CategoriaResponse;
import com.codeconheiner.tienda.catalogo.ProductoRepository;
import com.codeconheiner.tienda.catalogo.ProductoRequest;
import com.codeconheiner.tienda.catalogo.ProductoResponse;
import com.codeconheiner.tienda.catalogo.ProductoService;
import com.codeconheiner.tienda.common.ApiException;
import com.codeconheiner.tienda.config.SecurityConfig;
import com.codeconheiner.tienda.config.TiendaProperties;
import com.codeconheiner.tienda.usuarios.Usuario;
import com.codeconheiner.tienda.usuarios.UsuarioRepository;
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
                .andExpect(jsonPath("$.rol").value("CLIENTE"));

        mvc.perform(post("/api/auth/login").contentType("application/json")
                        .content("{\"email\":\"ana@example.com\",\"password\":\"clave-segura\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
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
    void reglaDeNegocioSqlDevuelve409ConMensajeGenerico() throws Exception {
        String producto = """
                {"sku":"SQL-P0001","nombre":"Teclado","precio":1000,"categoriaId":1,"stock":3}
                """;

        mvc.perform(post("/api/admin/productos").contentType("application/json").content(producto))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail")
                        .value("La operación viola una restricción de datos (duplicado o referencia inexistente)."));
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
                    "Tienda de prueba", "COP", new BigDecimal("0.19"), BigDecimal.ZERO, 5, 15,
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
        AuthService authService(InMemoryUsuarioRepository usuarios, JwtService jwtService,
                TiendaProperties properties, org.springframework.security.crypto.password.PasswordEncoder encoder) {
            return new AuthService(usuarios, encoder, jwtService, properties);
        }

        @Bean
        InMemoryProductoService productoService(TiendaProperties properties) {
            return new InMemoryProductoService(properties);
        }

        @Bean
        CategoriaRepository categoriaRepository() {
            return new InMemoryCategoriaRepository();
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

        void limpiar() {
            usuarios.clear();
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
                throw new DataIntegrityViolationException("Regla de negocio", new SQLException("P0001", "P0001"));
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
                    actual.marca(), actual.garantiaMeses(), actual.stockDisponible(), false));
        }

        void limpiar() {
            productos.clear();
            siguienteId = 1;
        }

        private static ProductoResponse respuesta(long id, ProductoRequest request, boolean activo) {
            return new ProductoResponse(id, request.sku(), request.nombre(), request.sku().toLowerCase(Locale.ROOT),
                    request.descripcion(), request.precio(), request.categoriaId(), "Computadores", request.marcaId(),
                    null, request.garantiaMeses() == null ? 12 : request.garantiaMeses(), request.stock(), activo);
        }
    }
}