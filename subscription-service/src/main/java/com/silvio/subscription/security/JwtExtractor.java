package com.silvio.subscription.security;

import org.springframework.stereotype.Component;

@Component
public class JwtExtractor {

    public String extraerUsuario(String authHeader) {
        try {
            String token = authHeader.substring(7);
            String payload = token.split("\\.")[1];
            String decodedPayload = new String(
                    java.util.Base64.getUrlDecoder().decode(payload));
            return decodedPayload.split("\"sub\":\"")[1].split("\"")[0];
        } catch (Exception e) {
            throw new RuntimeException("No se pudo extraer el usuario del token");
        }
    }
}