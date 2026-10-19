package com.codeconheiner.tienda.pedidos;

import com.codeconheiner.tienda.carrito.dto.CarritoItemResponse;
import com.codeconheiner.tienda.config.TiendaProperties;
import com.codeconheiner.tienda.cupones.CuponService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Cálculo de los importes del checkout (subtotal, descuento, IVA y envío). Responsabilidad
 * única y aislada de {@link PedidoService}, que queda enfocado en orquestar el pedido:
 * los totales deben coincidir con la verificación que hace el trigger de V3.
 */
@Component
public class CalculadoraTotales {

    private final TiendaProperties props;
    private final CuponService cupones;

    public CalculadoraTotales(TiendaProperties props, CuponService cupones) {
        this.props = props;
        this.cupones = cupones;
    }

    public Totales calcular(List<CarritoItemResponse> items, Long cuponId) {
        BigDecimal subtotal = items.stream()
                .map(CarritoItemResponse::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal descuento = cuponId == null
                ? BigDecimal.ZERO
                : cupones.calcularDescuento(cuponId, subtotal).setScale(0, RoundingMode.HALF_UP);
        BigDecimal iva = subtotal.multiply(props.iva()).setScale(0, RoundingMode.HALF_UP);
        BigDecimal costoEnvio = subtotal.compareTo(props.envioGratisDesde()) >= 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(props.costoEnvioBase());
        BigDecimal total = subtotal.subtract(descuento).add(iva).add(costoEnvio);
        return new Totales(subtotal, descuento, iva, costoEnvio, total);
    }

    /** Importes del pedido en pesos (el descuento de cupones puede tener decimales). */
    public record Totales(BigDecimal subtotal, BigDecimal descuento, BigDecimal iva,
            BigDecimal costoEnvio, BigDecimal total) {}
}
