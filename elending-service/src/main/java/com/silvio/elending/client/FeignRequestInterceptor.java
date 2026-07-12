package com.silvio.elending.client;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

// Interceptor Feign que propaga el token JWT automáticamente a todos
// los Feign Clients (IdentityClient, CatalogClient, SubscriptionClient, LicenseClient).
// Lee el token desde SecurityContextHolder — JwtAuthenticationFilter lo pobló
// al recibir la petición entrante.
//
// Sin este interceptor, cada Feign Client tendría que recibir el authHeader
// como parámetro en cada método, lo que duplica lógica y aumenta el acoplamiento.
@Component
public class FeignRequestInterceptor implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate template) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        // Verifica que el credentials sea un String antes de castear
        // para evitar ClassCastException si otro componente pobló el contexto
        // con un tipo de credencial distinto
        if (authentication != null && authentication.getCredentials() instanceof String token
                && !token.isBlank()) {
            template.header("Authorization", "Bearer " + token);
        }
    }
}
