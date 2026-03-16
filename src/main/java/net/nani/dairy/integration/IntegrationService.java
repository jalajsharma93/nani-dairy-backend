package net.nani.dairy.integration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import net.nani.dairy.integration.dto.CreateIntegrationConnectorRequest;
import net.nani.dairy.integration.dto.IntegrationConnectorMetricsResponse;
import net.nani.dairy.integration.dto.IntegrationConnectorResponse;
import net.nani.dairy.integration.dto.IntegrationEventResponse;
import net.nani.dairy.integration.dto.IntegrationIngestRequest;
import net.nani.dairy.integration.dto.IntegrationIngestResponse;
import net.nani.dairy.integration.dto.IntegrationMonitoringResponse;
import net.nani.dairy.integration.dto.RotateConnectorTokenResponse;
import net.nani.dairy.integration.dto.UpdateIntegrationConnectorStatusRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class IntegrationService {

    private final IntegrationConnectorRepository connectorRepository;
    private final IntegrationEventRepository eventRepository;
    private final List<IntegrationAdapter> adapters;
    private final ObjectMapper objectMapper;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public IntegrationConnectorResponse createConnector(CreateIntegrationConnectorRequest req, String actor) {
        String normalizedName = normalizeRequired(req.getName(), "Connector name is required");
        ConnectorType connectorType = req.getConnectorType();
        if (connectorType == null) {
            throw new IllegalArgumentException("connectorType is required");
        }

        String connectorKey = normalizeConnectorKey(req.getConnectorKey(), connectorType);
        if (connectorRepository.existsByConnectorKeyIgnoreCase(connectorKey)) {
            throw new IllegalArgumentException("Connector key already exists");
        }

        String provisioningToken = generateProvisioningToken();
        IntegrationConnectorEntity entity = IntegrationConnectorEntity.builder()
                .connectorId(buildConnectorId(connectorType))
                .connectorKey(connectorKey)
                .name(normalizedName)
                .connectorType(connectorType)
                .status(ConnectorStatus.ACTIVE)
                .tokenHash(passwordEncoder.encode(provisioningToken))
                .allowedSource(trimToNull(req.getAllowedSource()))
                .createdBy(trimToNull(actor))
                .build();

        IntegrationConnectorEntity saved = connectorRepository.save(entity);
        return toConnectorResponse(saved, provisioningToken);
    }

    public List<IntegrationConnectorResponse> listConnectors(ConnectorStatus status, ConnectorType connectorType) {
        List<IntegrationConnectorEntity> rows = status != null
                ? connectorRepository.findByStatusOrderByNameAsc(status)
                : connectorRepository.findAllByOrderByNameAsc();

        if (connectorType != null) {
            rows = rows.stream().filter(row -> row.getConnectorType() == connectorType).toList();
        }

        return rows.stream()
                .sorted(Comparator.comparing(IntegrationConnectorEntity::getName, String.CASE_INSENSITIVE_ORDER))
                .map(row -> toConnectorResponse(row, null))
                .toList();
    }

    @Transactional
    public IntegrationConnectorResponse updateConnectorStatus(
            String connectorId,
            UpdateIntegrationConnectorStatusRequest req,
            String actor
    ) {
        IntegrationConnectorEntity connector = connectorRepository.findById(connectorId)
                .orElseThrow(() -> new IllegalArgumentException("Connector not found"));

        if (req.getStatus() == null) {
            throw new IllegalArgumentException("status is required");
        }

        connector.setStatus(req.getStatus());
        if (req.getStatus() == ConnectorStatus.ACTIVE) {
            connector.setLastError(null);
        } else {
            connector.setLastError(trimToNull(req.getReason()));
        }

        IntegrationConnectorEntity saved = connectorRepository.save(connector);
        return toConnectorResponse(saved, null);
    }

    @Transactional
    public RotateConnectorTokenResponse rotateToken(String connectorId, String actor) {
        IntegrationConnectorEntity connector = connectorRepository.findById(connectorId)
                .orElseThrow(() -> new IllegalArgumentException("Connector not found"));

        String provisioningToken = generateProvisioningToken();
        connector.setTokenHash(passwordEncoder.encode(provisioningToken));
        connector.setLastError(null);

        IntegrationConnectorEntity saved = connectorRepository.save(connector);
        return RotateConnectorTokenResponse.builder()
                .connectorId(saved.getConnectorId())
                .connectorKey(saved.getConnectorKey())
                .provisioningToken(provisioningToken)
                .rotatedAt(OffsetDateTime.now())
                .build();
    }

    @Transactional
    public IntegrationIngestResponse ingest(
            String connectorKey,
            String connectorToken,
            IntegrationIngestRequest req,
            String sourceIp
    ) {
        String normalizedKey = normalizeRequired(connectorKey, "connectorKey is required").toLowerCase();
        String token = normalizeRequired(connectorToken, "Missing X-Connector-Token header");

        IntegrationConnectorEntity connector = connectorRepository.findByConnectorKeyIgnoreCase(normalizedKey)
                .orElseThrow(() -> new IllegalArgumentException("Connector not found"));

        if (connector.getStatus() != ConnectorStatus.ACTIVE) {
            throw new IllegalArgumentException("Connector is inactive");
        }

        if (!passwordEncoder.matches(token, connector.getTokenHash())) {
            throw new AccessDeniedException("Invalid connector token");
        }

        String allowedSource = trimToNull(connector.getAllowedSource());
        String source = trimToNull(sourceIp);
        if (allowedSource != null && source != null) {
            if (!source.equalsIgnoreCase(allowedSource) && !source.startsWith(allowedSource)) {
                throw new AccessDeniedException("Source IP not allowed for this connector");
            }
        }

        String rawPayloadJson = toJson(req.getPayload());

        IntegrationEventEntity event = IntegrationEventEntity.builder()
                .integrationEventId(buildEventId())
                .connectorId(connector.getConnectorId())
                .connectorKey(connector.getConnectorKey())
                .connectorType(connector.getConnectorType())
                .externalEventId(trimToNull(req.getExternalEventId()))
                .deviceId(trimToNull(req.getDeviceId()))
                .eventType(trimToNull(req.getEventType()))
                .sourceIp(source)
                .rawPayloadJson(rawPayloadJson)
                .normalizedPayloadJson(null)
                .occurredAt(req.getOccurredAt())
                .receivedAt(OffsetDateTime.now())
                .processedAt(null)
                .status(IntegrationIngestStatus.RECEIVED)
                .errorMessage(null)
                .build();

        event = eventRepository.save(event);

        try {
            IntegrationAdapter adapter = resolveAdapter(connector.getConnectorType());
            NormalizedIntegrationData normalized = adapter.normalize(req);

            event.setEventType(trimToNull(normalized.getEventType()));
            event.setDeviceId(trimToNull(normalized.getDeviceId()));
            event.setNormalizedPayloadJson(toJson(normalized.getNormalizedPayload()));
            event.setStatus(IntegrationIngestStatus.NORMALIZED);
            event.setErrorMessage(null);
            event.setProcessedAt(OffsetDateTime.now());

            connector.setLastSeenAt(OffsetDateTime.now());
            connector.setLastError(null);
            connectorRepository.save(connector);

            IntegrationEventEntity saved = eventRepository.save(event);
            return toIngestResponse(saved);
        } catch (Exception e) {
            String error = summarizeError(e);
            event.setStatus(IntegrationIngestStatus.FAILED);
            event.setErrorMessage(error);
            event.setProcessedAt(OffsetDateTime.now());

            connector.setLastSeenAt(OffsetDateTime.now());
            connector.setLastError(error);
            connectorRepository.save(connector);

            IntegrationEventEntity saved = eventRepository.save(event);
            return toIngestResponse(saved);
        }
    }

    public List<IntegrationEventResponse> listEvents(
            String connectorId,
            IntegrationIngestStatus status,
            Integer sinceHours,
            Integer limit
    ) {
        int safeLimit = limit == null ? 200 : Math.max(1, Math.min(limit, 500));
        int windowHours = sinceHours == null ? 72 : Math.max(1, Math.min(sinceHours, 24 * 30));
        OffsetDateTime since = OffsetDateTime.now().minusHours(windowHours);

        List<IntegrationEventEntity> rows = trimToNull(connectorId) != null
                ? eventRepository.findByConnectorIdOrderByReceivedAtDesc(connectorId)
                : eventRepository.findTop500ByOrderByReceivedAtDesc();

        if (status != null) {
            rows = rows.stream().filter(row -> row.getStatus() == status).toList();
        }
        rows = rows.stream()
                .filter(row -> row.getReceivedAt() != null && !row.getReceivedAt().isBefore(since))
                .limit(safeLimit)
                .toList();

        return rows.stream().map(this::toEventResponse).toList();
    }

    public IntegrationMonitoringResponse monitoring(Integer windowHours) {
        int safeWindowHours = windowHours == null ? 24 : Math.max(1, Math.min(windowHours, 24 * 30));
        OffsetDateTime since = OffsetDateTime.now().minusHours(safeWindowHours);

        List<IntegrationConnectorEntity> connectors = connectorRepository.findAllByOrderByNameAsc();
        List<IntegrationConnectorMetricsResponse> connectorMetrics = connectors.stream()
                .map(connector -> IntegrationConnectorMetricsResponse.builder()
                        .connectorId(connector.getConnectorId())
                        .connectorKey(connector.getConnectorKey())
                        .name(connector.getName())
                        .connectorType(connector.getConnectorType())
                        .status(connector.getStatus())
                        .lastSeenAt(connector.getLastSeenAt())
                        .lastError(connector.getLastError())
                        .totalEvents(eventRepository.countByConnectorId(connector.getConnectorId()))
                        .normalizedEvents(eventRepository.countByConnectorIdAndStatus(
                                connector.getConnectorId(),
                                IntegrationIngestStatus.NORMALIZED
                        ))
                        .failedEvents(eventRepository.countByConnectorIdAndStatus(
                                connector.getConnectorId(),
                                IntegrationIngestStatus.FAILED
                        ))
                        .build())
                .toList();

        return IntegrationMonitoringResponse.builder()
                .generatedAt(OffsetDateTime.now())
                .totalEvents(eventRepository.count())
                .normalizedEvents(eventRepository.countByStatus(IntegrationIngestStatus.NORMALIZED))
                .failedEvents(eventRepository.countByStatus(IntegrationIngestStatus.FAILED))
                .last24hEvents(eventRepository.countByReceivedAtGreaterThanEqual(since))
                .last24hFailedEvents(eventRepository.countByStatusAndReceivedAtGreaterThanEqual(
                        IntegrationIngestStatus.FAILED,
                        since
                ))
                .activeConnectors(connectorRepository.countByStatus(ConnectorStatus.ACTIVE))
                .inactiveConnectors(connectorRepository.countByStatus(ConnectorStatus.INACTIVE))
                .connectors(connectorMetrics)
                .build();
    }

    private IntegrationAdapter resolveAdapter(ConnectorType connectorType) {
        for (IntegrationAdapter adapter : adapters) {
            if (adapter.type() == connectorType) {
                return adapter;
            }
        }
        throw new IllegalArgumentException("No adapter found for connector type " + connectorType);
    }

    private IntegrationConnectorResponse toConnectorResponse(IntegrationConnectorEntity row, String provisioningToken) {
        return IntegrationConnectorResponse.builder()
                .connectorId(row.getConnectorId())
                .connectorKey(row.getConnectorKey())
                .name(row.getName())
                .connectorType(row.getConnectorType())
                .status(row.getStatus())
                .allowedSource(row.getAllowedSource())
                .createdBy(row.getCreatedBy())
                .lastSeenAt(row.getLastSeenAt())
                .lastError(row.getLastError())
                .createdAt(row.getCreatedAt())
                .updatedAt(row.getUpdatedAt())
                .provisioningToken(provisioningToken)
                .build();
    }

    private IntegrationEventResponse toEventResponse(IntegrationEventEntity row) {
        return IntegrationEventResponse.builder()
                .integrationEventId(row.getIntegrationEventId())
                .connectorId(row.getConnectorId())
                .connectorKey(row.getConnectorKey())
                .connectorType(row.getConnectorType())
                .externalEventId(row.getExternalEventId())
                .deviceId(row.getDeviceId())
                .eventType(row.getEventType())
                .sourceIp(row.getSourceIp())
                .status(row.getStatus())
                .errorMessage(row.getErrorMessage())
                .occurredAt(row.getOccurredAt())
                .receivedAt(row.getReceivedAt())
                .processedAt(row.getProcessedAt())
                .rawPayloadJson(row.getRawPayloadJson())
                .normalizedPayloadJson(row.getNormalizedPayloadJson())
                .build();
    }

    private IntegrationIngestResponse toIngestResponse(IntegrationEventEntity row) {
        return IntegrationIngestResponse.builder()
                .integrationEventId(row.getIntegrationEventId())
                .connectorId(row.getConnectorId())
                .connectorKey(row.getConnectorKey())
                .eventType(row.getEventType())
                .deviceId(row.getDeviceId())
                .status(row.getStatus())
                .errorMessage(row.getErrorMessage())
                .receivedAt(row.getReceivedAt())
                .processedAt(row.getProcessedAt())
                .build();
    }

    private String buildConnectorId(ConnectorType connectorType) {
        return "CONN_" + connectorType.name() + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private String buildEventId() {
        return "IEVT_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    private String normalizeConnectorKey(String value, ConnectorType connectorType) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            normalized = connectorType.name().toLowerCase() + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 6);
        }
        normalized = normalized.toLowerCase().replaceAll("[^a-z0-9_-]", "-");
        normalized = normalized.replaceAll("-+", "-");
        if (normalized.length() > 100) {
            normalized = normalized.substring(0, 100);
        }
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("Invalid connector key");
        }
        return normalized;
    }

    private String generateProvisioningToken() {
        byte[] bytes = new byte[24];
        new SecureRandom().nextBytes(bytes);
        StringBuilder out = new StringBuilder();
        for (byte b : bytes) {
            out.append(String.format("%02x", b));
        }
        return out.toString();
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize payload", e);
        }
    }

    private String summarizeError(Throwable throwable) {
        if (throwable == null) {
            return "Unknown integration error";
        }
        String message = trimToNull(throwable.getMessage());
        String summary = message != null ? message : throwable.getClass().getSimpleName();
        if (summary.length() > 480) {
            summary = summary.substring(0, 480);
        }
        return summary;
    }

    private String normalizeRequired(String value, String message) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
