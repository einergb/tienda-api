package com.codeconheiner.tienda.auth;

import com.codeconheiner.tienda.auth.dto.LoginRequest;
import com.codeconheiner.tienda.auth.dto.RegistroRequest;
import com.codeconheiner.tienda.auth.dto.TokenResponse;
import com.codeconheiner.tienda.common.ApiException;
import com.codeconheiner.tienda.config.TiendaProperties;
import com.codeconheiner.tienda.usuarios.Usuario;
import com.codeconheiner.tienda.usuarios.UsuarioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final TiendaProperties props;

    public AuthService(UsuarioRepository usuarios, PasswordEncoder encoder, JwtService jwt, TiendaProperties props) {
        this.usuarios = usuarios;
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

    private TokenResponse respuesta(Usuario u) {
        return new TokenResponse(jwt.generar(u), "Bearer", props.jwt().expiracionMinutos() * 60L, u.nombre(), u.rol());
    }
}
