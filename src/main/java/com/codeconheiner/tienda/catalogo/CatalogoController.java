package com.codeconheiner.tienda.catalogo;

import com.codeconheiner.tienda.config.TiendaProperties;
import java.util.List;
import java.util.Map;
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
    @GetMapping("/tienda")
    public Map<String, Object> tienda() {
        return Map.of(
                "nombre", props.nombre(),
                "moneda", props.moneda(),
                "iva", props.iva(),
                "envioGratisDesde", props.envioGratisDesde());
    }

    @GetMapping("/categorias")
    public List<CategoriaResponse> categorias() {
        return categorias.listarActivas();
    }

    @GetMapping("/productos")
    public List<ProductoResponse> productos(
            @RequestParam(required = false) Long categoriaId,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {
        return productos.listarPublico(categoriaId, q, page, size);
    }

    @GetMapping("/productos/{id}")
    public ProductoResponse producto(@PathVariable long id) {
        return productos.detallePublico(id);
    }
}
