package net.nani.dairy.tasks;

import lombok.RequiredArgsConstructor;
import net.nani.dairy.auth.AuthUserEntity;
import net.nani.dairy.auth.AuthUserRepository;
import net.nani.dairy.auth.UserRole;
import net.nani.dairy.tasks.dto.CreateTaskTemplateRequest;
import net.nani.dairy.tasks.dto.TaskAutomationReminderResponse;
import net.nani.dairy.tasks.dto.TaskAutomationRunResponse;
import net.nani.dairy.tasks.dto.TaskTemplateResponse;
import net.nani.dairy.tasks.dto.UpdateTaskTemplateRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TaskTemplateService {

    private static final int DEFAULT_REMINDER_LEAD_MINUTES = 60;
    private static final int DEFAULT_REMINDER_REPEAT_MINUTES = 180;
    private static final int DEFAULT_ESCALATION_DELAY_MINUTES = 240;

    private final TaskTemplateRepository taskTemplateRepository;
    private final TaskItemRepository taskItemRepository;
    private final AuthUserRepository authUserRepository;

    public List<TaskTemplateResponse> list(Boolean activeOnly) {
        List<TaskTemplateEntity> rows;
        if (Boolean.TRUE.equals(activeOnly)) {
            rows = taskTemplateRepository.findByActiveTrueOrderByCreatedAtAsc();
        } else {
            rows = taskTemplateRepository.findAll().stream()
                    .sorted(Comparator.comparing(TaskTemplateEntity::getCreatedAt))
                    .toList();
        }
        return rows.stream().map(this::toTemplateResponse).toList();
    }

    @Transactional
    public TaskTemplateResponse create(
            CreateTaskTemplateRequest req,
            String actorUsername,
            UserRole actorRole
    ) {
        TaskTemplateEntity entity = TaskTemplateEntity.builder()
                .taskTemplateId(buildTemplateId())
                .build();

        applyPayload(entity, normalizeCreate(req), actorUsername, actorRole);
        return toTemplateResponse(taskTemplateRepository.save(entity));
    }

    @Transactional
    public TaskTemplateResponse update(
            String taskTemplateId,
            UpdateTaskTemplateRequest req,
            String actorUsername,
            UserRole actorRole
    ) {
        TaskTemplateEntity entity = taskTemplateRepository.findById(taskTemplateId)
                .orElseThrow(() -> new IllegalArgumentException("Task template not found"));

        applyPayload(entity, normalizeUpdate(req), actorUsername, actorRole);
        return toTemplateResponse(taskTemplateRepository.save(entity));
    }

    @Transactional
    public TaskAutomationRunResponse run(LocalDate date, boolean dryRun, String actorUsername) {
        LocalDate effectiveDate = date != null ? date : LocalDate.now();
        LocalDateTime now = LocalDateTime.now();
        String actor = normalizeActor(actorUsername);

        List<TaskTemplateEntity> activeTemplates = loadActiveTemplatesForDate(effectiveDate);
        Map<String, TaskTemplateEntity> templatesById = activeTemplates.stream()
                .collect(Collectors.toMap(TaskTemplateEntity::getTaskTemplateId, t -> t, (first, second) -> first));

        int generated = 0;
        int updated = 0;
        int escalated = 0;

        Map<String, TaskItemEntity> dirty = new LinkedHashMap<>();

        for (TaskTemplateEntity template : activeTemplates) {
            if (!shouldRunOnDate(template, effectiveDate)) {
                continue;
            }

            String sourceRefId = buildTemplateSourceRef(template.getTaskTemplateId(), effectiveDate);
            Optional<TaskItemEntity> existingOpt = taskItemRepository.findBySourceRefId(sourceRefId);
            if (existingOpt.isEmpty()) {
                TaskItemEntity task = buildTaskFromTemplate(template, effectiveDate, sourceRefId, actor, now);
                generated += 1;
                if (!dryRun) {
                    dirty.put(task.getTaskId(), task);
                }
                continue;
            }

            TaskItemEntity task = existingOpt.get();
            boolean changed = syncTaskWithTemplate(task, template, effectiveDate);
            if (changed) {
                updated += 1;
                if (!dryRun) {
                    dirty.put(task.getTaskId(), task);
                }
            }
        }

        List<TaskAutomationReminderResponse> reminders = new ArrayList<>();
        List<TaskItemEntity> openTasks = taskItemRepository
                .findByStatusInAndTaskDateLessThanEqualOrderByTaskDateAscDueTimeAscCreatedAtAsc(
                        List.of(TaskStatus.PENDING, TaskStatus.IN_PROGRESS),
                        effectiveDate
                );

        for (TaskItemEntity task : openTasks) {
            String templateId = parseTemplateId(task.getSourceRefId());
            if (templateId == null) {
                continue;
            }
            TaskTemplateEntity template = templatesById.get(templateId);
            if (template == null || !template.isActive()) {
                continue;
            }

            if (task.getDueTime() != null) {
                int escalationDelay = normalizeDelayMinutes(template.getEscalationDelayMinutes(), DEFAULT_ESCALATION_DELAY_MINUTES);
                if (escalationDelay > 0 && task.getEscalatedAt() == null) {
                    LocalDateTime escalationAt = LocalDateTime.of(task.getTaskDate(), task.getDueTime()).plusMinutes(escalationDelay);
                    if (!now.isBefore(escalationAt)) {
                        escalated += 1;
                        if (!dryRun) {
                            UserRole previousRole = task.getAssignedRole();
                            UserRole escalatedRole = template.getEscalateToRole() != null
                                    ? template.getEscalateToRole()
                                    : UserRole.MANAGER;
                            task.setAssignedRole(escalatedRole);
                            task.setAssignedToUsername(null);
                            task.setAssignedByUsername(actor);
                            task.setAssignedAt(now);
                            task.setPriority(TaskPriority.HIGH);
                            task.setEscalatedAt(now);
                            task.setEscalationCount((task.getEscalationCount() == null ? 0 : task.getEscalationCount()) + 1);
                            task.setDetails(appendEscalationNote(task.getDetails(), previousRole, escalatedRole, now));
                            dirty.put(task.getTaskId(), task);
                        }
                    }
                }
            }

            int reminderLead = normalizeDelayMinutes(template.getReminderLeadMinutes(), DEFAULT_REMINDER_LEAD_MINUTES);
            int reminderRepeat = normalizeDelayMinutes(template.getReminderRepeatMinutes(), DEFAULT_REMINDER_REPEAT_MINUTES);
            if (reminderLead <= 0 || reminderRepeat <= 0) {
                continue;
            }

            LocalTime dueTime = task.getDueTime() != null ? task.getDueTime() : LocalTime.of(18, 0);
            LocalDateTime dueAt = LocalDateTime.of(task.getTaskDate(), dueTime);
            LocalDateTime reminderThreshold = dueAt.minusMinutes(reminderLead);
            LocalDateTime lastReminder = task.getReminderSentAt();
            boolean reminderWindowOpen = !now.isBefore(reminderThreshold);
            boolean reminderRepeatElapsed = lastReminder == null || !lastReminder.isAfter(now.minusMinutes(reminderRepeat));
            if (reminderWindowOpen && reminderRepeatElapsed) {
                String message = now.isAfter(dueAt)
                        ? "Task is overdue. Please complete as soon as possible."
                        : "Task is due soon. Please action before due time.";
                reminders.add(TaskAutomationReminderResponse.builder()
                        .taskId(task.getTaskId())
                        .taskDate(task.getTaskDate())
                        .title(task.getTitle())
                        .taskType(task.getTaskType())
                        .status(task.getStatus())
                        .priority(task.getPriority())
                        .dueTime(task.getDueTime())
                        .assignedRole(task.getAssignedRole())
                        .assignedToUsername(task.getAssignedToUsername())
                        .message(message)
                        .reminderAt(now)
                        .build());

                if (!dryRun) {
                    task.setReminderSentAt(now);
                    dirty.put(task.getTaskId(), task);
                }
            }
        }

        if (!dryRun && !dirty.isEmpty()) {
            taskItemRepository.saveAll(dirty.values());
        }

        return TaskAutomationRunResponse.builder()
                .date(effectiveDate)
                .executedAt(OffsetDateTime.now())
                .processedTemplates(activeTemplates.size())
                .generatedTasks(generated)
                .updatedTasks(updated)
                .escalatedTasks(escalated)
                .remindersTriggered(reminders.size())
                .reminders(reminders)
                .build();
    }

    private TaskItemEntity buildTaskFromTemplate(
            TaskTemplateEntity template,
            LocalDate taskDate,
            String sourceRefId,
            String actor,
            LocalDateTime now
    ) {
        return TaskItemEntity.builder()
                .taskId(buildTaskId())
                .taskDate(taskDate)
                .taskType(template.getTaskType() != null ? template.getTaskType() : TaskType.OTHER)
                .title(template.getTitle())
                .details(template.getDetails())
                .assignedRole(template.getAssignedRole() != null ? template.getAssignedRole() : UserRole.WORKER)
                .assignedToUsername(trimToNull(template.getAssignedToUsername()))
                .assignedByUsername(actor)
                .assignedAt(now)
                .priority(template.getPriority() != null ? template.getPriority() : TaskPriority.MEDIUM)
                .status(TaskStatus.PENDING)
                .dueTime(template.getDueTime())
                .sourceRefId(sourceRefId)
                .completedAt(null)
                .completedBy(null)
                .reminderSentAt(null)
                .escalatedAt(null)
                .escalationCount(0)
                .build();
    }

    private boolean syncTaskWithTemplate(TaskItemEntity task, TaskTemplateEntity template, LocalDate effectiveDate) {
        boolean changed = false;

        if (!Objects.equals(task.getTaskDate(), effectiveDate)) {
            task.setTaskDate(effectiveDate);
            changed = true;
        }

        TaskType taskType = template.getTaskType() != null ? template.getTaskType() : TaskType.OTHER;
        if (task.getTaskType() != taskType) {
            task.setTaskType(taskType);
            changed = true;
        }

        String templateTitle = trimToNull(template.getTitle());
        if (!sameText(task.getTitle(), templateTitle)) {
            task.setTitle(templateTitle);
            changed = true;
        }

        String templateDetails = trimToNull(template.getDetails());
        if (!sameText(task.getDetails(), templateDetails)) {
            task.setDetails(templateDetails);
            changed = true;
        }

        if (!isClosed(task.getStatus())) {
            UserRole assignedRole = template.getAssignedRole() != null ? template.getAssignedRole() : UserRole.WORKER;
            if (task.getAssignedRole() != assignedRole) {
                task.setAssignedRole(assignedRole);
                changed = true;
            }

            String assignedTo = trimToNull(template.getAssignedToUsername());
            if (!sameText(task.getAssignedToUsername(), assignedTo)) {
                task.setAssignedToUsername(assignedTo);
                changed = true;
            }

            TaskPriority priority = template.getPriority() != null ? template.getPriority() : TaskPriority.MEDIUM;
            if (task.getPriority() != priority) {
                task.setPriority(priority);
                changed = true;
            }
        }

        if (!Objects.equals(task.getDueTime(), template.getDueTime())) {
            task.setDueTime(template.getDueTime());
            changed = true;
        }

        return changed;
    }

    private List<TaskTemplateEntity> loadActiveTemplatesForDate(LocalDate date) {
        List<TaskTemplateEntity> bounded = taskTemplateRepository
                .findByActiveTrueAndStartDateLessThanEqualAndEndDateGreaterThanEqualOrderByCreatedAtAsc(date, date);
        List<TaskTemplateEntity> openEnded = taskTemplateRepository
                .findByActiveTrueAndStartDateLessThanEqualAndEndDateIsNullOrderByCreatedAtAsc(date);

        Map<String, TaskTemplateEntity> merged = new LinkedHashMap<>();
        for (TaskTemplateEntity row : bounded) {
            merged.put(row.getTaskTemplateId(), row);
        }
        for (TaskTemplateEntity row : openEnded) {
            merged.putIfAbsent(row.getTaskTemplateId(), row);
        }
        return new ArrayList<>(merged.values());
    }

    private boolean shouldRunOnDate(TaskTemplateEntity template, LocalDate date) {
        if (!template.isActive()) {
            return false;
        }
        if (template.getStartDate() != null && date.isBefore(template.getStartDate())) {
            return false;
        }
        if (template.getEndDate() != null && date.isAfter(template.getEndDate())) {
            return false;
        }

        TaskTemplateFrequency frequency = template.getFrequency() != null
                ? template.getFrequency()
                : TaskTemplateFrequency.DAILY;
        if (frequency == TaskTemplateFrequency.DAILY) {
            return true;
        }

        Set<DayOfWeek> days = parseDaysOfWeek(template.getDaysOfWeekCsv());
        if (days.isEmpty()) {
            LocalDate start = template.getStartDate() != null ? template.getStartDate() : date;
            return start.getDayOfWeek() == date.getDayOfWeek();
        }
        return days.contains(date.getDayOfWeek());
    }

    private void applyPayload(
            TaskTemplateEntity entity,
            NormalizedTemplatePayload payload,
            String actorUsername,
            UserRole actorRole
    ) {
        validateActorPermissions(actorRole, payload);

        String assignedTo = resolveAssignee(payload.assignedToUsername(), payload.assignedRole());

        entity.setTitle(payload.title());
        entity.setDetails(payload.details());
        entity.setTaskType(payload.taskType());
        entity.setAssignedRole(payload.assignedRole());
        entity.setAssignedToUsername(assignedTo);
        entity.setPriority(payload.priority());
        entity.setDueTime(payload.dueTime());
        entity.setFrequency(payload.frequency());
        entity.setDaysOfWeekCsv(toDaysOfWeekCsv(payload.daysOfWeek()));
        entity.setStartDate(payload.startDate());
        entity.setEndDate(payload.endDate());
        entity.setActive(payload.active());
        entity.setReminderLeadMinutes(payload.reminderLeadMinutes());
        entity.setReminderRepeatMinutes(payload.reminderRepeatMinutes());
        entity.setEscalationDelayMinutes(payload.escalationDelayMinutes());
        entity.setEscalateToRole(payload.escalateToRole());

        if (!sameText(trimToNull(entity.getAssignedToUsername()), assignedTo)) {
            entity.setAssignedToUsername(assignedTo);
        }
        normalizeActor(actorUsername);
    }

    private void validateActorPermissions(UserRole actorRole, NormalizedTemplatePayload payload) {
        UserRole safeRole = actorRole != null ? actorRole : UserRole.WORKER;
        if (safeRole != UserRole.ADMIN && safeRole != UserRole.MANAGER && safeRole != UserRole.FEED_MANAGER) {
            throw new AccessDeniedException("Only ADMIN, MANAGER or FEED_MANAGER can manage task templates");
        }

        if (safeRole == UserRole.MANAGER && payload.assignedRole() == UserRole.ADMIN) {
            throw new AccessDeniedException("Manager cannot create admin task templates");
        }

        if (safeRole == UserRole.FEED_MANAGER) {
            if (!(payload.assignedRole() == UserRole.FEED_MANAGER || payload.assignedRole() == UserRole.WORKER)) {
                throw new AccessDeniedException("Feed manager can create templates only for FEED_MANAGER/WORKER");
            }
            if (payload.taskType() != TaskType.FEED) {
                throw new AccessDeniedException("Feed manager templates must be FEED task type");
            }
            if (!(payload.escalateToRole() == UserRole.MANAGER || payload.escalateToRole() == UserRole.FEED_MANAGER)) {
                throw new AccessDeniedException("Feed manager escalation can target only MANAGER/FEED_MANAGER");
            }
        }
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
        if (assignee.getRole() != assignedRole) {
            throw new IllegalArgumentException("Assigned user role must match assignedRole");
        }
        return assignee.getUsername();
    }

    private NormalizedTemplatePayload normalizeCreate(CreateTaskTemplateRequest req) {
        if (req == null) {
            throw new IllegalArgumentException("Request body is required");
        }
        return normalizePayload(
                req.getTitle(),
                req.getDetails(),
                req.getTaskType(),
                req.getAssignedRole(),
                req.getAssignedToUsername(),
                req.getPriority(),
                req.getDueTime(),
                req.getFrequency(),
                req.getDaysOfWeek(),
                req.getStartDate(),
                req.getEndDate(),
                req.getActive(),
                req.getReminderLeadMinutes(),
                req.getReminderRepeatMinutes(),
                req.getEscalationDelayMinutes(),
                req.getEscalateToRole()
        );
    }

    private NormalizedTemplatePayload normalizeUpdate(UpdateTaskTemplateRequest req) {
        if (req == null) {
            throw new IllegalArgumentException("Request body is required");
        }
        return normalizePayload(
                req.getTitle(),
                req.getDetails(),
                req.getTaskType(),
                req.getAssignedRole(),
                req.getAssignedToUsername(),
                req.getPriority(),
                req.getDueTime(),
                req.getFrequency(),
                req.getDaysOfWeek(),
                req.getStartDate(),
                req.getEndDate(),
                req.getActive(),
                req.getReminderLeadMinutes(),
                req.getReminderRepeatMinutes(),
                req.getEscalationDelayMinutes(),
                req.getEscalateToRole()
        );
    }

    private NormalizedTemplatePayload normalizePayload(
            String title,
            String details,
            TaskType taskType,
            UserRole assignedRole,
            String assignedToUsername,
            TaskPriority priority,
            LocalTime dueTime,
            TaskTemplateFrequency frequency,
            List<String> daysOfWeek,
            LocalDate startDate,
            LocalDate endDate,
            Boolean active,
            Integer reminderLeadMinutes,
            Integer reminderRepeatMinutes,
            Integer escalationDelayMinutes,
            UserRole escalateToRole
    ) {
        String normalizedTitle = normalizeRequired(title, "Template title is required");
        TaskType normalizedType = taskType != null ? taskType : TaskType.OTHER;
        UserRole normalizedRole = assignedRole != null ? assignedRole : UserRole.WORKER;
        TaskPriority normalizedPriority = priority != null ? priority : TaskPriority.MEDIUM;
        TaskTemplateFrequency normalizedFrequency = frequency != null ? frequency : TaskTemplateFrequency.DAILY;
        LocalDate normalizedStartDate = startDate != null ? startDate : LocalDate.now();
        LocalDate normalizedEndDate = endDate;
        if (normalizedEndDate != null && normalizedEndDate.isBefore(normalizedStartDate)) {
            throw new IllegalArgumentException("endDate cannot be before startDate");
        }

        Set<DayOfWeek> parsedDays = parseDaysOfWeek(daysOfWeek);
        if (normalizedFrequency == TaskTemplateFrequency.WEEKLY && parsedDays.isEmpty()) {
            parsedDays = EnumSet.of(normalizedStartDate.getDayOfWeek());
        }

        int normalizedReminderLead = normalizeDelayMinutes(reminderLeadMinutes, DEFAULT_REMINDER_LEAD_MINUTES);
        int normalizedReminderRepeat = normalizeDelayMinutes(reminderRepeatMinutes, DEFAULT_REMINDER_REPEAT_MINUTES);
        int normalizedEscalationDelay = normalizeDelayMinutes(escalationDelayMinutes, DEFAULT_ESCALATION_DELAY_MINUTES);

        UserRole normalizedEscalateToRole = escalateToRole != null ? escalateToRole : UserRole.MANAGER;

        return new NormalizedTemplatePayload(
                normalizedTitle,
                trimToNull(details),
                normalizedType,
                normalizedRole,
                trimToNull(assignedToUsername),
                normalizedPriority,
                dueTime,
                normalizedFrequency,
                parsedDays,
                normalizedStartDate,
                normalizedEndDate,
                active == null || active,
                normalizedReminderLead,
                normalizedReminderRepeat,
                normalizedEscalationDelay,
                normalizedEscalateToRole
        );
    }

    private int normalizeDelayMinutes(Integer value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value < 0) {
            throw new IllegalArgumentException("Minute value cannot be negative");
        }
        return Math.min(value, 7 * 24 * 60);
    }

    private Set<DayOfWeek> parseDaysOfWeek(List<String> values) {
        if (values == null || values.isEmpty()) {
            return EnumSet.noneOf(DayOfWeek.class);
        }
        Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
        for (String value : values) {
            String normalized = trimToNull(value);
            if (normalized == null) {
                continue;
            }
            try {
                days.add(DayOfWeek.valueOf(normalized.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("Invalid dayOfWeek: " + normalized);
            }
        }
        return days;
    }

    private Set<DayOfWeek> parseDaysOfWeek(String csv) {
        String normalizedCsv = trimToNull(csv);
        if (normalizedCsv == null) {
            return EnumSet.noneOf(DayOfWeek.class);
        }
        return parseDaysOfWeek(Arrays.asList(normalizedCsv.split(",")));
    }

    private String toDaysOfWeekCsv(Set<DayOfWeek> days) {
        if (days == null || days.isEmpty()) {
            return null;
        }
        return days.stream()
                .sorted(Comparator.comparingInt(DayOfWeek::getValue))
                .map(DayOfWeek::name)
                .collect(Collectors.joining(","));
    }

    private TaskTemplateResponse toTemplateResponse(TaskTemplateEntity row) {
        List<String> days = parseDaysOfWeek(row.getDaysOfWeekCsv()).stream()
                .sorted(Comparator.comparingInt(DayOfWeek::getValue))
                .map(DayOfWeek::name)
                .toList();

        return TaskTemplateResponse.builder()
                .taskTemplateId(row.getTaskTemplateId())
                .title(row.getTitle())
                .details(row.getDetails())
                .taskType(row.getTaskType())
                .assignedRole(row.getAssignedRole())
                .assignedToUsername(row.getAssignedToUsername())
                .priority(row.getPriority())
                .dueTime(row.getDueTime())
                .frequency(row.getFrequency())
                .daysOfWeek(days)
                .startDate(row.getStartDate())
                .endDate(row.getEndDate())
                .active(row.isActive())
                .reminderLeadMinutes(row.getReminderLeadMinutes())
                .reminderRepeatMinutes(row.getReminderRepeatMinutes())
                .escalationDelayMinutes(row.getEscalationDelayMinutes())
                .escalateToRole(row.getEscalateToRole())
                .createdAt(row.getCreatedAt())
                .updatedAt(row.getUpdatedAt())
                .build();
    }

    private boolean isClosed(TaskStatus status) {
        return status == TaskStatus.DONE || status == TaskStatus.SKIPPED;
    }

    private String parseTemplateId(String sourceRefId) {
        String normalized = trimToNull(sourceRefId);
        if (normalized == null || !normalized.startsWith("TASK_TEMPLATE:")) {
            return null;
        }
        String[] parts = normalized.split(":");
        if (parts.length < 3) {
            return null;
        }
        return trimToNull(parts[1]);
    }

    private String appendEscalationNote(String details, UserRole previousRole, UserRole escalatedRole, LocalDateTime now) {
        String base = trimToNull(details);
        String note = "Escalated to " + escalatedRole + " at " + now.toLocalTime().withSecond(0).withNano(0)
                + (previousRole != null ? " (from " + previousRole + ")" : "");
        if (base == null) {
            return note;
        }
        if (base.contains(note)) {
            return base;
        }
        return base + " | " + note;
    }

    private String buildTemplateSourceRef(String templateId, LocalDate date) {
        return "TASK_TEMPLATE:" + templateId + ":" + date;
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

    private boolean sameText(String left, String right) {
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
        return normalized == null ? "system-task-automation" : normalized;
    }

    private String buildTemplateId() {
        return "TTM_" + UUID.randomUUID().toString().substring(0, 8);
    }

    private String buildTaskId() {
        return "TSK_" + UUID.randomUUID().toString().substring(0, 8);
    }

    private record NormalizedTemplatePayload(
            String title,
            String details,
            TaskType taskType,
            UserRole assignedRole,
            String assignedToUsername,
            TaskPriority priority,
            LocalTime dueTime,
            TaskTemplateFrequency frequency,
            Set<DayOfWeek> daysOfWeek,
            LocalDate startDate,
            LocalDate endDate,
            boolean active,
            int reminderLeadMinutes,
            int reminderRepeatMinutes,
            int escalationDelayMinutes,
            UserRole escalateToRole
    ) {
    }
}
