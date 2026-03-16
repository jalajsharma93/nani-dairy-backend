package net.nani.dairy.integration;

import net.nani.dairy.integration.dto.IntegrationIngestRequest;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class RfidIntegrationAdapter implements IntegrationAdapter {

    @Override
    public ConnectorType type() {
        return ConnectorType.RFID;
    }

    @Override
    public NormalizedIntegrationData normalize(IntegrationIngestRequest request) {
        Map<String, Object> payload = request.getPayload();
        String tagId = stringFrom(payload.get("tagId"));
        if (tagId == null) {
            tagId = stringFrom(payload.get("tag"));
        }
        if (tagId == null) {
            throw new IllegalArgumentException("RFID payload must include tagId/tag");
        }

        String deviceId = firstNonBlank(
                request.getDeviceId(),
                stringFrom(payload.get("deviceId")),
                "rfid-device"
        );

        String eventType = firstNonBlank(request.getEventType(), "RFID_SCAN");

        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("tagId", tagId);
        normalized.put("gate", firstNonBlank(stringFrom(payload.get("gate")), stringFrom(payload.get("reader")), "unknown"));
        normalized.put("scanDirection", firstNonBlank(stringFrom(payload.get("direction")), "UNKNOWN"));
        normalized.put("occurredAt", request.getOccurredAt());

        return NormalizedIntegrationData.builder()
                .eventType(eventType)
                .deviceId(deviceId)
                .normalizedPayload(normalized)
                .build();
    }

    private String stringFrom(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }
}
