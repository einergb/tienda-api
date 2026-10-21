package com.codeconheiner.tienda.auth;

import com.codeconheiner.tienda.auth.dto.ConfirmacionEmailRequest;
import com.codeconheiner.tienda.auth.dto.LoginRequest;
import com.codeconheiner.tienda.auth.dto.PasswordChangeRequest;
import com.codeconheiner.tienda.auth.dto.ReenviarConfirmacionRequest;
import com.codeconheiner.tienda.auth.dto.RefreshRequest;
import com.codeconheiner.tienda.auth.dto.RegistroPendienteResponse;
import com.codeconheiner.tienda.auth.dto.RegistroRequest;
import com.codeconheiner.tienda.auth.dto.TokenResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService service;

    public AuthController(AuthService service) {
        this.service = service;
    }

    @PostMapping("/registro")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public RegistroPendienteResponse registro(@Valid @RequestBody RegistroRequest request,
            HttpServletRequest httpRequest) {
        return service.registrar(request, httpRequest);
    }

    @PostMapping("/confirmar-email")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirmarEmail(@Valid @RequestBody ConfirmacionEmailRequest request) {
        service.confirmarEmail(request.token());
    }

    @GetMapping("/confirmar-email")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirmarEmailGet(@RequestParam("token") String token) {
        service.confirmarEmail(token);
    }

    @PostMapping("/reenviar-confirmacion")
    public RegistroPendienteResponse reenviarConfirmacion(@Valid @RequestBody ReenviarConfirmacionRequest request,
            HttpServletRequest httpRequest) {
        return service.reenviarConfirmacion(request.email(), httpRequest);
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return service.login(request);
    }

    /** Rota el refresh token por uno nuevo y devuelve un access token fresco. */
    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return service.refrescar(request.refreshToken());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody RefreshRequest request) {
        service.logout(request.refreshToken());
    }

    @PutMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cambiarPassword(@Valid @RequestBody PasswordChangeRequest request) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        service.cambiarPassword(email, request.passwordActual(), request.passwordNuevo());
    }
}
