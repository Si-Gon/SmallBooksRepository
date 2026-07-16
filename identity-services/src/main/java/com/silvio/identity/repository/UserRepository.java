package com.silvio.identity.repository;

import com.silvio.identity.model.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);

    // Carga el usuario con sus roles de forma eager solo cuando se invoca este método
    @EntityGraph(attributePaths = "roles")
    @Query("SELECT u FROM User u WHERE u.username = :username")
    Optional<User> findByUsernameWithRoles(@Param("username") String username);

    Optional<User> findByResetTokenHash(String resetTokenHash);
    Optional<User> findByRefreshTokenHash(String refreshTokenHash);
}
