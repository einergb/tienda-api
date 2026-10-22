package com.codeconheiner.tienda.catalogo;

import com.codeconheiner.tienda.config.TiendaProperties;
import java.util.List;
import java.util.Map;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Endpoints publicos (solo lectura) del catalogo. */
@RestController
@RequestMapping("/api/catalogo")
public class CatalogoController {

    private final ProductoService productos;
    private final CategoriaRepository categorias;
    private final TiendaProperties props;

    public CatalogoController(ProductoService productos, CategoriaRepository categorias, TiendaProperties props) {
        this.productos = productos;
        this.categorias = categorias;
        this.props = props;
    }

    /** Datos de la tienda para que el frontend se configure (nombre, moneda, IVA...). */
    @Cacheable(value = "tienda-info", key = "'actual'")
    @GetMapping("/tienda")
    public Map<String, Object> tienda() {
        return Map.of(
                "nombre", props.nombre(),
                "moneda", props.moneda(),
                "iva", props.iva(),
                "envioGratisDesde", props.envioGratisDesde());
    }

    @Cacheable(value = "categorias", key = "'todas'")
    @GetMapping("/categorias")
    public List<CategoriaResponse> categorias() {
        return categorias.listarActivas();
    }

    /**
     * Listado publico del catalogo.
     *
    * <p>Los unicos {@code @RequestParam} son {@code categoriaId}, {@code q}, {@code page} y
    * {@code size}; la consulta fija el orden por id descendente y no expone un parametro de
    * ordenacion. La clave incluye categoria, pagina y tamaño; {@code q} no se cachea cuando
    * contiene texto libre ({@code unless}) para evitar entradas ilimitadas.
     */
    @Cacheable(value = "catalogo-listado",
            key = "T(com.codeconheiner.tienda.catalogo.CatalogoController).claveListado(#categoriaId, #q, #page, #size)",
            unless = "#q != null && !#q.trim().isEmpty()")
    @GetMapping("/productos")
    public List<ProductoResponse> productos(
            @RequestParam(required = false) Long categoriaId,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {
        return productos.listarPublico(categoriaId, q, page, size);
    }

    @Cacheable(value = "producto", key = "#id")
    @GetMapping("/productos/{id}")
    public ProductoResponse producto(@PathVariable long id) {
        return productos.detallePublico(id);
    }

    /**
     * Clave de cache del listado. Incluye todos los parametros que filtran o paginan el
     * resultado y normaliza pagina y tamaño con los mismos limites que aplica
     * {@link ProductoService#listarPublico}, para que dos peticiones equivalentes compartan
     * la misma entrada.
     */
    public static String claveListado(Long categoriaId, String q, int page, int size) {
        return "cat:" + categoriaId
                + ":q:" + (q == null ? "" : q.trim())
                + ":p:" + ProductoService.pagina(page)
                + ":s:" + ProductoService.limite(size);
    }
}
