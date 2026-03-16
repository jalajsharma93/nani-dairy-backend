package net.nani.dairy.integration;

import net.nani.dairy.integration.dto.IntegrationIngestRequest;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class CustomIntegrationAdapter implements IntegrationAdapter {

    @Override
    public ConnectorType type() {
        return ConnectorType.CUSTOM;
    }

    @Override
    public NormalizedIntegrationData normalize(IntegrationIngestRequest request) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("payload", request.getPayload());
        normalized.put("occurredAt", request.getOccurredAt());

        return NormalizedIntegrationData.builder()
                .eventType(request.getEventType() != null ? request.getEventType() : "CUSTOM_EVENT")
                .deviceId(request.getDeviceId() != null ? request.getDeviceId() : "custom-device")
                .normalizedPayload(normalized)
                .build();
    }
}
