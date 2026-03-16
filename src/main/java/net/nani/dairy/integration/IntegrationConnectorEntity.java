package net.nani.dairy.integration;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Entity
@Table(
        name = "integration_connector",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_integration_connector_key", columnNames = "connector_key")
        },
        indexes = {
                @Index(name = "idx_integration_connector_type", columnList = "connector_type"),
                @Index(name = "idx_integration_connector_status", columnList = "status")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IntegrationConnectorEntity {

    @Id
    @Column(name = "connector_id", length = 80, nullable = false)
    private String connectorId;

    @Column(name = "connector_key", length = 120, nullable = false)
    private String connectorKey;

    @Column(name = "name", length = 120, nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "connector_type", length = 40, nullable = false)
    private ConnectorType connectorType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private ConnectorStatus status;

    @Column(name = "token_hash", length = 200, nullable = false)
    private String tokenHash;

    @Column(name = "allowed_source", length = 160)
    private String allowedSource;

    @Column(name = "created_by", length = 120)
    private String createdBy;

    @Column(name = "last_seen_at")
    private OffsetDateTime lastSeenAt;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (status == null) {
            status = ConnectorStatus.ACTIVE;
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
