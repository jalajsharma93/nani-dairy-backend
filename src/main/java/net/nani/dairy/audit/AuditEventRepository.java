package net.nani.dairy.audit;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditEventRepository extends JpaRepository<AuditEventEntity, String> {
    List<AuditEventEntity> findTop500ByOrderByCreatedAtDesc();

    List<AuditEventEntity> findByModuleIgnoreCaseOrderByCreatedAtDesc(String module);

    List<AuditEventEntity> findByTargetRefIdIgnoreCaseOrderByCreatedAtDesc(String targetRefId);
}
