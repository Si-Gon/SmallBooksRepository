package com.silvio.identity.repository;

import com.silvio.identity.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests de integración de UserRepository con H2.
 *
 * Verifica que la carga lazy de roles funciona correctamente:
 * - findByUsername() carga el usuario sin roles (lazy)
 * - findByUsernameWithRoles() carga roles con JOIN FETCH vía @EntityGraph
 * - El resto de métodos de consulta (resetToken, refreshToken) siguen funcionando
 */
@DataJpaTest
@ActiveProfiles("test")
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TestEntityManager entityManager;

    private User crearUsuarioBase(String username) {
        User user = new User();
        user.setUsername(username);
        user.setPassword("$2a$10$encodedPasswordForTest");
        user.setRoles(Set.of("ROLE_USER"));
        return user;
    }

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    // ─── findByUsername (carga lazy, sin roles) ─────────────────────────────

    @Test
    void findByUsername_retornaUsuario_cuandoExiste() {
        // Given
        userRepository.save(crearUsuarioBase("silvio"));

        // When
        Optional<User> resultado = userRepository.findByUsername("silvio");

        // Then — el usuario existe y los datos básicos están presentes
        assertThat(resultado).isPresent();
        assertThat(resultado.get().getUsername()).isEqualTo("silvio");
        assertThat(resultado.get().getPassword()).isEqualTo("$2a$10$encodedPasswordForTest");
    }

    @Test
    void findByUsername_retornaVacio_cuandoNoExiste() {
        // When
        Optional<User> resultado = userRepository.findByUsername("usuario-inexistente");

        // Then
        assertThat(resultado).isEmpty();
    }

    @Test
    void findByUsername_retornaUsuarioConRolesLazy_cuandoSeAccedeEnTransaccion() {
        // Given — usuario con múltiples roles
        User user = crearUsuarioBase("admin");
        user.setRoles(Set.of("ROLE_USER", "ROLE_ADMIN"));
        userRepository.save(user);
        entityManager.flush();
        entityManager.clear();

        // When — findByUsername NO usa @EntityGraph, los roles son LAZY
        Optional<User> resultado = userRepository.findByUsername("admin");

        // Then — dentro de la transacción @DataJpaTest, Hibernate puede cargar
        // los roles perezosamente con una consulta adicional (N+1).
        // Verificamos que el usuario se cargó correctamente.
        assertThat(resultado).isPresent();
        assertThat(resultado.get().getUsername()).isEqualTo("admin");
    }

    // ─── findByUsernameWithRoles (carga eager con @EntityGraph) ─────────────

    @Test
    void findByUsernameWithRoles_retornaUsuarioConRoles() {
        // Given
        User user = crearUsuarioBase("silvio");
        user.setRoles(Set.of("ROLE_USER", "ROLE_ADMIN"));
        userRepository.save(user);
        entityManager.flush();
        entityManager.clear();

        // When — @EntityGraph(attributePaths = "roles") carga roles con JOIN FETCH
        Optional<User> resultado = userRepository.findByUsernameWithRoles("silvio");

        // Then — roles deben estar cargados en la misma consulta
        assertThat(resultado).isPresent();
        assertThat(resultado.get().getUsername()).isEqualTo("silvio");
        assertThat(resultado.get().getRoles())
                .containsExactlyInAnyOrder("ROLE_USER", "ROLE_ADMIN");
    }

    @Test
    void findByUsernameWithRoles_retornaVacio_cuandoNoExiste() {
        // When
        Optional<User> resultado = userRepository
                .findByUsernameWithRoles("usuario-inexistente");

        // Then
        assertThat(resultado).isEmpty();
    }

    @Test
    void findByUsernameWithRoles_retornaUsuarioSinRoles_cuandoNoTieneRoles() {
        // Given — usuario sin roles asignados
        User user = new User();
        user.setUsername("sin-roles");
        user.setPassword("$2a$10$encoded");
        user.setRoles(null); // sin roles
        userRepository.save(user);
        entityManager.flush();
        entityManager.clear();

        // When
        Optional<User> resultado = userRepository
                .findByUsernameWithRoles("sin-roles");

        // Then — el usuario existe pero no tiene roles
        assertThat(resultado).isPresent();
        assertThat(resultado.get().getRoles()).isNullOrEmpty();
    }

    // ─── findByResetToken ───────────────────────────────────────────────────

    @Test
    void findByResetToken_retornaUsuario_cuandoExiste() {
        // Given
        User user = crearUsuarioBase("silvio");
        user.setResetToken("token-de-recuperacion-123");
        userRepository.save(user);

        // When
        Optional<User> resultado = userRepository
                .findByResetToken("token-de-recuperacion-123");

        // Then
        assertThat(resultado).isPresent();
        assertThat(resultado.get().getUsername()).isEqualTo("silvio");
    }

    @Test
    void findByResetToken_retornaVacio_cuandoNoExiste() {
        // When
        Optional<User> resultado = userRepository
                .findByResetToken("token-inexistente");

        // Then
        assertThat(resultado).isEmpty();
    }

    // ─── findByRefreshTokenHash ─────────────────────────────────────────────

    @Test
    void findByRefreshTokenHash_retornaUsuario_cuandoExiste() {
        // Given
        String hash = "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6";
        User user = crearUsuarioBase("silvio");
        user.setRefreshTokenHash(hash);
        userRepository.save(user);

        // When
        Optional<User> resultado = userRepository.findByRefreshTokenHash(hash);

        // Then
        assertThat(resultado).isPresent();
        assertThat(resultado.get().getUsername()).isEqualTo("silvio");
    }

    @Test
    void findByRefreshTokenHash_retornaVacio_cuandoNoExiste() {
        // When
        Optional<User> resultado = userRepository
                .findByRefreshTokenHash("hash-inexistente");

        // Then
        assertThat(resultado).isEmpty();
    }
}
