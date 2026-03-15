package net.nani.dairy.notifications;

import lombok.RequiredArgsConstructor;
import net.nani.dairy.auth.AuthUserEntity;
import net.nani.dairy.auth.AuthUserRepository;
import net.nani.dairy.auth.UserRole;
import net.nani.dairy.notifications.dto.CreateNotificationRequest;
import net.nani.dairy.notifications.dto.NotificationCreateResultResponse;
import net.nani.dairy.notifications.dto.NotificationMarkAllReadResponse;
import net.nani.dairy.notifications.dto.NotificationResponse;
import net.nani.dairy.notifications.dto.NotificationUnreadCountResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationEventRepository notificationEventRepository;
    private final AuthUserRepository authUserRepository;
    private final List<NotificationChannelAdapter> adapters;

    public List<NotificationResponse> list(
            String actorUsername,
            UserRole actorRole,
            boolean privilegedActor,
            boolean allRecipients,
            String recipientUsername,
            UserRole recipientRole,
            String eventType,
            NotificationPriority priority,
            NotificationChannel channel,
            NotificationDeliveryStatus deliveryStatus,
            Boolean read,
            Integer limit
    ) {
        int safeLimit = limit == null ? 200 : Math.max(1, Math.min(limit, 500));
        String actor = normalizeRequiredActor(actorUsername);

        List<NotificationEventEntity> rows;
        if (privilegedActor && allRecipients) {
            if (trimToNull(recipientUsername) != null) {
                rows = notificationEventRepository.findByRecipientUsernameIgnoreCaseOrderByCreatedAtDesc(recipientUsername);
            } else if (recipientRole != null) {
                rows = notificationEventRepository.findByRecipientRoleOrderByCreatedAtDesc(recipientRole);
            } else {
                rows = notificationEventRepository.findAllByOrderByCreatedAtDesc();
            }
        } else {
            rows = notificationEventRepository.findByRecipientUsernameIgnoreCaseOrderByCreatedAtDesc(actor);
        }

        return rows.stream()
                .filter(row -> matchesEventType(row, eventType))
                .filter(row -> priority == null || row.getPriority() == priority)
                .filter(row -> channel == null || row.getChannel() == channel)
                .filter(row -> deliveryStatus == null || row.getDeliveryStatus() == deliveryStatus)
                .filter(row -> read == null || (read ? row.getReadAt() != null : row.getReadAt() == null))
                .sorted(Comparator.comparing(NotificationEventEntity::getCreatedAt).reversed())
                .limit(safeLimit)
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public NotificationCreateResultResponse create(
            CreateNotificationRequest req,
            String actorUsername,
            UserRole actorRole,
            boolean privilegedActor
    ) {
        String actor = normalizeRequiredActor(actorUsername);
        UserRole safeRole = actorRole != null ? actorRole : UserRole.WORKER;

        NotificationAudienceType audienceType = req.getAudienceType() != null
                ? req.getAudienceType()
                : NotificationAudienceType.SELF;

        if (!privilegedActor && audienceType != NotificationAudienceType.SELF) {
            throw new AccessDeniedException("Only ADMIN/MANAGER can send targeted/broadcast notifications");
        }

        List<NotificationRecipient> recipients = resolveRecipients(req, audienceType, actor, safeRole, privilegedActor);
        if (recipients.isEmpty()) {
            throw new IllegalArgumentException("No active recipients found for selected audience");
        }

        NotificationPriority priority = req.getPriority() != null ? req.getPriority() : NotificationPriority.MEDIUM;
        NotificationChannel channel = req.getChannel() != null ? req.getChannel() : NotificationChannel.IN_APP;
        int maxRetries = req.getMaxRetries() != null ? req.getMaxRetries() : 5;

        String normalizedEventType = normalizeRequired(req.getEventType(), "eventType is required");
        String normalizedTitle = normalizeRequired(req.getTitle(), "title is required");
        String normalizedMessage = normalizeRequired(req.getMessage(), "message is required");

        List<NotificationResponse> created = new ArrayList<>();
        int sent = 0;
        int failed = 0;

        for (NotificationRecipient recipient : recipients) {
            NotificationEventEntity entity = NotificationEventEntity.builder()
                    .notificationId(buildNotificationId())
                    .eventType(normalizedEventType)
                    .title(normalizedTitle)
                    .message(normalizedMessage)
                    .priority(priority)
                    .channel(channel)
                    .deliveryStatus(NotificationDeliveryStatus.PENDING)
                    .recipientUsername(recipient.username())
                    .recipientRole(recipient.role())
                    .sourceRefId(trimToNull(req.getSourceRefId()))
                    .metadataJson(trimToNull(req.getMetadataJson()))
                    .readAt(null)
                    .deliveredAt(null)
                    .retryCount(0)
                    .maxRetries(maxRetries)
                    .lastError(null)
                    .createdBy(actor)
                    .build();

            applyDispatchResult(entity);
            NotificationEventEntity saved = notificationEventRepository.save(entity);
            if (saved.getDeliveryStatus() == NotificationDeliveryStatus.SENT) {
                sent++;
            } else {
                failed++;
            }
            created.add(toResponse(saved));
        }

        return NotificationCreateResultResponse.builder()
                .audienceType(audienceType)
                .recipientCount(recipients.size())
                .createdCount(created.size())
                .sentCount(sent)
                .failedCount(failed)
                .notifications(created)
                .build();
    }

    @Transactional
    public NotificationResponse markRead(
            String notificationId,
            String actorUsername,
            boolean privilegedActor
    ) {
        NotificationEventEntity entity = notificationEventRepository.findById(notificationId)
                .orElseThrow(() -> new IllegalArgumentException("Notification not found"));
        String actor = normalizeRequiredActor(actorUsername);

        if (!privilegedActor && !actor.equalsIgnoreCase(trimToNull(entity.getRecipientUsername()))) {
            throw new AccessDeniedException("Cannot update another user's notification");
        }

        if (entity.getReadAt() == null) {
            entity.setReadAt(OffsetDateTime.now());
            entity = notificationEventRepository.save(entity);
        }
        return toResponse(entity);
    }
    @Transactional
    public NotificationMarkAllReadResponse markAllRead(
            String actorUsername,
            boolean privilegedActor,
            boolean allRecipients,
            String recipientUsername
    ) {
        String actor = normalizeRequiredActor(actorUsername);
        boolean markAllRecipients = privilegedActor && allRecipients;

        List<NotificationEventEntity> unread;
        String target = null;
        if (markAllRecipients) {
            String explicitRecipient = trimToNull(recipientUsername);
            if (explicitRecipient != null) {
                target = explicitRecipient;
                unread = notificationEventRepository
                        .findByRecipientUsernameIgnoreCaseAndReadAtIsNullOrderByCreatedAtDesc(explicitRecipient);
            } else {
                unread = notificationEventRepository.findByReadAtIsNullOrderByCreatedAtDesc();
            }
        } else {
            target = actor;
            unread = notificationEventRepository
                    .findByRecipientUsernameIgnoreCaseAndReadAtIsNullOrderByCreatedAtDesc(actor);
        }

        OffsetDateTime now = OffsetDateTime.now();
        for (NotificationEventEntity row : unread) {
            row.setReadAt(now);
        }
        if (!unread.isEmpty()) {
            notificationEventRepository.saveAll(unread);
        }

        return NotificationMarkAllReadResponse.builder()
                .allRecipients(markAllRecipients && target == null)
                .recipientUsername(target)
                .markedCount(unread.size())
                .readAt(now)
                .build();
    }

    public NotificationUnreadCountResponse unreadCount(
            String actorUsername,
            UserRole actorRole,
            boolean privilegedActor,
            boolean allRecipients,
            String recipientUsername,
            UserRole recipientRole
    ) {
        String actor = normalizeRequiredActor(actorUsername);
        long count;

        if (privilegedActor && allRecipients) {
            if (trimToNull(recipientUsername) != null) {
                count = notificationEventRepository.countByRecipientUsernameIgnoreCaseAndReadAtIsNull(recipientUsername);
            } else if (recipientRole != null) {
                count = notificationEventRepository.countByRecipientRoleAndReadAtIsNull(recipientRole);
            } else {
                count = notificationEventRepository.countByReadAtIsNull();
            }
        } else {
            count = notificationEventRepository.countByRecipientUsernameIgnoreCaseAndReadAtIsNull(actor);
        }

        return NotificationUnreadCountResponse.builder()
                .allRecipients(privilegedActor && allRecipients)
                .recipientUsername(privilegedActor && allRecipients ? trimToNull(recipientUsername) : actor)
                .recipientRole(privilegedActor && allRecipients ? recipientRole : actorRole)
                .unreadCount(count)
                .build();
    }

    @Transactional
    public NotificationResponse retry(
            String notificationId,
            String actorUsername,
            boolean privilegedActor
    ) {
        NotificationEventEntity entity = notificationEventRepository.findById(notificationId)
                .orElseThrow(() -> new IllegalArgumentException("Notification not found"));
        String actor = normalizeRequiredActor(actorUsername);

        if (!privilegedActor && !actor.equalsIgnoreCase(trimToNull(entity.getRecipientUsername()))) {
            throw new AccessDeniedException("Cannot retry notification for another user");
        }
        if (entity.getDeliveryStatus() == NotificationDeliveryStatus.SENT) {
            return toResponse(entity);
        }
        int retryCount = entity.getRetryCount() != null ? entity.getRetryCount() : 0;
        int maxRetries = entity.getMaxRetries() != null ? entity.getMaxRetries() : 5;
        if (retryCount >= maxRetries) {
            throw new IllegalArgumentException("Max retry limit reached");
        }

        applyDispatchResult(entity);
        NotificationEventEntity saved = notificationEventRepository.save(entity);
        return toResponse(saved);
    }

    private void applyDispatchResult(NotificationEventEntity entity) {
        NotificationChannelAdapter adapter = resolveAdapter(entity.getChannel());
        NotificationDispatchResult dispatchResult = adapter.dispatch(entity);

        if (dispatchResult.isSuccess()) {
            entity.setDeliveryStatus(NotificationDeliveryStatus.SENT);
            entity.setDeliveredAt(dispatchResult.getDeliveredAt() != null ? dispatchResult.getDeliveredAt() : OffsetDateTime.now());
            entity.setLastError(null);
            return;
        }

        int retryCount = entity.getRetryCount() != null ? entity.getRetryCount() : 0;
        entity.setRetryCount(retryCount + 1);
        entity.setDeliveryStatus(NotificationDeliveryStatus.FAILED);
        entity.setDeliveredAt(null);
        entity.setLastError(trimToNull(dispatchResult.getErrorMessage()));
    }

    private NotificationChannelAdapter resolveAdapter(NotificationChannel channel) {
        NotificationChannel effectiveChannel = channel != null ? channel : NotificationChannel.IN_APP;
        for (NotificationChannelAdapter adapter : adapters) {
            if (adapter.channel() == effectiveChannel) {
                return adapter;
            }
        }
        throw new IllegalArgumentException("No channel adapter configured for " + effectiveChannel);
    }

    private List<NotificationRecipient> resolveRecipients(
            CreateNotificationRequest req,
            NotificationAudienceType audienceType,
            String actor,
            UserRole actorRole,
            boolean privilegedActor
    ) {
        if (audienceType == NotificationAudienceType.SELF) {
            return List.of(resolveActorRecipient(actor, actorRole));
        }

        if (!privilegedActor) {
            throw new AccessDeniedException("Only ADMIN/MANAGER can target other users");
        }

        List<AuthUserEntity> recipients;
        if (audienceType == NotificationAudienceType.USERNAME) {
            String recipientUsername = trimToNull(req.getRecipientUsername());
            if (recipientUsername == null) {
                throw new IllegalArgumentException("recipientUsername is required for USERNAME audience");
            }
            AuthUserEntity user = authUserRepository.findByUsernameIgnoreCase(recipientUsername)
                    .filter(AuthUserEntity::isActive)
                    .orElseThrow(() -> new IllegalArgumentException("Active recipient user not found"));
            recipients = List.of(user);
        } else if (audienceType == NotificationAudienceType.ROLE) {
            if (req.getRecipientRole() == null) {
                throw new IllegalArgumentException("recipientRole is required for ROLE audience");
            }
            recipients = authUserRepository.findByActiveTrueAndRoleInOrderByUsernameAsc(List.of(req.getRecipientRole()));
        } else if (audienceType == NotificationAudienceType.ROLES) {
            List<UserRole> roles = req.getRecipientRoles() != null
                    ? req.getRecipientRoles().stream().filter(role -> role != null).toList()
                    : List.of();
            if (roles.isEmpty()) {
                throw new IllegalArgumentException("recipientRoles is required for ROLES audience");
            }
            recipients = authUserRepository.findByActiveTrueAndRoleInOrderByUsernameAsc(roles);
        } else if (audienceType == NotificationAudienceType.ALL_ACTIVE) {
            recipients = authUserRepository.findByActiveTrueOrderByUsernameAsc();
        } else {
            throw new IllegalArgumentException("Unsupported audience type");
        }

        Map<String, NotificationRecipient> unique = new LinkedHashMap<>();
        for (AuthUserEntity user : recipients) {
            String username = trimToNull(user.getUsername());
            if (username == null) {
                continue;
            }
            unique.putIfAbsent(username.toLowerCase(), new NotificationRecipient(username, user.getRole()));
        }
        return new ArrayList<>(unique.values());
    }

    private NotificationRecipient resolveActorRecipient(String actor, UserRole actorRole) {
        return authUserRepository.findByUsernameIgnoreCase(actor)
                .filter(AuthUserEntity::isActive)
                .map(user -> new NotificationRecipient(user.getUsername(), user.getRole()))
                .orElseGet(() -> new NotificationRecipient(actor, actorRole != null ? actorRole : UserRole.WORKER));
    }

    private NotificationResponse toResponse(NotificationEventEntity row) {
        return NotificationResponse.builder()
                .notificationId(row.getNotificationId())
                .eventType(row.getEventType())
                .title(row.getTitle())
                .message(row.getMessage())
                .priority(row.getPriority())
                .channel(row.getChannel())
                .deliveryStatus(row.getDeliveryStatus())
                .recipientUsername(row.getRecipientUsername())
                .recipientRole(row.getRecipientRole())
                .sourceRefId(row.getSourceRefId())
                .metadataJson(row.getMetadataJson())
                .readAt(row.getReadAt())
                .deliveredAt(row.getDeliveredAt())
                .retryCount(row.getRetryCount())
                .maxRetries(row.getMaxRetries())
                .lastError(row.getLastError())
                .createdBy(row.getCreatedBy())
                .createdAt(row.getCreatedAt())
                .updatedAt(row.getUpdatedAt())
                .read(row.getReadAt() != null)
                .build();
    }

    private boolean matchesEventType(NotificationEventEntity row, String eventType) {
        String required = trimToNull(eventType);
        if (required == null) {
            return true;
        }
        String actual = trimToNull(row.getEventType());
        return actual != null && actual.equalsIgnoreCase(required);
    }

    private String buildNotificationId() {
        return "NTF_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    private String normalizeRequiredActor(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new IllegalArgumentException("Unauthorized");
        }
        return normalized;
    }

    private String normalizeRequired(String value, String message) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private record NotificationRecipient(String username, UserRole role) {
    }
}
