package net.nani.dairy.tasks;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.nani.dairy.auth.UserRole;
import net.nani.dairy.tasks.dto.CreateTaskTemplateRequest;
import net.nani.dairy.tasks.dto.TaskAutomationRunResponse;
import net.nani.dairy.tasks.dto.TaskTemplateResponse;
import net.nani.dairy.tasks.dto.UpdateTaskTemplateRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class TaskTemplateController {

    private final TaskTemplateService taskTemplateService;

    @GetMapping("/task-templates")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','FEED_MANAGER')")
    public List<TaskTemplateResponse> list(
            @RequestParam(required = false) Boolean activeOnly
    ) {
        return taskTemplateService.list(activeOnly);
    }

    @PostMapping("/task-templates")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','FEED_MANAGER')")
    public TaskTemplateResponse create(
            @Valid @RequestBody CreateTaskTemplateRequest req,
            Authentication authentication
    ) {
        return taskTemplateService.create(req, actor(authentication), actorRole(authentication));
    }

    @PutMapping("/task-templates/{taskTemplateId}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','FEED_MANAGER')")
    public TaskTemplateResponse update(
            @PathVariable String taskTemplateId,
            @Valid @RequestBody UpdateTaskTemplateRequest req,
            Authentication authentication
    ) {
        return taskTemplateService.update(taskTemplateId, req, actor(authentication), actorRole(authentication));
    }

    @PostMapping("/task-automation/run")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','FEED_MANAGER')")
    public TaskAutomationRunResponse run(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false, defaultValue = "false") boolean dryRun,
            Authentication authentication
    ) {
        return taskTemplateService.run(date, dryRun, actor(authentication));
    }

    private String actor(Authentication authentication) {
        return authentication != null ? authentication.getName() : "system-task-automation";
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
}
