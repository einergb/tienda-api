package com.codeconheiner.tienda.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.codeconheiner.tienda.auth.dto.LoginRequest;
import com.codeconheiner.tienda.auth.dto.RegistroRequest;
import com.codeconheiner.tienda.auth.dto.TokenResponse;
import com.codeconheiner.tienda.common.ApiException;
import com.codeconheiner.tienda.config.TiendaProperties;
import com.codeconheiner.tienda.usuarios.Usuario;
import com.codeconheiner.tienda.usuarios.UsuarioRepository;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthServiceTest {

    private final TiendaProperties properties = new TiendaProperties(
                "Tienda", "COP", BigDecimal.ZERO, BigDecimal.ZERO, 5, 15,
                new TiendaProperties.Jwt("test-only-secret-with-at-least-32-bytes", 30),
                new TiendaProperties.Admin("admin@example.com", ""),
                new TiendaProperties.Cors(List.of("http://localhost:5173")));
    private final FakeUsuarioRepository usuarios = new FakeUsuarioRepository();
    private final PasswordEncoder encoder = new BCryptPasswordEncoder();
    private final JwtService jwt = new JwtService(properties);
    private final AuthService service = new AuthService(usuarios, encoder, jwt, properties);

    @Test
    void registraUsuarioNormalizandoDatosYGenerandoToken() {
        RegistroRequest request = new RegistroRequest("  Ana Pérez  ", " ANA@EXAMPLE.COM ", "clave-segura");

        TokenResponse respuesta = service.registrar(request);

        assertThat(respuesta.tipo()).isEqualTo("Bearer");
        assertThat(respuesta.expiraEnSegundos()).isEqualTo(1800);
        assertThat(respuesta.nombre()).isEqualTo("Ana Pérez");
        assertThat(respuesta.rol()).isEqualTo("CLIENTE");
        assertThat(jwt.validar(respuesta.token()).getSubject()).isEqualTo("ana@example.com");
        assertThat(jwt.validar(respuesta.token()).get("rol", String.class)).isEqualTo("CLIENTE");
        assertThat(encoder.matches("clave-segura", usuarios.passwordHash("ana@example.com"))).isTrue();
    }

    @Test
    void rechazaRegistroConCorreoDuplicado() {
        service.registrar(new RegistroRequest("Ana", "ana@example.com", "clave-segura"));

        ApiException error = catchThrowableOfType(
                () -> service.registrar(new RegistroRequest("Ana", "ana@example.com", "clave-segura")),
                ApiException.class);

        assertThat(error).isNotNull();
        assertThat(error.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(error).hasMessage("El correo ya está registrado.");
    }

    @Test
    void iniciaSesionConCredencialesCorrectas() {
        service.registrar(new RegistroRequest("Ana", "ana@example.com", "clave-segura"));

        TokenResponse respuesta = service.login(new LoginRequest("ana@example.com", "clave-segura"));

        assertThat(respuesta.rol()).isEqualTo("CLIENTE");
        assertThat(jwt.validar(respuesta.token()).getSubject()).isEqualTo("ana@example.com");
    }

    @Test
    void rechazaCredencialesIncorrectasConMensajeGenerico() {
        ApiException error = catchThrowableOfType(
            () -> service.login(new LoginRequest("nadie@example.com", "incorrecta")), ApiException.class);

        assertThat(error).isNotNull();
        assertThat(error.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(error).hasMessage("Correo o contraseña incorrectos.");
    }

    private static class FakeUsuarioRepository extends UsuarioRepository {

        private final Map<String, Usuario> usuarios = new HashMap<>();

        FakeUsuarioRepository() {
            super((JdbcTemplate) null);
        }

        @Override
        public Optional<Usuario> buscarPorEmail(String email) {
            return Optional.ofNullable(usuarios.get(email.toLowerCase(Locale.ROOT)));
        }

        @Override
        public boolean existePorEmail(String email) {
            return usuarios.containsKey(email.toLowerCase(Locale.ROOT));
        }

        @Override
        public Usuario crear(String nombre, String email, String passwordHash, String rol) {
            Usuario usuario = new Usuario((long) usuarios.size() + 1, nombre, email, passwordHash, rol, true);
            usuarios.put(email.toLowerCase(Locale.ROOT), usuario);
            return usuario;
        }

        String passwordHash(String email) {
            return usuarios.get(email.toLowerCase(Locale.ROOT)).passwordHash();
        }
    }
}