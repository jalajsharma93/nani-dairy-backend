package net.nani.dairy.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface AuthUserAuditRepository extends JpaRepository<AuthUserAuditEntity, String> {
    List<AuthUserAuditEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
