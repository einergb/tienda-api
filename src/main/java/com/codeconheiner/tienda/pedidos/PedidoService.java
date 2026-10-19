package com.codeconheiner.tienda.pedidos;

import com.codeconheiner.tienda.carrito.CarritoRepository;
import com.codeconheiner.tienda.carrito.dto.CarritoItemResponse;
import com.codeconheiner.tienda.common.ApiException;
import com.codeconheiner.tienda.config.TiendaProperties;
import com.codeconheiner.tienda.cupones.CuponService;
import com.codeconheiner.tienda.direcciones.DireccionService;
import com.codeconheiner.tienda.direcciones.dto.DireccionResponse;
import com.codeconheiner.tienda.pedidos.dto.PedidoRequest;
import com.codeconheiner.tienda.pedidos.dto.PedidoResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PedidoService {

    private static final Set<String> ESTADOS = Set.of("PENDIENTE", "PAGADO", "EN_PREPARACION", "ENVIADO",
            "ENTREGADO", "CANCELADO", "DEVUELTO");

    private final PedidoRepository repo;
    private final CarritoRepository carritos;
    private final DireccionService direcciones;
    private final CuponService cupones;
    private final TiendaProperties props;

    public PedidoService(PedidoRepository repo, CarritoRepository carritos, DireccionService direcciones,
            CuponService cupones, TiendaProperties props) {
        this.repo = repo;
        this.carritos = carritos;
        this.direcciones = direcciones;
        this.cupones = cupones;
        this.props = props;
    }

    public List<PedidoResponse> listarPorUsuario(Long usuarioId) {
        return repo.listarPorUsuario(usuarioId);
    }

    public PedidoResponse obtenerPorId(Long usuarioId, Long pedidoId) {
        return repo.obtenerPorId(usuarioId, pedidoId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Pedido no encontrado"));
    }

    /**
     * Checkout: convierte el carrito en un pedido. Los triggers de V3 generan el numero,
     * reservan el stock, validan el cupon y verifican los totales al confirmar la transaccion.
     */
    @Transactional
    public PedidoResponse crear(Long usuarioId, PedidoRequest request) {
        Long carritoId = carritos.obtenerIdCarritoPorUsuario(usuarioId).orElse(null);
        List<CarritoItemResponse> items = carritoId == null ? List.of() : carritos.listarItems(carritoId);
        if (items.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "El carrito está vacío");
        }
        DireccionResponse dir = direcciones.obtenerParaPedido(usuarioId, request.direccionId());

        BigDecimal subtotal = items.stream()
                .map(CarritoItemResponse::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal descuento = request.cuponId() == null
                ? BigDecimal.ZERO
                : cupones.calcularDescuento(request.cuponId(), subtotal).setScale(0, RoundingMode.HALF_UP);
        BigDecimal iva = subtotal.multiply(props.iva()).setScale(0, RoundingMode.HALF_UP);
        BigDecimal costoEnvio = subtotal.compareTo(props.envioGratisDesde()) >= 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(props.costoEnvioBase());
        BigDecimal total = subtotal.subtract(descuento).add(iva).add(costoEnvio);

        Long pedidoId = repo.crear(usuarioId, dir, subtotal, descuento, iva, costoEnvio, total, request.cuponId());
        for (CarritoItemResponse item : items) {
            repo.agregarItem(pedidoId, item.productoId(), item.cantidad());
        }
        carritos.vaciarCarrito(carritoId);
        return repo.obtenerPorId(usuarioId, pedidoId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Pedido no encontrado"));
    }

    public List<PedidoResponse> listarTodos(String estado) {
        return repo.listarTodos(estado);
    }

    public PedidoResponse obtenerPorIdAdmin(Long pedidoId) {
        return repo.obtenerPorIdAdmin(pedidoId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Pedido no encontrado"));
    }

    /** Transiciones manuales; V3 valida la maquina de estados y ajusta inventario/cupones. */
    public PedidoResponse cambiarEstado(Long pedidoId, String estado) {
        String nuevo = estado == null ? "" : estado.trim().toUpperCase(Locale.ROOT);
        if (!ESTADOS.contains(nuevo)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Estado de pedido no válido");
        }
        if (repo.cambiarEstado(pedidoId, nuevo) == 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Pedido no encontrado");
        }
        return repo.obtenerPorIdAdmin(pedidoId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Pedido no encontrado"));
    }
}
