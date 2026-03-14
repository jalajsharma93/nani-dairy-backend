package net.nani.dairy.feed;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(
        name = "feed_procurement_run",
        indexes = {
                @Index(name = "idx_feed_procurement_run_plan_date", columnList = "plan_date"),
                @Index(name = "idx_feed_procurement_run_created_at", columnList = "created_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeedProcurementRunEntity {

    @Id
    @Column(name = "feed_procurement_run_id", length = 40, nullable = false, updatable = false)
    private String feedProcurementRunId;

    @Column(name = "plan_date", nullable = false)
    private LocalDate planDate;

    @Column(name = "task_date", nullable = false)
    private LocalDate taskDate;

    @Column(name = "run_mode", length = 20, nullable = false)
    private String runMode;

    @Column(name = "lookback_days", nullable = false)
    private int lookbackDays;

    @Column(name = "horizon_days", nullable = false)
    private int horizonDays;

    @Column(name = "considered_items", nullable = false)
    private int consideredItems;

    @Column(name = "created_tasks", nullable = false)
    private int createdTasks;

    @Column(name = "skipped_tasks", nullable = false)
    private int skippedTasks;

    @Column(name = "actor", length = 120)
    private String actor;

    @Column(name = "notes", length = 400)
    private String notes;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}
