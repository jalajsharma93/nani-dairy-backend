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

import java.time.OffsetDateTime;

@Entity
@Table(name = "customer_record", indexes = {
        @Index(name = "idx_customer_record_active", columnList = "is_active"),
        @Index(name = "idx_customer_record_route", columnList = "route_name"),
        @Index(name = "idx_customer_record_subscription", columnList = "subscription_active")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerRecordEntity {

    @Id
    @Column(name = "customer_id", length = 80, nullable = false)
    private String customerId;

    @Column(name = "customer_name", length = 120, nullable = false)
    private String customerName;

    @Enumerated(EnumType.STRING)
    @Column(name = "customer_type", length = 30, nullable = false)
    private CustomerType customerType;

    @Column(name = "phone", length = 40)
    private String phone;

    @Column(name = "route_name", length = 80)
    private String routeName;

    @Column(name = "collection_point", length = 120)
    private String collectionPoint;

    @Column(name = "subscription_active", nullable = false)
    private boolean subscriptionActive;

    @Column(name = "daily_subscription_qty")
    private Double dailySubscriptionQty;

    @Column(name = "is_active", nullable = false)
    private boolean isActive;

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
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
