package com.codeconheiner.tienda.auth.captcha;

public interface CaptchaVerifier {
    boolean verificar(String token, String ip);
}
