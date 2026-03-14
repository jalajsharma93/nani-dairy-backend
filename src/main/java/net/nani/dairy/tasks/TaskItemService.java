package net.nani.dairy.tasks;

import lombok.RequiredArgsConstructor;
import net.nani.dairy.auth.AuthUserEntity;
import net.nani.dairy.auth.AuthUserRepository;
import net.nani.dairy.auth.UserRole;
import net.nani.dairy.feed.FeedManagementService;
import net.nani.dairy.feed.FeedSopTaskPriority;
import net.nani.dairy.feed.FeedSopTaskStatus;
import net.nani.dairy.feed.dto.UpdateFeedSopTaskRequest;
import net.nani.dairy.feed.dto.UpdateFeedSopTaskStatusRequest;
import net.nani.dairy.sales.DeliveryTaskStatus;
import net.nani.dairy.sales.DeliveryTaskService;
import net.nani.dairy.sales.dto.UpdateDeliveryTaskStatusRequest;
import net.nani.dairy.tasks.dto.CreateTaskItemRequest;
import net.nani.dairy.tasks.dto.TaskItemResponse;
import net.nani.dairy.tasks.dto.UpdateTaskItemRequest;
import net.nani.dairy.tasks.dto.UpdateTaskItemStatusRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TaskItemService {

    private final TaskItemRepository taskItemRepository;
    private final AuthUserRepository authUserRepository;
    private final DeliveryTaskService deliveryTaskService;
    private final FeedManagementService feedManagementService;

    public List<TaskItemResponse> list(
            LocalDate date,
            TaskStatus status,
            TaskType taskType,
            UserRole assignedRole,
            String assignedToUsername,
            String actorUsername,
            UserRole actorRole,
            boolean privilegedActor
    ) {
        List<TaskItemEntity> rows = date != null
                ? taskItemRepository.findByTaskDateOrderByPriorityDescCreatedAtAsc(date)
                : taskItemRepository.findAllByOrderByTaskDateAscPriorityDescCreatedAtAsc();

        if (status != null) {
            rows = rows.stream().filter(row -> row.getStatus() == status).toList();
        }
        if (taskType != null) {
            rows = rows.stream().filter(row -> row.getTaskType() == taskType).toList();
        }
        if (assignedRole != null) {
            rows = rows.stream().filter(row -> row.getAssignedRole() == assignedRole).toList();
        }

        String normalizedActor = normalizeActor(actorUsername);
        String normalizedAssignedTo = trimToNull(assignedToUsername);
        UserRole safeActorRole = actorRole != null ? actorRole : UserRole.WORKER;
        boolean adminActor = isAdmin(safeActorRole);
        boolean managerActor = isManager(safeActorRole);

        if (normalizedAssignedTo != null) {
            if (!adminActor && !normalizedAssignedTo.equalsIgnoreCase(normalizedActor)) {
                if (!managerActor || !isTeamUser(normalizedAssignedTo)) {
                    throw new IllegalArgumentException("Cannot view tasks assigned to another user");
                }
            }
            rows = rows.stream()
                    .filter(row -> normalizedAssignedTo.equalsIgnoreCase(trimToNull(row.getAssignedToUsername())))
                    .toList();
        }

        if (!adminActor && !managerActor) {
            rows = rows.stream()
                    .filter(row -> {
                        String assignee = trimToNull(row.getAssignedToUsername());
                        if (assignee != null) {
                            return assignee.equalsIgnoreCase(normalizedActor);
                        }
                        return row.getAssignedRole() == safeActorRole;
                    })
                    .toList();
        } else if (managerActor) {
            rows = rows.stream()
                    .filter(row -> canManagerSeeTask(row, normalizedActor))
                    .toList();
        }

        return rows.stream()
                .sorted(
                        Comparator.comparing(TaskItemEntity::getTaskDate)
                                .thenComparing((TaskItemEntity row) -> row.getDueTime() == null ? "" : row.getDueTime().toString())
                                .thenComparing(TaskItemEntity::getCreatedAt)
                )
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public TaskItemResponse create(
            CreateTaskItemRequest req,
            String actorUsername,
            UserRole actorRole,
            boolean privilegedActor
    ) {
        UserRole safeActorRole = actorRole != null ? actorRole : UserRole.WORKER;
        UserRole finalAssignedRole = req.getAssignedRole() != null ? req.getAssignedRole() : safeActorRole;
        String normalizedActor = normalizeActor(actorUsername);
        String normalizedAssignedTo = resolveAssignee(req.getAssignedToUsername(), finalAssignedRole);
        boolean adminActor = isAdmin(safeActorRole);
        boolean managerActor = isManager(safeActorRole);

        if (!adminActor && !managerActor) {
            if (finalAssignedRole != safeActorRole) {
                throw new IllegalArgumentException("You can create tasks only for your role");
            }
            if (normalizedAssignedTo != null && !normalizedAssignedTo.equalsIgnoreCase(normalizedActor)) {
                throw new IllegalArgumentException("You can assign tasks only to yourself");
            }
            if (normalizedAssignedTo == null) {
                normalizedAssignedTo = normalizedActor;
            }
        } else if (managerActor) {
            if (!(finalAssignedRole == UserRole.MANAGER || isTeamRole(finalAssignedRole))) {
                throw new IllegalArgumentException("Manager can create MANAGER or worker-team tasks only");
            }
            if (finalAssignedRole == UserRole.MANAGER) {
                if (normalizedAssignedTo != null && !normalizedAssignedTo.equalsIgnoreCase(normalizedActor)) {
                    throw new IllegalArgumentException("Manager tasks can be assigned only to self");
                }
                if (normalizedAssignedTo == null) {
                    normalizedAssignedTo = normalizedActor;
                }
            }
        }

        TaskItemEntity entity = TaskItemEntity.builder()
                .taskId(buildId())
                .taskDate(req.getTaskDate() != null ? req.getTaskDate() : LocalDate.now())
                .taskType(req.getTaskType() != null ? req.getTaskType() : TaskType.OTHER)
                .title(normalizeRequired(req.getTitle(), "Task title is required"))
                .details(trimToNull(req.getDetails()))
                .assignedRole(finalAssignedRole)
                .assignedToUsername(normalizedAssignedTo)
                .assignedByUsername(normalizedAssignedTo != null ? normalizedActor : null)
                .assignedAt(normalizedAssignedTo != null ? LocalDateTime.now() : null)
                .priority(req.getPriority() != null ? req.getPriority() : TaskPriority.MEDIUM)
                .status(TaskStatus.PENDING)
                .dueTime(req.getDueTime())
                .sourceRefId(trimToNull(req.getSourceRefId()))
                .completedAt(null)
                .completedBy(null)
                .build();

        return toResponse(taskItemRepository.save(entity));
    }

    @Transactional
    public TaskItemResponse update(
            String taskId,
            UpdateTaskItemRequest req,
            String actorUsername,
            UserRole actorRole
    ) {
        TaskItemEntity entity = taskItemRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found"));

        String normalizedAssignedTo = resolveAssignee(req.getAssignedToUsername(), req.getAssignedRole());
        String previousAssignedTo = trimToNull(entity.getAssignedToUsername());
        String normalizedActor = normalizeActor(actorUsername);
        UserRole safeActorRole = actorRole != null ? actorRole : UserRole.WORKER;
        boolean adminActor = isAdmin(safeActorRole);
        boolean managerActor = isManager(safeActorRole);

        if (!adminActor && !managerActor) {
            if (req.getAssignedRole() != safeActorRole) {
                throw new IllegalArgumentException("You can update tasks only for your role");
            }
            if (normalizedAssignedTo != null && !normalizedAssignedTo.equalsIgnoreCase(normalizedActor)) {
                throw new IllegalArgumentException("You can assign tasks only to yourself");
            }
            if (normalizedAssignedTo == null) {
                normalizedAssignedTo = normalizedActor;
            }
        } else if (managerActor) {
            if (!(req.getAssignedRole() == UserRole.MANAGER || isTeamRole(req.getAssignedRole()))) {
                throw new IllegalArgumentException("Manager can update MANAGER or worker-team tasks only");
            }
            if (req.getAssignedRole() == UserRole.MANAGER) {
                if (normalizedAssignedTo != null && !normalizedAssignedTo.equalsIgnoreCase(normalizedActor)) {
                    throw new IllegalArgumentException("Manager tasks can be assigned only to self");
                }
                if (normalizedAssignedTo == null) {
                    normalizedAssignedTo = normalizedActor;
                }
            }
        }

        String deliveryTaskId = extractDeliveryTaskId(entity.getSourceRefId(), entity.getTaskType());
        if (deliveryTaskId != null) {
            throw new AccessDeniedException("Delivery-linked tasks are managed from Delivery module. Use status update endpoint only.");
        }

        String feedTaskId = extractFeedTaskId(entity.getSourceRefId(), entity.getTaskType());
        if (feedTaskId != null) {
            if (req.getTaskType() != TaskType.FEED) {
                throw new IllegalArgumentException("Linked feed task type cannot be changed");
            }
            String requestedSourceRefId = trimToNull(req.getSourceRefId());
            String currentSourceRefId = trimToNull(entity.getSourceRefId());
            if (requestedSourceRefId != null
                    && currentSourceRefId != null
                    && !requestedSourceRefId.equalsIgnoreCase(currentSourceRefId)) {
                throw new IllegalArgumentException("Linked feed task sourceRefId cannot be changed");
            }

            feedManagementService.updateTask(
                    feedTaskId,
                    UpdateFeedSopTaskRequest.builder()
                            .taskDate(req.getTaskDate())
                            .title(req.getTitle())
                            .details(req.getDetails())
                            .assignedRole(req.getAssignedRole())
                            .assignedToUsername(req.getAssignedToUsername())
                            .priority(mapToFeedPriority(req.getPriority()))
                            .status(mapToFeedStatus(req.getStatus()))
                            .dueTime(req.getDueTime())
                            .build(),
                    actorUsername
            );
            return taskItemRepository.findBySourceRefId(entity.getSourceRefId())
                    .map(this::toResponse)
                    .orElseThrow(() -> new IllegalArgumentException("Task sync failed for feed task"));
        }

        entity.setTaskDate(req.getTaskDate());
        entity.setTaskType(req.getTaskType());
        entity.setTitle(normalizeRequired(req.getTitle(), "Task title is required"));
        entity.setDetails(trimToNull(req.getDetails()));
        entity.setAssignedRole(req.getAssignedRole());
        entity.setAssignedToUsername(normalizedAssignedTo);
        if (!sameUsername(previousAssignedTo, normalizedAssignedTo)) {
            entity.setAssignedByUsername(normalizedAssignedTo != null ? normalizedActor : null);
            entity.setAssignedAt(normalizedAssignedTo != null ? LocalDateTime.now() : null);
        }
        entity.setPriority(req.getPriority());
        entity.setStatus(req.getStatus());
        entity.setDueTime(req.getDueTime());
        entity.setSourceRefId(trimToNull(req.getSourceRefId()));
        applyCompletionMetadata(entity, req.getStatus(), actorUsername);

        return toResponse(taskItemRepository.save(entity));
    }

    @Transactional
    public TaskItemResponse updateStatus(
            String taskId,
            UpdateTaskItemStatusRequest req,
            String actorUsername,
            UserRole actorRole,
            boolean privilegedActor
    ) {
        TaskItemEntity entity = taskItemRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found"));
        String normalizedActor = normalizeActor(actorUsername);
        UserRole safeActorRole = actorRole != null ? actorRole : UserRole.WORKER;
        String assignedTo = trimToNull(entity.getAssignedToUsername());
        boolean adminActor = isAdmin(safeActorRole);
        boolean managerActor = isManager(safeActorRole);
        String feedTaskId = extractFeedTaskId(entity.getSourceRefId(), entity.getTaskType());
        boolean feedManagerOnFeedTask = safeActorRole == UserRole.FEED_MANAGER && feedTaskId != null;

        if (!adminActor && !managerActor && !feedManagerOnFeedTask) {
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
        } else if (managerActor) {
            if (!canManagerSeeTask(entity, normalizedActor)) {
                throw new IllegalArgumentException("Manager can update only own and worker-team tasks");
            }
            if (assignedTo == null && entity.getAssignedRole() == UserRole.MANAGER) {
                entity.setAssignedToUsername(normalizedActor);
                entity.setAssignedByUsername("system");
                entity.setAssignedAt(LocalDateTime.now());
            }
        }

        String deliveryTaskId = extractDeliveryTaskId(entity.getSourceRefId(), entity.getTaskType());
        if (deliveryTaskId != null) {
            deliveryTaskService.updateStatus(
                    deliveryTaskId,
                    UpdateDeliveryTaskStatusRequest.builder()
                            .status(mapToDeliveryStatus(req.getStatus()))
                            .build(),
                    actorUsername,
                    adminActor || managerActor
            );
            return taskItemRepository.findBySourceRefId(entity.getSourceRefId())
                    .map(this::toResponse)
                    .orElseThrow(() -> new IllegalArgumentException("Task sync failed for delivery task"));
        }
        if (feedTaskId != null) {
            feedManagementService.updateTaskStatus(
                    feedTaskId,
                    UpdateFeedSopTaskStatusRequest.builder()
                            .status(mapToFeedStatus(req.getStatus()))
                            .build(),
                    actorUsername,
                    safeActorRole,
                    adminActor || managerActor || safeActorRole == UserRole.FEED_MANAGER
            );
            return taskItemRepository.findBySourceRefId(entity.getSourceRefId())
                    .map(this::toResponse)
                    .orElseThrow(() -> new IllegalArgumentException("Task sync failed for feed task"));
        }

        entity.setStatus(req.getStatus());
        applyCompletionMetadata(entity, req.getStatus(), actorUsername);
        return toResponse(taskItemRepository.save(entity));
    }

    private void applyCompletionMetadata(TaskItemEntity entity, TaskStatus status, String actor) {
        if (status == TaskStatus.DONE) {
            entity.setCompletedAt(LocalDateTime.now());
            entity.setCompletedBy(normalizeActor(actor));
        } else {
            entity.setCompletedAt(null);
            entity.setCompletedBy(null);
        }
    }

    private TaskItemResponse toResponse(TaskItemEntity row) {
        return TaskItemResponse.builder()
                .taskId(row.getTaskId())
                .taskDate(row.getTaskDate())
                .taskType(row.getTaskType())
                .title(row.getTitle())
                .details(row.getDetails())
                .assignedRole(row.getAssignedRole())
                .assignedToUsername(row.getAssignedToUsername())
                .assignedByUsername(row.getAssignedByUsername())
                .assignedAt(row.getAssignedAt())
                .priority(row.getPriority())
                .status(row.getStatus())
                .dueTime(row.getDueTime())
                .sourceRefId(row.getSourceRefId())
                .completedAt(row.getCompletedAt())
                .completedBy(row.getCompletedBy())
                .reminderSentAt(row.getReminderSentAt())
                .escalatedAt(row.getEscalatedAt())
                .escalationCount(row.getEscalationCount())
                .createdAt(row.getCreatedAt())
                .updatedAt(row.getUpdatedAt())
                .build();
    }

    private String resolveAssignee(String assignedToUsername, UserRole assignedRole) {
        String normalized = trimToNull(assignedToUsername);
        if (normalized == null) {
            return null;
        }
        AuthUserEntity assignee = authUserRepository.findByUsernameIgnoreCase(normalized)
                .orElseThrow(() -> new IllegalArgumentException("Assigned user not found"));
        if (!assignee.isActive()) {
            throw new IllegalArgumentException("Assigned user is inactive");
        }
        if (assignedRole != assignee.getRole()) {
            throw new IllegalArgumentException("Assigned user role must match assignedRole");
        }
        return assignee.getUsername();
    }

    private String normalizeRequired(String value, String errorMessage) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new IllegalArgumentException(errorMessage);
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

    private boolean isAdmin(UserRole role) {
        return role == UserRole.ADMIN;
    }

    private boolean isManager(UserRole role) {
        return role == UserRole.MANAGER;
    }

    private boolean isTeamRole(UserRole role) {
        return role == UserRole.WORKER
                || role == UserRole.DELIVERY
                || role == UserRole.FEED_MANAGER
                || role == UserRole.VET;
    }

    private boolean isTeamUser(String username) {
        AuthUserEntity user = authUserRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new IllegalArgumentException("Assigned user not found"));
        return user.isActive() && isTeamRole(user.getRole());
    }

    private boolean canManagerSeeTask(TaskItemEntity row, String managerUsername) {
        String assignee = trimToNull(row.getAssignedToUsername());
        if (assignee != null && assignee.equalsIgnoreCase(managerUsername)) {
            return true;
        }
        if (row.getAssignedRole() == UserRole.MANAGER) {
            return assignee == null;
        }
        return isTeamRole(row.getAssignedRole());
    }

    private String extractDeliveryTaskId(String sourceRefId, TaskType taskType) {
        if (taskType != TaskType.DELIVERY) {
            return null;
        }
        String normalized = trimToNull(sourceRefId);
        if (normalized == null || !normalized.startsWith("DELIVERY_TASK:")) {
            return null;
        }
        String id = normalized.substring("DELIVERY_TASK:".length()).trim();
        return id.isEmpty() ? null : id;
    }

    private String extractFeedTaskId(String sourceRefId, TaskType taskType) {
        if (taskType != TaskType.FEED) {
            return null;
        }
        String normalized = trimToNull(sourceRefId);
        if (normalized == null || !normalized.startsWith("FEED_TASK:")) {
            return null;
        }
        String id = normalized.substring("FEED_TASK:".length()).trim();
        return id.isEmpty() ? null : id;
    }

    private DeliveryTaskStatus mapToDeliveryStatus(TaskStatus status) {
        if (status == null) {
            return DeliveryTaskStatus.PENDING;
        }
        return switch (status) {
            case DONE -> DeliveryTaskStatus.DELIVERED;
            case SKIPPED -> DeliveryTaskStatus.SKIPPED;
            case PENDING -> DeliveryTaskStatus.PENDING;
            case IN_PROGRESS -> throw new IllegalArgumentException("Delivery tasks support PENDING, DONE or SKIPPED only");
        };
    }

    private FeedSopTaskStatus mapToFeedStatus(TaskStatus status) {
        if (status == null) {
            return FeedSopTaskStatus.PENDING;
        }
        return switch (status) {
            case PENDING -> FeedSopTaskStatus.PENDING;
            case IN_PROGRESS -> FeedSopTaskStatus.IN_PROGRESS;
            case DONE -> FeedSopTaskStatus.DONE;
            case SKIPPED -> throw new IllegalArgumentException("Feed tasks support PENDING, IN_PROGRESS or DONE only");
        };
    }

    private FeedSopTaskPriority mapToFeedPriority(TaskPriority priority) {
        if (priority == null) {
            return FeedSopTaskPriority.MEDIUM;
        }
        return switch (priority) {
            case LOW -> FeedSopTaskPriority.LOW;
            case MEDIUM -> FeedSopTaskPriority.MEDIUM;
            case HIGH -> FeedSopTaskPriority.HIGH;
        };
    }

    private String buildId() {
        return "TSK_" + UUID.randomUUID().toString().substring(0, 8);
    }
}
