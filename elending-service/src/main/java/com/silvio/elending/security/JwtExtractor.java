package com.silvio.elending.security;

import com.silvio.elending.exception.TokenExtraccionException;
import org.springframework.stereotype.Component;

// Componente utilitario que extrae el username del token JWT
// Centraliza esta responsabilidad fuera de los controllers
// cumpliendo el principio de separacion de responsabilidades (CSR)
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
            throw new TokenExtraccionException();
        }
    }
}