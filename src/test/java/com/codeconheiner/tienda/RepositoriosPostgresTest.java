package com.codeconheiner.tienda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.codeconheiner.tienda.auth.RefreshTokenRepository;
import com.codeconheiner.tienda.carrito.CarritoRepository;
import com.codeconheiner.tienda.carrito.dto.CarritoItemResponse;
import com.codeconheiner.tienda.common.ApiException;
import com.codeconheiner.tienda.config.TareasSeguridad;
import com.codeconheiner.tienda.cupones.CuponService;
import com.codeconheiner.tienda.cupones.dto.CuponRequest;
import com.codeconheiner.tienda.cupones.dto.CuponResponse;
import com.codeconheiner.tienda.direcciones.DireccionService;
import com.codeconheiner.tienda.direcciones.dto.DireccionRequest;
import com.codeconheiner.tienda.direcciones.dto.DireccionResponse;
import com.codeconheiner.tienda.envios.EnvioRepository;
import com.codeconheiner.tienda.envios.dto.EnvioResponse;
import com.codeconheiner.tienda.facturas.EnvioCopia;
import com.codeconheiner.tienda.facturas.FacturaEmision;
import com.codeconheiner.tienda.facturas.FacturaRepository;
import com.codeconheiner.tienda.inventario.InventarioRepository;
import com.codeconheiner.tienda.inventario.dto.StockBajoResponse;
import com.codeconheiner.tienda.inventario.dto.StockInfoResponse;
import com.codeconheiner.tienda.pagos.PagoDetalle;
import com.codeconheiner.tienda.pagos.PagoRepository;
import com.codeconheiner.tienda.pagos.dto.PagoResponse;
import com.codeconheiner.tienda.pedidos.PedidoRepository;
import com.codeconheiner.tienda.pedidos.PedidoService;
import com.codeconheiner.tienda.pedidos.dto.PedidoRequest;
import com.codeconheiner.tienda.pedidos.dto.PedidoResponse;
import com.codeconheiner.tienda.reportes.ReporteRepository;
import com.codeconheiner.tienda.reportes.dto.ResumenVentasResponse;
import com.codeconheiner.tienda.resenas.ResenaRepository;
import com.codeconheiner.tienda.resenas.dto.ResenaRequest;
import com.codeconheiner.tienda.resenas.dto.ResenaResponse;
import com.codeconheiner.tienda.seguridad.Auditoria;
import com.codeconheiner.tienda.webhooks.WebhookEventosRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Pruebas de integración contra la BD PostgreSQL real (localhost:5432/tienda_db).
 * Valida el SQL de los repositorios nuevos y las reglas de V3 (triggers/funciones),
 * que no se ejercitan con los tests WebMvc basados en fakes in-memory.
 */
@SpringBootTest
class RepositoriosPostgresTest {

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:postgresql://localhost:5432/tienda_db");
        registry.add("spring.datasource.username", () -> "tienda_user");
        registry.add("spring.datasource.password", () -> "tienda_pass");
        registry.add("tienda.jwt.secret", () -> "repositorios-tests-jwt-secret-at-least-32-bytes");
        registry.add("tienda.admin.email", () -> "admin@tienda.local");
        registry.add("tienda.admin.password", () -> "");
    }

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TransactionTemplate tx;

        private final List<Long> usuariosCreados = new ArrayList<>();
        private final List<Long> carritosCreados = new ArrayList<>();
        private final List<Long> productosCreados = new ArrayList<>();
        private final List<Long> pedidosCreados = new ArrayList<>();

    @Autowired
    private CarritoRepository carritoRepo;

    @Autowired
    private InventarioRepository inventarioRepo;

    @Autowired
    private PedidoRepository pedidoRepo;

    @Autowired
    private PagoRepository pagoRepo;

    @Autowired
    private FacturaRepository facturaRepo;

    @Autowired
    private ReporteRepository reporteRepo;

    @Autowired
    private WebhookEventosRepository webhookEventosRepo;

    @Autowired
    private EnvioRepository envioRepo;

    @Autowired
    private DireccionService direccionService;

    @Autowired
    private CuponService cuponService;

    @Autowired
    private PedidoService pedidoService;

    @Autowired
    private ResenaRepository resenaRepo;

    @Autowired
    private RefreshTokenRepository refreshTokenRepo;

    @Autowired
    private Auditoria auditoria;

    @Autowired
    private com.codeconheiner.tienda.catalogo.ProductoRepository productoRepo;

    @Autowired
    private TareasSeguridad tareaExpiracion;

    private final List<Long> cuponesCreados = new ArrayList<>();

        @BeforeEach
        void verificarBaseAuxiliar() {
                assertThat(jdbc.queryForObject("SELECT current_database()", String.class)).isEqualTo("tienda_db");
                        limpiarFixturesDeEjecucionesAnteriores();
        }

        @AfterEach
        void limpiarFixturesCreadosPorLaPrueba() {
                for (Long pedidoId : pedidosCreados) {
                        jdbc.update("DELETE FROM envios WHERE pedido_id = ?", pedidoId);
                        jdbc.update("DELETE FROM pagos WHERE pedido_id = ?", pedidoId);
                        jdbc.update("DELETE FROM pedidos WHERE id = ?", pedidoId);
                }
                jdbc.update("DELETE FROM webhook_eventos WHERE evento_id LIKE 'IT-%'");
                jdbc.update("DELETE FROM auditoria WHERE accion LIKE 'IT-%'");
                for (Long carritoId : carritosCreados) {
                        jdbc.update("DELETE FROM carritos WHERE id = ?", carritoId);
                }
                for (Long productoId : productosCreados) {
                        jdbc.update("DELETE FROM productos WHERE id = ?", productoId);
                }
                for (Long cuponId : cuponesCreados) {
                        jdbc.update("DELETE FROM cupones WHERE id = ?", cuponId);
                }
                for (Long usuarioId : usuariosCreados) {
                        jdbc.update("DELETE FROM usuarios WHERE id = ?", usuarioId);
                }
                pedidosCreados.clear();
                carritosCreados.clear();
                productosCreados.clear();
                cuponesCreados.clear();
                usuariosCreados.clear();
        }
    
            private void limpiarFixturesDeEjecucionesAnteriores() {
                String pedidosDePrueba = """
                        SELECT p.id FROM pedidos p
                        JOIN usuarios u ON u.id = p.usuario_id
                        WHERE u.email LIKE 'it-%@tienda.test'
                        """;
                jdbc.update("DELETE FROM envios WHERE pedido_id IN (" + pedidosDePrueba + ")");
                jdbc.update("DELETE FROM pagos WHERE pedido_id IN (" + pedidosDePrueba + ")");
                jdbc.update("DELETE FROM pedidos WHERE id IN (" + pedidosDePrueba + ")");
                jdbc.update("DELETE FROM carritos WHERE usuario_id IN "
                        + "(SELECT id FROM usuarios WHERE email LIKE 'it-%@tienda.test')");
                jdbc.update("DELETE FROM cupones WHERE codigo LIKE 'IT-%'");
                jdbc.update("DELETE FROM webhook_eventos WHERE evento_id LIKE 'IT-%'");
                jdbc.update("DELETE FROM auditoria WHERE accion LIKE 'IT-%'");
                jdbc.update("DELETE FROM productos WHERE sku LIKE 'IT-%' AND nombre LIKE 'Producto IT %'");
                jdbc.update("DELETE FROM usuarios WHERE email LIKE 'it-%@tienda.test'");
            }

    @Test
    void migracionCreoEsquemaTriggersYFunciones() {
        Integer tablas = jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_type='BASE TABLE'",
                Integer.class);
        Integer triggers = jdbc.queryForObject(
                "SELECT count(*) FROM pg_trigger WHERE NOT tgisinternal", Integer.class);
        Integer funciones = jdbc.queryForObject("""
                SELECT count(*) FROM pg_proc p JOIN pg_namespace n ON n.oid = p.pronamespace
                WHERE n.nspname = 'public'
                """, Integer.class);

        assertThat(tablas).isGreaterThanOrEqualTo(19);
        assertThat(triggers).isGreaterThanOrEqualTo(16);
        assertThat(funciones).isGreaterThanOrEqualTo(15);
        Integer refreshTokens = jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_schema='public'"
                        + " AND table_name='refresh_tokens'",
                Integer.class);
        assertThat(refreshTokens).isEqualTo(1);
    }

    @Test
    void inventarioRepositoryObtieneStockReal() {
        long productoId = crearProductoUnico(10, 3);

        StockInfoResponse stock = inventarioRepo.obtenerStock(productoId).orElseThrow();

        assertThat(stock.productoId()).isEqualTo(productoId);
        assertThat(stock.stock()).isEqualTo(10);
        assertThat(stock.stockReservado()).isZero();
        assertThat(stock.stockDisponible()).isEqualTo(10);
        assertThat(stock.stockMinimo()).isEqualTo(3);
    }

    @Test
    void inventarioRepositoryDevuelveVacioParaProductoInexistente() {
        assertThat(inventarioRepo.obtenerStock(99999999L)).isEmpty();
    }

    @Test
    void productoRepositoryGuardaYDevuelveLaClaveDeImagen() {
        long productoId = crearProductoUnico(3, 1);

        assertThat(productoRepo.obtenerImagenKey(productoId)).isNull();

        assertThat(productoRepo.actualizarImagenKey(productoId, "productos/" + productoId + "/abc.png"))
                .isEqualTo(1);
        assertThat(productoRepo.obtenerImagenKey(productoId)).isEqualTo("productos/" + productoId + "/abc.png");
        assertThat(productoRepo.buscarPorId(productoId).orElseThrow().imagenKey())
                .isEqualTo("productos/" + productoId + "/abc.png");

        // quitar la imagen deja la columna en null
        productoRepo.actualizarImagenKey(productoId, null);
        assertThat(productoRepo.obtenerImagenKey(productoId)).isNull();
    }

    @Test
    void carritoRepositoryGestionaItemsContraStockReal() {
        long usuarioId = crearUsuarioUnico();
        long productoId = crearProductoUnico(5, 1);

        // crear carrito
        assertThat(carritoRepo.obtenerIdCarritoPorUsuario(usuarioId)).isEmpty();
        long carritoId = crearCarrito(usuarioId);
        assertThat(carritoRepo.obtenerIdCarritoPorUsuario(usuarioId)).contains(carritoId);

        // agregar item (el trigger valida stock disponible)
        carritoRepo.agregarItem(carritoId, productoId, 2);
        List<CarritoItemResponse> items = carritoRepo.listarItems(carritoId);
        assertThat(items).hasSize(1);
        assertThat(items.get(0).sku()).isEqualTo(jdbc.queryForObject(
                "SELECT sku FROM productos WHERE id = ?", String.class, productoId));
        assertThat(items.get(0).cantidad()).isEqualTo(2);
        assertThat(items.get(0).subtotal()).isEqualByComparingTo(new BigDecimal("2000"));
        assertThat(items.get(0).stockDisponible()).isEqualTo(5);

        // acumular cantidad del mismo producto (ON CONFLICT DO UPDATE)
        carritoRepo.agregarItem(carritoId, productoId, 1);
        assertThat(carritoRepo.listarItems(carritoId).get(0).cantidad()).isEqualTo(3);

        // actualizar, subtotal y vaciar
        carritoRepo.actualizarCantidad(carritoId, productoId, 4);
        assertThat(carritoRepo.obtenerSubtotal(carritoId).orElseThrow())
                .isEqualByComparingTo(new BigDecimal("4000"));

        carritoRepo.eliminarItem(carritoId, productoId);
        assertThat(carritoRepo.listarItems(carritoId)).isEmpty();

                carritoRepo.agregarItem(carritoId, productoId, 1);
                carritoRepo.vaciarCarrito(carritoId);
                assertThat(carritoRepo.listarItems(carritoId)).isEmpty();
    }

    @Test
    void carritoRepositoryRechazaCantidadQueSuperaStockReal() {
        long usuarioId = crearUsuarioUnico();
        long productoId = crearProductoUnico(1, 1);
        long carritoId = crearCarrito(usuarioId);

        // el trigger de V3 lanza P0001 al superar el stock disponible
        org.assertj.core.api.Assertions.catchThrowableOfType(
                () -> carritoRepo.agregarItem(carritoId, productoId, 2),
                org.springframework.dao.DataAccessException.class);

        assertThat(carritoRepo.listarItems(carritoId)).isEmpty();
    }

    @Test
    void pedidoRepositoryCreaPedidoReservandoStockYListandoItems() {
        long usuarioId = crearUsuarioUnico();
        long productoId = crearProductoUnico(8, 2);
        BigDecimal precio = jdbc.queryForObject(
                "SELECT precio FROM productos WHERE id = ?", BigDecimal.class, productoId);

        int antes = jdbc.queryForObject(
                "SELECT stock_reservado FROM inventario WHERE producto_id = ?", Integer.class, productoId);
        BigDecimal subtotal = precio.multiply(BigDecimal.valueOf(3));
        BigDecimal iva = subtotal.multiply(new BigDecimal("0.19")).setScale(0, RoundingMode.HALF_UP);
        BigDecimal total = subtotal.add(iva);

        Long pedidoId = tx.execute(status -> {
            Long id = jdbc.queryForObject("""
                    INSERT INTO pedidos (numero, usuario_id, estado, subtotal, descuento, iva, costo_envio, total,
                                         envio_destinatario, envio_telefono, envio_departamento, envio_ciudad,
                                         envio_direccion)
                    VALUES (NULL, ?, 'PENDIENTE', ?, 0, ?, 0, ?, 'Ana Perez', '3001234567',
                            'Bogota', 'Bogota', 'Calle 1 #2-3')
                    RETURNING id
                    """, Long.class, usuarioId, subtotal, iva, total);
            // el trigger de V3 completa sku/nombre/precio/subtotal y reserva stock
            jdbc.update("INSERT INTO pedido_items (pedido_id, producto_id, cantidad) VALUES (?, ?, ?)",
                    id, productoId, 3);
            return id;
        });
                pedidosCreados.add(Objects.requireNonNull(pedidoId));

        // V3: numero generado y reserva_expira_en (15 min)
        String numero = jdbc.queryForObject("SELECT numero FROM pedidos WHERE id = ?", String.class, pedidoId);
        OffsetDateTime reserva = jdbc.queryForObject("SELECT reserva_expira_en FROM pedidos WHERE id = ?",
                OffsetDateTime.class, pedidoId);
        assertThat(numero).startsWith("PED-");
        assertThat(reserva).isAfter(OffsetDateTime.now().plusMinutes(10));

        // V3: la reserva de stock se incrementó en 3
        int despues = jdbc.queryForObject(
                "SELECT stock_reservado FROM inventario WHERE producto_id = ?", Integer.class, productoId);
        assertThat(despues).isEqualTo(antes + 3);

        // repositorio: listado y detalle con items (valida los JOIN de item SQL)
        List<PedidoResponse> pedidos = pedidoRepo.listarPorUsuario(usuarioId);
        assertThat(pedidos).hasSize(1);
        assertThat(pedidos.get(0).numero()).isEqualTo(numero);
        assertThat(pedidos.get(0).subtotal()).isEqualByComparingTo(subtotal);
        assertThat(pedidos.get(0).total()).isEqualByComparingTo(total);
        assertThat(pedidos.get(0).items()).hasSize(1);
        assertThat(pedidos.get(0).items().get(0).sku())
                .isEqualTo(jdbc.queryForObject("SELECT sku FROM productos WHERE id = ?", String.class, productoId));
        assertThat(pedidos.get(0).items().get(0).subtotal()).isEqualByComparingTo(subtotal);

        PedidoResponse detalle = pedidoRepo.obtenerPorId(usuarioId, pedidoId).orElseThrow();
        assertThat(detalle.items()).hasSize(1);
        assertThat(pedidoRepo.obtenerPorId(crearUsuarioUnico(), pedidoId)).isEmpty();
    }

    @Test
    void pagoRepositoryRegistraYListaPagosReales() {
        long usuarioId = crearUsuarioUnico();
        long productoId = crearProductoUnico(5, 1);
        long pedidoId = crearPedido(usuarioId, productoId, 1);

        long total = jdbc.queryForObject("SELECT total FROM pedidos WHERE id = ?", Long.class, pedidoId);

        // PENDIENTE no valida monto
        jdbc.update("""
                INSERT INTO pagos (pedido_id, pasarela, referencia, estado, monto)
                VALUES (?, 'wompi', ?, 'PENDIENTE', ?)
                """, pedidoId, "REF-" + UUID.randomUUID().toString().substring(0, 8), total);

        List<PagoResponse> pagos = pagoRepo.listarPorPedido(pedidoId);
        assertThat(pagos).hasSize(1);
        assertThat(pagos.get(0).pedidoId()).isEqualTo(pedidoId);
        assertThat(pagos.get(0).estado()).isEqualTo("PENDIENTE");
        assertThat(pagos.get(0).monto()).isEqualByComparingTo(BigDecimal.valueOf(total));
        assertThat(pagoRepo.listarPorPedido(99999999L)).isEmpty();
    }

    @Test
    void pagoAprobadoMarcaPedidoPagadoYValidaMonto() {
        long usuarioId = crearUsuarioUnico();
        long productoId = crearProductoUnico(5, 1);
        long pedidoId = crearPedido(usuarioId, productoId, 1);
        long total = jdbc.queryForObject("SELECT total FROM pedidos WHERE id = ?", Long.class, pedidoId);

        // V3: pago APROBADO con monto == total => pedido pasa a PAGADO
        jdbc.update("""
                INSERT INTO pagos (pedido_id, pasarela, referencia, estado, monto)
                VALUES (?, 'wompi', ?, 'APROBADO', ?)
                """, pedidoId, "REF-" + UUID.randomUUID().toString().substring(0, 8), total);

        String estado = jdbc.queryForObject("SELECT estado FROM pedidos WHERE id = ?", String.class, pedidoId);
        assertThat(estado).isEqualTo("PAGADO");
    }

    @Test
    void pagoRepositoryGuardaIdempotenciaSesionYBuscaPorReferencia() {
        long usuarioId = crearUsuarioUnico();
        long productoId = crearProductoUnico(5, 1);
        long pedidoId = crearPedido(usuarioId, productoId, 1);
        BigDecimal total = jdbc.queryForObject("SELECT total FROM pedidos WHERE id = ?",
                BigDecimal.class, pedidoId);
        String clave = "IT-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String referencia = "PED-IT-" + UUID.randomUUID().toString().substring(0, 8);

        PagoResponse creado = pagoRepo.crear(pedidoId, "WOMPI", referencia, "PENDIENTE", total, clave);
        pagoRepo.guardarSesion(creado.id(), "SIM-" + referencia);

        PagoDetalle porClave = pagoRepo.buscarPorIdempotencyKey(clave).orElseThrow();
        assertThat(porClave.id()).isEqualTo(creado.id());
        assertThat(porClave.idempotencyKey()).isEqualTo(clave);
        assertThat(porClave.sesionId()).isEqualTo("SIM-" + referencia);

        // el webhook localiza el intento por la referencia que enviamos a la pasarela
        assertThat(pagoRepo.buscarPorReferencia(referencia).orElseThrow().id()).isEqualTo(creado.id());
        assertThat(pagoRepo.buscarPorIdempotencyKey("IT-clave-inexistente")).isEmpty();
        assertThat(pagoRepo.buscarPorReferencia("PED-IT-inexistente")).isEmpty();

        // V5: la clave de idempotencia es única (índice parcial) -> no se duplica el cobro
        assertThat(catchThrowableOfType(() -> pagoRepo.crear(pedidoId, "WOMPI",
                referencia + "-2", "PENDIENTE", total, clave), DataAccessException.class))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    void facturaRepositoryEmiteUnaSolaVezPorPedidoYDevuelveElEnvio() {
        long usuarioId = crearUsuarioUnico();
        long productoId = crearProductoUnico(5, 1);
        long pedidoId = crearPedido(usuarioId, productoId, 1, "PAGADO");
        BigDecimal total = jdbc.queryForObject("SELECT total FROM pedidos WHERE id = ?",
                BigDecimal.class, pedidoId);

        FacturaEmision primera = facturaRepo.emitir(pedidoId, total);
        FacturaEmision segunda = facturaRepo.emitir(pedidoId, total);

        assertThat(segunda.id()).isEqualTo(primera.id());
        assertThat(segunda.numero()).isEqualTo(primera.numero());
        assertThat(primera.numero()).matches("FAC-\\d{4}-\\d{6}");
        assertThat(primera.total()).isEqualByComparingTo(total);
        assertThat(facturaRepo.buscarPorPedido(pedidoId).orElseThrow().numero())
                .isEqualTo(primera.numero());

        // la factura refleja la copia del envío que el pedido guardó al comprar
        EnvioCopia envio = facturaRepo.buscarEnvioPedido(pedidoId).orElseThrow();
        assertThat(envio.destinatario()).isEqualTo("Ana Perez");
        assertThat(envio.ciudad()).isEqualTo("Bogota");
        assertThat(facturaRepo.buscarEnvioPedido(99999999L)).isEmpty();
    }

    @Test
    void webhookEventosRepositoryDeduplicaLasNotificacionesRepetidas() {
        String eventoId = "IT-" + UUID.randomUUID();

        // la restricción UNIQUE (pasarela, evento_id) de V5 ignora las reenviadas
        assertThat(webhookEventosRepo.registrarSiNuevo("WOMPI", eventoId)).isTrue();
        assertThat(webhookEventosRepo.registrarSiNuevo("WOMPI", eventoId)).isFalse();
        // el mismo id bajo otra pasarela es otro evento
        assertThat(webhookEventosRepo.registrarSiNuevo("EPAYCO", eventoId)).isTrue();
    }

    @Test
    void reporteRepositoryAgregaVentasPorRangoYValorDeInventario() {
        long usuarioId = crearUsuarioUnico();
        long productoId = crearProductoUnico(5, 1);
        crearPedido(usuarioId, productoId, 2, "PAGADO");

        ResumenVentasResponse resumen = reporteRepo.resumen(
                OffsetDateTime.now().minusDays(1), OffsetDateTime.now().plusDays(1));
        assertThat(resumen.pedidos()).isGreaterThanOrEqualTo(1);
        assertThat(resumen.porEstado()).containsKey("PAGADO");
        assertThat(resumen.topProductos()).isNotEmpty();
        assertThat(resumen.ventasPorDia()).isNotEmpty();

        ReporteRepository.TotalesInventario inventario = reporteRepo.totalesInventario();
        assertThat(inventario.productos()).isGreaterThanOrEqualTo(1);
        assertThat(inventario.unidades()).isGreaterThanOrEqualTo(2);
        assertThat(inventario.valor()).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    void envioRepositoryCreaYObtieneEnvioDePedidoPagado() {
        long usuarioId = crearUsuarioUnico();
        long productoId = crearProductoUnico(5, 1);
        // pedido directamente PAGADO para poder crear envío (regla de V3)
        long pedidoId = crearPedido(usuarioId, productoId, 1, "PAGADO");

        // el envío se crea explícitamente; al insertarlo V3 propone el pedido a EN_PREPARACION
        jdbc.update("INSERT INTO envios (pedido_id, estado) VALUES (?, 'PREPARANDO')", pedidoId);

        EnvioResponse envio = envioRepo.obtenerPorPedido(pedidoId).orElseThrow();
        // aún no se ha despachado
        assertThat(envio.estado()).isEqualTo("PREPARANDO");
        String estadoTrasInsertar = jdbc.queryForObject("SELECT estado FROM pedidos WHERE id = ?",
                String.class, pedidoId);
        assertThat(estadoTrasInsertar).isEqualTo("EN_PREPARACION");

        // V3: despachar exige transportadora y guía; el trigger setea enviado_en
        jdbc.update("""
                UPDATE envios SET estado = 'EN_CAMINO', transportadora = ?, guia = ?
                WHERE pedido_id = ?
                """, "Servientrega", "GUIA-" + pedidoId, pedidoId);

        EnvioResponse despachado = envioRepo.obtenerPorPedido(pedidoId).orElseThrow();
        assertThat(despachado.estado()).isEqualTo("EN_CAMINO");
        assertThat(despachado.transportadora()).isEqualTo("Servientrega");
        assertThat(despachado.guia()).isEqualTo("GUIA-" + pedidoId);
        assertThat(despachado.enviadoEn()).isNotNull();

        // el estado se propagó al pedido (V3)
        String estadoPedido = jdbc.queryForObject("SELECT estado FROM pedidos WHERE id = ?", String.class, pedidoId);
        assertThat(estadoPedido).isEqualTo("ENVIADO");

        assertThat(envioRepo.obtenerPorPedido(99999999L)).isEmpty();
    }

    // ---------- Nuevos módulos y checkout (V4 + servicios) ----------

    @Test
    void direccionServiceGarantizaUnaSolaPrincipalYGestionaCrud() {
        long usuarioId = crearUsuarioUnico();
        DireccionResponse primera = direccionService.crear(usuarioId, direccionSolicitud("Casa", true));
        DireccionResponse segunda = direccionService.crear(usuarioId, direccionSolicitud("Oficina", true));

        List<DireccionResponse> direcciones = direccionService.listar(usuarioId);
        assertThat(direcciones).hasSize(2);
        List<DireccionResponse> principales = direcciones.stream().filter(DireccionResponse::esPrincipal).toList();
        assertThat(principales).hasSize(1);
        assertThat(principales.get(0).alias()).isEqualTo("Oficina");

        DireccionResponse actualizada = direccionService.actualizar(usuarioId, primera.id(),
                new DireccionRequest("Casa", "Ana Perez", "3009999999", "Cundinamarca", "Chia",
                        "Carrera 2 #3-4", null, false));
        assertThat(actualizada.telefono()).isEqualTo("3009999999");
        assertThat(actualizada.ciudad()).isEqualTo("Chia");

        // una direccion ajena no existe para este usuario
        long otroUsuario = crearUsuarioUnico();
        assertThat(catchThrowableOfType(
                () -> direccionService.eliminar(otroUsuario, primera.id()), ApiException.class))
                .hasMessage("Dirección no encontrada");
        assertThat(direccionService.listar(usuarioId)).hasSize(2);

        direccionService.eliminar(usuarioId, primera.id());
        assertThat(direccionService.listar(usuarioId)).hasSize(1);
    }

    @Test
    void cuponServiceGestionaCrudYCalculaDescuentoComoElTrigger() {
        CuponResponse cupon = cuponService.crear(
                new CuponRequest(codigoCuponUnico(), "PORCENTAJE", new BigDecimal("10"),
                        BigDecimal.ZERO, 100, null, null, true));
        cuponesCreados.add(cupon.id());

        assertThat(cuponService.listar()).extracting(CuponResponse::id).contains(cupon.id());
        assertThat(cuponService.validar(cupon.codigo(), new BigDecimal("5000")).descuento())
                .isEqualByComparingTo(new BigDecimal("500"));
        assertThat(cuponService.calcularDescuento(cupon.id(), new BigDecimal("2500")))
                .isEqualByComparingTo(new BigDecimal("250"));

        CuponResponse actualizado = cuponService.actualizar(cupon.id(),
                new CuponRequest(cupon.codigo(), "MONTO", new BigDecimal("1500"),
                        new BigDecimal("1000"), null, null, null, true));
        assertThat(actualizado.tipo()).isEqualTo("MONTO");
        assertThat(cuponService.calcularDescuento(cupon.id(), new BigDecimal("2000")))
                .isEqualByComparingTo(new BigDecimal("1500"));

        // por debajo del monto mínimo la regla la rechaza en la API (antes de tocar la BD)
        assertThat(catchThrowableOfType(
                () -> cuponService.calcularDescuento(cupon.id(), new BigDecimal("500")), ApiException.class))
                .hasMessageContaining("compra mínima");
    }

    @Test
    void checkoutCreaPedidoReservaStockAplicaCuponYVaciaCarrito() {
        long usuarioId = crearUsuarioUnico();
        long productoId = crearProductoUnico(10, 2);
        long carritoId = crearCarrito(usuarioId);
        carritoRepo.agregarItem(carritoId, productoId, 2);
        long direccionId = direccionService.crear(usuarioId, direccionSolicitud("Casa", true)).id();
        CuponResponse cupon = cuponService.crear(
                new CuponRequest(codigoCuponUnico(), "PORCENTAJE", new BigDecimal("10"),
                        BigDecimal.ZERO, null, null, null, true));
        cuponesCreados.add(cupon.id());

        PedidoResponse pedido = pedidoService.crear(usuarioId, new PedidoRequest(direccionId, cupon.id()));
        pedidosCreados.add(pedido.id());

        assertThat(pedido.numero()).startsWith("PED-");
        assertThat(pedido.estado()).isEqualTo("PENDIENTE");
        assertThat(pedido.subtotal()).isEqualByComparingTo(new BigDecimal("2000"));
        assertThat(pedido.descuento()).isEqualByComparingTo(new BigDecimal("200"));
        assertThat(pedido.iva()).isEqualByComparingTo(new BigDecimal("380"));
        assertThat(pedido.costoEnvio()).isEqualByComparingTo(new BigDecimal("10000"));
        assertThat(pedido.total()).isEqualByComparingTo(new BigDecimal("12180"));
        assertThat(pedido.items()).hasSize(1);
        assertThat(pedido.items().get(0).cantidad()).isEqualTo(2);
        assertThat(pedido.reservaExpiraEn()).isAfter(OffsetDateTime.now().plusMinutes(10));

        assertThat(stockReservado(productoId)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT usos_actuales FROM cupones WHERE id = ?", Integer.class,
                cupon.id())).isEqualTo(1);
        assertThat(carritoRepo.listarItems(carritoId)).isEmpty();
        // los datos de envío se copiaron desde la dirección
        assertThat(jdbc.queryForObject("SELECT envio_direccion FROM pedidos WHERE id = ?", String.class,
                pedido.id())).isEqualTo("Calle 1 #2-3");
    }

    @Test
    void checkoutRechazaCuponInactivoYCarritoVacio() {
        long usuarioId = crearUsuarioUnico();
        long productoId = crearProductoUnico(5, 1);
        long carritoId = crearCarrito(usuarioId);
        long direccionId = direccionService.crear(usuarioId, direccionSolicitud("Casa", true)).id();

        // carrito vacío
        assertThat(catchThrowableOfType(
                () -> pedidoService.crear(usuarioId, new PedidoRequest(direccionId, null)), ApiException.class))
                .hasMessage("El carrito está vacío");

        // cupón inactivo: la regla la resuelve la API con 400 sin crear el pedido
        carritoRepo.agregarItem(carritoId, productoId, 1);
        CuponResponse cupon = cuponService.crear(
                new CuponRequest(codigoCuponUnico(), "PORCENTAJE", new BigDecimal("10"),
                        BigDecimal.ZERO, null, null, null, false));
        cuponesCreados.add(cupon.id());

        assertThat(catchThrowableOfType(
                () -> pedidoService.crear(usuarioId, new PedidoRequest(direccionId, cupon.id())), ApiException.class))
                .hasMessageContaining("inactivo");
        assertThat(pedidoRepo.listarPorUsuario(usuarioId)).isEmpty();
    }

    @Test
    void transicionDeEstadoInvalidaLanzaReglaP0001YLaValidaEsOk() {
        long usuarioId = crearUsuarioUnico();
        long productoId = crearProductoUnico(5, 1);
        long pedidoId = crearPedido(usuarioId, productoId, 1);

        // V3 rechaza PENDIENTE -> ENTREGADO con RAISE EXCEPTION (P0001)
        DataAccessException error = catchThrowableOfType(
                () -> pedidoService.cambiarEstado(pedidoId, "ENTREGADO"), DataAccessException.class);
        assertThat(error).isNotNull();
        assertThat(sqlState(error)).isEqualTo("P0001");
        assertThat(error.getMostSpecificCause()).hasMessageContaining("Transicion de estado no permitida");
        assertThat(jdbc.queryForObject("SELECT estado FROM pedidos WHERE id = ?", String.class, pedidoId))
                .isEqualTo("PENDIENTE");

        // transición válida
        PedidoResponse cancelado = pedidoService.cambiarEstado(pedidoId, "CANCELADO");
        assertThat(cancelado.estado()).isEqualTo("CANCELADO");

        // estado desconocido lo rechaza la API antes de tocar la BD
        assertThat(catchThrowableOfType(
                () -> pedidoService.cambiarEstado(pedidoId, "CUALQUIERA"), ApiException.class))
                .hasMessage("Estado de pedido no válido");
    }

    @Test
    void listarPedidosAdminFiltraPorEstado() {
        long usuarioId = crearUsuarioUnico();
        long productoId = crearProductoUnico(5, 1);
        long pedidoId = crearPedido(usuarioId, productoId, 1);

        assertThat(pedidoRepo.listarTodos(null).stream().map(PedidoResponse::id)).contains(pedidoId);
        assertThat(pedidoRepo.listarTodos("PENDIENTE").stream().map(PedidoResponse::id)).contains(pedidoId);
        assertThat(pedidoRepo.listarTodos("PAGADO").stream().map(PedidoResponse::id)).doesNotContain(pedidoId);
        assertThat(pedidoRepo.obtenerPorIdAdmin(pedidoId)).isPresent();
    }

    @Test
    void expirarReservasCancelaPedidoVencidoYLiberaStock() {
        long usuarioId = crearUsuarioUnico();
        long productoId = crearProductoUnico(5, 1);
        long pedidoId = crearPedido(usuarioId, productoId, 2);
        assertThat(stockReservado(productoId)).isEqualTo(2);

        // adelanta la expiración de la reserva (la función V3 solo afecta vencidos)
        jdbc.update("UPDATE pedidos SET reserva_expira_en = now() - interval '1 minute' WHERE id = ?",
                pedidoId);

        tareaExpiracion.expirarReservas();

        assertThat(jdbc.queryForObject("SELECT estado FROM pedidos WHERE id = ?", String.class, pedidoId))
                .isEqualTo("CANCELADO");
        assertThat(stockReservado(productoId)).isZero();
    }

    @Test
    void resenaExigeCompraEntregadaYSeGestionaContraLaBD() {
        long usuarioId = crearUsuarioUnico();
        long productoId = crearProductoUnico(5, 1);
        long pedidoId = crearPedido(usuarioId, productoId, 1); // PENDIENTE

        // V3: sin pedido ENTREGADO rechaza con P0001
        DataAccessException error = catchThrowableOfType(
                () -> resenaRepo.crear(usuarioId, new ResenaRequest(productoId, 5, "Muy bueno")),
                DataAccessException.class);
        assertThat(error).isNotNull();
        assertThat(sqlState(error)).isEqualTo("P0001");
        assertThat(error.getMostSpecificCause()).hasMessageContaining("Solo puedes resenar productos");

        // con pedido ENTREGADO la reseña se inserta y se lista con el nombre del usuario
        crearPedido(usuarioId, productoId, 1, "ENTREGADO");
        Long resenaId = resenaRepo.crear(usuarioId, new ResenaRequest(productoId, 4, "Bien"));
        ResenaResponse resena = resenaRepo.buscarPropia(resenaId, usuarioId).orElseThrow();
        assertThat(resena.calificacion()).isEqualTo(4);
        assertThat(resena.usuario()).isNotBlank();
        assertThat(resenaRepo.listarPorProducto(productoId)).hasSize(1);

        assertThat(resenaRepo.actualizar(resenaId, usuarioId, new ResenaRequest(productoId, 5, "Excelente")))
                .isEqualTo(1);
        assertThat(resenaRepo.listarPorProducto(productoId).get(0).comentario()).isEqualTo("Excelente");

        // la limpieza borra los pedidos y, en cascada, las reseñas del usuario
        assertThat(resenaRepo.eliminar(resenaId, usuarioId)).isEqualTo(1);
        assertThat(resenaRepo.listarPorProducto(productoId)).isEmpty();
    }

    @Test
    void stockBajoListaProductosEnUmbral() {
        long productoId = crearProductoUnico(2, 5); // disponible 2 <= mínimo 5

        List<StockBajoResponse> bajos = inventarioRepo.listarStockBajo();
        StockBajoResponse encontrado = bajos.stream()
                .filter(s -> s.productoId() == productoId).findFirst().orElseThrow();
        assertThat(encontrado.stock()).isEqualTo(2);
        assertThat(encontrado.stockMinimo()).isEqualTo(5);
        assertThat(encontrado.sku()).startsWith("IT-");
    }

    @Test
    void refreshTokenRepositoryPersisteYRevocaTokens() {
        long usuarioId = crearUsuarioUnico();
        String hash = java.util.HexFormat.of().formatHex(
                java.util.UUID.randomUUID().toString().replace("-", "").getBytes());
        refreshTokenRepo.crearFamilia(usuarioId, hash, OffsetDateTime.now().plusDays(1));

        var guardado = refreshTokenRepo.buscarPorHash(hash).orElseThrow();
        assertThat(guardado.usuarioId()).isEqualTo(usuarioId);
        assertThat(guardado.revocado()).isFalse();
        assertThat(guardado.sesionId()).isNotNull();

        assertThat(refreshTokenRepo.revocar(hash)).isEqualTo(1);
        assertThat(refreshTokenRepo.buscarPorHash(hash).orElseThrow().usado()).isTrue();
        assertThat(refreshTokenRepo.revocar(hash)).isZero();

        String otroHash = java.util.HexFormat.of().formatHex(
                java.util.UUID.randomUUID().toString().replace("-", "").getBytes());
        refreshTokenRepo.crearFamilia(usuarioId, otroHash, OffsetDateTime.now().plusDays(1));
        // El primer token quedó "usado" (no revocado): la revocación masiva lo
        // incluye junto al segundo token todavía vigente.
        assertThat(refreshTokenRepo.revocarTodosDeUsuario(usuarioId)).isEqualTo(2);
        assertThat(refreshTokenRepo.buscarPorHash(otroHash).orElseThrow().revocado()).isTrue();
        assertThat(refreshTokenRepo.buscarPorHash(hash).orElseThrow().revocado()).isTrue();
    }

    @Test
    void refreshTokenFamiliaDetectaReusoYRevocaLaCadenaCompleta() {
        long usuarioId = crearUsuarioUnico();
        String hash1 = java.util.HexFormat.of().formatHex(
                java.util.UUID.randomUUID().toString().replace("-", "").getBytes());

        Long familia = refreshTokenRepo.crearFamilia(usuarioId, hash1, OffsetDateTime.now().plusDays(1));
        var token1 = refreshTokenRepo.buscarPorHash(hash1).orElseThrow();
        assertThat(token1.sesionId()).isEqualTo(familia);

        // Rotación: el token 1 se marca usado y se encadena el token 2 en la misma familia
        String hash2 = java.util.HexFormat.of().formatHex(
                java.util.UUID.randomUUID().toString().replace("-", "").getBytes());
        Long id2 = refreshTokenRepo.crearEnFamilia(usuarioId, token1.sesionId(), hash2,
                OffsetDateTime.now().plusDays(1));
        assertThat(refreshTokenRepo.marcarUsado(hash1, id2)).isEqualTo(1);
        assertThat(refreshTokenRepo.marcarUsado(hash1, id2)).isZero();

        var token2 = refreshTokenRepo.buscarPorHash(hash2).orElseThrow();
        assertThat(token2.sesionId()).isEqualTo(familia);
        assertThat(token2.usado()).isFalse();

        // Reuso del token ya rotado: se revoca la familia entera
        assertThat(refreshTokenRepo.revocarFamilia(usuarioId, token1.sesionId())).isEqualTo(2);
        assertThat(refreshTokenRepo.buscarPorHash(hash2).orElseThrow().revocado()).isTrue();
        assertThat(refreshTokenRepo.revocarFamilia(usuarioId, token1.sesionId())).isZero();
    }

    @Test
    void auditoriaRegistraAccionesSinTumbarLaOperacion() {
        long usuarioId = crearUsuarioUnico();

        auditoria.registrar(usuarioId, "IT-acceso", "pedidos", "42",
                java.util.Map.of("clave", "valor"));
        auditoria.registrarActual("IT-actual", "pedidos", "43", null);

        Integer conUsuario = jdbc.queryForObject(
                "SELECT count(*) FROM auditoria WHERE accion = 'IT-acceso' AND usuario_id = ?",
                Integer.class, usuarioId);
        assertThat(conUsuario).isEqualTo(1);
        String ip = jdbc.queryForObject(
                "SELECT ip FROM auditoria WHERE accion = 'IT-acceso' AND usuario_id = ?",
                String.class, usuarioId);
        assertThat(ip).isNotBlank();
        String detalle = jdbc.queryForObject(
                "SELECT detalle::text FROM auditoria WHERE accion = 'IT-acceso' AND usuario_id = ?",
                String.class, usuarioId);
        assertThat(detalle).contains("valor");

        // Fuera de petición autenticada la auditoría tampoco debe tumbar la operación
        Integer sinUsuario = jdbc.queryForObject(
                "SELECT count(*) FROM auditoria WHERE accion = 'IT-actual'", Integer.class);
        assertThat(sinUsuario).isEqualTo(1);
    }

    // ---------- Helpers ----------

    private int stockReservado(long productoId) {
        return jdbc.queryForObject("SELECT stock_reservado FROM inventario WHERE producto_id = ?",
                Integer.class, productoId);
    }

    private static String sqlState(Throwable error) {
        for (Throwable causa = error; causa != null; causa = causa.getCause()) {
            if (causa instanceof SQLException sql && sql.getSQLState() != null) {
                return sql.getSQLState();
            }
        }
        return null;
    }

    private static DireccionRequest direccionSolicitud(String alias, boolean principal) {
        return new DireccionRequest(alias, "Ana Perez", "3001234567", "Bogota", "Bogota",
                "Calle 1 #2-3", null, principal);
    }

    private static String codigoCuponUnico() {
        return "IT-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private long crearUsuarioUnico() {
        String email = "it-" + UUID.randomUUID() + "@tienda.test";
        long id = jdbc.queryForObject("""
                INSERT INTO usuarios (nombre, email, password_hash, rol_id)
                VALUES ('Cliente Integracion', ?, 'hash-de-prueba',
                        (SELECT id FROM roles WHERE nombre = 'CLIENTE'))
                RETURNING id
                """, Long.class, email);
        usuariosCreados.add(id);
        return id;
    }

    private long crearProductoUnico(int stock, int stockMinimo) {
        String sufijo = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String sku = "IT-" + sufijo;
        long id = jdbc.queryForObject("""
                INSERT INTO productos (sku, nombre, slug, precio, categoria_id)
                VALUES (?, ?, ?, 1000, 1)
                RETURNING id
                """, Long.class, sku, "Producto IT " + sufijo, "it-" + sufijo);
        productosCreados.add(id);
        jdbc.update("INSERT INTO inventario (producto_id, stock, stock_minimo) VALUES (?, ?, ?)",
                id, stock, stockMinimo);
        return id;
    }

    private long crearCarrito(long usuarioId) {
        long id = jdbc.queryForObject("INSERT INTO carritos (usuario_id) VALUES (?) RETURNING id",
                Long.class, usuarioId);
        carritosCreados.add(id);
        return id;
    }

    private long crearPedido(long usuarioId, long productoId, int cantidad) {
        return crearPedido(usuarioId, productoId, cantidad, "PENDIENTE");
    }

    private long crearPedido(long usuarioId, long productoId, int cantidad, String estado) {
        BigDecimal precio = jdbc.queryForObject(
                "SELECT precio FROM productos WHERE id = ?", BigDecimal.class, productoId);
        BigDecimal subtotal = precio.multiply(BigDecimal.valueOf(cantidad));
        BigDecimal total = subtotal;
                Long id = tx.execute(status -> {
                        Long pedidoCreadoId = jdbc.queryForObject("""
                    INSERT INTO pedidos (numero, usuario_id, estado, subtotal, descuento, iva, costo_envio, total,
                                         envio_destinatario, envio_telefono, envio_departamento, envio_ciudad,
                                         envio_direccion)
                    VALUES (NULL, ?, ?, ?, 0, 0, 0, ?, 'Ana Perez', '3001234567',
                            'Bogota', 'Bogota', 'Calle 1 #2-3')
                    RETURNING id
                    """, Long.class, usuarioId, estado, subtotal, total);
            jdbc.update("INSERT INTO pedido_items (pedido_id, producto_id, cantidad) VALUES (?, ?, ?)",
                    pedidoCreadoId, productoId, cantidad);
            return pedidoCreadoId;
        });
        long pedidoId = Objects.requireNonNull(id);
        pedidosCreados.add(pedidoId);
        return pedidoId;
    }
}
