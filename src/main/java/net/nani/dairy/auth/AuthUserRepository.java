package net.nani.dairy.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AuthUserRepository extends JpaRepository<AuthUserEntity, String> {
    Optional<AuthUserEntity> findByUsernameIgnoreCase(String username);

    boolean existsByUsernameIgnoreCase(String username);

    long countByRoleAndActive(UserRole role, boolean active);

    List<AuthUserEntity> findByActiveTrueOrderByUsernameAsc();

    List<AuthUserEntity> findByActiveTrueAndRoleInOrderByUsernameAsc(Collection<UserRole> roles);
}
