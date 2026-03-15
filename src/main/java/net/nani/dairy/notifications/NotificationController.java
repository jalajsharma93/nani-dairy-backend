package net.nani.dairy.notifications;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.nani.dairy.auth.UserRole;
import net.nani.dairy.notifications.dto.CreateNotificationRequest;
import net.nani.dairy.notifications.dto.NotificationCreateResultResponse;
import net.nani.dairy.notifications.dto.NotificationMarkAllReadResponse;
import net.nani.dairy.notifications.dto.NotificationResponse;
import net.nani.dairy.notifications.dto.NotificationUnreadCountResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','WORKER','FEED_MANAGER','DELIVERY','VET')")
    public List<NotificationResponse> list(
            @RequestParam(required = false, defaultValue = "false") boolean allRecipients,
            @RequestParam(required = false) String recipientUsername,
            @RequestParam(required = false) UserRole recipientRole,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) NotificationPriority priority,
            @RequestParam(required = false) NotificationChannel channel,
            @RequestParam(required = false) NotificationDeliveryStatus deliveryStatus,
            @RequestParam(required = false) Boolean read,
            @RequestParam(required = false) Integer limit,
            Authentication authentication
    ) {
        return notificationService.list(
                actor(authentication),
                actorRole(authentication),
                hasAnyRole(authentication, "ADMIN", "MANAGER"),
                allRecipients,
                recipientUsername,
                recipientRole,
                eventType,
                priority,
                channel,
                deliveryStatus,
                read,
                limit
        );
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','WORKER','FEED_MANAGER','DELIVERY','VET')")
    public NotificationCreateResultResponse create(
            @Valid @RequestBody CreateNotificationRequest req,
            Authentication authentication
    ) {
        return notificationService.create(
                req,
                actor(authentication),
                actorRole(authentication),
                hasAnyRole(authentication, "ADMIN", "MANAGER")
        );
    }

    @PostMapping("/{notificationId}/read")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','WORKER','FEED_MANAGER','DELIVERY','VET')")
    public NotificationResponse markRead(
            @PathVariable String notificationId,
            Authentication authentication
    ) {
        return notificationService.markRead(
                notificationId,
                actor(authentication),
                hasAnyRole(authentication, "ADMIN", "MANAGER")
        );
    }

    @PostMapping("/read-all")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','WORKER','FEED_MANAGER','DELIVERY','VET')")
    public NotificationMarkAllReadResponse markAllRead(
            @RequestParam(required = false, defaultValue = "false") boolean allRecipients,
            @RequestParam(required = false) String recipientUsername,
            Authentication authentication
    ) {
        return notificationService.markAllRead(
                actor(authentication),
                hasAnyRole(authentication, "ADMIN", "MANAGER"),
                allRecipients,
                recipientUsername
        );
    }

    @GetMapping("/unread-count")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','WORKER','FEED_MANAGER','DELIVERY','VET')")
    public NotificationUnreadCountResponse unreadCount(
            @RequestParam(required = false, defaultValue = "false") boolean allRecipients,
            @RequestParam(required = false) String recipientUsername,
            @RequestParam(required = false) UserRole recipientRole,
            Authentication authentication
    ) {
        return notificationService.unreadCount(
                actor(authentication),
                actorRole(authentication),
                hasAnyRole(authentication, "ADMIN", "MANAGER"),
                allRecipients,
                recipientUsername,
                recipientRole
        );
    }

    @PostMapping("/{notificationId}/retry")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','WORKER','FEED_MANAGER','DELIVERY','VET')")
    public NotificationResponse retry(
            @PathVariable String notificationId,
            Authentication authentication
    ) {
        return notificationService.retry(
                notificationId,
                actor(authentication),
                hasAnyRole(authentication, "ADMIN", "MANAGER")
        );
    }

    private String actor(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new IllegalArgumentException("Unauthorized");
        }
        return authentication.getName();
    }

    private UserRole actorRole(Authentication authentication) {
        if (authentication == null || authentication.getAuthorities() == null) {
            return null;
        }
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            String value = authority == null ? "" : authority.getAuthority();
            if (value.startsWith("ROLE_")) {
                try {
                    return UserRole.valueOf(value.substring("ROLE_".length()).toUpperCase());
                } catch (IllegalArgumentException ignored) {
                    // no-op
                }
            }
        }
        return null;
    }

    private boolean hasAnyRole(Authentication authentication, String... roles) {
        if (authentication == null || authentication.getAuthorities() == null) {
            return false;
        }
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            String value = authority == null ? "" : authority.getAuthority();
            for (String role : roles) {
                if (("ROLE_" + role).equalsIgnoreCase(value)) {
                    return true;
                }
            }
        }
        return false;
    }
}
