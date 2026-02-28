package net.nani.dairy.feed;

import lombok.RequiredArgsConstructor;
import net.nani.dairy.auth.AuthUserEntity;
import net.nani.dairy.auth.AuthUserRepository;
import net.nani.dairy.auth.UserRole;
import net.nani.dairy.feed.dto.*;
import net.nani.dairy.tasks.TaskAutomationService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FeedManagementService {

    private final FeedMaterialRepository feedMaterialRepository;
    private final FeedRecipeRepository feedRecipeRepository;
    private final FeedSopTaskRepository feedSopTaskRepository;
    private final AuthUserRepository authUserRepository;
    private final TaskAutomationService taskAutomationService;

    public FeedManagementSummaryResponse summary(LocalDate date) {
        LocalDate effectiveDate = date != null ? date : LocalDate.now();
        List<FeedMaterialEntity> materials = feedMaterialRepository.findAllByOrderByMaterialNameAsc();
        List<FeedRecipeEntity> recipes = feedRecipeRepository.findAllByOrderByUpdatedAtDesc();
        List<FeedSopTaskEntity> tasks = feedSopTaskRepository.findByTaskDateOrderByPriorityDescCreatedAtAsc(effectiveDate);

        int lowStock = 0;
        double totalInventoryValue = 0;
        for (FeedMaterialEntity material : materials) {
            if (isLowStock(material)) {
                lowStock++;
            }
            if (material.getCostPerUnit() != null && material.getCostPerUnit() > 0) {
                totalInventoryValue += material.getAvailableQty() * material.getCostPerUnit();
            }
        }

        int openTasks = 0;
        int doneTasks = 0;
        for (FeedSopTaskEntity task : tasks) {
            if (task.getStatus() == FeedSopTaskStatus.DONE) {
                doneTasks++;
            } else {
                openTasks++;
            }
        }

        int activeRecipes = 0;
        for (FeedRecipeEntity recipe : recipes) {
            if (recipe.isActive()) {
                activeRecipes++;
            }
        }

        return FeedManagementSummaryResponse.builder()
                .date(effectiveDate)
                .totalMaterials(materials.size())
                .lowStockMaterials(lowStock)
                .activeRecipes(activeRecipes)
                .openTasks(openTasks)
                .doneTasksToday(doneTasks)
                .totalInventoryValue(totalInventoryValue)
                .build();
    }

    public List<FeedMaterialResponse> listMaterials(Boolean lowStockOnly) {
        var rows = feedMaterialRepository.findAllByOrderByMaterialNameAsc();
        if (Boolean.TRUE.equals(lowStockOnly)) {
            return rows.stream().filter(this::isLowStock).map(this::toMaterialResponse).toList();
        }
        return rows.stream().map(this::toMaterialResponse).toList();
    }

    public FeedMaterialResponse createMaterial(CreateFeedMaterialRequest req) {
        String name = normalizeRequired(req.getMaterialName(), "Material name is required");
        feedMaterialRepository.findByMaterialNameIgnoreCase(name)
                .ifPresent(existing -> {
                    throw new IllegalArgumentException("Material with same name already exists");
                });

        FeedMaterialEntity entity = FeedMaterialEntity.builder()
                .feedMaterialId(buildId("FM"))
                .materialName(name)
                .category(req.getCategory())
                .unit(req.getUnit())
                .availableQty(req.getAvailableQty())
                .reorderLevelQty(req.getReorderLevelQty())
                .costPerUnit(req.getCostPerUnit())
                .supplierName(trimToNull(req.getSupplierName()))
                .notes(trimToNull(req.getNotes()))
                .build();
        return toMaterialResponse(feedMaterialRepository.save(entity));
    }

    public FeedMaterialResponse updateMaterial(String materialId, UpdateFeedMaterialRequest req) {
        FeedMaterialEntity entity = feedMaterialRepository.findById(materialId)
                .orElseThrow(() -> new IllegalArgumentException("Material not found"));

        String name = normalizeRequired(req.getMaterialName(), "Material name is required");
        feedMaterialRepository.findByMaterialNameIgnoreCase(name).ifPresent(existing -> {
            if (!existing.getFeedMaterialId().equals(materialId)) {
                throw new IllegalArgumentException("Material with same name already exists");
            }
        });

        entity.setMaterialName(name);
        entity.setCategory(req.getCategory());
        entity.setUnit(req.getUnit());
        entity.setAvailableQty(req.getAvailableQty());
        entity.setReorderLevelQty(req.getReorderLevelQty());
        entity.setCostPerUnit(req.getCostPerUnit());
        entity.setSupplierName(trimToNull(req.getSupplierName()));
        entity.setNotes(trimToNull(req.getNotes()));

        return toMaterialResponse(feedMaterialRepository.save(entity));
    }

    public FeedMaterialResponse adjustMaterialStock(String materialId, AdjustFeedStockRequest req, String actor) {
        FeedMaterialEntity entity = feedMaterialRepository.findById(materialId)
                .orElseThrow(() -> new IllegalArgumentException("Material not found"));

        double delta = req.getQuantityDelta();
        if (delta == 0) {
            throw new IllegalArgumentException("Stock delta cannot be 0");
        }

        double next = entity.getAvailableQty() + delta;
        if (next < 0) {
            throw new IllegalArgumentException("Stock cannot be negative");
        }

        entity.setAvailableQty(next);
        String reason = trimToNull(req.getReason());
        if (reason != null) {
            String audit = String.format(Locale.ENGLISH, "[%s] %s (%+.2f)", actor, reason, delta);
            entity.setNotes(appendNote(entity.getNotes(), audit));
        }

        return toMaterialResponse(feedMaterialRepository.save(entity));
    }

    public List<FeedRecipeResponse> listRecipes(Boolean activeOnly, FeedRationPhase rationPhase) {
        List<FeedRecipeEntity> rows;
        if (rationPhase != null) {
            rows = feedRecipeRepository.findByRationPhaseOrderByUpdatedAtDesc(rationPhase);
        } else if (Boolean.TRUE.equals(activeOnly)) {
            rows = feedRecipeRepository.findByActiveTrueOrderByUpdatedAtDesc();
        } else {
            rows = feedRecipeRepository.findAllByOrderByUpdatedAtDesc();
        }
        if (Boolean.TRUE.equals(activeOnly) && rationPhase != null) {
            rows = rows.stream().filter(FeedRecipeEntity::isActive).toList();
        }
        return rows.stream().map(this::toRecipeResponse).toList();
    }

    public FeedRecipeResponse createRecipe(CreateFeedRecipeRequest req) {
        FeedRecipeEntity entity = FeedRecipeEntity.builder()
                .feedRecipeId(buildId("FR"))
                .recipeName(normalizeRequired(req.getRecipeName(), "Recipe name is required"))
                .rationPhase(req.getRationPhase())
                .targetAnimalCount(req.getTargetAnimalCount())
                .ingredients(normalizeRequired(req.getIngredients(), "Ingredients are required"))
                .instructions(trimToNull(req.getInstructions()))
                .active(req.getActive() == null || req.getActive())
                .build();
        return toRecipeResponse(feedRecipeRepository.save(entity));
    }

    public FeedRecipeResponse updateRecipe(String recipeId, UpdateFeedRecipeRequest req) {
        FeedRecipeEntity entity = feedRecipeRepository.findById(recipeId)
                .orElseThrow(() -> new IllegalArgumentException("Recipe not found"));

        entity.setRecipeName(normalizeRequired(req.getRecipeName(), "Recipe name is required"));
        entity.setRationPhase(req.getRationPhase());
        entity.setTargetAnimalCount(req.getTargetAnimalCount());
        entity.setIngredients(normalizeRequired(req.getIngredients(), "Ingredients are required"));
        entity.setInstructions(trimToNull(req.getInstructions()));
        entity.setActive(req.getActive());
        return toRecipeResponse(feedRecipeRepository.save(entity));
    }

    public List<FeedSopTaskResponse> listTasks(
            LocalDate date,
            FeedSopTaskStatus status,
            UserRole assignedRole,
            String assignedToUsername,
            String actorUsername,
            UserRole actorRole,
            boolean privilegedActor
    ) {
        List<FeedSopTaskEntity> rows = findTasks(date, status, assignedRole);
        String normalizedActor = normalizeActor(actorUsername);
        String normalizedAssignedTo = trimToNull(assignedToUsername);

        if (normalizedAssignedTo != null) {
            if (!privilegedActor && !normalizedAssignedTo.equalsIgnoreCase(normalizedActor)) {
                throw new IllegalArgumentException("Cannot view tasks assigned to another user");
            }
            rows = rows.stream()
                    .filter(row -> normalizedAssignedTo.equalsIgnoreCase(trimToNull(row.getAssignedToUsername())))
                    .toList();
        }

        if (!privilegedActor) {
            UserRole safeActorRole = actorRole != null ? actorRole : UserRole.WORKER;
            rows = rows.stream()
                    .filter(row -> {
                        String assignee = trimToNull(row.getAssignedToUsername());
                        if (assignee != null) {
                            return assignee.equalsIgnoreCase(normalizedActor);
                        }
                        return row.getAssignedRole() == safeActorRole;
                    })
                    .toList();
        }

        return rows.stream().map(this::toTaskResponse).toList();
    }

    public FeedSopTaskResponse createTask(CreateFeedSopTaskRequest req, String actor) {
        UserRole finalAssignedRole = req.getAssignedRole() != null ? req.getAssignedRole() : UserRole.WORKER;
        String normalizedAssignedTo = resolveAssignee(req.getAssignedToUsername(), finalAssignedRole);
        FeedSopTaskEntity entity = FeedSopTaskEntity.builder()
                .feedTaskId(buildId("FT"))
                .taskDate(req.getTaskDate())
                .title(normalizeRequired(req.getTitle(), "Task title is required"))
                .details(trimToNull(req.getDetails()))
                .assignedRole(finalAssignedRole)
                .assignedToUsername(normalizedAssignedTo)
                .assignedByUsername(normalizedAssignedTo != null ? normalizeActor(actor) : null)
                .assignedAt(normalizedAssignedTo != null ? LocalDateTime.now() : null)
                .priority(req.getPriority() != null ? req.getPriority() : FeedSopTaskPriority.MEDIUM)
                .status(FeedSopTaskStatus.PENDING)
                .dueTime(req.getDueTime())
                .build();
        FeedSopTaskEntity saved = feedSopTaskRepository.save(entity);
        taskAutomationService.upsertFromFeedTask(saved);
        return toTaskResponse(saved);
    }

    public FeedSopTaskResponse updateTask(String taskId, UpdateFeedSopTaskRequest req, String actor) {
        FeedSopTaskEntity entity = feedSopTaskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found"));

        String normalizedAssignedTo = resolveAssignee(req.getAssignedToUsername(), req.getAssignedRole());
        String previousAssignee = trimToNull(entity.getAssignedToUsername());

        entity.setTaskDate(req.getTaskDate());
        entity.setTitle(normalizeRequired(req.getTitle(), "Task title is required"));
        entity.setDetails(trimToNull(req.getDetails()));
        entity.setAssignedRole(req.getAssignedRole());
        entity.setAssignedToUsername(normalizedAssignedTo);
        if (!sameUsername(previousAssignee, normalizedAssignedTo)) {
            entity.setAssignedByUsername(normalizedAssignedTo != null ? normalizeActor(actor) : null);
            entity.setAssignedAt(normalizedAssignedTo != null ? LocalDateTime.now() : null);
        }
        entity.setPriority(req.getPriority());
        entity.setStatus(req.getStatus());
        entity.setDueTime(req.getDueTime());
        applyCompletionMetadata(entity, req.getStatus(), actor);
        FeedSopTaskEntity saved = feedSopTaskRepository.save(entity);
        taskAutomationService.upsertFromFeedTask(saved);
        return toTaskResponse(saved);
    }

    public FeedSopTaskResponse updateTaskStatus(
            String taskId,
            UpdateFeedSopTaskStatusRequest req,
            String actor,
            UserRole actorRole,
            boolean privilegedActor
    ) {
        FeedSopTaskEntity entity = feedSopTaskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found"));
        String normalizedActor = normalizeActor(actor);
        String assignedTo = trimToNull(entity.getAssignedToUsername());
        UserRole safeActorRole = actorRole != null ? actorRole : UserRole.WORKER;

        if (!privilegedActor) {
            if (assignedTo != null && !assignedTo.equalsIgnoreCase(normalizedActor)) {
                throw new IllegalArgumentException("Task is assigned to another user");
            }
            if (assignedTo == null) {
                if (entity.getAssignedRole() != safeActorRole) {
                    throw new IllegalArgumentException("Task role does not match your role");
                }
                entity.setAssignedToUsername(normalizedActor);
                entity.setAssignedByUsername("system");
                entity.setAssignedAt(LocalDateTime.now());
            }
        }

        entity.setStatus(req.getStatus());
        applyCompletionMetadata(entity, req.getStatus(), actor);
        FeedSopTaskEntity saved = feedSopTaskRepository.save(entity);
        taskAutomationService.upsertFromFeedTask(saved);
        return toTaskResponse(saved);
    }

    private void applyCompletionMetadata(FeedSopTaskEntity entity, FeedSopTaskStatus status, String actor) {
        if (status == FeedSopTaskStatus.DONE) {
            entity.setCompletedAt(LocalDateTime.now());
            entity.setCompletedBy(actor);
        } else {
            entity.setCompletedAt(null);
            entity.setCompletedBy(null);
        }
    }

    private FeedMaterialResponse toMaterialResponse(FeedMaterialEntity entity) {
        return FeedMaterialResponse.builder()
                .feedMaterialId(entity.getFeedMaterialId())
                .materialName(entity.getMaterialName())
                .category(entity.getCategory())
                .unit(entity.getUnit())
                .availableQty(entity.getAvailableQty())
                .reorderLevelQty(entity.getReorderLevelQty())
                .costPerUnit(entity.getCostPerUnit())
                .supplierName(entity.getSupplierName())
                .notes(entity.getNotes())
                .lowStock(isLowStock(entity))
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private FeedRecipeResponse toRecipeResponse(FeedRecipeEntity entity) {
        return FeedRecipeResponse.builder()
                .feedRecipeId(entity.getFeedRecipeId())
                .recipeName(entity.getRecipeName())
                .rationPhase(entity.getRationPhase())
                .targetAnimalCount(entity.getTargetAnimalCount())
                .ingredients(entity.getIngredients())
                .instructions(entity.getInstructions())
                .active(entity.isActive())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private FeedSopTaskResponse toTaskResponse(FeedSopTaskEntity entity) {
        return FeedSopTaskResponse.builder()
                .feedTaskId(entity.getFeedTaskId())
                .taskDate(entity.getTaskDate())
                .title(entity.getTitle())
                .details(entity.getDetails())
                .assignedRole(entity.getAssignedRole())
                .assignedToUsername(entity.getAssignedToUsername())
                .assignedByUsername(entity.getAssignedByUsername())
                .assignedAt(entity.getAssignedAt())
                .priority(entity.getPriority())
                .status(entity.getStatus())
                .dueTime(entity.getDueTime())
                .completedAt(entity.getCompletedAt())
                .completedBy(entity.getCompletedBy())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private boolean isLowStock(FeedMaterialEntity entity) {
        return entity.getAvailableQty() <= entity.getReorderLevelQty();
    }

    private String buildId(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().substring(0, 8);
    }

    private List<FeedSopTaskEntity> findTasks(LocalDate date, FeedSopTaskStatus status, UserRole assignedRole) {
        if (date != null && status != null && assignedRole != null) {
            return feedSopTaskRepository.findByTaskDateAndStatusAndAssignedRoleOrderByPriorityDescCreatedAtAsc(
                    date,
                    status,
                    assignedRole
            );
        }
        if (date != null && status != null) {
            return feedSopTaskRepository.findByTaskDateAndStatusOrderByPriorityDescCreatedAtAsc(date, status);
        }
        if (date != null && assignedRole != null) {
            return feedSopTaskRepository.findByTaskDateAndAssignedRoleOrderByPriorityDescCreatedAtAsc(date, assignedRole);
        }
        if (date != null) {
            return feedSopTaskRepository.findByTaskDateOrderByPriorityDescCreatedAtAsc(date);
        }
        if (status != null && assignedRole != null) {
            return feedSopTaskRepository.findByStatusAndAssignedRoleOrderByTaskDateAscPriorityDescCreatedAtAsc(status, assignedRole);
        }
        if (status != null) {
            return feedSopTaskRepository.findByStatusOrderByTaskDateAscPriorityDescCreatedAtAsc(status);
        }
        if (assignedRole != null) {
            return feedSopTaskRepository.findByAssignedRoleOrderByTaskDateAscPriorityDescCreatedAtAsc(assignedRole);
        }
        return feedSopTaskRepository.findAllByOrderByTaskDateAscCreatedAtAsc();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizeRequired(String value, String errorMessage) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new IllegalArgumentException(errorMessage);
        }
        return normalized;
    }

    private String resolveAssignee(String assignedToUsername, UserRole assignedRole) {
        String normalizedAssignedTo = trimToNull(assignedToUsername);
        if (normalizedAssignedTo == null) {
            return null;
        }
        AuthUserEntity assignee = authUserRepository.findByUsernameIgnoreCase(normalizedAssignedTo)
                .orElseThrow(() -> new IllegalArgumentException("Assigned user not found"));
        if (!assignee.isActive()) {
            throw new IllegalArgumentException("Assigned user is inactive");
        }
        if (assignedRole != assignee.getRole()) {
            throw new IllegalArgumentException("Assigned user role must match assignedRole");
        }
        return assignee.getUsername();
    }

    private boolean sameUsername(String left, String right) {
        String a = trimToNull(left);
        String b = trimToNull(right);
        if (a == null && b == null) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        return a.equalsIgnoreCase(b);
    }

    private String normalizeActor(String actorUsername) {
        String normalized = trimToNull(actorUsername);
        return normalized == null ? "unknown" : normalized;
    }

    private String appendNote(String current, String line) {
        if (current == null || current.isBlank()) {
            return line;
        }
        if (line == null || line.isBlank()) {
            return current;
        }
        String appended = current + "\n" + line;
        if (appended.length() > 500) {
            return appended.substring(appended.length() - 500);
        }
        return appended;
    }
}
