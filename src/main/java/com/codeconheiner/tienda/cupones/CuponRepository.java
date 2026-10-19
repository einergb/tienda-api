package com.codeconheiner.tienda.cupones;

import com.codeconheiner.tienda.cupones.dto.CuponRequest;
import com.codeconheiner.tienda.cupones.dto.CuponResponse;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class CuponRepository {

    private static final RowMapper<CuponResponse> MAPPER = (rs, n) -> new CuponResponse(
            rs.getLong("id"),
            rs.getString("codigo"),
            rs.getString("tipo"),
            rs.getBigDecimal("valor"),
            rs.getBigDecimal("monto_minimo"),
            (Integer) rs.getObject("usos_maximos"),
            rs.getInt("usos_actuales"),
            rs.getObject("vigente_desde", java.time.OffsetDateTime.class),
            rs.getObject("vigente_hasta", java.time.OffsetDateTime.class),
            rs.getBoolean("activo"));

    private static final String COLUMNAS =
            "id, codigo, tipo, valor, monto_minimo, usos_maximos, usos_actuales, vigente_desde, vigente_hasta, activo";

    private final JdbcTemplate jdbc;

    public CuponRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<CuponResponse> listar() {
        return jdbc.query("SELECT " + COLUMNAS + " FROM cupones ORDER BY id DESC", MAPPER);
    }

    public Optional<CuponResponse> buscarPorId(Long id) {
        return jdbc.query("SELECT " + COLUMNAS + " FROM cupones WHERE id = ?", MAPPER, id).stream().findFirst();
    }

    public Optional<CuponResponse> buscarPorCodigo(String codigo) {
        return jdbc.query("SELECT " + COLUMNAS + " FROM cupones WHERE UPPER(codigo) = UPPER(?)",
                MAPPER, codigo).stream().findFirst();
    }

    public Long crear(CuponRequest r) {
        return jdbc.queryForObject("""
                INSERT INTO cupones (codigo, tipo, valor, monto_minimo, usos_maximos, vigente_desde, vigente_hasta,
                                     activo)
                VALUES (?, ?, ?, ?, ?, COALESCE(?, now()), ?, ?)
                RETURNING id
                """, Long.class, r.codigo().trim().toUpperCase(), r.tipo().trim().toUpperCase(), r.valor(),
                r.montoMinimo() == null ? BigDecimal.ZERO : r.montoMinimo(), r.usosMaximos(), r.vigenteDesde(),
                r.vigenteHasta(), r.activo() == null || r.activo());
    }

    /** @return filas afectadas (0 si el cupon no existe) */
    public int actualizar(Long id, CuponRequest r) {
        return jdbc.update("""
                UPDATE cupones
                SET codigo = ?, tipo = ?, valor = ?, monto_minimo = ?, usos_maximos = ?,
                    vigente_desde = COALESCE(?, vigente_desde), vigente_hasta = ?, activo = ?
                WHERE id = ?
                """, r.codigo().trim().toUpperCase(), r.tipo().trim().toUpperCase(), r.valor(),
                r.montoMinimo() == null ? BigDecimal.ZERO : r.montoMinimo(), r.usosMaximos(),
                r.vigenteDesde(), r.vigenteHasta(), r.activo() == null || r.activo(), id);
    }
}
