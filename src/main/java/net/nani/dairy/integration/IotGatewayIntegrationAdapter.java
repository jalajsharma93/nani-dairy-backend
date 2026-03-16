package net.nani.dairy.integration;

import net.nani.dairy.integration.dto.IntegrationIngestRequest;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class IotGatewayIntegrationAdapter implements IntegrationAdapter {

    @Override
    public ConnectorType type() {
        return ConnectorType.IOT_GATEWAY;
    }

    @Override
    public NormalizedIntegrationData normalize(IntegrationIngestRequest request) {
        Map<String, Object> payload = request.getPayload();
        if (payload == null || payload.isEmpty()) {
            throw new IllegalArgumentException("IoT payload cannot be empty");
        }

        String deviceId = firstNonBlank(
                request.getDeviceId(),
                stringFrom(payload.get("deviceId")),
                stringFrom(payload.get("gatewayId")),
                "iot-gateway"
        );
        String eventType = firstNonBlank(request.getEventType(), "IOT_TELEMETRY");

        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("payload", payload);
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
