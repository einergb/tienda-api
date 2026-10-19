package com.codeconheiner.tienda;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codeconheiner.tienda.auth.JwtService;
import com.codeconheiner.tienda.carrito.CarritoController;
import com.codeconheiner.tienda.carrito.CarritoRepository;
import com.codeconheiner.tienda.carrito.CarritoService;
import com.codeconheiner.tienda.carrito.dto.CarritoItemResponse;
import com.codeconheiner.tienda.common.GlobalExceptionHandler;
import com.codeconheiner.tienda.config.SecurityConfig;
import com.codeconheiner.tienda.config.TiendaProperties;
import com.codeconheiner.tienda.cupones.AdminCuponController;
import com.codeconheiner.tienda.cupones.CuponController;
import com.codeconheiner.tienda.cupones.CuponRepository;
import com.codeconheiner.tienda.cupones.CuponService;
import com.codeconheiner.tienda.cupones.dto.CuponRequest;
import com.codeconheiner.tienda.cupones.dto.CuponResponse;
import com.codeconheiner.tienda.direcciones.DireccionController;
import com.codeconheiner.tienda.direcciones.DireccionRepository;
import com.codeconheiner.tienda.direcciones.DireccionService;
import com.codeconheiner.tienda.direcciones.dto.DireccionRequest;
import com.codeconheiner.tienda.direcciones.dto.DireccionResponse;
import com.codeconheiner.tienda.envios.AdminEnvioController;
import com.codeconheiner.tienda.envios.EnvioController;
import com.codeconheiner.tienda.envios.EnvioRepository;
import com.codeconheiner.tienda.envios.EnvioService;
import com.codeconheiner.tienda.envios.dto.EnvioResponse;
import com.codeconheiner.tienda.inventario.InventarioController;
import com.codeconheiner.tienda.inventario.InventarioRepository;
import com.codeconheiner.tienda.inventario.InventarioService;
import com.codeconheiner.tienda.inventario.dto.StockInfoResponse;
import com.codeconheiner.tienda.pagos.PagoController;
import com.codeconheiner.tienda.pagos.PagoRepository;
import com.codeconheiner.tienda.pagos.PagoService;
import com.codeconheiner.tienda.pagos.dto.PagoResponse;
import com.codeconheiner.tienda.pedidos.AdminPedidoController;
import com.codeconheiner.tienda.pedidos.PedidoController;
import com.codeconheiner.tienda.pedidos.PedidoRepository;
import com.codeconheiner.tienda.pedidos.PedidoService;
import com.codeconheiner.tienda.pedidos.dto.PedidoItemResponse;
import com.codeconheiner.tienda.pedidos.dto.PedidoResponse;
import com.codeconheiner.tienda.resenas.ResenaController;
import com.codeconheiner.tienda.resenas.ResenaRepository;
import com.codeconheiner.tienda.resenas.ResenaService;
import com.codeconheiner.tienda.resenas.dto.ResenaRequest;
import com.codeconheiner.tienda.resenas.dto.ResenaResponse;
import com.codeconheiner.tienda.usuarios.Usuario;
import com.codeconheiner.tienda.usuarios.UsuarioRepository;
import com.codeconheiner.tienda.usuarios.UsuarioService;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {
        CarritoController.class, InventarioController.class, PedidoController.class,
        PagoController.class, EnvioController.class, DireccionController.class,
        CuponController.class, ResenaController.class, AdminPedidoController.class,
        AdminEnvioController.class, AdminCuponController.class})
@Import({SecurityConfig.class, GlobalExceptionHandler.class, ModulosEndpointsTest.TestBeans.class})
class ModulosEndpointsTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private FakeCarritoRepository carrito;

    @Autowired
    private FakeInventarioRepository inventario;

    @Autowired
    private FakePedidoRepository pedidos;

    @Autowired
    private FakePagoRepository pagos;

    @Autowired
    private FakeEnvioRepository envios;

    @Autowired
    private FakeDireccionRepository direcciones;

    @Autowired
    private FakeCuponRepository cupones;

    @Autowired
    private FakeResenaRepository resenas;

    // ---------- Carrito ----------

    @Test
    @WithMockUser(username = "ana@example.com")
    void carritoVacioDevuelveTotalesEnCero() throws Exception {
        carrito.limpiar();

        mvc.perform(get("/api/carrito"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.subtotal").value(0))
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    @WithMockUser(username = "ana@example.com")
    void agregarItemAlCarritoDevuelve201YItem() throws Exception {
        carrito.limpiar();

        mvc.perform(post("/api/carrito/items").contentType("application/json")
                        .content("{\"productoId\":1,\"cantidad\":2}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].productoId").value(1))
                .andExpect(jsonPath("$.items[0].cantidad").value(2));
    }

    @Test
    @WithMockUser(username = "ana@example.com")
    void agregarItemInvalidoDevuelve400ConErrores() throws Exception {
        carrito.limpiar();

        mvc.perform(post("/api/carrito/items").contentType("application/json")
                        .content("{\"productoId\":null,\"cantidad\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Los datos enviados no son válidos."))
                .andExpect(jsonPath("$.errores.productoId").value("El producto es obligatorio"))
                .andExpect(jsonPath("$.errores.cantidad").value("La cantidad debe ser mayor o igual a 1"));
    }

    @Test
    void carritoSinTokenDevuelve401() throws Exception {
        mvc.perform(get("/api/carrito"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail")
                        .value("Autenticación requerida: inicia sesión o proporciona un token válido."));
    }

    @Test
    @WithMockUser(username = "ana@example.com")
    void actualizarYEliminarItemDelCarrito() throws Exception {
        carrito.limpiar();
        mvc.perform(post("/api/carrito/items").contentType("application/json")
                        .content("{\"productoId\":1,\"cantidad\":1}"))
                .andExpect(status().isCreated());

        mvc.perform(put("/api/carrito/items/1").contentType("application/json")
                        .content("{\"productoId\":1,\"cantidad\":5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].cantidad").value(5));

        mvc.perform(delete("/api/carrito/items/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(username = "ana@example.com")
    void vaciarCarritoDevuelve204() throws Exception {
        carrito.limpiar();
        mvc.perform(post("/api/carrito/items").contentType("application/json")
                        .content("{\"productoId\":1,\"cantidad\":1}"))
                .andExpect(status().isCreated());

        mvc.perform(delete("/api/carrito"))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/carrito"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());
    }

    // ---------- Inventario ----------

    @Test
    void inventarioDevuelveStockDelProducto() throws Exception {
        inventario.agregar(1L, new StockInfoResponse(1L, 10, 2, 8, 5));

        mvc.perform(get("/api/inventario/productos/1/stock"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stock").value(10))
                .andExpect(jsonPath("$.stockReservado").value(2))
                .andExpect(jsonPath("$.stockDisponible").value(8))
                .andExpect(jsonPath("$.stockMinimo").value(5));
    }

    @Test
    void inventarioInexistenteDevuelve404() throws Exception {
        mvc.perform(get("/api/inventario/productos/999/stock"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Producto no encontrado"));
    }

    // ---------- Pedidos ----------

    @Test
    @WithMockUser(username = "ana@example.com")
    void listarPedidosDevuelveLista() throws Exception {
        pedidos.limpiar();
        pedidos.agregar(1L, pedido(1L, "PED-1", "PAGADO"));

        mvc.perform(get("/api/pedidos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].numero").value("PED-1"))
                .andExpect(jsonPath("$[0].estado").value("PAGADO"));
    }

    @Test
    @WithMockUser(username = "ana@example.com")
    void pedidoPorIdDevuelveDetalle() throws Exception {
        pedidos.limpiar();
        pedidos.agregar(1L, pedido(1L, "PED-1", "PENDIENTE"));

        mvc.perform(get("/api/pedidos/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numero").value("PED-1"))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].sku").value("SKU-1"));
    }

    @Test
    @WithMockUser(username = "ana@example.com")
    void pedidoInexistenteDevuelve404() throws Exception {
        pedidos.limpiar();

        mvc.perform(get("/api/pedidos/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Pedido no encontrado"));
    }

    @Test
    @WithMockUser(username = "ana@example.com")
    void checkoutConCarritoYPagoFuncionanDePuntaAPunta() throws Exception {
        carrito.limpiar();
        pedidos.limpiar();
        direcciones.limpiar();
        pagos.limpiar();

        // el cliente agrega un producto al carrito y crea una direccion
        mvc.perform(post("/api/carrito/items").contentType("application/json")
                        .content("{\"productoId\":1,\"cantidad\":2}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/direcciones").contentType("application/json").content("""
                        {"destinatario":"Ana Perez","telefono":"3001234567","departamento":"Bogota",
                         "ciudad":"Bogota","direccion":"Calle 1 #2-3"}
                        """))
                .andExpect(status().isCreated());

        // checkout: convierte el carrito en pedido PENDIENTE con reserva
        String cuerpo = mvc.perform(post("/api/pedidos").contentType("application/json")
                        .content("{\"direccionId\":1}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].productoId").value(1))
                .andExpect(jsonPath("$.items[0].cantidad").value(2))
                // subtotal 2000 + IVA 19% (380) + envio 10000
                .andExpect(jsonPath("$.subtotal").value(2000))
                .andExpect(jsonPath("$.total").value(12380))
                .andReturn().getResponse().getContentAsString();

        long pedidoId = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(cuerpo).path("id").asLong();

        // el carrito quedo vacio
        mvc.perform(get("/api/carrito"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());

        // pago aprobado mueve el pedido a PAGADO
        mvc.perform(post("/api/pagos").contentType("application/json")
                        .content("{\"pedidoId\":" + pedidoId + ",\"pasarela\":\"wompi\",\"referencia\":\"REF-1\","
                                + "\"estado\":\"APROBADO\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("APROBADO"))
                .andExpect(jsonPath("$.monto").value(12380));

        mvc.perform(get("/api/pedidos/" + pedidoId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("PAGADO"));
    }

    @Test
    @WithMockUser(username = "ana@example.com")
    void checkoutSinItemsDevuelve400() throws Exception {
        carrito.limpiar();
        pedidos.limpiar();

        mvc.perform(post("/api/pedidos").contentType("application/json")
                        .content("{\"direccionId\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("El carrito está vacío"));
    }

    @Test
    @WithMockUser(username = "ana@example.com")
    void crearPedidoInvalidoDevuelve400() throws Exception {
        mvc.perform(post("/api/pedidos").contentType("application/json")
                        .content("{\"direccionId\":null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.direccionId").value("La dirección es obligatoria"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCambiaEstadoDelPedidoYValidaElValor() throws Exception {
        pedidos.limpiar();
        pedidos.agregar(1L, pedido(1L, "PED-1", "PENDIENTE"));

        mvc.perform(put("/api/admin/pedidos/1/estado").contentType("application/json")
                        .content("{\"estado\":\"cancelado\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CANCELADO"));

        mvc.perform(put("/api/admin/pedidos/1/estado").contentType("application/json")
                        .content("{\"estado\":\"CUALQUIERA\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Estado de pedido no válido"));

        mvc.perform(put("/api/admin/pedidos/999/estado").contentType("application/json")
                        .content("{\"estado\":\"CANCELADO\"}"))
                .andExpect(status().isNotFound());

        mvc.perform(get("/api/admin/pedidos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    // ---------- Pagos ----------

    @Test
    @WithMockUser(username = "ana@example.com")
    void listarPagosPorPedidoDevuelveLista() throws Exception {
        pagos.limpiar();
        pagos.agregar(pago(1L, 1L, "APROBADO"));

        mvc.perform(get("/api/pagos/pedido/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].estado").value("APROBADO"))
                .andExpect(jsonPath("$[0].pedidoId").value(1));
    }

    @Test
    @WithMockUser(username = "ana@example.com")
    void crearPagoRechazaEstadosInvalidosYPedidosNoPendientes() throws Exception {
        pedidos.limpiar();
        pedidos.agregar(1L, pedido(1L, "PED-1", "PENDIENTE"));
        pedidos.agregar(2L, pedido(2L, "PED-2", "PAGADO"));
        pagos.limpiar();

        mvc.perform(post("/api/pagos").contentType("application/json")
                        .content("{\"pedidoId\":1,\"pasarela\":\"wompi\",\"referencia\":\"REF-1\","
                                + "\"estado\":\"RECHAZO\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Estado de pago no válido"));

        mvc.perform(post("/api/pagos").contentType("application/json")
                        .content("{\"pedidoId\":2,\"pasarela\":\"wompi\",\"referencia\":\"REF-2\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("El pedido no admite pagos en el estado actual"));

        mvc.perform(post("/api/pagos").contentType("application/json")
                        .content("{\"pedidoId\":999,\"pasarela\":\"wompi\",\"referencia\":\"REF-3\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Pedido no encontrado"));
    }

    @Test
    @WithMockUser(username = "ana@example.com")
    void crearPagoSinEstadoQuedaPendiente() throws Exception {
        pedidos.limpiar();
        pedidos.agregar(1L, pedido(1L, "PED-1", "PENDIENTE"));
        pagos.limpiar();

        mvc.perform(post("/api/pagos").contentType("application/json")
                        .content("{\"pedidoId\":1,\"pasarela\":\"wompi\",\"referencia\":\"REF-9\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.monto").value(2380));
    }

    @Test
    void pagosSinTokenDevuelve401() throws Exception {
        mvc.perform(get("/api/pagos/pedido/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "ana@example.com")
    void crearPagoInvalidoDevuelve400ConErrores() throws Exception {
        mvc.perform(post("/api/pagos").contentType("application/json")
                        .content("{\"pedidoId\":null,\"pasarela\":\"\",\"referencia\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.pedidoId").value("El pedido es obligatorio"))
                .andExpect(jsonPath("$.errores.pasarela").value("La pasarela es obligatoria"))
                .andExpect(jsonPath("$.errores.referencia").value("La referencia es obligatoria"));
    }

    // ---------- Envíos ----------

    @Test
    @WithMockUser(username = "ana@example.com")
    void envioPorPedidoDevuelveEstado() throws Exception {
        envios.agregar(1L, new EnvioResponse(1L, 1L, "Servientrega", "GUIA-1", "EN_CAMINO", null, null));

        mvc.perform(get("/api/envios/pedido/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transportadora").value("Servientrega"))
                .andExpect(jsonPath("$.guia").value("GUIA-1"))
                .andExpect(jsonPath("$.estado").value("EN_CAMINO"));
    }

    @Test
    @WithMockUser(username = "ana@example.com")
    void envioInexistenteDevuelve404() throws Exception {
        envios.limpiar();

        mvc.perform(get("/api/envios/pedido/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Envio no encontrado"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCreaEnvioYLoDespacha() throws Exception {
        envios.limpiar();

        String cuerpo = mvc.perform(post("/api/admin/envios").contentType("application/json")
                        .content("{\"pedidoId\":1,\"transportadora\":\"Servientrega\",\"guia\":\"G-1\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("PREPARANDO"))
                .andExpect(jsonPath("$.transportadora").value("Servientrega"))
                .andReturn().getResponse().getContentAsString();
        long envioId = new com.fasterxml.jackson.databind.ObjectMapper().readTree(cuerpo).path("id").asLong();

        mvc.perform(put("/api/admin/envios/" + envioId).contentType("application/json")
                        .content("{\"estado\":\"EN_CAMINO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EN_CAMINO"))
                .andExpect(jsonPath("$.guia").value("G-1"));

        mvc.perform(put("/api/admin/envios/" + envioId).contentType("application/json")
                        .content("{\"estado\":\"INVENTADO\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Estado de envío no válido"));
    }

    // ---------- Direcciones ----------

    @Test
    @WithMockUser(username = "ana@example.com")
    void direccionesPermitenGestionCompleta() throws Exception {
        direcciones.limpiar();

        mvc.perform(post("/api/direcciones").contentType("application/json").content("""
                        {"destinatario":"Ana Perez","telefono":"3001234567","departamento":"Bogota",
                         "ciudad":"Bogota","direccion":"Calle 1 #2-3","esPrincipal":true}
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.esPrincipal").value(true));

        mvc.perform(get("/api/direcciones"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        mvc.perform(put("/api/direcciones/1").contentType("application/json").content("""
                        {"destinatario":"Ana Perez","telefono":"3009999999","departamento":"Cundinamarca",
                         "ciudad":"Chia","direccion":"Carrera 2 #3-4"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ciudad").value("Chia"));

        mvc.perform(delete("/api/direcciones/999"))
                .andExpect(status().isNotFound());

        mvc.perform(delete("/api/direcciones/1"))
                .andExpect(status().isNoContent());
    }

    // ---------- Cupones ----------

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCreaCuponYClienteLoValida() throws Exception {
        cupones.limpiar();

        mvc.perform(post("/api/admin/cupones").contentType("application/json")
                        .content("{\"codigo\":\"VERANO10\",\"tipo\":\"PORCENTAJE\",\"valor\":10}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.codigo").value("VERANO10"));

        mvc.perform(get("/api/cupones/validar").param("codigo", "VERANO10").param("subtotal", "10000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigo").value("VERANO10"))
                .andExpect(jsonPath("$.descuento").value(1000));
    }

    @Test
    @WithMockUser(username = "ana@example.com")
    void cuponInexistenteDevuelve404() throws Exception {
        cupones.limpiar();

        mvc.perform(get("/api/cupones/validar").param("codigo", "NADA").param("subtotal", "1000"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Cupón no encontrado"));
    }

    // ---------- Reseñas ----------

    @Test
    void resenasDeProductoSonPublicas() throws Exception {
        resenas.limpiar();

        mvc.perform(get("/api/resenas/producto/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void resenasSinTokenDevuelven401EnEscritura() throws Exception {
        mvc.perform(post("/api/resenas").contentType("application/json")
                        .content("{\"productoId\":1,\"calificacion\":5}"))
                .andExpect(status().isUnauthorized());
    }

    // ---------- Helpers / Beans ----------

    private static PedidoResponse pedido(long id, String numero, String estado) {
        List<PedidoItemResponse> items = List.of(
                new PedidoItemResponse(1L, 1L, "SKU-1", "Producto", new BigDecimal("1000"), 2,
                        new BigDecimal("2000")));
        return new PedidoResponse(id, numero, estado, new BigDecimal("2000"), BigDecimal.ZERO,
                new BigDecimal("380"), BigDecimal.ZERO, new BigDecimal("2380"),
                OffsetDateTime.now(), OffsetDateTime.now(), items);
    }

    private static PagoResponse pago(long id, long pedidoId, String estado) {
        return new PagoResponse(id, pedidoId, "wompi", "ref-" + id, estado, new BigDecimal("2380"),
                OffsetDateTime.now());
    }

    @TestConfiguration
    static class TestBeans {

        @Bean
        TiendaProperties tiendaProperties() {
            return new TiendaProperties(
                    "Tienda de prueba", "COP", new BigDecimal("0.19"), new BigDecimal("200000"), 10000, 5, 15,
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
        UsuarioService usuarioService(InMemoryUsuarioRepository usuarios) {
            return new UsuarioService(usuarios);
        }

        @Bean
        FakeCarritoRepository carritoRepository() {
            return new FakeCarritoRepository();
        }

        @Bean
        CarritoService carritoService(CarritoRepository repo, TiendaProperties props) {
            return new CarritoService(repo, props);
        }

        @Bean
        FakeInventarioRepository inventarioRepository() {
            return new FakeInventarioRepository();
        }

        @Bean
        InventarioService inventarioService(InventarioRepository repo) {
            return new InventarioService(repo);
        }

        @Bean
        FakeDireccionRepository direccionRepository() {
            return new FakeDireccionRepository();
        }

        @Bean
        DireccionService direccionService(DireccionRepository repo) {
            return new DireccionService(repo);
        }

        @Bean
        FakeCuponRepository cuponRepository() {
            return new FakeCuponRepository();
        }

        @Bean
        CuponService cuponService(CuponRepository repo) {
            return new CuponService(repo);
        }

        @Bean
        FakeResenaRepository resenaRepository() {
            return new FakeResenaRepository();
        }

        @Bean
        ResenaService resenaService(ResenaRepository repo) {
            return new ResenaService(repo);
        }

        @Bean
        FakePedidoRepository pedidoRepository() {
            return new FakePedidoRepository();
        }

        @Bean
        PedidoService pedidoService(PedidoRepository repo, CarritoRepository carritoRepository,
                DireccionService direccionService, CuponService cuponService, TiendaProperties props) {
            return new PedidoService(repo, carritoRepository, direccionService, cuponService, props);
        }

        @Bean
        FakePagoRepository pagoRepository(FakePedidoRepository pedidoRepository) {
            return new FakePagoRepository(pedidoRepository);
        }

        @Bean
        PagoService pagoService(PagoRepository repo, PedidoRepository pedidoRepository) {
            return new PagoService(repo, pedidoRepository);
        }

        @Bean
        FakeEnvioRepository envioRepository() {
            return new FakeEnvioRepository();
        }

        @Bean
        EnvioService envioService(EnvioRepository repo) {
            return new EnvioService(repo);
        }
    }

    static class InMemoryUsuarioRepository extends UsuarioRepository {

        InMemoryUsuarioRepository() {
            super((JdbcTemplate) null);
        }

        @Override
        public Optional<Usuario> buscarPorEmail(String email) {
            if (email == null || email.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(new Usuario(1L, "Ana", email, "hash", "CLIENTE", true));
        }
    }

    static class FakeCarritoRepository extends CarritoRepository {

        private final Map<Long, Long> carritos = new HashMap<>();
        private final Map<Long, List<CarritoItemResponse>> items = new HashMap<>();
        private long siguiente = 1;

        FakeCarritoRepository() {
            super((JdbcTemplate) null);
        }

        void limpiar() {
            carritos.clear();
            items.clear();
            siguiente = 1;
        }

        @Override
        public Optional<Long> obtenerIdCarritoPorUsuario(Long usuarioId) {
            return carritos.entrySet().stream()
                    .filter(e -> e.getValue().equals(usuarioId))
                    .map(Map.Entry::getKey)
                    .findFirst();
        }

        @Override
        public Long crearCarrito(Long usuarioId) {
            long id = siguiente++;
            carritos.put(id, usuarioId);
            items.put(id, new ArrayList<>());
            return id;
        }

        @Override
        public void agregarItem(Long carritoId, Long productoId, Integer cantidad) {
            BigDecimal precio = new BigDecimal("1000");
            items.computeIfAbsent(carritoId, k -> new ArrayList<>())
                    .add(new CarritoItemResponse(siguiente++, productoId, "SKU-" + productoId,
                            "Producto", precio, cantidad, precio.multiply(BigDecimal.valueOf(cantidad)), 10));
        }

        @Override
        public List<CarritoItemResponse> listarItems(Long carritoId) {
            return List.copyOf(items.getOrDefault(carritoId, List.of()));
        }

        @Override
        public void actualizarCantidad(Long carritoId, Long productoId, Integer cantidad) {
            List<CarritoItemResponse> lista = items.getOrDefault(carritoId, new ArrayList<>());
            for (int i = 0; i < lista.size(); i++) {
                CarritoItemResponse it = lista.get(i);
                if (it.productoId().equals(productoId)) {
                    lista.set(i, new CarritoItemResponse(it.id(), productoId, it.sku(), it.nombreProducto(),
                            it.precioUnitario(), cantidad,
                            it.precioUnitario().multiply(BigDecimal.valueOf(cantidad)), it.stockDisponible()));
                }
            }
        }

        @Override
        public void eliminarItem(Long carritoId, Long productoId) {
            items.getOrDefault(carritoId, new ArrayList<>())
                    .removeIf(it -> it.productoId().equals(productoId));
        }

        @Override
        public void vaciarCarrito(Long carritoId) {
            items.getOrDefault(carritoId, new ArrayList<>()).clear();
        }
    }

    static class FakeInventarioRepository extends InventarioRepository {

        private final Map<Long, StockInfoResponse> datos = new HashMap<>();

        FakeInventarioRepository() {
            super((JdbcTemplate) null);
        }

        void agregar(long productoId, StockInfoResponse stock) {
            datos.put(productoId, stock);
        }

        void limpiar() {
            datos.clear();
        }

        @Override
        public Optional<StockInfoResponse> obtenerStock(Long productoId) {
            return Optional.ofNullable(datos.get(productoId));
        }
    }

    static class FakeDireccionRepository extends DireccionRepository {

        private final Map<Long, DireccionResponse> direcciones = new HashMap<>();
        private long siguiente = 1;

        FakeDireccionRepository() {
            super((JdbcTemplate) null);
        }

        void limpiar() {
            direcciones.clear();
            siguiente = 1;
        }

        @Override
        public List<DireccionResponse> listarPorUsuario(Long usuarioId) {
            return new ArrayList<>(direcciones.values());
        }

        @Override
        public Optional<DireccionResponse> buscarPropia(Long id, Long usuarioId) {
            return Optional.ofNullable(direcciones.get(id));
        }

        @Override
        public Long crear(Long usuarioId, DireccionRequest r) {
            long id = siguiente++;
            direcciones.put(id, new DireccionResponse(id, r.alias(), r.destinatario(), r.telefono(),
                    r.departamento(), r.ciudad(), r.direccion(), r.indicaciones(),
                    Boolean.TRUE.equals(r.esPrincipal())));
            return id;
        }

        @Override
        public int actualizar(Long id, Long usuarioId, DireccionRequest r) {
            if (!direcciones.containsKey(id)) {
                return 0;
            }
            direcciones.put(id, new DireccionResponse(id, r.alias(), r.destinatario(), r.telefono(),
                    r.departamento(), r.ciudad(), r.direccion(), r.indicaciones(),
                    Boolean.TRUE.equals(r.esPrincipal())));
            return 1;
        }

        @Override
        public int eliminar(Long id, Long usuarioId) {
            return direcciones.remove(id) == null ? 0 : 1;
        }
    }

    static class FakeCuponRepository extends CuponRepository {

        private final Map<Long, CuponResponse> cupones = new HashMap<>();
        private long siguiente = 1;

        FakeCuponRepository() {
            super((JdbcTemplate) null);
        }

        void limpiar() {
            cupones.clear();
            siguiente = 1;
        }

        @Override
        public List<CuponResponse> listar() {
            return new ArrayList<>(cupones.values());
        }

        @Override
        public Optional<CuponResponse> buscarPorId(Long id) {
            return Optional.ofNullable(cupones.get(id));
        }

        @Override
        public Optional<CuponResponse> buscarPorCodigo(String codigo) {
            return cupones.values().stream().filter(c -> c.codigo().equalsIgnoreCase(codigo)).findFirst();
        }

        @Override
        public Long crear(CuponRequest r) {
            long id = siguiente++;
            cupones.put(id, new CuponResponse(id, r.codigo(), r.tipo(), r.valor(),
                    r.montoMinimo() == null ? BigDecimal.ZERO : r.montoMinimo(), r.usosMaximos(), 0,
                    OffsetDateTime.now().minusMinutes(1), r.vigenteHasta(), r.activo() == null || r.activo()));
            return id;
        }

        @Override
        public int actualizar(Long id, CuponRequest r) {
            if (!cupones.containsKey(id)) {
                return 0;
            }
            CuponResponse anterior = cupones.get(id);
            cupones.put(id, new CuponResponse(id, r.codigo(), r.tipo(), r.valor(),
                    r.montoMinimo() == null ? BigDecimal.ZERO : r.montoMinimo(), r.usosMaximos(),
                    anterior.usosActuales(), OffsetDateTime.now().minusMinutes(1), r.vigenteHasta(),
                    r.activo() == null || r.activo()));
            return 1;
        }
    }

    static class FakeResenaRepository extends ResenaRepository {

        private final Map<Long, ResenaResponse> resenas = new HashMap<>();
        private long siguiente = 1;

        FakeResenaRepository() {
            super((JdbcTemplate) null);
        }

        void limpiar() {
            resenas.clear();
            siguiente = 1;
        }

        @Override
        public List<ResenaResponse> listarPorProducto(Long productoId) {
            return resenas.values().stream().filter(r -> r.productoId().equals(productoId)).toList();
        }

        @Override
        public Optional<ResenaResponse> buscarPropia(Long id, Long usuarioId) {
            return Optional.ofNullable(resenas.get(id));
        }

        @Override
        public Long crear(Long usuarioId, ResenaRequest r) {
            long id = siguiente++;
            resenas.put(id, new ResenaResponse(id, r.productoId(), usuarioId, "Ana", r.calificacion(),
                    r.comentario(), OffsetDateTime.now()));
            return id;
        }

        @Override
        public int actualizar(Long id, Long usuarioId, ResenaRequest r) {
            ResenaResponse actual = resenas.get(id);
            if (actual == null) {
                return 0;
            }
            resenas.put(id, new ResenaResponse(id, actual.productoId(), usuarioId, actual.usuario(),
                    r.calificacion(), r.comentario(), actual.creadoEn()));
            return 1;
        }

        @Override
        public int eliminar(Long id, Long usuarioId) {
            return resenas.remove(id) == null ? 0 : 1;
        }
    }

    static class FakePedidoRepository extends PedidoRepository {

        private final Map<Long, PedidoResponse> pedidos = new HashMap<>();
        private final Map<Long, List<PedidoItemResponse>> items = new HashMap<>();
        private long siguiente = 1;

        FakePedidoRepository() {
            super((JdbcTemplate) null);
        }

        void limpiar() {
            pedidos.clear();
            items.clear();
            siguiente = 1;
        }

        void agregar(long usuarioId, PedidoResponse pedido) {
            pedidos.put(pedido.id(), pedido);
            items.put(pedido.id(), new ArrayList<>(pedido.items()));
        }

        /** Simula el trigger trg_pago_aprobado de V3. */
        void marcarPagado(Long pedidoId) {
            PedidoResponse actual = pedidos.get(pedidoId);
            if (actual != null) {
                pedidos.put(pedidoId, reemplazar(actual, "PAGADO"));
            }
        }

        @Override
        public List<PedidoResponse> listarPorUsuario(Long usuarioId) {
            return new ArrayList<>(pedidos.values());
        }

        @Override
        public Optional<PedidoResponse> obtenerPorId(Long usuarioId, Long pedidoId) {
            return Optional.ofNullable(pedidos.get(pedidoId));
        }

        @Override
        public List<PedidoResponse> listarTodos(String estado) {
            return new ArrayList<>(pedidos.values());
        }

        @Override
        public Optional<PedidoResponse> obtenerPorIdAdmin(Long pedidoId) {
            return Optional.ofNullable(pedidos.get(pedidoId));
        }

        @Override
        public int cambiarEstado(Long pedidoId, String estado) {
            PedidoResponse actual = pedidos.get(pedidoId);
            if (actual == null) {
                return 0;
            }
            pedidos.put(pedidoId, reemplazar(actual, estado));
            return 1;
        }

        @Override
        public Long crear(Long usuarioId, DireccionResponse dir, BigDecimal subtotal, BigDecimal descuento,
                BigDecimal iva, BigDecimal costoEnvio, BigDecimal total, Long cuponId) {
            long id = siguiente++;
            pedidos.put(id, new PedidoResponse(id, "PED-" + id, "PENDIENTE", subtotal, descuento, iva,
                    costoEnvio, total, OffsetDateTime.now().plusMinutes(15), OffsetDateTime.now(), List.of()));
            items.put(id, new ArrayList<>());
            return id;
        }

        @Override
        public void agregarItem(Long pedidoId, Long productoId, Integer cantidad) {
            BigDecimal precio = new BigDecimal("1000");
            items.computeIfAbsent(pedidoId, k -> new ArrayList<>())
                    .add(new PedidoItemResponse((long) items.size(), productoId, "SKU-" + productoId,
                            "Producto", precio, cantidad, precio.multiply(BigDecimal.valueOf(cantidad))));
            PedidoResponse actual = pedidos.get(pedidoId);
            pedidos.put(pedidoId, new PedidoResponse(actual.id(), actual.numero(), actual.estado(),
                    actual.subtotal(), actual.descuento(), actual.iva(), actual.costoEnvio(), actual.total(),
                    actual.reservaExpiraEn(), actual.creadoEn(), List.copyOf(items.get(pedidoId))));
        }

        private static PedidoResponse reemplazar(PedidoResponse p, String estado) {
            return new PedidoResponse(p.id(), p.numero(), estado, p.subtotal(), p.descuento(), p.iva(),
                    p.costoEnvio(), p.total(), p.reservaExpiraEn(), p.creadoEn(), p.items());
        }
    }

    static class FakePagoRepository extends PagoRepository {

        private final List<PagoResponse> datos = new ArrayList<>();
        private final FakePedidoRepository pedidos;
        private long siguiente = 1;

        FakePagoRepository(FakePedidoRepository pedidos) {
            super((JdbcTemplate) null);
            this.pedidos = pedidos;
        }

        void limpiar() {
            datos.clear();
            siguiente = 1;
        }

        void agregar(PagoResponse pago) {
            datos.add(pago);
        }

        @Override
        public List<PagoResponse> listarPorPedido(Long pedidoId) {
            return datos.stream().filter(p -> p.pedidoId().equals(pedidoId)).toList();
        }

        @Override
        public PagoResponse crear(Long pedidoId, String pasarela, String referencia, String estado,
                BigDecimal monto) {
            PagoResponse pago = new PagoResponse(siguiente++, pedidoId, pasarela, referencia, estado, monto,
                    OffsetDateTime.now());
            datos.add(pago);
            // simula el trigger trg_pago_aprobado de V3
            if ("APROBADO".equals(estado)) {
                pedidos.marcarPagado(pedidoId);
            }
            return pago;
        }
    }

    static class FakeEnvioRepository extends EnvioRepository {

        private final Map<Long, EnvioResponse> datos = new HashMap<>();
        private long siguiente = 1;

        FakeEnvioRepository() {
            super((JdbcTemplate) null);
        }

        void agregar(long pedidoId, EnvioResponse envio) {
            datos.put(pedidoId, envio);
        }

        void limpiar() {
            datos.clear();
            siguiente = 1;
        }

        @Override
        public Optional<EnvioResponse> obtenerPorPedido(Long pedidoId) {
            return datos.values().stream().filter(e -> e.pedidoId().equals(pedidoId)).findFirst();
        }

        @Override
        public Optional<EnvioResponse> obtenerPorId(Long id) {
            return datos.values().stream().filter(e -> e.id().equals(id)).findFirst();
        }

        @Override
        public List<EnvioResponse> listar(String estado) {
            return new ArrayList<>(datos.values());
        }

        @Override
        public EnvioResponse crear(Long pedidoId, String transportadora, String guia, String estado) {
            long id = siguiente++;
            EnvioResponse envio = new EnvioResponse(id, pedidoId, transportadora, guia, estado, null, null);
            datos.put(id, envio);
            return envio;
        }

        @Override
        public int actualizar(Long id, String estado, String transportadora, String guia) {
            EnvioResponse actual = datos.get(id);
            if (actual == null) {
                return 0;
            }
            OffsetDateTime enviadoEn = "EN_CAMINO".equals(estado) ? OffsetDateTime.now() : actual.enviadoEn();
            OffsetDateTime entregadoEn = "ENTREGADO".equals(estado) ? OffsetDateTime.now() : actual.entregadoEn();
            datos.put(id, new EnvioResponse(actual.id(), actual.pedidoId(),
                    transportadora == null ? actual.transportadora() : transportadora,
                    guia == null ? actual.guia() : guia, estado, enviadoEn, entregadoEn));
            return 1;
        }
    }
}
