package com.codeconheiner.tienda;

import static org.assertj.core.api.Assertions.assertThat;
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
import com.codeconheiner.tienda.common.Hashes;
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
import com.codeconheiner.tienda.facturas.EnvioCopia;
import com.codeconheiner.tienda.facturas.FacturaEmision;
import com.codeconheiner.tienda.facturas.FacturaRepository;
import com.codeconheiner.tienda.facturas.FacturaService;
import com.codeconheiner.tienda.inventario.InventarioController;
import com.codeconheiner.tienda.inventario.InventarioRepository;
import com.codeconheiner.tienda.inventario.InventarioService;
import com.codeconheiner.tienda.inventario.dto.StockBajoResponse;
import com.codeconheiner.tienda.inventario.dto.StockInfoResponse;
import com.codeconheiner.tienda.pagos.PagoController;
import com.codeconheiner.tienda.pagos.PagoDetalle;
import com.codeconheiner.tienda.pagos.PagoRepository;
import com.codeconheiner.tienda.pagos.PagoService;
import com.codeconheiner.tienda.pagos.dto.PagoConPasarelaRequest;
import com.codeconheiner.tienda.pagos.dto.PagoResponse;
import com.codeconheiner.tienda.pagos.dto.SesionPagoResponse;
import com.codeconheiner.tienda.pagos.pasarela.EpaycoFirmaWebhook;
import com.codeconheiner.tienda.pagos.pasarela.EpaycoPasarela;
import com.codeconheiner.tienda.pagos.pasarela.FirmaWebhookRegistry;
import com.codeconheiner.tienda.pagos.pasarela.PasarelaRegistry;
import com.codeconheiner.tienda.pagos.pasarela.WompiFirmaWebhook;
import com.codeconheiner.tienda.pagos.pasarela.WompiPasarela;
import com.codeconheiner.tienda.pedidos.AdminPedidoController;
import com.codeconheiner.tienda.pedidos.CalculadoraTotales;
import com.codeconheiner.tienda.pedidos.PedidoController;
import com.codeconheiner.tienda.pedidos.PedidoRepository;
import com.codeconheiner.tienda.pedidos.PedidoService;
import com.codeconheiner.tienda.pedidos.dto.PedidoItemResponse;
import com.codeconheiner.tienda.pedidos.dto.PedidoResponse;
import com.codeconheiner.tienda.reportes.ReporteController;
import com.codeconheiner.tienda.reportes.ReporteRepository;
import com.codeconheiner.tienda.reportes.ReporteService;
import com.codeconheiner.tienda.reportes.dto.InventarioReporteResponse;
import com.codeconheiner.tienda.reportes.dto.ResumenVentasResponse;
import com.codeconheiner.tienda.resenas.ResenaController;
import com.codeconheiner.tienda.resenas.ResenaRepository;
import com.codeconheiner.tienda.resenas.ResenaService;
import com.codeconheiner.tienda.resenas.dto.ResenaRequest;
import com.codeconheiner.tienda.resenas.dto.ResenaResponse;
import com.codeconheiner.tienda.usuarios.Usuario;
import com.codeconheiner.tienda.usuarios.UsuarioRepository;
import com.codeconheiner.tienda.usuarios.UsuarioService;
import com.codeconheiner.tienda.webhooks.EpaycoEventoParser;
import com.codeconheiner.tienda.webhooks.EventoWebhookParser;
import com.codeconheiner.tienda.webhooks.WebhookController;
import com.codeconheiner.tienda.webhooks.WebhookEvento;
import com.codeconheiner.tienda.webhooks.WebhookEventosRepository;
import com.codeconheiner.tienda.webhooks.WebhookPagoService;
import com.codeconheiner.tienda.webhooks.WompiEventoParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
        AdminEnvioController.class, AdminCuponController.class, ReporteController.class,
        WebhookController.class})
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

    @Autowired
    private FakeFacturaRepository facturas;

    @Autowired
    private FakeReporteRepository reportes;

    @Autowired
    private FakeWebhookEventosRepository webhooks;

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

    // ---------- Pasarelas de pago (idempotencia saliente) ----------

    @Test
    @WithMockUser(username = "ana@example.com")
    void crearSesionEnPasarelaDevuelveSesionSimuladaYClaveDeIdempotencia() throws Exception {
        pedidos.limpiar();
        pedidos.agregar(1L, pedido(1L, "PED-1", "PENDIENTE"));
        pagos.limpiar();

        mvc.perform(post("/api/pagos/sesion").contentType("application/json")
                        .content("{\"pedidoId\":1,\"pasarela\":\"WOMPI\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.pasarela").value("WOMPI"))
                .andExpect(jsonPath("$.referencia").value(org.hamcrest.Matchers.startsWith("PED-1-")))
                .andExpect(jsonPath("$.sesionId").value(org.hamcrest.Matchers.startsWith("SIM-")))
                .andExpect(jsonPath("$.claveIdempotencia").isNotEmpty());
    }

    @Test
    @WithMockUser(username = "ana@example.com")
    void reintentarConLaMismaClaveDevuelveElMismoIntento() throws Exception {
        pedidos.limpiar();
        pedidos.agregar(1L, pedido(1L, "PED-1", "PENDIENTE"));
        pagos.limpiar();

        String clave = "11111111-2222-3333-4444-555555555555";
        String cuerpo = "{\"pedidoId\":1,\"pasarela\":\"WOMPI\",\"claveIdempotencia\":\"" + clave + "\"}";

        mvc.perform(post("/api/pagos/sesion").contentType("application/json").content(cuerpo))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.claveIdempotencia").value(clave))
                .andExpect(jsonPath("$.pagoId").value(1));

        mvc.perform(post("/api/pagos/sesion").contentType("application/json").content(cuerpo))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.pagoId").value(1))
                .andExpect(jsonPath("$.sesionId").value("SIM-PED-1-11111111"));

        assertThat(pagos.total()).isEqualTo(1);
    }

    @Test
    @WithMockUser(username = "ana@example.com")
    void crearSesionConPasarelaDesconocidaDevuelve400() throws Exception {
        pedidos.limpiar();
        pedidos.agregar(1L, pedido(1L, "PED-1", "PENDIENTE"));

        mvc.perform(post("/api/pagos/sesion").contentType("application/json")
                        .content("{\"pedidoId\":1,\"pasarela\":\"PAYU\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Pasarela no soportada"));
    }

    @Test
    @WithMockUser(username = "ana@example.com")
    void crearSesionDePedidoYaPagadoDevuelve409() throws Exception {
        pedidos.limpiar();
        pedidos.agregar(1L, pedido(1L, "PED-1", "PAGADO"));

        mvc.perform(post("/api/pagos/sesion").contentType("application/json")
                        .content("{\"pedidoId\":1,\"pasarela\":\"WOMPI\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("El pedido no admite pagos en el estado actual"));
    }

    // ---------- Webhooks de pasarela ----------

    @Test
    void webhookConFirmaInvalidaDevuelve401() throws Exception {
        mvc.perform(post("/api/webhooks/wompi").contentType("application/json")
                        .header("X-Event-Checksum", "firma-falsa")
                        .content("{\"event\":\"transaction.updated\",\"data\":{}}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Firma del webhook inválida"));
    }

    @Test
    void webhookAprobadoDeWompiMarcaElPedidoComoPagado() throws Exception {
        pedidos.limpiar();
        pagos.limpiar();
        webhooks.limpiar();
        pedidos.agregar(1L, pedido(1L, "PED-1", "PENDIENTE"));
        pagos.crear(1L, "WOMPI", "PED-1-abc12345", "PENDIENTE", new BigDecimal("2380"), "clave-webhook");

        String cuerpo = "{\"event\":\"transaction.updated\",\"data\":{\"id\":\"tx-1\","
                + "\"status\":\"APPROVED\",\"reference\":\"PED-1-abc12345\","
                + "\"amount_in_cents\":238000}}";

        mvc.perform(post("/api/webhooks/wompi").contentType("application/json")
                        .header("X-Event-Checksum", Hashes.sha256Hex(cuerpo + "wompi-test-secreto"))
                        .content(cuerpo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("procesado"));

        assertThat(pagos.buscarPorReferencia("PED-1-abc12345").orElseThrow().estado())
                .isEqualTo("APROBADO");
        assertThat(pedidos.obtenerPorId(1L, 1L).orElseThrow().estado()).isEqualTo("PAGADO");
    }

    @Test
    void webhookDuplicadoSeIgnoraYNoReprocesa() throws Exception {
        pedidos.limpiar();
        pagos.limpiar();
        webhooks.limpiar();
        pedidos.agregar(1L, pedido(1L, "PED-1", "PENDIENTE"));
        pagos.crear(1L, "WOMPI", "PED-1-abc12345", "PENDIENTE", new BigDecimal("2380"), "clave-webhook");

        String cuerpo = "{\"event\":\"transaction.updated\",\"data\":{\"id\":\"tx-1\","
                + "\"status\":\"APPROVED\",\"reference\":\"PED-1-abc12345\","
                + "\"amount_in_cents\":238000}}";
        String firma = Hashes.sha256Hex(cuerpo + "wompi-test-secreto");

        mvc.perform(post("/api/webhooks/wompi").contentType("application/json")
                        .header("X-Event-Checksum", firma).content(cuerpo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("procesado"));

        mvc.perform(post("/api/webhooks/wompi").contentType("application/json")
                        .header("X-Event-Checksum", firma).content(cuerpo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("duplicado"));
    }

    @Test
    void webhookRechazadoDejaElPedidoPendiente() throws Exception {
        pedidos.limpiar();
        pagos.limpiar();
        webhooks.limpiar();
        pedidos.agregar(1L, pedido(1L, "PED-1", "PENDIENTE"));
        pagos.crear(1L, "WOMPI", "PED-1-abc12345", "PENDIENTE", new BigDecimal("2380"), "clave-webhook");

        String cuerpo = "{\"event\":\"transaction.updated\",\"data\":{\"id\":\"tx-2\","
                + "\"status\":\"DECLINED\",\"reference\":\"PED-1-abc12345\","
                + "\"amount_in_cents\":238000}}";

        mvc.perform(post("/api/webhooks/wompi").contentType("application/json")
                        .header("X-Event-Checksum", Hashes.sha256Hex(cuerpo + "wompi-test-secreto"))
                        .content(cuerpo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("procesado"));

        assertThat(pagos.buscarPorReferencia("PED-1-abc12345").orElseThrow().estado())
                .isEqualTo("RECHAZADO");
        assertThat(pedidos.obtenerPorId(1L, 1L).orElseThrow().estado()).isEqualTo("PENDIENTE");
    }

    @Test
    void webhookDeEpaycoConFirmaValidaProcesaElPago() throws Exception {
        pedidos.limpiar();
        pagos.limpiar();
        webhooks.limpiar();
        pedidos.agregar(1L, pedido(1L, "PED-1", "PENDIENTE"));
        pagos.crear(1L, "EPAYCO", "PED-1-abc12345", "PENDIENTE", new BigDecimal("2380"), "clave-webhook");

        String cuerpo = "{\"type\":\"charge\",\"data\":{\"x_transaction_id\":\"tx-9\","
                + "\"x_invoice\":\"PED-1-abc12345\",\"x_cod_response\":\"1\","
                + "\"x_amount\":\"2380.00\"}}";

        mvc.perform(post("/api/webhooks/epayco").contentType("application/json")
                        .header("x-signature", Hashes.sha256Hex(cuerpo + "epayco-test-llave"))
                        .content(cuerpo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("procesado"));

        assertThat(pedidos.obtenerPorId(1L, 1L).orElseThrow().estado()).isEqualTo("PAGADO");
    }

    // ---------- Facturas ----------

    @Test
    @WithMockUser(username = "ana@example.com")
    void facturaDePedidoPagadoSeEmiteYEsIdempotente() throws Exception {
        pedidos.limpiar();
        facturas.limpiar();
        pedidos.agregar(1L, pedido(1L, "PED-1", "PAGADO"));
        facturas.agregarEnvio(1L, "Ana", "Antioquia", "Medellin", "Calle 1 # 2-3");

        mvc.perform(get("/api/pedidos/1/factura"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numero").value("FAC-2026-000001"))
                .andExpect(jsonPath("$.tienda").value("Tienda de prueba"))
                .andExpect(jsonPath("$.numeroPedido").value("PED-1"))
                .andExpect(jsonPath("$.destinatario").value("Ana"))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.total").value(2380));

        mvc.perform(get("/api/pedidos/1/factura"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numero").value("FAC-2026-000001"));

        assertThat(facturas.total()).isEqualTo(1);
    }

    @Test
    @WithMockUser(username = "ana@example.com")
    void facturaDePedidoNoPagadoDevuelve409() throws Exception {
        pedidos.limpiar();
        pedidos.agregar(1L, pedido(1L, "PED-1", "PENDIENTE"));

        mvc.perform(get("/api/pedidos/1/factura"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("La factura solo se emite para pedidos pagados"));
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    void adminEmiteFacturaDeCualquierPedido() throws Exception {
        pedidos.limpiar();
        facturas.limpiar();
        pedidos.agregar(5L, pedido(3L, "PED-3", "ENTREGADO"));

        mvc.perform(get("/api/admin/pedidos/3/factura"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numero").value("FAC-2026-000001"))
                .andExpect(jsonPath("$.numeroPedido").value("PED-3"));
    }

    // ---------- Reportes ----------

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    void reporteDeVentasDevuelveResumen() throws Exception {
        mvc.perform(get("/api/admin/reportes/ventas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pedidos").value(4))
                .andExpect(jsonPath("$.pedidosPagados").value(3))
                .andExpect(jsonPath("$.porEstado.PAGADO").value(3))
                .andExpect(jsonPath("$.topProductos.length()").value(1))
                .andExpect(jsonPath("$.ventasPorDia[0].dia").value("2026-01-01"));
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    void reporteDeVentasConRangoInvalidoDevuelve400() throws Exception {
        mvc.perform(get("/api/admin/reportes/ventas")
                        .param("desde", "2026-02-01T00:00:00Z")
                        .param("hasta", "2026-01-01T00:00:00Z"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("El rango de fechas no es válido"));
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    void reporteDeInventarioDevuelveTotales() throws Exception {
        mvc.perform(get("/api/admin/reportes/inventario"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productos").value(3))
                .andExpect(jsonPath("$.valorInventario").value(500000))
                .andExpect(jsonPath("$.bajoUmbral.length()").value(1))
                .andExpect(jsonPath("$.bajoUmbral[0].sku").value("SKU-1"));
    }

    @Test
    @WithMockUser(username = "ana@example.com")
    void reportesRequierenRolAdmin() throws Exception {
        mvc.perform(get("/api/admin/reportes/ventas")).andExpect(status().isForbidden());
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
                    new TiendaProperties.Cors(List.of("http://localhost:5173")),
                    new TiendaProperties.RateLimit(300, 10, 60),
                    new TiendaProperties.Pagos("simulado",
                            new TiendaProperties.Pagos.Wompi(null, null, null, "wompi-test-secreto", null),
                            new TiendaProperties.Pagos.Epayco(null, null, null, "epayco-test-llave")),
                    new TiendaProperties.Captcha("test-secret", null),
                    new TiendaProperties.Mail("no-reply@test.local", "http://localhost:8080"),
                    new TiendaProperties.Storage("http://localhost:9000", "key", "secret", "bucket", "http://localhost:9000"));
        }

        @Bean
        com.codeconheiner.tienda.catalogo.storage.ImageStorage imageStorage() {
            return new com.codeconheiner.tienda.catalogo.storage.UrlOnlyImageStorage();
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
        CalculadoraTotales calculadoraTotales(TiendaProperties props, CuponService cuponService) {
            return new CalculadoraTotales(props, cuponService);
        }

        @Bean
        PedidoService pedidoService(PedidoRepository repo, CarritoRepository carritoRepository,
                DireccionService direccionService, CalculadoraTotales calculadoraTotales,
                com.codeconheiner.tienda.seguridad.Auditoria auditoria) {
            return new PedidoService(repo, carritoRepository, direccionService, calculadoraTotales, auditoria);
        }

        @Bean
        FakePagoRepository pagoRepository(FakePedidoRepository pedidoRepository) {
            return new FakePagoRepository(pedidoRepository);
        }

        @Bean
        PasarelaRegistry pasarelaRegistry(TiendaProperties properties) {
            return new PasarelaRegistry(
                    List.of(new WompiPasarela(properties), new EpaycoPasarela(properties)));
        }

        @Bean
        PagoService pagoService(PagoRepository repo, PedidoRepository pedidoRepository,
                PasarelaRegistry pasarelaRegistry, UsuarioRepository usuarios, TiendaProperties properties) {
            return new PagoService(repo, pedidoRepository, pasarelaRegistry, usuarios, properties);
        }

        @Bean
        FakeFacturaRepository facturaRepository() {
            return new FakeFacturaRepository();
        }

        @Bean
        FacturaService facturaService(FacturaRepository repo, PedidoRepository pedidoRepository,
                TiendaProperties properties, com.codeconheiner.tienda.seguridad.Auditoria auditoria) {
            return new FacturaService(repo, pedidoRepository, properties, auditoria);
        }

        @Bean
        FakeReporteRepository reporteRepository() {
            return new FakeReporteRepository();
        }

        @Bean
        ReporteService reporteService(ReporteRepository repo, InventarioRepository inventarioRepository) {
            return new ReporteService(repo, inventarioRepository);
        }

        @Bean
        FirmaWebhookRegistry firmaWebhookRegistry(TiendaProperties properties) {
            return new FirmaWebhookRegistry(
                    List.of(new WompiFirmaWebhook(properties), new EpaycoFirmaWebhook(properties)));
        }

        @Bean
        EventoWebhookParser wompiEventoParser(ObjectMapper objectMapper) {
            return new WompiEventoParser(objectMapper);
        }

        @Bean
        EventoWebhookParser epaycoEventoParser(ObjectMapper objectMapper) {
            return new EpaycoEventoParser(objectMapper);
        }

        @Bean
        FakeWebhookEventosRepository webhookEventosRepository() {
            return new FakeWebhookEventosRepository();
        }

        @Bean
        WebhookPagoService webhookPagoService(WebhookEventosRepository eventos, PagoRepository pagos,
                com.codeconheiner.tienda.seguridad.Auditoria auditoria) {
            return new WebhookPagoService(eventos, pagos, auditoria);
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

        @Override
        public Optional<Usuario> buscarPorId(Long id) {
            if (id == null) {
                return Optional.empty();
            }
            return Optional.of(new Usuario(id, "Ana", "ana@example.com", "hash", "CLIENTE", true));
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

        @Override
        public List<StockBajoResponse> listarStockBajo() {
            return List.of(new StockBajoResponse(1L, "SKU-1", "Producto", 1, 0, 5));
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

        private final Map<Long, PagoResponse> datos = new LinkedHashMap<>();
        private final Map<Long, String> sesiones = new HashMap<>();
        private final Map<String, Long> porClave = new HashMap<>();
        private final Map<String, Long> porReferencia = new HashMap<>();
        private final FakePedidoRepository pedidos;
        private long siguiente = 1;

        FakePagoRepository(FakePedidoRepository pedidos) {
            super((JdbcTemplate) null);
            this.pedidos = pedidos;
        }

        void limpiar() {
            datos.clear();
            sesiones.clear();
            porClave.clear();
            porReferencia.clear();
            siguiente = 1;
        }

        void agregar(PagoResponse pago) {
            datos.put(pago.id(), pago);
            porReferencia.put(pago.referencia(), pago.id());
            siguiente = Math.max(siguiente, pago.id() + 1);
        }

        int total() {
            return datos.size();
        }

        @Override
        public List<PagoResponse> listarPorPedido(Long pedidoId) {
            return datos.values().stream().filter(p -> p.pedidoId().equals(pedidoId)).toList();
        }

        @Override
        public PagoResponse crear(Long pedidoId, String pasarela, String referencia, String estado,
                BigDecimal monto) {
            return crear(pedidoId, pasarela, referencia, estado, monto, null);
        }

        @Override
        public PagoResponse crear(Long pedidoId, String pasarela, String referencia, String estado,
                BigDecimal monto, String idempotencyKey) {
            PagoResponse pago = new PagoResponse(siguiente++, pedidoId, pasarela, referencia, estado, monto,
                    OffsetDateTime.now());
            datos.put(pago.id(), pago);
            porReferencia.put(referencia, pago.id());
            if (idempotencyKey != null) {
                porClave.put(idempotencyKey, pago.id());
            }
            // simula el trigger trg_pago_aprobado de V3
            if ("APROBADO".equals(estado)) {
                pedidos.marcarPagado(pedidoId);
            }
            return pago;
        }

        @Override
        public Optional<PagoDetalle> buscarPorIdempotencyKey(String clave) {
            return Optional.ofNullable(porClave.get(clave)).map(this::detalle);
        }

        @Override
        public Optional<PagoDetalle> buscarPorReferencia(String referencia) {
            return Optional.ofNullable(porReferencia.get(referencia)).map(this::detalle);
        }

        @Override
        public void guardarSesion(Long pagoId, String sesionId) {
            sesiones.put(pagoId, sesionId);
        }

        @Override
        public int actualizarEstado(Long pagoId, String estado) {
            PagoResponse pago = datos.get(pagoId);
            if (pago == null) {
                return 0;
            }
            datos.put(pagoId, new PagoResponse(pago.id(), pago.pedidoId(), pago.pasarela(),
                    pago.referencia(), estado, pago.monto(), pago.creadoEn()));
            // simula el trigger trg_pago_aprobado de V3
            if ("APROBADO".equals(estado)) {
                pedidos.marcarPagado(pago.pedidoId());
            }
            return 1;
        }

        private PagoDetalle detalle(Long pagoId) {
            PagoResponse p = datos.get(pagoId);
            String clave = porClave.entrySet().stream()
                    .filter(e -> e.getValue().equals(pagoId))
                    .map(Map.Entry::getKey)
                    .findFirst()
                    .orElse(null);
            return new PagoDetalle(p.id(), p.pedidoId(), p.pasarela(), p.referencia(), p.estado(),
                    p.monto(), sesiones.get(pagoId), clave);
        }
    }

    /** Facturas en memoria: la emisión es idempotente por pedido. */
    static class FakeFacturaRepository extends FacturaRepository {

        private final Map<Long, FacturaEmision> emitidas = new LinkedHashMap<>();
        private final Map<Long, EnvioCopia> envios = new HashMap<>();
        private long siguiente = 1;

        FakeFacturaRepository() {
            super((JdbcTemplate) null);
        }

        void limpiar() {
            emitidas.clear();
            envios.clear();
            siguiente = 1;
        }

        void agregarEnvio(Long pedidoId, String destinatario, String departamento, String ciudad,
                String direccion) {
            envios.put(pedidoId, new EnvioCopia(destinatario, "3001234567", departamento, ciudad, direccion));
        }

        int total() {
            return emitidas.size();
        }

        @Override
        public Optional<FacturaEmision> buscarPorPedido(Long pedidoId) {
            return Optional.ofNullable(emitidas.get(pedidoId));
        }

        @Override
        public Optional<EnvioCopia> buscarEnvioPedido(Long pedidoId) {
            return Optional.ofNullable(envios.get(pedidoId));
        }

        @Override
        public FacturaEmision emitir(Long pedidoId, BigDecimal total) {
            FacturaEmision existente = emitidas.get(pedidoId);
            if (existente != null) {
                return existente;
            }
            FacturaEmision nueva = new FacturaEmision(siguiente,
                    "FAC-2026-" + String.format("%06d", siguiente), total, OffsetDateTime.now());
            siguiente++;
            emitidas.put(pedidoId, nueva);
            return nueva;
        }
    }

    /** Reportes con datos fijos para verificar el contrato HTTP. */
    static class FakeReporteRepository extends ReporteRepository {

        FakeReporteRepository() {
            super((JdbcTemplate) null);
        }

        @Override
        public ResumenVentasResponse resumen(OffsetDateTime desde, OffsetDateTime hasta) {
            return new ResumenVentasResponse(desde, hasta, 4, 3, new BigDecimal("48000"),
                    new BigDecimal("12000"), Map.of("PAGADO", 3, "PENDIENTE", 1),
                    List.of(new ResumenVentasResponse.TopProducto(1L, "Producto", 5,
                            new BigDecimal("30000"))),
                    List.of(new ResumenVentasResponse.VentaDiaria("2026-01-01", 4,
                            new BigDecimal("48000"))));
        }

        @Override
        public TotalesInventario totalesInventario() {
            return new TotalesInventario(3, 10, new BigDecimal("500000"));
        }
    }

    /** Deduplicación de webhooks en memoria: el evento solo se registra la primera vez. */
    static class FakeWebhookEventosRepository extends WebhookEventosRepository {

        private final Set<String> eventos = new HashSet<>();

        FakeWebhookEventosRepository() {
            super((JdbcTemplate) null);
        }

        void limpiar() {
            eventos.clear();
        }

        @Override
        public boolean registrarSiNuevo(String pasarela, String eventoId) {
            return eventos.add(pasarela + ":" + eventoId);
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
