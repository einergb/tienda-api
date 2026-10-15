package com.codeconheiner.tienda.catalogo;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CategoriaRepository {

    private final JdbcTemplate jdbc;

    public CategoriaRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<CategoriaResponse> listarActivas() {
        return jdbc.query("""
                SELECT id, nombre, slug, categoria_padre_id
                FROM categorias WHERE activa ORDER BY nombre
                """, (rs, i) -> new CategoriaResponse(
                rs.getLong("id"),
                rs.getString("nombre"),
                rs.getString("slug"),
                (Long) rs.getObject("categoria_padre_id")));
    }
}
