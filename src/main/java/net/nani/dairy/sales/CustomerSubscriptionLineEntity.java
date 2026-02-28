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
import java.time.LocalTime;
import java.time.OffsetDateTime;

@Entity
@Table(name = "customer_subscription_line", indexes = {
        @Index(name = "idx_subscription_line_customer", columnList = "customer_id"),
        @Index(name = "idx_subscription_line_active", columnList = "is_active")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerSubscriptionLineEntity {

    @Id
    @Column(name = "subscription_line_id", length = 80, nullable = false)
    private String subscriptionLineId;

    @Column(name = "customer_id", length = 80, nullable = false)
    private String customerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "task_shift", length = 10, nullable = false)
    private Shift taskShift;

    @Enumerated(EnumType.STRING)
    @Column(name = "product_type", length = 30, nullable = false)
    private ProductType productType;

    @Column(name = "quantity", nullable = false)
    private double quantity;

    @Column(name = "unit_price", nullable = false)
    private double unitPrice;

    @Column(name = "preferred_time")
    private LocalTime preferredTime;

    @Column(name = "active_days_csv", length = 140)
    private String activeDaysCsv;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "notes", length = 500)
    private String notes;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (taskShift == null) taskShift = Shift.AM;
        if (productType == null) productType = ProductType.MILK;
        if (activeDaysCsv == null || activeDaysCsv.trim().isEmpty()) {
            activeDaysCsv = "MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY,SATURDAY,SUNDAY";
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
