package com.codeconheiner.tienda.auth;

import com.codeconheiner.tienda.auth.RefreshTokenRepository.RefreshToken;
import com.codeconheiner.tienda.auth.dto.LoginRequest;
import com.codeconheiner.tienda.auth.dto.RegistroRequest;
import com.codeconheiner.tienda.auth.dto.TokenResponse;
import com.codeconheiner.tienda.common.ApiException;
import com.codeconheiner.tienda.config.TiendaProperties;
import com.codeconheiner.tienda.usuarios.Usuario;
import com.codeconheiner.tienda.usuarios.UsuarioRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private static final int DIAS_REFRESH_TOKEN = 7;
    private static final SecureRandom ALEATORIO = new SecureRandom();

    private final UsuarioRepository usuarios;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final TiendaProperties props;

    public AuthService(UsuarioRepository usuarios, RefreshTokenRepository refreshTokens, PasswordEncoder encoder,
            JwtService jwt, TiendaProperties props) {
        this.usuarios = usuarios;
        this.refreshTokens = refreshTokens;
        this.encoder = encoder;
        this.jwt = jwt;
        this.props = props;
    }

    public TokenResponse registrar(RegistroRequest r) {
        String email = r.email().trim().toLowerCase();
        if (usuarios.existePorEmail(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "El correo ya está registrado.");
        }
        Usuario u = usuarios.crear(r.nombre().trim(), email, encoder.encode(r.password()), "CLIENTE");
        return respuesta(u);
    }

    public TokenResponse login(LoginRequest r) {
        Usuario u = usuarios.buscarPorEmail(r.email().trim())
                .filter(Usuario::activo)
                .filter(x -> encoder.matches(r.password(), x.passwordHash()))
            .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Correo o contraseña incorrectos."));
        return respuesta(u);
    }

    /** Emite un nuevo par de tokens; el refresh token anterior queda revocado (rotación). */
    public TokenResponse refrescar(String refreshToken) {
        String hash = sha256(refreshToken);
        RefreshToken guardado = refreshTokens.buscarPorHash(hash)
                .filter(t -> !t.revocado())
                .filter(t -> t.expiraEn().isAfter(OffsetDateTime.now()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Sesión inválida o expirada."));
        Usuario u = usuarios.buscarPorId(guardado.usuarioId())
                .filter(Usuario::activo)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Sesión inválida o expirada."));
        refreshTokens.revocar(hash);
        return respuesta(u);
    }

    /** Revoca el refresh token. Responde sin error aunque el token ya no exista. */
    public void logout(String refreshToken) {
        refreshTokens.revocar(sha256(refreshToken));
    }

    public void cambiarPassword(String email, String passwordActual, String passwordNuevo) {
        Usuario u = usuarios.buscarPorEmail(email.trim())
                .filter(Usuario::activo)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED,
                        "Autenticación requerida: inicia sesión o proporciona un token válido."));
        if (!encoder.matches(passwordActual, u.passwordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "La contraseña actual no es correcta.");
        }
        usuarios.actualizarPassword(u.id(), encoder.encode(passwordNuevo));
        // cualquier sesión abierta con refresh tokens queda invalidada
        refreshTokens.revocarTodosDeUsuario(u.id());
    }

    private TokenResponse respuesta(Usuario u) {
        String refresh = generarRefreshToken(u.id());
        return new TokenResponse(jwt.generar(u), "Bearer", props.jwt().expiracionMinutos() * 60L, u.nombre(), u.rol(),
                refresh);
    }

    private String generarRefreshToken(Long usuarioId) {
        byte[] bytes = new byte[32];
        ALEATORIO.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        refreshTokens.crear(usuarioId, sha256(token),
                OffsetDateTime.now().plus(DIAS_REFRESH_TOKEN, ChronoUnit.DAYS));
        return token;
    }

    private static String sha256(String valor) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return java.util.HexFormat.of().formatHex(digest.digest(valor.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
