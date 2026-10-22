package com.codeconheiner.tienda.catalogo;

import com.codeconheiner.tienda.catalogo.storage.StorageService;
import com.codeconheiner.tienda.common.ApiException;
import com.codeconheiner.tienda.config.TiendaProperties;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ProductoService {

    private static final Logger log = LoggerFactory.getLogger(ProductoService.class);
    private static final int GARANTIA_POR_DEFECTO = 12;

    private final ProductoRepository repo;
    private final TiendaProperties props;
    private final StorageService storageService;

    public ProductoService(ProductoRepository repo, TiendaProperties props, StorageService storageService) {
        this.repo = repo;
        this.props = props;
        this.storageService = storageService;
    }

    public List<ProductoResponse> listarPublico(Long categoriaId, String q, int page, int size) {
        int limite = limite(size);
        int pagina = pagina(page);
        return repo.listarPublico(categoriaId, q, limite, pagina * limite).stream()
                .map(this::conImagenUrl)
                .toList();
    }

    /** Tamaño maximo de pagina: entre 1 y 50 productos. */
    public static int limite(int size) {
        return Math.min(Math.max(size, 1), 50);
    }

    /** Numero de pagina valido (nunca negativo). */
    public static int pagina(int page) {
        return Math.max(page, 0);
    }

    /** Regla: un producto solo se muestra si esta activo y tiene stock. */
    public ProductoResponse detallePublico(long id) {
        return repo.buscarPorId(id)
                .filter(p -> p.activo() && p.stockDisponible() > 0)
                .map(this::conImagenUrl)
                .orElseThrow(() -> noEncontrado());
    }

    @Transactional
    public ProductoResponse crear(ProductoRequest r) {
        long id = repo.crear(r, slug(r), garantia(r));
        repo.crearInventario(id, r.stock(), stockMinimo(r));
        return repo.buscarPorId(id).map(this::conImagenUrl).orElseThrow(() -> noEncontrado());
    }

    @Transactional
    public ProductoResponse actualizar(long id, ProductoRequest r) {
        if (repo.actualizar(id, r, slug(r), garantia(r)) == 0) {
            throw noEncontrado();
        }
        repo.actualizarInventario(id, r.stock(), stockMinimo(r));
        return repo.buscarPorId(id).map(this::conImagenUrl).orElseThrow(() -> noEncontrado());
    }

    /** Borrado logico: el producto deja de mostrarse pero se conserva el historial de pedidos. */
    public void desactivar(long id) {
        if (repo.desactivar(id) == 0) {
            throw noEncontrado();
        }
    }

    @Transactional(readOnly = true)
    public List<ProductoResponse> listarTodos() {
        return repo.findAll().stream().map(this::conImagenUrl).toList();
    }

    @Transactional
    public ProductoResponse reactivar(long id) {
        if (repo.activar(id) == 0) {
            throw noEncontrado();
        }
        return repo.buscarPorId(id).map(this::conImagenUrl).orElseThrow(ProductoService::noEncontrado);
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

    @Transactional
    public String subirImagenGaleria(long id, MultipartFile archivo) {
        verificarProducto(id);
        String key = storageService.subirArchivo(archivo, "productos/" + id);
        return storageService.urlPublica(key);
    }

    @Transactional
    public void eliminarImagenGaleria(long id, long imagenId) {
        verificarProducto(id);
        String url = repo.obtenerImagenGaleriaUrl(id, imagenId);
        if (url == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Imagen no encontrada");
        }
        if (repo.eliminarImagen(id, imagenId) == 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Imagen no encontrada");
        }
        Optional<String> clave = storageService.derivarClaveDesdeUrlPublica(url);
        if (clave.isPresent()) {
            storageService.eliminar(clave.get());
        } else if (esUrlDeAlmacenamientoPropio(url)) {
            // Intento best effort sin clave conocida; mejor no forzar
            log.debug("No se pudo derivar clave desde URL de galería propia para producto {} imagen {}", id, imagenId);
        }
    }

    private boolean esUrlDeAlmacenamientoPropio(String url) {
        String base = null;
        try {
            if (props != null && props.storage() != null) {
                base = props.storage().publicBaseUrl();
            }
        } catch (Exception ignored) {
        }
        if (base != null && !base.isBlank()) {
            String baseLimpia = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
            if (url != null && url.startsWith(baseLimpia + "/")) {
                return true;
            }
        }
        return url != null && url.contains("/tienda-productos") || (url != null && url.contains("img.codeconheiner"));
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

    /**
     * Sustituye la imagen del producto: primero sube el objeto nuevo, despues guarda la clave y
     * al final borra el anterior. Si la subida falla la base de datos no se toca; si el borrado del
     * anterior falla solo queda un aviso en el log.
     */
    @Transactional
    public ProductoResponse subirImagen(long id, MultipartFile file) {
        verificarProducto(id);
        String anterior = repo.obtenerImagenKey(id);
        String nueva = storageService.subir(file, id);
        repo.actualizarImagenKey(id, nueva);
        if (anterior != null && !anterior.isBlank() && !anterior.equals(nueva)) {
            storageService.eliminar(anterior);
        }
        return repo.buscarPorId(id).map(this::conImagenUrl).orElseThrow(ProductoService::noEncontrado);
    }

    /** Quita la imagen del producto: borra el objeto y deja imagen_key en null. */
    @Transactional
    public void eliminarImagen(long id) {
        verificarProducto(id);
        String anterior = repo.obtenerImagenKey(id);
        String urlAnterior = anterior == null || anterior.isBlank() ? null : storageService.urlPublica(anterior);
        repo.actualizarImagenKey(id, null);
        if (urlAnterior != null) {
            repo.eliminarImagenPorUrl(id, urlAnterior);
        }
        storageService.eliminar(anterior);
    }

    private void verificarProducto(long id) {
        repo.buscarPorId(id).orElseThrow(ProductoService::noEncontrado);
    }

    /** Calcula la URL publica de la imagen a partir de la clave guardada en el almacenamiento. */
    private ProductoResponse conImagenUrl(ProductoResponse p) {
        return new ProductoResponse(p.id(), p.sku(), p.nombre(), p.slug(), p.descripcion(), p.precio(),
                p.categoriaId(), p.categoria(), p.marcaId(), p.marca(), p.garantiaMeses(), p.stockDisponible(),
                p.activo(), p.imagenes(), p.atributos(), p.imagenKey(),
                storageService.urlPublica(p.imagenKey()));
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
