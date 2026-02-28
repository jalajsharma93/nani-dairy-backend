package net.nani.dairy.stock;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(
        name = "processing_stock_txn",
        uniqueConstraints = @UniqueConstraint(name = "uk_processing_stock_txn_source_key", columnNames = "source_key"),
        indexes = {
                @Index(name = "idx_processing_stock_txn_date", columnList = "txn_date"),
                @Index(name = "idx_processing_stock_txn_type", columnList = "txn_type")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProcessingStockTxnEntity {

    @Id
    @Column(name = "stock_txn_id", length = 40, nullable = false, updatable = false)
    private String stockTxnId;

    @Column(name = "txn_date", nullable = false)
    private LocalDate txnDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "txn_type", length = 40, nullable = false)
    private ProcessingStockTxnType txnType;

    @Column(name = "source_key", length = 160)
    private String sourceKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_stage", length = 24)
    private ProcessingStockStage fromStage;

    @Column(name = "input_qty")
    private Double inputQty;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_stage", length = 24)
    private ProcessingStockStage toStage;

    @Column(name = "output_qty")
    private Double outputQty;

    @Column(name = "notes", length = 500)
    private String notes;

    @Column(name = "actor_username", length = 120)
    private String actorUsername;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    public void prePersist() {
        createdAt = OffsetDateTime.now();
    }
}
