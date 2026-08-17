package com.generationb.foundation;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);
    Optional<User> findByUsername(String username);

    /** Requirement #35: the user-management list, brand-scoped. */
    Page<User> findAllByBrandId(UUID brandId, Pageable pageable);

    /** Guards the "never leave a brand without an admin" rule. */
    @Query("""
        SELECT COUNT(u) FROM User u
        WHERE u.brandId = :brandId
          AND u.role = 'ADMIN'
          AND u.active = true
          AND u.id <> :excludingUserId
        """)
    long countActiveAdmins(@Param("brandId") UUID brandId,
                           @Param("excludingUserId") UUID excludingUserId);

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
