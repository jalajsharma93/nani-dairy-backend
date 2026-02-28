package net.nani.dairy.tasks;

import net.nani.dairy.auth.UserRole;
import net.nani.dairy.feed.FeedManagementService;
import net.nani.dairy.feed.FeedSopTaskEntity;
import net.nani.dairy.feed.FeedSopTaskPriority;
import net.nani.dairy.feed.FeedSopTaskRepository;
import net.nani.dairy.feed.FeedSopTaskStatus;
import net.nani.dairy.feed.dto.CreateFeedSopTaskRequest;
import net.nani.dairy.tasks.dto.UpdateTaskItemRequest;
import net.nani.dairy.tasks.dto.UpdateTaskItemStatusRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:nani_task_sync;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.h2.console.enabled=false"
})
class FeedTaskSyncIntegrationTest {

    @Autowired
    private FeedManagementService feedManagementService;

    @Autowired
    private FeedSopTaskRepository feedSopTaskRepository;

    @Autowired
    private TaskItemService taskItemService;

    @Autowired
    private TaskItemRepository taskItemRepository;

    @Test
    void createFeedTask_syncsToGenericTask() {
        LocalDate taskDate = LocalDate.now();
        LocalTime dueTime = LocalTime.of(6, 30);

        var feedTask = feedManagementService.createTask(
                CreateFeedSopTaskRequest.builder()
                        .taskDate(taskDate)
                        .title("Prepare AM ration")
                        .details("Mix silage + concentrate")
                        .assignedRole(UserRole.WORKER)
                        .priority(FeedSopTaskPriority.HIGH)
                        .dueTime(dueTime)
                        .build(),
                "admin"
        );

        String sourceRefId = "FEED_TASK:" + feedTask.getFeedTaskId();
        TaskItemEntity genericTask = taskItemRepository.findBySourceRefId(sourceRefId)
                .orElseThrow(() -> new AssertionError("Generic task not created for feed task"));

        assertEquals(TaskType.FEED, genericTask.getTaskType());
        assertEquals("Prepare AM ration", genericTask.getTitle());
        assertEquals(TaskStatus.PENDING, genericTask.getStatus());
        assertEquals(TaskPriority.HIGH, genericTask.getPriority());
        assertEquals(taskDate, genericTask.getTaskDate());
        assertEquals(dueTime, genericTask.getDueTime());
    }

    @Test
    void updateGenericTaskStatus_syncsBackToFeedTask() {
        var feedTask = feedManagementService.createTask(
                CreateFeedSopTaskRequest.builder()
                        .taskDate(LocalDate.now())
                        .title("Prepare PM ration")
                        .assignedRole(UserRole.WORKER)
                        .priority(FeedSopTaskPriority.MEDIUM)
                        .build(),
                "admin"
        );
        String sourceRefId = "FEED_TASK:" + feedTask.getFeedTaskId();
        TaskItemEntity genericTask = taskItemRepository.findBySourceRefId(sourceRefId)
                .orElseThrow(() -> new AssertionError("Generic task not created for feed task"));

        taskItemService.updateStatus(
                genericTask.getTaskId(),
                UpdateTaskItemStatusRequest.builder().status(TaskStatus.DONE).build(),
                "worker",
                UserRole.WORKER,
                false
        );

        FeedSopTaskEntity feedUpdated = feedSopTaskRepository.findById(feedTask.getFeedTaskId())
                .orElseThrow(() -> new AssertionError("Feed task missing"));
        TaskItemEntity genericUpdated = taskItemRepository.findBySourceRefId(sourceRefId)
                .orElseThrow(() -> new AssertionError("Generic task missing after sync"));

        assertEquals(FeedSopTaskStatus.DONE, feedUpdated.getStatus());
        assertNotNull(feedUpdated.getCompletedAt());
        assertEquals("worker", feedUpdated.getCompletedBy());
        assertEquals(TaskStatus.DONE, genericUpdated.getStatus());
    }

    @Test
    void updateGenericTaskFields_syncsBackToFeedTask() {
        var feedTask = feedManagementService.createTask(
                CreateFeedSopTaskRequest.builder()
                        .taskDate(LocalDate.now())
                        .title("Initial title")
                        .details("Initial details")
                        .assignedRole(UserRole.WORKER)
                        .priority(FeedSopTaskPriority.MEDIUM)
                        .build(),
                "admin"
        );

        String sourceRefId = "FEED_TASK:" + feedTask.getFeedTaskId();
        TaskItemEntity genericTask = taskItemRepository.findBySourceRefId(sourceRefId)
                .orElseThrow(() -> new AssertionError("Generic task not created for feed task"));

        LocalDate nextDate = LocalDate.now().plusDays(1);
        LocalTime nextDue = LocalTime.of(8, 15);

        taskItemService.update(
                genericTask.getTaskId(),
                UpdateTaskItemRequest.builder()
                        .taskDate(nextDate)
                        .taskType(TaskType.FEED)
                        .title("Updated feed prep")
                        .details("Updated details for batch")
                        .assignedRole(UserRole.WORKER)
                        .assignedToUsername(null)
                        .priority(TaskPriority.LOW)
                        .status(TaskStatus.IN_PROGRESS)
                        .dueTime(nextDue)
                        .sourceRefId(sourceRefId)
                        .build(),
                "admin",
                UserRole.ADMIN
        );

        FeedSopTaskEntity feedUpdated = feedSopTaskRepository.findById(feedTask.getFeedTaskId())
                .orElseThrow(() -> new AssertionError("Feed task missing"));
        TaskItemEntity genericUpdated = taskItemRepository.findBySourceRefId(sourceRefId)
                .orElseThrow(() -> new AssertionError("Generic task missing after sync"));

        assertEquals("Updated feed prep", feedUpdated.getTitle());
        assertEquals("Updated details for batch", feedUpdated.getDetails());
        assertEquals(nextDate, feedUpdated.getTaskDate());
        assertEquals(nextDue, feedUpdated.getDueTime());
        assertEquals(FeedSopTaskPriority.LOW, feedUpdated.getPriority());
        assertEquals(FeedSopTaskStatus.IN_PROGRESS, feedUpdated.getStatus());

        assertEquals("Updated feed prep", genericUpdated.getTitle());
        assertEquals(TaskPriority.LOW, genericUpdated.getPriority());
        assertEquals(TaskStatus.IN_PROGRESS, genericUpdated.getStatus());
    }
}

