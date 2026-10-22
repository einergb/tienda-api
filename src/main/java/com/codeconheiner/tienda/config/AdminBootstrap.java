package com.codeconheiner.tienda.config;

import com.codeconheiner.tienda.usuarios.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Crea el administrador inicial si ADMIN_PASSWORD esta definido y el usuario no existe. */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final TiendaProperties props;
    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;

    public AdminBootstrap(TiendaProperties props, UsuarioRepository usuarios, PasswordEncoder encoder) {
        this.props = props;
        this.usuarios = usuarios;
        this.encoder = encoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        String email = props.admin().email();
        String password = props.admin().password();
        if (password == null || password.isBlank()) {
            log.warn("ADMIN_PASSWORD no definido: no se crea administrador inicial.");
            return;
        }
        if (email == null || email.isBlank()) {
            log.warn("ADMIN_EMAIL no definido: no se crea administrador inicial.");
            return;
        }
        String emailNormalizado = email.trim().toLowerCase();
        if (usuarios.existePorEmail(emailNormalizado)) {
            return;
        }
        usuarios.crearAdmin("Administrador", emailNormalizado, encoder.encode(password));
        log.info("Administrador inicial creado: {}", emailNormalizado);
    }
}
