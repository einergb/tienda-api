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
                "Tienda", "COP", BigDecimal.ZERO, BigDecimal.ZERO, 10000, 5, 15,
                new TiendaProperties.Jwt("test-only-secret-with-at-least-32-bytes", 30),
                new TiendaProperties.Admin("admin@example.com", ""),
                new TiendaProperties.Cors(List.of("http://localhost:5173")));
    private final FakeUsuarioRepository usuarios = new FakeUsuarioRepository();
    private final FakeRefreshTokenRepository refreshTokens = new FakeRefreshTokenRepository();
    private final PasswordEncoder encoder = new BCryptPasswordEncoder();
    private final JwtService jwt = new JwtService(properties);
    private final AuthService service = new AuthService(usuarios, refreshTokens, encoder, jwt, properties);

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

    @Test
    void refrescarRotaElTokenYElAnteriorDejaDeServir() {
        TokenResponse registro = service.registrar(new RegistroRequest("Ana", "ana@example.com", "clave-segura"));

        TokenResponse refrescado = service.refrescar(registro.refreshToken());

        assertThat(refrescado.token()).isNotBlank();
        assertThat(refrescado.refreshToken()).isNotEqualTo(registro.refreshToken());
        assertThat(jwt.validar(refrescado.token()).getSubject()).isEqualTo("ana@example.com");

        ApiException error = catchThrowableOfType(
                () -> service.refrescar(registro.refreshToken()), ApiException.class);
        assertThat(error).isNotNull();
        assertThat(error.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(error).hasMessage("Sesión inválida o expirada.");
    }

    @Test
    void logoutInvalidaElRefreshToken() {
        TokenResponse registro = service.registrar(new RegistroRequest("Ana", "ana@example.com", "clave-segura"));

        service.logout(registro.refreshToken());

        ApiException error = catchThrowableOfType(
                () -> service.refrescar(registro.refreshToken()), ApiException.class);
        assertThat(error).isNotNull();
        assertThat(error.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void cambiarPasswordExigeLaActualYCierraLasSesiones() {
        TokenResponse registro = service.registrar(new RegistroRequest("Ana", "ana@example.com", "clave-segura"));

        ApiException incorrecta = catchThrowableOfType(
                () -> service.cambiarPassword("ana@example.com", "mala", "clave-nueva-segura"), ApiException.class);
        assertThat(incorrecta).isNotNull();
        assertThat(incorrecta.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(incorrecta).hasMessage("La contraseña actual no es correcta.");

        service.cambiarPassword("ana@example.com", "clave-segura", "clave-nueva-segura");

        assertThat(service.login(new LoginRequest("ana@example.com", "clave-nueva-segura")).rol())
                .isEqualTo("CLIENTE");
        ApiException sesion = catchThrowableOfType(
                () -> service.refrescar(registro.refreshToken()), ApiException.class);
        assertThat(sesion).isNotNull();
        assertThat(sesion.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private static class FakeRefreshTokenRepository extends RefreshTokenRepository {

        private final Map<String, RefreshTokenRepository.RefreshToken> tokens = new HashMap<>();
        private long siguiente = 1;

        FakeRefreshTokenRepository() {
            super((JdbcTemplate) null);
        }

        @Override
        public Long crear(Long usuarioId, String tokenHash, java.time.OffsetDateTime expiraEn) {
            long id = siguiente++;
            tokens.put(tokenHash, new RefreshTokenRepository.RefreshToken(id, usuarioId, expiraEn, false));
            return id;
        }

        @Override
        public Optional<RefreshTokenRepository.RefreshToken> buscarPorHash(String tokenHash) {
            return Optional.ofNullable(tokens.get(tokenHash));
        }

        @Override
        public int revocar(String tokenHash) {
            RefreshTokenRepository.RefreshToken token = tokens.get(tokenHash);
            if (token == null || token.revocado()) {
                return 0;
            }
            tokens.put(tokenHash, new RefreshTokenRepository.RefreshToken(token.id(), token.usuarioId(),
                    token.expiraEn(), true));
            return 1;
        }

        @Override
        public int revocarTodosDeUsuario(Long usuarioId) {
            int n = 0;
            for (Map.Entry<String, RefreshTokenRepository.RefreshToken> e : tokens.entrySet()) {
                RefreshTokenRepository.RefreshToken t = e.getValue();
                if (t.usuarioId().equals(usuarioId) && !t.revocado()) {
                    e.setValue(new RefreshTokenRepository.RefreshToken(t.id(), t.usuarioId(), t.expiraEn(), true));
                    n++;
                }
            }
            return n;
        }
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

        @Override
        public Optional<Usuario> buscarPorId(Long id) {
            return usuarios.values().stream().filter(u -> u.id().equals(id)).findFirst();
        }

        @Override
        public void actualizarPassword(Long id, String passwordHash) {
            usuarios.replaceAll((email, u) -> u.id().equals(id)
                    ? new Usuario(u.id(), u.nombre(), u.email(), passwordHash, u.rol(), u.activo()) : u);
        }

        String passwordHash(String email) {
            return usuarios.get(email.toLowerCase(Locale.ROOT)).passwordHash();
        }
    }
}