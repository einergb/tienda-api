package com.codeconheiner.tienda.auth;

import com.codeconheiner.tienda.auth.RefreshTokenStore.Sesion;
import com.codeconheiner.tienda.auth.dto.LoginRequest;
import com.codeconheiner.tienda.auth.dto.RegistroRequest;
import com.codeconheiner.tienda.auth.dto.TokenResponse;
import com.codeconheiner.tienda.common.ApiException;
import com.codeconheiner.tienda.common.Hashes;
import com.codeconheiner.tienda.config.TiendaProperties;
import com.codeconheiner.tienda.seguridad.Auditoria;
import com.codeconheiner.tienda.usuarios.Usuario;
import com.codeconheiner.tienda.usuarios.UsuarioRepository;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.security.SecureRandom;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private static final int DIAS_REFRESH_TOKEN = 7;
    private static final SecureRandom ALEATORIO = new SecureRandom();
    private static final String CREDENCIALES_INVALIDAS = "Correo o contraseña incorrectos.";
    private static final String SESION_INVALIDA = "Sesión inválida o expirada.";

    private final UsuarioRepository usuarios;
    private final RefreshTokenStore refreshTokens;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final TiendaProperties props;
    private final Auditoria auditoria;

    public AuthService(UsuarioRepository usuarios, RefreshTokenStore refreshTokens, PasswordEncoder encoder,
            JwtService jwt, TiendaProperties props, Auditoria auditoria) {
        this.usuarios = usuarios;
        this.refreshTokens = refreshTokens;
        this.encoder = encoder;
        this.jwt = jwt;
        this.props = props;
        this.auditoria = auditoria;
    }

    public TokenResponse registrar(RegistroRequest r) {
        String email = r.email().trim().toLowerCase();
        if (usuarios.existePorEmail(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "El correo ya está registrado.");
        }
        Usuario u = usuarios.crear(r.nombre().trim(), email, encoder.encode(r.password()), "CLIENTE");
        auditoria.registrar(u.id(), "usuario.registro", "usuarios", String.valueOf(u.id()), null);
        return respuesta(u);
    }

    /** Login con registro de intentos fallidos (auditoría) para poder detectar fuerza bruta. */
    public TokenResponse login(LoginRequest r) {
        String email = r.email().trim().toLowerCase();
        Usuario u = usuarios.buscarPorEmail(email)
                .filter(Usuario::activo)
                .filter(x -> encoder.matches(r.password(), x.passwordHash()))
                .orElseThrow(() -> {
                    // No se revela si el correo existe: el mensaje es siempre el mismo,
                    // pero en auditoría queda constancia del intento fallido.
                    auditoria.registrar(null, "auth.login_fallido", "usuarios", null,
                            java.util.Map.of("email", email));
                    return new ApiException(HttpStatus.UNAUTHORIZED, CREDENCIALES_INVALIDAS);
                });
        auditoria.registrar(u.id(), "auth.login", "usuarios", String.valueOf(u.id()), null);
        return respuesta(u);
    }

    /**
     * Emite un nuevo par de tokens y marca el anterior como usado (rotación).
     *
     * Si el token presentado ya se había usado o revocado, se asume robo de
     * credenciales: se revoca la familia completa y todas las sesiones del usuario.
     */
    public TokenResponse refrescar(String refreshToken) {
        String hash = Hashes.sha256Hex(refreshToken);
        Sesion sesion = refreshTokens.buscarPorHash(hash)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, SESION_INVALIDA));

        if (!sesion.vigente(OffsetDateTime.now())) {
            // Reuso (o token revocado/expirado): se corta la familia por seguridad.
            refreshTokens.revocarFamilia(sesion.usuarioId(), sesion.sesionId());
            auditoria.registrar(sesion.usuarioId(), "auth.reuso_refresh_token", "refresh_tokens",
                    String.valueOf(sesion.sesionId()), null);
            throw new ApiException(HttpStatus.UNAUTHORIZED, SESION_INVALIDA);
        }

        Usuario u = usuarios.buscarPorId(sesion.usuarioId())
                .filter(Usuario::activo)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, SESION_INVALIDA));

        String nuevoRefresh = generarToken();
        Long nuevoId = refreshTokens.crearEnFamilia(u.id(), sesion.sesionId(), Hashes.sha256Hex(nuevoRefresh),
                OffsetDateTime.now().plus(DIAS_REFRESH_TOKEN, ChronoUnit.DAYS));
        if (refreshTokens.marcarUsado(hash, nuevoId) == 0) {
            // Otra petición rotó el mismo token en paralelo: se descarta el nuevo.
            refreshTokens.revocarFamilia(sesion.usuarioId(), sesion.sesionId());
            throw new ApiException(HttpStatus.UNAUTHORIZED, SESION_INVALIDA);
        }
        return new TokenResponse(jwt.generar(u), "Bearer", props.jwt().expiracionMinutos() * 60L, u.nombre(),
                u.rol(), nuevoRefresh);
    }

    /** Revoca la familia del token presentado. Responde sin error aunque ya no exista. */
    public void logout(String refreshToken) {
        refreshTokens.buscarPorHash(Hashes.sha256Hex(refreshToken))
                .ifPresent(s -> refreshTokens.revocarFamilia(s.usuarioId(), s.sesionId()));
    }

    public void cambiarPassword(String email, String passwordActual, String passwordNuevo) {
        Usuario u = usuarios.buscarPorEmail(email.trim())
                .filter(Usuario::activo)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED,
                        "Autenticación requerida: inicia sesión o proporciona un token válido."));
        if (!encoder.matches(passwordActual, u.passwordHash())) {
            auditoria.registrar(u.id(), "auth.password_fallido", "usuarios", String.valueOf(u.id()), null);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "La contraseña actual no es correcta.");
        }
        usuarios.actualizarPassword(u.id(), encoder.encode(passwordNuevo));
        // cualquier sesión abierta con refresh tokens queda invalidada
        refreshTokens.revocarTodosDeUsuario(u.id());
        auditoria.registrar(u.id(), "auth.password_cambio", "usuarios", String.valueOf(u.id()), null);
    }

    private TokenResponse respuesta(Usuario u) {
        String refresh = generarToken();
        refreshTokens.crearFamilia(u.id(), Hashes.sha256Hex(refresh),
                OffsetDateTime.now().plus(DIAS_REFRESH_TOKEN, ChronoUnit.DAYS));
        return new TokenResponse(jwt.generar(u), "Bearer", props.jwt().expiracionMinutos() * 60L, u.nombre(), u.rol(),
                refresh);
    }

    private static String generarToken() {
        byte[] bytes = new byte[32];
        ALEATORIO.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
