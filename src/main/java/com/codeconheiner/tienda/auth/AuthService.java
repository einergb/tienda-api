package com.codeconheiner.tienda.auth;

import com.codeconheiner.tienda.auth.RefreshTokenStore.Sesion;
import com.codeconheiner.tienda.auth.captcha.CaptchaVerifier;
import com.codeconheiner.tienda.auth.dto.LoginRequest;
import com.codeconheiner.tienda.auth.dto.RegistroPendienteResponse;
import com.codeconheiner.tienda.auth.dto.RegistroRequest;
import com.codeconheiner.tienda.auth.dto.TokenResponse;
import com.codeconheiner.tienda.auth.mail.EmailService;
import com.codeconheiner.tienda.auth.verification.ConsentimientoRepository;
import com.codeconheiner.tienda.auth.verification.VerificationTokenRepository;
import com.codeconheiner.tienda.common.ApiException;
import com.codeconheiner.tienda.common.Hashes;
import com.codeconheiner.tienda.config.IpResolver;
import com.codeconheiner.tienda.config.TiendaProperties;
import com.codeconheiner.tienda.seguridad.Auditoria;
import com.codeconheiner.tienda.usuarios.Usuario;
import com.codeconheiner.tienda.usuarios.UsuarioRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final CaptchaVerifier captchaVerifier;
    private final VerificationTokenRepository verificationTokens;
    private final ConsentimientoRepository consentimientos;
    private final EmailService emailService;

    public AuthService(UsuarioRepository usuarios, RefreshTokenStore refreshTokens, PasswordEncoder encoder,
            JwtService jwt, TiendaProperties props, Auditoria auditoria, CaptchaVerifier captchaVerifier,
            VerificationTokenRepository verificationTokens, ConsentimientoRepository consentimientos,
            EmailService emailService) {
        this.usuarios = usuarios;
        this.refreshTokens = refreshTokens;
        this.encoder = encoder;
        this.jwt = jwt;
        this.props = props;
        this.auditoria = auditoria;
        this.captchaVerifier = captchaVerifier;
        this.verificationTokens = verificationTokens;
        this.consentimientos = consentimientos;
        this.emailService = emailService;
    }

    @Transactional
    public RegistroPendienteResponse registrar(RegistroRequest r, HttpServletRequest request) {
        String email = r.email().trim().toLowerCase();
        String emailConfirmacion = r.emailConfirmacion() == null ? null : r.emailConfirmacion().trim().toLowerCase();
        if (emailConfirmacion == null || !emailConfirmacion.equals(email)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Los correos electrónicos no coinciden");
        }
        if (usuarios.existePorEmail(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "El correo ya está registrado.");
        }
        if (r.aceptaTerminos() == null || !r.aceptaTerminos()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Debe aceptar los términos y condiciones");
        }
        if (r.aceptaTratamientoDatos() == null || !r.aceptaTratamientoDatos()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Debe autorizar el tratamiento de datos");
        }

        String ip = request == null ? null : IpResolver.resolve(request);
        captchaVerifier.verificar(r.captchaToken(), ip);

        String nombreCompleto = (r.nombre() == null || r.nombre().isBlank())
                ? String.join(" ", new String[] { r.primerNombre(), r.segundoNombre(), r.primerApellido(),
                        r.segundoApellido() }).replaceAll("\\s+", " ").trim()
                : r.nombre().trim();
        Usuario u = usuarios.crearCompleto(nombreCompleto, r.primerNombre(), r.segundoNombre(), r.primerApellido(),
                r.segundoApellido(), email, r.telefono(), encoder.encode(r.password()), "CLIENTE");

        consentimientos.guardar(u.id(), "TERMINOS", "v1", request);
        consentimientos.guardar(u.id(), "TRATAMIENTO_DATOS", "v1", request);
        if (Boolean.TRUE.equals(r.aceptaMarketing())) {
            consentimientos.guardar(u.id(), "MARKETING", "v1", request);
        }

        String token = generarToken();
        verificationTokens.crear(u.id(), token, OffsetDateTime.now().plus(24, ChronoUnit.HOURS));
        try {
            emailService.enviarConfirmacion(email, token);
        } catch (Exception e) {
            // No interrumpir el registro si falla el envío; se puede reenviar
        }
        auditoria.registrar(u.id(), "usuario.registro_pendiente", "usuarios", String.valueOf(u.id()), null);
        return new RegistroPendienteResponse("Registro pendiente de confirmación. Revisa tu correo electrónico.");
    }

    /** Login con registro de intentos fallidos (auditoría) para poder detectar fuerza bruta. */
    public TokenResponse login(LoginRequest r) {
        String email = r.email().trim().toLowerCase();
        Usuario u = usuarios.buscarPorEmail(email)
                .filter(Usuario::activo)
                .filter(x -> Boolean.TRUE.equals(x.emailVerificado()) && "ACTIVO".equals(x.estado()))
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
                .filter(x -> Boolean.TRUE.equals(x.emailVerificado()) && "ACTIVO".equals(x.estado()))
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

    @Transactional
    public void confirmarEmail(String token) {
        Long usuarioId = verificationTokens.consumir(token, OffsetDateTime.now())
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Token inválido o expirado"));
        usuarios.activarYVerificar(usuarioId);
        auditoria.registrar(usuarioId, "auth.email_confirmado", "usuarios", String.valueOf(usuarioId), null);
    }

    @Transactional
    public RegistroPendienteResponse reenviarConfirmacion(String email, HttpServletRequest request) {
        String correo = email.trim().toLowerCase();
        Usuario u = usuarios.buscarPorEmail(correo).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                "Usuario no encontrado"));
        if (Boolean.TRUE.equals(u.emailVerificado()) && "ACTIVO".equals(u.estado())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "El correo ya ha sido confirmado");
        }
        verificationTokens.invalidarTodos(u.id());
        int recientes = verificationTokens.contarRecientes(u.id(), OffsetDateTime.now().minusHours(1));
        if (recientes >= 3) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                    "Demasiadas solicitudes de reenvío. Intenta de nuevo más tarde.");
        }
        String token = generarToken();
        verificationTokens.crear(u.id(), token, OffsetDateTime.now().plus(24, ChronoUnit.HOURS));
        try {
            emailService.enviarConfirmacion(correo, token);
        } catch (Exception e) {
            // estrategia de reintento mediante nuevo endpoint / límite
        }
        auditoria.registrar(u.id(), "auth.email_reenvio", "usuarios", String.valueOf(u.id()), null);
        return new RegistroPendienteResponse("Se ha enviado un nuevo enlace de confirmación.");
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
