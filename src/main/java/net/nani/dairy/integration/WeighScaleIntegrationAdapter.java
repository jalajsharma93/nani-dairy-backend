package net.nani.dairy.integration;

import net.nani.dairy.integration.dto.IntegrationIngestRequest;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class WeighScaleIntegrationAdapter implements IntegrationAdapter {

    @Override
    public ConnectorType type() {
        return ConnectorType.WEIGH_SCALE;
    }

    @Override
    public NormalizedIntegrationData normalize(IntegrationIngestRequest request) {
        Map<String, Object> payload = request.getPayload();

        Double weightKg = numberFrom(payload.get("weightKg"));
        if (weightKg == null) {
            weightKg = numberFrom(payload.get("weight"));
        }
        if (weightKg == null) {
            throw new IllegalArgumentException("Weigh-scale payload must include weightKg/weight");
        }

        String deviceId = firstNonBlank(
                request.getDeviceId(),
                stringFrom(payload.get("deviceId")),
                stringFrom(payload.get("scaleId")),
                "weigh-scale"
        );
        String eventType = firstNonBlank(request.getEventType(), "WEIGHT_READING");

        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("weightKg", weightKg);
        normalized.put("unit", firstNonBlank(stringFrom(payload.get("unit")), "KG"));
        normalized.put("animalTag", stringFrom(payload.get("animalTag")));
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

    private Double numberFrom(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value).trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
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
