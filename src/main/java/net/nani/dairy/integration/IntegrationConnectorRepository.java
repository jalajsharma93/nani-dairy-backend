package net.nani.dairy.integration;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface IntegrationConnectorRepository extends JpaRepository<IntegrationConnectorEntity, String> {
    Optional<IntegrationConnectorEntity> findByConnectorKeyIgnoreCase(String connectorKey);

    boolean existsByConnectorKeyIgnoreCase(String connectorKey);

    List<IntegrationConnectorEntity> findAllByOrderByNameAsc();

    List<IntegrationConnectorEntity> findByStatusOrderByNameAsc(ConnectorStatus status);

    long countByStatus(ConnectorStatus status);
}
