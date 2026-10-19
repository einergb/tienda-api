package com.codeconheiner.tienda.cupones;

import com.codeconheiner.tienda.common.ApiException;
import com.codeconheiner.tienda.cupones.dto.CuponRequest;
import com.codeconheiner.tienda.cupones.dto.CuponResponse;
import com.codeconheiner.tienda.cupones.dto.ValidacionCuponResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cupones de descuento. Las mismas reglas se aplican en el trigger de V3
 * (fn_pedido_antes_insert) al confirmar el pedido; aqui se validan antes para
 * dar un mensaje inmediato al cliente.
 */
@Service
public class CuponService {

    private static final Set<String> TIPOS = Set.of("PORCENTAJE", "MONTO");

    private final CuponRepository repo;

    public CuponService(CuponRepository repo) {
        this.repo = repo;
    }

    public List<CuponResponse> listar() {
        return repo.listar();
    }

    public CuponResponse obtener(Long id) {
        return repo.buscarPorId(id).orElseThrow(CuponService::noEncontrado);
    }

    @Transactional
    public CuponResponse crear(CuponRequest request) {
        validarTipo(request.tipo());
        Long id = repo.crear(request);
        return repo.buscarPorId(id).orElseThrow(() -> noEncontrado());
    }

    @Transactional
    public CuponResponse actualizar(Long id, CuponRequest request) {
        validarTipo(request.tipo());
        if (repo.actualizar(id, request) == 0) {
            throw noEncontrado();
        }
        return repo.buscarPorId(id).orElseThrow(() -> noEncontrado());
    }

    /** Valida un codigo contra un subtotal y devuelve el descuento que produciria. */
    public ValidacionCuponResponse validar(String codigo, BigDecimal subtotal) {
        CuponResponse cupon = repo.buscarPorCodigo(codigo == null ? "" : codigo.trim())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Cupón no encontrado"));
        BigDecimal descuento = validarReglasYCalcular(cupon, subtotal);
        return new ValidacionCuponResponse(cupon.id(), cupon.codigo(), cupon.tipo(), cupon.valor(), descuento);
    }

    /** Uso interno del checkout: devuelve el descuento a aplicar (0 si no hay cupon). */
    public BigDecimal calcularDescuento(Long cuponId, BigDecimal subtotal) {
        CuponResponse cupon = repo.buscarPorId(cuponId).orElseThrow(() -> noEncontrado());
        return validarReglasYCalcular(cupon, subtotal);
    }

    /** Replica las reglas de fn_pedido_antes_insert (V3). */
    private BigDecimal validarReglasYCalcular(CuponResponse cupon, BigDecimal subtotal) {
        OffsetDateTime ahora = OffsetDateTime.now();
        if (!cupon.activo()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "El cupón " + cupon.codigo() + " está inactivo");
        }
        if (ahora.isBefore(cupon.vigenteDesde())
                || (cupon.vigenteHasta() != null && ahora.isAfter(cupon.vigenteHasta()))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "El cupón " + cupon.codigo() + " está fuera de vigencia");
        }
        if (cupon.usosMaximos() != null && cupon.usosActuales() >= cupon.usosMaximos()) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "El cupón " + cupon.codigo() + " ya alcanzó su límite de usos");
        }
        BigDecimal minimo = cupon.montoMinimo() == null ? BigDecimal.ZERO : cupon.montoMinimo();
        if (subtotal.compareTo(minimo) < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "El cupón " + cupon.codigo() + " requiere una compra mínima de " + minimo.toPlainString());
        }
        BigDecimal descuento = "PORCENTAJE".equals(cupon.tipo())
                ? subtotal.multiply(cupon.valor()).divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP)
                : cupon.valor().min(subtotal).setScale(0, RoundingMode.HALF_UP);
        return descuento.min(subtotal);
    }

    private void validarTipo(String tipo) {
        if (tipo == null || !TIPOS.contains(tipo.trim().toUpperCase(Locale.ROOT))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "El tipo de cupón debe ser PORCENTAJE o MONTO");
        }
    }

    private static ApiException noEncontrado() {
        return new ApiException(HttpStatus.NOT_FOUND, "Cupón no encontrado");
    }
}
