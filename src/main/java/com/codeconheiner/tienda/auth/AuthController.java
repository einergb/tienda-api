package com.codeconheiner.tienda.auth;

import com.codeconheiner.tienda.auth.dto.LoginRequest;
import com.codeconheiner.tienda.auth.dto.PasswordChangeRequest;
import com.codeconheiner.tienda.auth.dto.RefreshRequest;
import com.codeconheiner.tienda.auth.dto.RegistroRequest;
import com.codeconheiner.tienda.auth.dto.TokenResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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
    @ResponseStatus(HttpStatus.CREATED)
    public TokenResponse registro(@Valid @RequestBody RegistroRequest request) {
        return service.registrar(request);
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
