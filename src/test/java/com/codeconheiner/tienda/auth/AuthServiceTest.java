package com.codeconheiner.tienda.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.codeconheiner.tienda.auth.dto.LoginRequest;
import com.codeconheiner.tienda.auth.dto.RegistroRequest;
import com.codeconheiner.tienda.auth.dto.TokenResponse;
import com.codeconheiner.tienda.common.ApiException;
import com.codeconheiner.tienda.config.TiendaProperties;
import com.codeconheiner.tienda.seguridad.Auditoria;
import com.codeconheiner.tienda.usuarios.Usuario;
import com.codeconheiner.tienda.usuarios.UsuarioRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
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
            new TiendaProperties.Cors(List.of("http://localhost:5173")),
            new TiendaProperties.RateLimit(300, 10, 60),
            TiendaProperties.Pagos.simulados());
    private final FakeUsuarioRepository usuarios = new FakeUsuarioRepository();
    private final FakeRefreshTokenRepository refreshTokens = new FakeRefreshTokenRepository();
    private final PasswordEncoder encoder = new BCryptPasswordEncoder();
    private final JwtService jwt = new JwtService(properties);
    private final AuthService service = new AuthService(usuarios, refreshTokens, encoder, jwt, properties,
            new NoOpAuditoria());

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
    void reusoDeRefreshTokenRevocaLaFamiliaCompleta() {
        TokenResponse registro = service.registrar(new RegistroRequest("Ana", "ana@example.com", "clave-segura"));
        TokenResponse refrescado = service.refrescar(registro.refreshToken());

        // alguien reutiliza el token ya rotado: se corta la familia entera
        ApiException reuso = catchThrowableOfType(
                () -> service.refrescar(registro.refreshToken()), ApiException.class);
        assertThat(reuso).isNotNull();
        assertThat(reuso.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);

        ApiException hermano = catchThrowableOfType(
                () -> service.refrescar(refrescado.refreshToken()), ApiException.class);
        assertThat(hermano).isNotNull();
        assertThat(hermano.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(hermano).hasMessage("Sesión inválida o expirada.");
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

    private static class NoOpAuditoria extends Auditoria {
        NoOpAuditoria() {
            super(null, null);
        }

        @Override
        public void registrar(Long usuarioId, String accion, String entidad, String entidadId,
                java.util.Map<String, ?> detalle) {
            // No-op en los tests unitarios
        }
    }

    private static class FakeRefreshTokenRepository implements RefreshTokenStore {

        private final List<Sesion> sesiones = new ArrayList<>();
        private long siguiente = 1;

        FakeRefreshTokenRepository() {
        }

        @Override
        public Long crearFamilia(Long usuarioId, String tokenHash, OffsetDateTime expiraEn) {
            long id = siguiente++;
            long familia = id;
            sesiones.add(new Sesion(id, usuarioId, familia, expiraEn, false, false, null, null));
            // guardamos hash -> id en un mapa implícito: vamos a buscar por hash
            // en este fake, almacenamos el hash como el último dígito
            storeHash(tokenHash, id);
            return id;
        }

        private final Map<String, Long> hashToId = new HashMap<>();

        private void storeHash(String tokenHash, Long id) {
            hashToId.put(tokenHash, id);
        }

        @Override
        public Long crearEnFamilia(Long usuarioId, Long sesionId, String tokenHash, OffsetDateTime expiraEn) {
            long id = siguiente++;
            sesiones.add(new Sesion(id, usuarioId, sesionId, expiraEn, false, false, null, null));
            storeHash(tokenHash, id);
            return id;
        }

        @Override
        public Optional<Sesion> buscarPorHash(String tokenHash) {
            Long id = hashToId.get(tokenHash);
            if (id == null) {
                return Optional.empty();
            }
            return sesiones.stream().filter(s -> s.id().equals(id)).findFirst();
        }

        @Override
        public int marcarUsado(String tokenHash, Long reemplazadoPor) {
            Optional<Sesion> s = buscarPorHash(tokenHash);
            if (s.isEmpty() || s.get().revocado() || s.get().usado()) {
                return 0;
            }
            Sesion actual = s.get();
            int idx = sesiones.indexOf(actual);
            if (idx >= 0) {
                sesiones.set(idx, new Sesion(actual.id(), actual.usuarioId(), actual.sesionId(), actual.expiraEn(),
                        false, true, OffsetDateTime.now(), null));
            }
            return 1;
        }

        @Override
        public int revocarFamilia(Long usuarioId, Long sesionId) {
            int n = 0;
            for (int i = 0; i < sesiones.size(); i++) {
                Sesion s = sesiones.get(i);
                if (s.usuarioId().equals(usuarioId) && s.sesionId().equals(sesionId) && !s.revocado()) {
                    sesiones.set(i, new Sesion(s.id(), s.usuarioId(), s.sesionId(), s.expiraEn(), true, s.usado(),
                            s.usadoEn(), OffsetDateTime.now()));
                    n++;
                }
            }
            return n;
        }

        @Override
        public int revocarTodosDeUsuario(Long usuarioId) {
            int n = 0;
            for (int i = 0; i < sesiones.size(); i++) {
                Sesion s = sesiones.get(i);
                if (s.usuarioId().equals(usuarioId) && !s.revocado()) {
                    sesiones.set(i, new Sesion(s.id(), s.usuarioId(), s.sesionId(), s.expiraEn(), true, s.usado(),
                            s.usadoEn(), OffsetDateTime.now()));
                    n++;
                }
            }
            return n;
        }

        @Override
        public int purgarAntiguos(int diasRetencion) {
            return 0;
        }

        @Override
        public int revocar(String tokenHash) {
            return marcarUsado(tokenHash, null);
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
        public Optional<Usuario> buscarPorId(Long id) {
            return usuarios.entrySet().stream()
                    .filter(e -> emailToId.get(e.getKey()).equals(id))
                    .map(Map.Entry::getValue)
                    .findFirst();
        }

        private final Map<String, Long> emailToId = new HashMap<>();
        private long siguienteId = 1;

        @Override
        public Usuario crear(String nombre, String email, String passwordHash, String rol) {
            long id = siguienteId++;
            Usuario u = new Usuario(id, nombre, email, passwordHash, rol, true);
            usuarios.put(email.toLowerCase(Locale.ROOT), u);
            emailToId.put(email.toLowerCase(Locale.ROOT), id);
            return u;
        }

        @Override
        public void actualizarPassword(Long id, String passwordHash) {
            for (Map.Entry<String, Usuario> e : usuarios.entrySet()) {
                Usuario u = e.getValue();
                if (emailToId.get(e.getKey()).equals(id)) {
                    usuarios.put(e.getKey(),
                            new Usuario(u.id(), u.nombre(), u.email(), passwordHash, u.rol(), u.activo()));
                }
            }
        }

        public String passwordHash(String email) {
            return usuarios.get(email.toLowerCase(Locale.ROOT)).passwordHash();
        }
    }
}
