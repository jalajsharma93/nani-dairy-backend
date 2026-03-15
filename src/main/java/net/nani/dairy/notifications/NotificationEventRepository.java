package net.nani.dairy.notifications;

import net.nani.dairy.auth.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationEventRepository extends JpaRepository<NotificationEventEntity, String> {
    List<NotificationEventEntity> findByRecipientUsernameIgnoreCaseOrderByCreatedAtDesc(String recipientUsername);

    List<NotificationEventEntity> findByRecipientRoleOrderByCreatedAtDesc(UserRole recipientRole);

    List<NotificationEventEntity> findAllByOrderByCreatedAtDesc();

    List<NotificationEventEntity> findByReadAtIsNullOrderByCreatedAtDesc();

    List<NotificationEventEntity> findByRecipientUsernameIgnoreCaseAndReadAtIsNullOrderByCreatedAtDesc(String recipientUsername);

    long countByRecipientUsernameIgnoreCaseAndReadAtIsNull(String recipientUsername);

    long countByRecipientRoleAndReadAtIsNull(UserRole recipientRole);

    long countByReadAtIsNull();
}
