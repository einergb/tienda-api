package com.codeconheiner.tienda.auth.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegistroRequest(
        @NotBlank(message = "El primer nombre es obligatorio")
        @Size(max = 60, message = "El primer nombre no puede superar 60 caracteres")
        String primerNombre,
        @Size(max = 60, message = "El segundo nombre no puede superar 60 caracteres")
        String segundoNombre,
        @NotBlank(message = "El primer apellido es obligatorio")
        @Size(max = 60, message = "El primer apellido no puede superar 60 caracteres")
        String primerApellido,
        @Size(max = 60, message = "El segundo apellido no puede superar 60 caracteres")
        String segundoApellido,
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 120, message = "El nombre no puede superar 120 caracteres")
        String nombre,
        @NotBlank(message = "El teléfono es obligatorio")
        @Size(max = 30, message = "El teléfono no puede superar 30 caracteres")
        String telefono,
        @NotBlank(message = "El correo es obligatorio")
        @Email(message = "El correo no tiene un formato válido")
        @Size(max = 160, message = "El correo no puede superar 160 caracteres")
        String email,
        @NotBlank(message = "La confirmación de correo es obligatoria")
        @Email(message = "La confirmación de correo no tiene un formato válido")
        @Size(max = 160, message = "La confirmación de correo no puede superar 160 caracteres")
        String emailConfirmacion,
        @NotBlank(message = "La contraseña es obligatoria")
        @Size(min = 8, max = 72, message = "La contraseña debe tener entre 8 y 72 caracteres")
        String password,
        @NotBlank(message = "El token CAPTCHA es obligatorio")
        String captchaToken,
        @NotNull(message = "Debe aceptar los términos y condiciones")
        @AssertTrue(message = "Debe aceptar los términos y condiciones")
        Boolean aceptaTerminos,
        @NotNull(message = "Debe autorizar el tratamiento de datos")
        @AssertTrue(message = "Debe autorizar el tratamiento de datos")
        Boolean aceptaTratamientoDatos,
        Boolean aceptaMarketing) {
    public RegistroRequest(String nombre, String email, String password) {
        this(null, null, null, null, nombre, null, email, email, password, "dummy", true, true, Boolean.FALSE);
    }
}
