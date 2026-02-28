package net.nani.dairy.sales;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import net.nani.dairy.milk.Shift;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "delivery_run_closure", indexes = {
        @Index(name = "idx_delivery_run_closure_date", columnList = "closure_date"),
        @Index(name = "idx_delivery_run_closure_route", columnList = "route_name"),
        @Index(name = "idx_delivery_run_closure_shift", columnList = "task_shift"),
        @Index(name = "idx_delivery_run_closure_closed_by", columnList = "closed_by")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeliveryRunClosureEntity {

    @Id
    @Column(name = "run_closure_id", nullable = false, length = 80)
    private String runClosureId;

    @Column(name = "closure_date", nullable = false)
    private LocalDate date;

    @Column(name = "route_name", nullable = false, length = 120)
    private String routeName;

    @Enumerated(EnumType.STRING)
    @Column(name = "task_shift", nullable = false, length = 10)
    private Shift shift;

    @Column(name = "total_stops", nullable = false)
    private long totalStops;

    @Column(name = "delivered_stops", nullable = false)
    private long deliveredStops;

    @Column(name = "pending_stops", nullable = false)
    private long pendingStops;

    @Column(name = "skipped_stops", nullable = false)
    private long skippedStops;

    @Column(name = "expected_collection", nullable = false)
    private double expectedCollection;

    @Column(name = "actual_collection", nullable = false)
    private double actualCollection;

    @Column(name = "variance", nullable = false)
    private double variance;

    @Column(name = "cash_collection", nullable = false)
    private double cashCollection;

    @Column(name = "upi_collection", nullable = false)
    private double upiCollection;

    @Column(name = "other_collection", nullable = false)
    private double otherCollection;

    @Column(name = "notes", length = 500)
    private String notes;

    @Column(name = "closed_by", nullable = false, length = 100)
    private String closedBy;

    @Column(name = "closed_at", nullable = false)
    private OffsetDateTime closedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        OffsetDateTime now = OffsetDateTime.now();
        if (closedAt == null) {
            closedAt = now;
        }
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
