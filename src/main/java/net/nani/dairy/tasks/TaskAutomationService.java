package net.nani.dairy.tasks;

import lombok.RequiredArgsConstructor;
import net.nani.dairy.auth.AuthUserRepository;
import net.nani.dairy.auth.UserRole;
import net.nani.dairy.feed.FeedSopTaskEntity;
import net.nani.dairy.feed.FeedSopTaskPriority;
import net.nani.dairy.feed.FeedSopTaskStatus;
import net.nani.dairy.milk.Shift;
import net.nani.dairy.sales.DeliveryTaskEntity;
import net.nani.dairy.sales.DeliveryTaskStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TaskAutomationService {

    private final TaskItemRepository taskItemRepository;
    private final AuthUserRepository authUserRepository;

    @Transactional
    public void upsertFromDeliveryTask(DeliveryTaskEntity deliveryTask) {
        if (deliveryTask == null || deliveryTask.getDeliveryTaskId() == null || deliveryTask.getDeliveryTaskId().isBlank()) {
            return;
        }
        String sourceRefId = "DELIVERY_TASK:" + deliveryTask.getDeliveryTaskId();
        TaskItemEntity entity = taskItemRepository.findBySourceRefId(sourceRefId)
                .orElseGet(() -> TaskItemEntity.builder().taskId(buildId()).build());

        String assignedTo = trimToNull(deliveryTask.getAssignedToUsername());
        UserRole assignedRole = resolveAssignedRole(assignedTo);
        TaskStatus mappedStatus = mapStatus(deliveryTask.getStatus());

        entity.setTaskDate(deliveryTask.getTaskDate() != null ? deliveryTask.getTaskDate() : LocalDate.now());
        entity.setTaskType(TaskType.DELIVERY);
        entity.setTitle(buildDeliveryTitle(deliveryTask));
        entity.setDetails(buildDetails(deliveryTask));
        entity.setAssignedRole(assignedRole);
        entity.setAssignedToUsername(assignedTo);
        entity.setAssignedByUsername(trimToNull(deliveryTask.getAssignedByUsername()));
        entity.setAssignedAt(deliveryTask.getAssignedAt() != null ? deliveryTask.getAssignedAt().toLocalDateTime() : null);
        entity.setPriority(resolvePriority(deliveryTask));
        entity.setStatus(mappedStatus);
        entity.setDueTime(resolveDeliveryDueTime(deliveryTask.getTaskShift()));
        entity.setSourceRefId(sourceRefId);
        if (mappedStatus == TaskStatus.DONE) {
            entity.setCompletedAt(deliveryTask.getCompletedAt() != null ? deliveryTask.getCompletedAt().toLocalDateTime() : null);
            entity.setCompletedBy(trimToNull(deliveryTask.getCompletedBy()));
        } else {
            entity.setCompletedAt(null);
            entity.setCompletedBy(null);
        }

        taskItemRepository.save(entity);
    }

    @Transactional
    public void upsertStockPipelineTask(
            LocalDate taskDate,
            String pipelineCode,
            String title,
            String details,
            UserRole assignedRole,
            TaskPriority priority,
            LocalTime dueTime
    ) {
        if (taskDate == null) {
            return;
        }
        String normalizedCode = trimToNull(pipelineCode);
        if (normalizedCode == null) {
            return;
        }
        String sourceRefId = "STOCK_PIPELINE:" + normalizedCode + ":" + taskDate;
        TaskItemEntity entity = taskItemRepository.findBySourceRefId(sourceRefId)
                .orElseGet(() -> TaskItemEntity.builder().taskId(buildId()).build());

        TaskStatus currentStatus = entity.getStatus();
        if (currentStatus == null) {
            currentStatus = TaskStatus.PENDING;
        }

        entity.setTaskDate(taskDate);
        entity.setTaskType(TaskType.FEED);
        entity.setTitle(normalizePipelineTitle(title));
        entity.setDetails(trimToNull(details));
        entity.setAssignedRole(assignedRole != null ? assignedRole : UserRole.WORKER);
        entity.setAssignedToUsername(trimToNull(entity.getAssignedToUsername()));
        entity.setAssignedByUsername(trimToNull(entity.getAssignedByUsername()));
        entity.setPriority(priority != null ? priority : TaskPriority.MEDIUM);
        entity.setDueTime(dueTime);
        entity.setSourceRefId(sourceRefId);
        entity.setStatus(currentStatus);
        if (currentStatus != TaskStatus.DONE) {
            entity.setCompletedAt(null);
            entity.setCompletedBy(null);
        }

        taskItemRepository.save(entity);
    }

    @Transactional
    public void upsertFromFeedTask(FeedSopTaskEntity feedTask) {
        if (feedTask == null || feedTask.getFeedTaskId() == null || feedTask.getFeedTaskId().isBlank()) {
            return;
        }
        String sourceRefId = "FEED_TASK:" + feedTask.getFeedTaskId();
        TaskItemEntity entity = taskItemRepository.findBySourceRefId(sourceRefId)
                .orElseGet(() -> TaskItemEntity.builder().taskId(buildId()).build());

        TaskStatus mappedStatus = mapStatus(feedTask.getStatus());
        TaskPriority mappedPriority = mapPriority(feedTask.getPriority());

        entity.setTaskDate(feedTask.getTaskDate() != null ? feedTask.getTaskDate() : LocalDate.now());
        entity.setTaskType(TaskType.FEED);
        entity.setTitle(normalizeFeedTitle(feedTask.getTitle()));
        entity.setDetails(trimToNull(feedTask.getDetails()));
        entity.setAssignedRole(feedTask.getAssignedRole() != null ? feedTask.getAssignedRole() : UserRole.WORKER);
        entity.setAssignedToUsername(trimToNull(feedTask.getAssignedToUsername()));
        entity.setAssignedByUsername(trimToNull(feedTask.getAssignedByUsername()));
        entity.setAssignedAt(feedTask.getAssignedAt());
        entity.setPriority(mappedPriority);
        entity.setStatus(mappedStatus);
        entity.setDueTime(feedTask.getDueTime());
        entity.setSourceRefId(sourceRefId);
        if (mappedStatus == TaskStatus.DONE) {
            entity.setCompletedAt(feedTask.getCompletedAt());
            entity.setCompletedBy(trimToNull(feedTask.getCompletedBy()));
        } else {
            entity.setCompletedAt(null);
            entity.setCompletedBy(null);
        }

        taskItemRepository.save(entity);
    }

    private TaskPriority resolvePriority(DeliveryTaskEntity deliveryTask) {
        if (deliveryTask.getStatus() == DeliveryTaskStatus.PENDING && deliveryTask.getTaskDate() != null) {
            if (!deliveryTask.getTaskDate().isAfter(LocalDate.now())) {
                return TaskPriority.HIGH;
            }
        }
        return TaskPriority.MEDIUM;
    }

    private UserRole resolveAssignedRole(String assignedTo) {
        if (assignedTo == null) {
            return UserRole.DELIVERY;
        }
        return authUserRepository.findByUsernameIgnoreCase(assignedTo)
                .map(user -> user.getRole())
                .orElse(UserRole.DELIVERY);
    }

    private TaskStatus mapStatus(DeliveryTaskStatus status) {
        if (status == null) {
            return TaskStatus.PENDING;
        }
        return switch (status) {
            case DELIVERED -> TaskStatus.DONE;
            case SKIPPED -> TaskStatus.SKIPPED;
            case PENDING -> TaskStatus.PENDING;
        };
    }

    private TaskStatus mapStatus(FeedSopTaskStatus status) {
        if (status == null) {
            return TaskStatus.PENDING;
        }
        return switch (status) {
            case PENDING -> TaskStatus.PENDING;
            case IN_PROGRESS -> TaskStatus.IN_PROGRESS;
            case DONE -> TaskStatus.DONE;
        };
    }

    private TaskPriority mapPriority(FeedSopTaskPriority priority) {
        if (priority == null) {
            return TaskPriority.MEDIUM;
        }
        return switch (priority) {
            case LOW -> TaskPriority.LOW;
            case MEDIUM -> TaskPriority.MEDIUM;
            case HIGH -> TaskPriority.HIGH;
        };
    }

    private String buildDetails(DeliveryTaskEntity deliveryTask) {
        String route = trimToNull(deliveryTask.getRouteName());
        String notes = trimToNull(deliveryTask.getNotes());
        String quantity = String.format("%.2f L", deliveryTask.getPlannedQtyLiters());
        String price = String.format("Rs %.2f", deliveryTask.getUnitPrice());
        String shift = deliveryTask.getTaskShift() != null ? deliveryTask.getTaskShift().name() : null;

        StringBuilder builder = new StringBuilder("Planned ").append(quantity).append(" @ ").append(price);
        if (shift != null) {
            builder.append(" | Shift: ").append(shift);
        }
        if (route != null) {
            builder.append(" | Route: ").append(route);
        }
        if (notes != null) {
            builder.append(" | ").append(notes);
        }
        return builder.toString();
    }

    private String safeCustomerName(String customerName) {
        String normalized = trimToNull(customerName);
        return normalized == null ? "Customer" : normalized;
    }

    private String buildDeliveryTitle(DeliveryTaskEntity deliveryTask) {
        String base = safeCustomerName(deliveryTask.getCustomerName());
        if (deliveryTask.getTaskShift() == null) {
            return "Delivery: " + base;
        }
        return "Delivery (" + deliveryTask.getTaskShift().name() + "): " + base;
    }

    private String normalizePipelineTitle(String title) {
        String normalized = trimToNull(title);
        return normalized == null ? "Stock Pipeline Task" : normalized;
    }

    private LocalTime resolveDeliveryDueTime(Shift shift) {
        if (shift == null || shift == Shift.AM) {
            return LocalTime.of(6, 30);
        }
        return LocalTime.of(17, 30);
    }

    private String normalizeFeedTitle(String title) {
        String normalized = trimToNull(title);
        return normalized == null ? "Feed Task" : normalized;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String buildId() {
        return "TSK_" + UUID.randomUUID().toString().substring(0, 8);
    }
}
