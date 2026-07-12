package com.silvio.elending.client;

import com.silvio.elending.dto.UsuarioDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

// Tests para IdentityClientFallbackFactory
// Verifica que cuando el circuito está abierto o identity-service no responde,
// se devuelvan respuestas degradadas coherentes.
class IdentityClientFallbackFactoryTest {

    private IdentityClientFallbackFactory fallbackFactory;

    @BeforeEach
    void setUp() {
        fallbackFactory = new IdentityClientFallbackFactory();
    }

    @Test
    void create_conExcepcion_retornaClienteConFallback() {
        // Given — cualquier excepción que active el CB
        RuntimeException causa = new RuntimeException("Connection refused");

        // When
        IdentityClient clienteFallback = fallbackFactory.create(causa);

        // Then — el cliente creado no es null y sus métodos devuelven degradado
        assertNotNull(clienteFallback);
        UsuarioDTO resultado = clienteFallback.obtenerUsuario("testuser");

        assertNotNull(resultado);
        assertEquals(0L, resultado.getId());
        assertEquals("testuser", resultado.getUsername());
        assertNotNull(resultado.getRoles());
        assertEquals(1, resultado.getRoles().size());
        assertTrue(resultado.getRoles().contains("ROLE_USER"));
    }

    @Test
    void create_conExcepcion_conNullMessage_retornaClienteFallback() {
        // Given — excepción con mensaje null (caso borde)
        RuntimeException causa = new RuntimeException();

        // When
        IdentityClient clienteFallback = fallbackFactory.create(causa);

        // Then — debe funcionar sin NPE porque el código maneja cause.getMessage() null
        assertNotNull(clienteFallback);
        UsuarioDTO resultado = clienteFallback.obtenerUsuario("user_null_msg");

        assertNotNull(resultado);
        assertEquals(0L, resultado.getId());
        assertEquals("user_null_msg", resultado.getUsername());
    }

    @Test
    void create_conExcepcion_yUsernameNull_retornaFallbackConUsernameNull() {
        // Given — excepción cualquiera
        RuntimeException causa = new RuntimeException("Timeout");

        // When
        IdentityClient clienteFallback = fallbackFactory.create(causa);

        // Then — el fallback preserva el username incluso si es null
        UsuarioDTO resultado = clienteFallback.obtenerUsuario(null);

        assertNotNull(resultado);
        assertEquals(0L, resultado.getId());
        assertNull(resultado.getUsername());
        assertTrue(resultado.getRoles().contains("ROLE_USER"));
    }

    @Test
    void fallback_retornaRolesInmutables() {
        // Given
        RuntimeException causa = new RuntimeException("Service unavailable");

        // When
        IdentityClient clienteFallback = fallbackFactory.create(causa);
        UsuarioDTO resultado = clienteFallback.obtenerUsuario("user_roles");

        // Then — Set.of() retorna un Set inmutable
        assertNotNull(resultado.getRoles());
        assertThrows(UnsupportedOperationException.class,
                () -> resultado.getRoles().add("ROLE_ADMIN"));
    }

    @Test
    void create_conExcepcionDeRed_retornaFallbackConsistente() {
        // Given — simula una excepción típica de red
        RuntimeException causa = new RuntimeException(
                "connect: Address is invalid, or socket could not be bound");

        // When
        IdentityClient clienteFallback = fallbackFactory.create(causa);
        UsuarioDTO resultado = clienteFallback.obtenerUsuario("network_user");

        // Then — respuesta consistente independientemente del tipo de excepción
        assertNotNull(resultado);
        assertEquals(0L, resultado.getId());
        assertEquals("network_user", resultado.getUsername());
        assertEquals(Set.of("ROLE_USER"), resultado.getRoles());
    }
}
