package net.nani.dairy.integration;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;

public interface IntegrationEventRepository extends JpaRepository<IntegrationEventEntity, String> {
    List<IntegrationEventEntity> findTop500ByOrderByReceivedAtDesc();

    List<IntegrationEventEntity> findByConnectorIdOrderByReceivedAtDesc(String connectorId);

    long countByStatus(IntegrationIngestStatus status);

    long countByReceivedAtGreaterThanEqual(OffsetDateTime since);

    long countByStatusAndReceivedAtGreaterThanEqual(IntegrationIngestStatus status, OffsetDateTime since);

    long countByConnectorId(String connectorId);

    long countByConnectorIdAndStatus(String connectorId, IntegrationIngestStatus status);
}
