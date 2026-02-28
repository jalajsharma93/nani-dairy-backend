package net.nani.dairy.tasks;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.nani.dairy.auth.UserRole;
import net.nani.dairy.tasks.dto.CreateTaskItemRequest;
import net.nani.dairy.tasks.dto.TaskItemResponse;
import net.nani.dairy.tasks.dto.UpdateTaskItemRequest;
import net.nani.dairy.tasks.dto.UpdateTaskItemStatusRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class TaskItemController {

    private final TaskItemService taskItemService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','WORKER','FEED_MANAGER','DELIVERY','VET')")
    public List<TaskItemResponse> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) TaskStatus status,
            @RequestParam(required = false) TaskType taskType,
            @RequestParam(required = false) UserRole assignedRole,
            @RequestParam(required = false) String assignedToUsername,
            Authentication authentication
    ) {
        return taskItemService.list(
                date,
                status,
                taskType,
                assignedRole,
                assignedToUsername,
                actor(authentication),
                actorRole(authentication),
                hasAnyRole(authentication, "ADMIN", "MANAGER", "FEED_MANAGER")
        );
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','WORKER','FEED_MANAGER','DELIVERY','VET')")
    public TaskItemResponse create(
            @Valid @RequestBody CreateTaskItemRequest req,
            Authentication authentication
    ) {
        return taskItemService.create(
                req,
                actor(authentication),
                actorRole(authentication),
                hasAnyRole(authentication, "ADMIN", "MANAGER", "FEED_MANAGER")
        );
    }

    @PutMapping("/{taskId}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','FEED_MANAGER')")
    public TaskItemResponse update(
            @PathVariable String taskId,
            @Valid @RequestBody UpdateTaskItemRequest req,
            Authentication authentication
    ) {
        return taskItemService.update(taskId, req, actor(authentication), actorRole(authentication));
    }

    @PostMapping("/{taskId}/status")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','WORKER','FEED_MANAGER','DELIVERY','VET')")
    public TaskItemResponse updateStatus(
            @PathVariable String taskId,
            @Valid @RequestBody UpdateTaskItemStatusRequest req,
            Authentication authentication
    ) {
        return taskItemService.updateStatus(
                taskId,
                req,
                actor(authentication),
                actorRole(authentication),
                hasAnyRole(authentication, "ADMIN", "MANAGER", "FEED_MANAGER")
        );
    }

    private String actor(Authentication authentication) {
        return authentication != null ? authentication.getName() : "unknown";
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
