package net.nani.dairy.sales;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.nani.dairy.sales.dto.AddDeliveryTaskAddonRequest;
import net.nani.dairy.sales.dto.DeliveryReconciliationRowResponse;
import net.nani.dairy.sales.dto.CreateDeliveryTaskRequest;
import net.nani.dairy.sales.dto.DeliveryDayPlanTriggerResponse;
import net.nani.dairy.sales.dto.CreateDeliveryRunClosureRequest;
import net.nani.dairy.sales.dto.DeliveryRunClosureResponse;
import net.nani.dairy.sales.dto.DeliveryRouteOptimizationResponse;
import net.nani.dairy.sales.dto.DeliveryTaskResponse;
import net.nani.dairy.sales.dto.SubscriptionGenerationPreviewResponse;
import net.nani.dairy.sales.dto.UpdateDeliveryTaskAssigneeRequest;
import net.nani.dairy.sales.dto.UpdateDeliveryTaskStatusBulkRequest;
import net.nani.dairy.sales.dto.UpdateDeliveryTaskStatusBulkResponse;
import net.nani.dairy.sales.dto.UpdateDeliveryTaskStatusRequest;
import org.springframework.format.annotation.DateTimeFormat;
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

import java.time.LocalDate;
import java.util.List;
import net.nani.dairy.milk.Shift;

@RestController
@RequestMapping("/api/delivery-tasks")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class DeliveryTaskController {

    private final DeliveryTaskService deliveryTaskService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','WORKER','DELIVERY')")
    public List<DeliveryTaskResponse> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            Authentication authentication,
            @RequestParam(required = false) DeliveryTaskStatus status
    ) {
        return deliveryTaskService.list(date, status, actor(authentication), hasAnyRole(authentication, "ADMIN", "MANAGER"));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public DeliveryTaskResponse create(
            @Valid @RequestBody CreateDeliveryTaskRequest req,
            Authentication authentication
    ) {
        return deliveryTaskService.create(req, actor(authentication));
    }

    @PostMapping("/add-on")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','DELIVERY')")
    public DeliveryTaskResponse addAddon(
            @Valid @RequestBody AddDeliveryTaskAddonRequest req,
            Authentication authentication
    ) {
        return deliveryTaskService.addAddon(req, actor(authentication));
    }

    @PostMapping("/{deliveryTaskId}/status")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','WORKER','DELIVERY')")
    public DeliveryTaskResponse updateStatus(
            @PathVariable String deliveryTaskId,
            @Valid @RequestBody UpdateDeliveryTaskStatusRequest req,
            Authentication authentication
    ) {
        return deliveryTaskService.updateStatus(
                deliveryTaskId,
                req,
                actor(authentication),
                hasAnyRole(authentication, "ADMIN", "MANAGER")
        );
    }

    @PostMapping("/bulk-status")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','WORKER','DELIVERY')")
    public UpdateDeliveryTaskStatusBulkResponse bulkUpdateStatus(
            @Valid @RequestBody UpdateDeliveryTaskStatusBulkRequest req,
            Authentication authentication
    ) {
        return deliveryTaskService.bulkUpdateStatus(
                req,
                actor(authentication),
                hasAnyRole(authentication, "ADMIN", "MANAGER")
        );
    }

    @PostMapping("/{deliveryTaskId}/assign")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public DeliveryTaskResponse assign(
            @PathVariable String deliveryTaskId,
            @Valid @RequestBody UpdateDeliveryTaskAssigneeRequest req,
            Authentication authentication
    ) {
        return deliveryTaskService.assignTask(
                deliveryTaskId,
                req,
                actor(authentication)
        );
    }

    @PostMapping("/generate-subscriptions")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public List<DeliveryTaskResponse> generateSubscriptions(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            Authentication authentication
    ) {
        LocalDate effectiveDate = date != null ? date : LocalDate.now();
        deliveryTaskService.generateSubscriptionTasks(effectiveDate, actor(authentication));
        return deliveryTaskService.list(effectiveDate, null, actor(authentication), true);
    }

    @PostMapping("/day-plan")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public DeliveryDayPlanTriggerResponse triggerDayPlan(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "true") boolean autoAssign,
            @RequestParam(defaultValue = "true") boolean optimize,
            Authentication authentication
    ) {
        return deliveryTaskService.triggerDayPlan(
                date != null ? date : LocalDate.now(),
                actor(authentication),
                autoAssign,
                optimize
        );
    }

    @PostMapping("/optimize")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public DeliveryRouteOptimizationResponse optimizeRoutes(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Shift shift,
            @RequestParam(required = false) String routeName,
            Authentication authentication
    ) {
        return deliveryTaskService.optimizeRoutes(
                date != null ? date : LocalDate.now(),
                shift,
                routeName,
                actor(authentication)
        );
    }

    @GetMapping("/generate-subscriptions/preview")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public SubscriptionGenerationPreviewResponse previewGenerateSubscriptions(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return deliveryTaskService.previewSubscriptionGeneration(date != null ? date : LocalDate.now());
    }

    @PostMapping("/run-closures")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','DELIVERY')")
    public DeliveryRunClosureResponse recordRunClosure(
            @Valid @RequestBody CreateDeliveryRunClosureRequest req,
            Authentication authentication
    ) {
        return deliveryTaskService.recordRunClosure(
                req,
                actor(authentication),
                hasAnyRole(authentication, "ADMIN", "MANAGER")
        );
    }

    @GetMapping("/run-closures")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','DELIVERY')")
    public List<DeliveryRunClosureResponse> listRunClosures(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            Authentication authentication
    ) {
        return deliveryTaskService.listRunClosures(
                date != null ? date : LocalDate.now(),
                actor(authentication),
                hasAnyRole(authentication, "ADMIN", "MANAGER")
        );
    }

    @GetMapping("/reconciliation")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','DELIVERY')")
    public List<DeliveryReconciliationRowResponse> reconciliation(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            Authentication authentication
    ) {
        return deliveryTaskService.reconciliation(
                date != null ? date : LocalDate.now(),
                actor(authentication),
                hasAnyRole(authentication, "ADMIN", "MANAGER")
        );
    }

    private String actor(Authentication authentication) {
        return authentication != null ? authentication.getName() : "unknown";
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
