package com.codeconheiner.tienda.catalogo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class ProductoRepository {

    private static final String BASE = """
            SELECT p.id, p.sku, p.nombre, p.slug, p.descripcion, p.precio,
                   p.categoria_id, c.nombre AS categoria,
                   p.marca_id, m.nombre AS marca, p.garantia_meses,
                   (i.stock - i.stock_reservado) AS stock_disponible, p.activo
            FROM productos p
            JOIN categorias c ON c.id = p.categoria_id
            LEFT JOIN marcas m ON m.id = p.marca_id
            JOIN inventario i ON i.producto_id = p.id
            """;

    private static final RowMapper<ProductoResponse> MAPPER = (rs, n) -> new ProductoResponse(
            rs.getLong("id"),
            rs.getString("sku"),
            rs.getString("nombre"),
            rs.getString("slug"),
            rs.getString("descripcion"),
            rs.getBigDecimal("precio"),
            rs.getLong("categoria_id"),
            rs.getString("categoria"),
            (Long) rs.getObject("marca_id"),
            rs.getString("marca"),
            rs.getInt("garantia_meses"),
            rs.getInt("stock_disponible"),
            rs.getBoolean("activo"),
            List.of(),
            Map.of());

    private final JdbcTemplate jdbc;

    public ProductoRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Catalogo publico: solo productos activos con stock disponible. */
    public List<ProductoResponse> listarPublico(Long categoriaId, String q, int limit, int offset) {
        StringBuilder sql = new StringBuilder(BASE)
                .append(" WHERE p.activo AND (i.stock - i.stock_reservado) > 0");
        List<Object> args = new ArrayList<>();
        if (categoriaId != null) {
            sql.append(" AND p.categoria_id = ?");
            args.add(categoriaId);
        }
        if (q != null && !q.isBlank()) {
            sql.append(" AND (p.nombre ILIKE ? OR p.descripcion ILIKE ?)");
            String like = "%" + q.trim() + "%";
            args.add(like);
            args.add(like);
        }
        sql.append(" ORDER BY p.id DESC LIMIT ? OFFSET ?");
        args.add(limit);
        args.add(offset);
        return jdbc.query(sql.toString(), MAPPER, args.toArray());
    }

    public Optional<ProductoResponse> buscarPorId(long id) {
        return jdbc.query(BASE + " WHERE p.id = ?", MAPPER, id).stream().findFirst()
                .map(p -> new ProductoResponse(p.id(), p.sku(), p.nombre(), p.slug(), p.descripcion(), p.precio(),
                        p.categoriaId(), p.categoria(), p.marcaId(), p.marca(), p.garantiaMeses(),
                        p.stockDisponible(), p.activo(), imagenes(id), atributos(id)));
    }

    private List<ImagenProductoResponse> imagenes(long productoId) {
        return jdbc.query("""
                SELECT id, url, orden, es_principal
                FROM producto_imagenes WHERE producto_id = ?
                ORDER BY orden, id
                """, (rs, n) -> new ImagenProductoResponse(
                rs.getLong("id"),
                rs.getString("url"),
                rs.getInt("orden"),
                rs.getBoolean("es_principal")), productoId);
    }

    private java.util.LinkedHashMap<String, String> atributos(long productoId) {
        java.util.LinkedHashMap<String, String> resultado = new java.util.LinkedHashMap<>();
        jdbc.query("""
                SELECT clave, valor FROM producto_atributos WHERE producto_id = ? ORDER BY clave
                """, (org.springframework.jdbc.core.RowCallbackHandler) rs ->
                resultado.put(rs.getString("clave"), rs.getString("valor")), productoId);
        return resultado;
    }

    public Long agregarImagen(long productoId, ImagenRequest r) {
        if (Boolean.TRUE.equals(r.esPrincipal())) {
            jdbc.update("UPDATE producto_imagenes SET es_principal = FALSE WHERE producto_id = ? AND es_principal",
                    productoId);
        }
        return jdbc.queryForObject("""
                INSERT INTO producto_imagenes (producto_id, url, orden, es_principal)
                VALUES (?, ?, ?, ?)
                RETURNING id
                """, Long.class, productoId, r.url().trim(), r.orden() == null ? 0 : r.orden(),
                Boolean.TRUE.equals(r.esPrincipal()));
    }

    public int eliminarImagen(long productoId, long imagenId) {
        return jdbc.update("DELETE FROM producto_imagenes WHERE id = ? AND producto_id = ?", imagenId, productoId);
    }

    public Long agregarAtributo(long productoId, AtributoRequest r) {
        return jdbc.queryForObject("""
                INSERT INTO producto_atributos (producto_id, clave, valor)
                VALUES (?, ?, ?)
                RETURNING id
                """, Long.class, productoId, r.clave().trim(), r.valor().trim());
    }

    public int eliminarAtributo(long productoId, String clave) {
        return jdbc.update("DELETE FROM producto_atributos WHERE producto_id = ? AND clave = ?", productoId, clave);
    }

    public long crear(ProductoRequest r, String slug, int garantiaMeses) {
        Long id = jdbc.queryForObject("""
                INSERT INTO productos (sku, nombre, slug, descripcion, precio, categoria_id, marca_id, garantia_meses)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING id
                """, Long.class,
                r.sku().trim(), r.nombre().trim(), slug, r.descripcion(), r.precio(),
                r.categoriaId(), r.marcaId(), garantiaMeses);
        return id;
    }

    public void crearInventario(long productoId, int stock, int stockMinimo) {
        jdbc.update("INSERT INTO inventario (producto_id, stock, stock_minimo) VALUES (?, ?, ?)",
                productoId, stock, stockMinimo);
    }

    /** @return filas afectadas (0 si el producto no existe) */
    public int actualizar(long id, ProductoRequest r, String slug, int garantiaMeses) {
        return jdbc.update("""
                UPDATE productos
                SET sku = ?, nombre = ?, slug = ?, descripcion = ?, precio = ?,
                    categoria_id = ?, marca_id = ?, garantia_meses = ?, actualizado_en = now()
                WHERE id = ?
                """,
                r.sku().trim(), r.nombre().trim(), slug, r.descripcion(), r.precio(),
                r.categoriaId(), r.marcaId(), garantiaMeses, id);
    }

    public void actualizarInventario(long productoId, int stock, int stockMinimo) {
        jdbc.update("UPDATE inventario SET stock = ?, stock_minimo = ? WHERE producto_id = ?",
                stock, stockMinimo, productoId);
    }

    public int desactivar(long id) {
        return jdbc.update("UPDATE productos SET activo = FALSE, actualizado_en = now() WHERE id = ?", id);
    }
}
