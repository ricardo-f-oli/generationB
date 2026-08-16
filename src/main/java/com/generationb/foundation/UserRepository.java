package com.generationb.foundation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);
    Optional<User> findByUsername(String username);

    default Optional<User> findByIdentifier(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return Optional.empty();
        }
        Optional<User> userByEmail = findByEmail(identifier.trim().toLowerCase());
        if (userByEmail.isPresent()) {
            return userByEmail;
        }
        return findByUsername(identifier.trim());
    }
}
