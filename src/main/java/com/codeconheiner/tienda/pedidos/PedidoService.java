package com.codeconheiner.tienda.pedidos;

import com.codeconheiner.tienda.carrito.CarritoRepository;
import com.codeconheiner.tienda.carrito.dto.CarritoItemResponse;
import com.codeconheiner.tienda.common.ApiException;
import com.codeconheiner.tienda.config.CacheCatalogo;
import com.codeconheiner.tienda.direcciones.DireccionService;
import com.codeconheiner.tienda.direcciones.dto.DireccionResponse;
import com.codeconheiner.tienda.pedidos.dto.PedidoRequest;
import com.codeconheiner.tienda.pedidos.dto.PedidoResponse;
import com.codeconheiner.tienda.pedidos.dto.PedidoItemResponse;
import com.codeconheiner.tienda.pedidos.CalculadoraTotales.Totales;
import com.codeconheiner.tienda.seguridad.Auditoria;
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
    private final CalculadoraTotales totales;
    private final Auditoria auditoria;
    private final CacheCatalogo cache;

    public PedidoService(PedidoRepository repo, CarritoRepository carritos, DireccionService direcciones,
            CalculadoraTotales totales, Auditoria auditoria, CacheCatalogo cache) {
        this.repo = repo;
        this.carritos = carritos;
        this.direcciones = direcciones;
        this.totales = totales;
        this.auditoria = auditoria;
        this.cache = cache;
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

        Totales importes = totales.calcular(items, request.cuponId());

        Long pedidoId = repo.crear(usuarioId, dir, importes.subtotal(), importes.descuento(),
                importes.iva(), importes.costoEnvio(), importes.total(), request.cuponId());
        for (CarritoItemResponse item : items) {
            repo.agregarItem(pedidoId, item.productoId(), item.cantidad());
        }
        carritos.vaciarCarrito(carritoId);
        PedidoResponse creado = repo.obtenerPorId(usuarioId, pedidoId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Pedido no encontrado"));
        // El trigger de V3 reservo stock: el detalle y los listados ya no son validos.
        cache.invalidarProductos(items.stream().map(CarritoItemResponse::productoId).toList());
        auditoria.registrar(usuarioId, "pedido.creado", "pedidos", String.valueOf(pedidoId),
                java.util.Map.of("numero", creado.numero(), "total", creado.total()));
        return creado;
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
        String anterior = repo.obtenerPorIdAdmin(pedidoId).map(PedidoResponse::estado).orElse(null);
        if (repo.cambiarEstado(pedidoId, nuevo) == 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Pedido no encontrado");
        }
        var detalle = new java.util.HashMap<String, Object>();
        detalle.put("anterior", anterior);
        detalle.put("nuevo", nuevo);
        // La maquina de estados de V3 adjusts el inventario (descuenta, libera o devuelve stock).
        PedidoResponse actualizado = repo.obtenerPorIdAdmin(pedidoId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Pedido no encontrado"));
        cache.invalidarProductos(actualizado.items().stream().map(PedidoItemResponse::productoId).toList());
        auditoria.registrarActual("pedido.estado", "pedidos", String.valueOf(pedidoId), detalle);
        return actualizado;
    }
}
