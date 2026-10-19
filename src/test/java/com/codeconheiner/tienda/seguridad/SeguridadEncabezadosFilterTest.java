package com.codeconheiner.tienda.seguridad;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class SeguridadEncabezadosFilterTest {

    private final SeguridadEncabezadosFilter filtro =
            new SeguridadEncabezadosFilter("default-src 'self'; frame-ancestors 'none'");

    @Test
    void agregaCabecerasDeSeguridadEnTodaRespuesta() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/catalogo/productos");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain cadena = (req, res) -> {
        };

        filtro.doFilter(request, response, cadena);

        assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(response.getHeader("X-Frame-Options")).isEqualTo("DENY");
        assertThat(response.getHeader("Referrer-Policy")).isEqualTo("strict-origin-when-cross-origin");
        assertThat(response.getHeader("Permissions-Policy")).contains("geolocation=()");
        assertThat(response.getHeader("Content-Security-Policy")).contains("frame-ancestors 'none'");
        // HSTS solo viaja por HTTPS
        assertThat(response.getHeader("Strict-Transport-Security")).isNull();
    }

    @Test
    void activaHstsSoloCuandoLaConexionEsSegura() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/catalogo/productos");
        request.setSecure(true);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filtro.doFilter(request, response, (req, res) -> {
        });

        assertThat(response.getHeader("Strict-Transport-Security")).contains("max-age=31536000");
    }
}
