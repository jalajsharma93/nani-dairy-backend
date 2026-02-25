package net.nani.dairy.feed;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "feed_material")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeedMaterialEntity {

    @Id
    @Column(name = "feed_material_id", length = 32, nullable = false, updatable = false)
    private String feedMaterialId;

    @Column(name = "material_name", length = 120, nullable = false)
    private String materialName;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", length = 32, nullable = false)
    private FeedMaterialCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "unit", length = 16, nullable = false)
    private FeedMaterialUnit unit;

    @Column(name = "available_qty", nullable = false)
    private double availableQty;

    @Column(name = "reorder_level_qty", nullable = false)
    private double reorderLevelQty;

    @Column(name = "cost_per_unit")
    private Double costPerUnit;

    @Column(name = "supplier_name", length = 160)
    private String supplierName;

    @Column(name = "notes", length = 512)
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
