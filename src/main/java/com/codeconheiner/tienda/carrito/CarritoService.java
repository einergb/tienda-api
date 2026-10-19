package com.codeconheiner.tienda.carrito;

import com.codeconheiner.tienda.carrito.dto.CarritoItemRequest;
import com.codeconheiner.tienda.carrito.dto.CarritoItemResponse;
import com.codeconheiner.tienda.carrito.dto.CarritoResponse;
import com.codeconheiner.tienda.common.ApiException;
import com.codeconheiner.tienda.config.TiendaProperties;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CarritoService {

    private final CarritoRepository repo;
    private final TiendaProperties props;

    public CarritoService(CarritoRepository repo, TiendaProperties props) {
        this.repo = repo;
        this.props = props;
    }

    @Transactional
    public CarritoResponse obtenerCarrito(Long usuarioId) {
        Long carritoId = obtenerOCrearCarrito(usuarioId);
        return construirCarrito(carritoId, usuarioId);
    }

    @Transactional
    public CarritoResponse agregarItem(Long usuarioId, CarritoItemRequest request) {
        Long carritoId = obtenerOCrearCarrito(usuarioId);
        try {
            repo.agregarItem(carritoId, request.productoId(), request.cantidad());
        } catch (org.springframework.dao.DataIntegrityViolationException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "No se pudo agregar el producto al carrito");
        }
        return construirCarrito(carritoId, usuarioId);
    }

    @Transactional
    public CarritoResponse actualizarItem(Long usuarioId, Long productoId, CarritoItemRequest request) {
        Long carritoId = obtenerOCrearCarrito(usuarioId);
        repo.actualizarCantidad(carritoId, productoId, request.cantidad());
        return construirCarrito(carritoId, usuarioId);
    }

    @Transactional
    public void eliminarItem(Long usuarioId, Long productoId) {
        Long carritoId = obtenerOCrearCarrito(usuarioId);
        repo.eliminarItem(carritoId, productoId);
    }

    @Transactional
    public void vaciarCarrito(Long usuarioId) {
        Long carritoId = obtenerOCrearCarrito(usuarioId);
        repo.vaciarCarrito(carritoId);
    }

    private Long obtenerOCrearCarrito(Long usuarioId) {
        return repo.obtenerIdCarritoPorUsuario(usuarioId)
                .orElseGet(() -> repo.crearCarrito(usuarioId));
    }

    private CarritoResponse construirCarrito(Long carritoId, Long usuarioId) {
        List<CarritoItemResponse> items = repo.listarItems(carritoId);
        BigDecimal subtotal = items.stream()
                .map(CarritoItemResponse::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal costoEnvio = subtotal.compareTo(props.envioGratisDesde()) >= 0 || subtotal.equals(BigDecimal.ZERO)
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(props.costoEnvioBase());
        BigDecimal iva = props.iva().multiply(subtotal);
        BigDecimal total = subtotal.add(iva).add(costoEnvio);
        return new CarritoResponse(carritoId, usuarioId, items, subtotal, costoEnvio, iva, total);
    }
}
