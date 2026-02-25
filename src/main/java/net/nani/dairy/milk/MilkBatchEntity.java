package net.nani.dairy.milk;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "milk_batch",
        uniqueConstraints = @UniqueConstraint(name = "uk_milk_batch_date_shift", columnNames = {"batch_date", "shift"}))
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class MilkBatchEntity {

    @Id
    @Column(name = "milk_batch_id", nullable = false, length = 80)
    private String milkBatchId; // user wants String IDs

    @Column(name = "batch_date", nullable = false)
    private LocalDate date;

    @Enumerated(EnumType.STRING)
    @Column(name = "shift", nullable = false, length = 10)
    private Shift shift;

    @Column(name = "total_liters", nullable = false)
    private double totalLiters;

    @Enumerated(EnumType.STRING)
    @Column(name = "qc_status", nullable = false, length = 20)
    private QcStatus qcStatus;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        var now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (qcStatus == null) qcStatus = QcStatus.PENDING;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
