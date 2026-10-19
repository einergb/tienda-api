package com.codeconheiner.tienda.catalogo;

import com.codeconheiner.tienda.common.ApiException;
import com.codeconheiner.tienda.config.TiendaProperties;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductoService {

    private static final int GARANTIA_POR_DEFECTO = 12;

    private final ProductoRepository repo;
    private final TiendaProperties props;

    public ProductoService(ProductoRepository repo, TiendaProperties props) {
        this.repo = repo;
        this.props = props;
    }

    public List<ProductoResponse> listarPublico(Long categoriaId, String q, int page, int size) {
        int limite = Math.min(Math.max(size, 1), 50);
        int pagina = Math.max(page, 0);
        return repo.listarPublico(categoriaId, q, limite, pagina * limite);
    }

    /** Regla: un producto solo se muestra si esta activo y tiene stock. */
    public ProductoResponse detallePublico(long id) {
        return repo.buscarPorId(id)
                .filter(p -> p.activo() && p.stockDisponible() > 0)
                .orElseThrow(() -> noEncontrado());
    }

    @Transactional
    public ProductoResponse crear(ProductoRequest r) {
        long id = repo.crear(r, slug(r), garantia(r));
        repo.crearInventario(id, r.stock(), stockMinimo(r));
        return repo.buscarPorId(id).orElseThrow(() -> noEncontrado());
    }

    @Transactional
    public ProductoResponse actualizar(long id, ProductoRequest r) {
        if (repo.actualizar(id, r, slug(r), garantia(r)) == 0) {
            throw noEncontrado();
        }
        repo.actualizarInventario(id, r.stock(), stockMinimo(r));
        return repo.buscarPorId(id).orElseThrow(() -> noEncontrado());
    }

    /** Borrado logico: el producto deja de mostrarse pero se conserva el historial de pedidos. */
    public void desactivar(long id) {
        if (repo.desactivar(id) == 0) {
            throw noEncontrado();
        }
    }

    public Long agregarImagen(long id, ImagenRequest request) {
        verificarProducto(id);
        return repo.agregarImagen(id, request);
    }

    public void eliminarImagen(long id, long imagenId) {
        verificarProducto(id);
        if (repo.eliminarImagen(id, imagenId) == 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Imagen no encontrada");
        }
    }

    public Long agregarAtributo(long id, AtributoRequest request) {
        verificarProducto(id);
        return repo.agregarAtributo(id, request);
    }

    public void eliminarAtributo(long id, String clave) {
        verificarProducto(id);
        if (repo.eliminarAtributo(id, clave) == 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Atributo no encontrado");
        }
    }

    private void verificarProducto(long id) {
        repo.buscarPorId(id).orElseThrow(ProductoService::noEncontrado);
    }

    private int garantia(ProductoRequest r) {
        return r.garantiaMeses() != null ? r.garantiaMeses() : GARANTIA_POR_DEFECTO;
    }

    private int stockMinimo(ProductoRequest r) {
        return r.stockMinimo() != null ? r.stockMinimo() : props.stockMinimoAlerta();
    }

    private static ApiException noEncontrado() {
        return new ApiException(HttpStatus.NOT_FOUND, "Producto no encontrado");
    }

    private static String slug(ProductoRequest r) {
        String base = Normalizer.normalize(r.nombre() + " " + r.sku(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return base.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
    }
}
