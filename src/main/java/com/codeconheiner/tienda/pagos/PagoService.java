package com.codeconheiner.tienda.pagos;

import com.codeconheiner.tienda.common.ApiException;
import com.codeconheiner.tienda.config.CacheCatalogo;
import com.codeconheiner.tienda.config.TiendaProperties;
import com.codeconheiner.tienda.pagos.dto.PagoConPasarelaRequest;
import com.codeconheiner.tienda.pagos.dto.PagoRequest;
import com.codeconheiner.tienda.pagos.dto.PagoResponse;
import com.codeconheiner.tienda.pagos.dto.SesionPagoResponse;
import com.codeconheiner.tienda.pagos.pasarela.CrearTransaccion;
import com.codeconheiner.tienda.pagos.pasarela.PasarelaPago;
import com.codeconheiner.tienda.pagos.pasarela.PasarelaRegistry;
import com.codeconheiner.tienda.pagos.pasarela.SesionPago;
import com.codeconheiner.tienda.pedidos.PedidoRepository;
import com.codeconheiner.tienda.pedidos.dto.PedidoResponse;
import com.codeconheiner.tienda.usuarios.UsuarioRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orquesta los pagos: registra intentos y, cuando el cliente elige una pasarela, crea la
 * transacción/sesión externa reutilizando la misma clave de idempotencia en los reintentos.
 * Depende de la abstracción {@link PasarelaPago}, no de las implementaciones concretas.
 */
@Service
public class PagoService {

    private static final Set<String> ESTADOS = Set.of("PENDIENTE", "APROBADO", "RECHAZADO");

    private final PagoRepository repo;
    private final PedidoRepository pedidos;
    private final PasarelaRegistry pasarelas;
    private final UsuarioRepository usuarios;
    private final TiendaProperties props;
    private final CacheCatalogo cache;

    public PagoService(PagoRepository repo, PedidoRepository pedidos, PasarelaRegistry pasarelas,
            UsuarioRepository usuarios, TiendaProperties props, CacheCatalogo cache) {
        this.repo = repo;
        this.pedidos = pedidos;
        this.pasarelas = pasarelas;
        this.usuarios = usuarios;
        this.props = props;
        this.cache = cache;
    }

    public List<PagoResponse> listarPorPedido(Long pedidoId) {
        return repo.listarPorPedido(pedidoId);
    }

    /**
     * Registra un intento de pago del pedido propio. El monto siempre es el total del pedido;
     * si el estado es APROBADO, los triggers de V3 marcan el pedido como PAGADO y descuentan stock.
     */
    @Transactional
    public PagoResponse crear(Long usuarioId, PagoRequest request) {
        PedidoResponse pedido = pedidoPropio(usuarioId, request.pedidoId());
        String estado = request.estado() == null || request.estado().isBlank()
                ? "PENDIENTE"
                : request.estado().trim().toUpperCase(Locale.ROOT);
        if (!ESTADOS.contains(estado)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Estado de pago no válido");
        }
        PagoResponse pago = repo.crear(pedido.id(), request.pasarela().trim(), request.referencia().trim(),
            estado, pedido.total());
        if ("APROBADO".equals(estado)) {
            cache.invalidarProductos(pedido.items().stream().map(item -> item.productoId()).toList());
        }
        return pago;
    }

    /**
     * Crea la sesión/transacción en la pasarela para que el usuario complete el pago en su
     * entorno seguro. La clave de idempotencia se genera una sola vez por intento y el
     * cliente puede reenviarla para reintentar sin duplicar el cobro.
     */
    @Transactional
    public SesionPagoResponse crearConPasarela(Long usuarioId, PagoConPasarelaRequest request) {
        PedidoResponse pedido = pedidoPropio(usuarioId, request.pedidoId());
        PasarelaPago pasarela = pasarelas.obtener(request.pasarela());

        String clave = request.claveIdempotencia() == null ? "" : request.claveIdempotencia().trim();
        if (clave.isEmpty()) {
            return crearIntento(usuarioId, pedido, pasarela, UUID.randomUUID().toString());
        }
        PagoDetalle existente = repo.buscarPorIdempotencyKey(clave).orElse(null);
        if (existente == null) {
            // primera vez que se ve esta clave (puede ser un reintento del cliente)
            return crearIntento(usuarioId, pedido, pasarela, clave);
        }
        if (!existente.pedidoId().equals(pedido.id())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "La clave de idempotencia no corresponde al pedido");
        }
        if (existente.sesionId() != null) {
            // la sesión ya fue creada: se devuelve sin volver a llamar a la pasarela
            return new SesionPagoResponse(existente.id(), existente.pasarela(), existente.referencia(),
                    clave, existente.sesionId(), null);
        }
        // el intento existe pero la llamada no se completó: reintentar con la MISMA clave
        SesionPago sesion = pasarela.crearSesion(transaccion(usuarioId, pedido, existente.referencia(), clave));
        repo.guardarSesion(existente.id(), sesion.sesionId());
        return new SesionPagoResponse(existente.id(), pasarela.nombre(), existente.referencia(), clave,
                sesion.sesionId(), sesion.urlPago());
    }

    private SesionPagoResponse crearIntento(Long usuarioId, PedidoResponse pedido, PasarelaPago pasarela,
            String clave) {
        String referencia = pedido.numero() + "-" + clave.substring(0, Math.min(8, clave.length()));
        PagoResponse intento = repo.crear(pedido.id(), pasarela.nombre(), referencia, "PENDIENTE",
                pedido.total(), clave);
        SesionPago sesion = pasarela.crearSesion(transaccion(usuarioId, pedido, referencia, clave));
        repo.guardarSesion(intento.id(), sesion.sesionId());
        return new SesionPagoResponse(intento.id(), pasarela.nombre(), referencia, clave,
                sesion.sesionId(), sesion.urlPago());
    }

    private CrearTransaccion transaccion(Long usuarioId, PedidoResponse pedido, String referencia,
            String clave) {
        String email = usuarios.buscarPorId(usuarioId).map(u -> u.email()).orElse(null);
        return new CrearTransaccion(clave, referencia, pedido.total(), props.moneda(), email, pedido.id());
    }

    private PedidoResponse pedidoPropio(Long usuarioId, Long pedidoId) {
        PedidoResponse pedido = pedidos.obtenerPorId(usuarioId, pedidoId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Pedido no encontrado"));
        if (!"PENDIENTE".equals(pedido.estado())) {
            throw new ApiException(HttpStatus.CONFLICT, "El pedido no admite pagos en el estado actual");
        }
        return pedido;
    }
}
