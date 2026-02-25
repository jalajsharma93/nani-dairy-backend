package net.nani.dairy.feed;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.nani.dairy.auth.UserRole;
import net.nani.dairy.feed.dto.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/feed-management")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class FeedManagementController {

    private final FeedManagementService feedManagementService;

    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','WORKER','FEED_MANAGER')")
    public FeedManagementSummaryResponse summary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return feedManagementService.summary(date);
    }

    @GetMapping("/materials")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','WORKER','FEED_MANAGER')")
    public List<FeedMaterialResponse> listMaterials(@RequestParam(required = false) Boolean lowStockOnly) {
        return feedManagementService.listMaterials(lowStockOnly);
    }

    @PostMapping("/materials")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','FEED_MANAGER')")
    public FeedMaterialResponse createMaterial(@Valid @RequestBody CreateFeedMaterialRequest req) {
        return feedManagementService.createMaterial(req);
    }

    @PutMapping("/materials/{materialId}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','FEED_MANAGER')")
    public FeedMaterialResponse updateMaterial(
            @PathVariable String materialId,
            @Valid @RequestBody UpdateFeedMaterialRequest req
    ) {
        return feedManagementService.updateMaterial(materialId, req);
    }

    @PostMapping("/materials/{materialId}/adjust")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','FEED_MANAGER')")
    public FeedMaterialResponse adjustMaterialStock(
            @PathVariable String materialId,
            @Valid @RequestBody AdjustFeedStockRequest req,
            Authentication authentication
    ) {
        return feedManagementService.adjustMaterialStock(materialId, req, actor(authentication));
    }

    @GetMapping("/recipes")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','WORKER','FEED_MANAGER')")
    public List<FeedRecipeResponse> listRecipes(
            @RequestParam(required = false) Boolean activeOnly,
            @RequestParam(required = false) FeedRationPhase rationPhase
    ) {
        return feedManagementService.listRecipes(activeOnly, rationPhase);
    }

    @PostMapping("/recipes")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','FEED_MANAGER')")
    public FeedRecipeResponse createRecipe(@Valid @RequestBody CreateFeedRecipeRequest req) {
        return feedManagementService.createRecipe(req);
    }

    @PutMapping("/recipes/{recipeId}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','FEED_MANAGER')")
    public FeedRecipeResponse updateRecipe(
            @PathVariable String recipeId,
            @Valid @RequestBody UpdateFeedRecipeRequest req
    ) {
        return feedManagementService.updateRecipe(recipeId, req);
    }

    @GetMapping("/tasks")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','WORKER','FEED_MANAGER')")
    public List<FeedSopTaskResponse> listTasks(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) FeedSopTaskStatus status,
            @RequestParam(required = false) UserRole assignedRole
    ) {
        return feedManagementService.listTasks(date, status, assignedRole);
    }

    @PostMapping("/tasks")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','FEED_MANAGER')")
    public FeedSopTaskResponse createTask(@Valid @RequestBody CreateFeedSopTaskRequest req) {
        return feedManagementService.createTask(req);
    }

    @PutMapping("/tasks/{taskId}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','FEED_MANAGER')")
    public FeedSopTaskResponse updateTask(
            @PathVariable String taskId,
            @Valid @RequestBody UpdateFeedSopTaskRequest req,
            Authentication authentication
    ) {
        return feedManagementService.updateTask(taskId, req, actor(authentication));
    }

    @PostMapping("/tasks/{taskId}/status")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','WORKER','FEED_MANAGER')")
    public FeedSopTaskResponse updateTaskStatus(
            @PathVariable String taskId,
            @Valid @RequestBody UpdateFeedSopTaskStatusRequest req,
            Authentication authentication
    ) {
        return feedManagementService.updateTaskStatus(taskId, req, actor(authentication));
    }

    private String actor(Authentication authentication) {
        return authentication != null ? authentication.getName() : "unknown";
    }
}
