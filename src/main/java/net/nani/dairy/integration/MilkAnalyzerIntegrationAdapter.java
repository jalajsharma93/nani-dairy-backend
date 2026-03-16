package net.nani.dairy.integration;

import net.nani.dairy.integration.dto.IntegrationIngestRequest;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class MilkAnalyzerIntegrationAdapter implements IntegrationAdapter {

    @Override
    public ConnectorType type() {
        return ConnectorType.MILK_ANALYZER;
    }

    @Override
    public NormalizedIntegrationData normalize(IntegrationIngestRequest request) {
        Map<String, Object> payload = request.getPayload();

        Double fat = numberFrom(payload.get("fat"));
        Double snf = numberFrom(payload.get("snf"));
        if (fat == null || snf == null) {
            throw new IllegalArgumentException("Milk analyzer payload must include fat and snf");
        }

        String deviceId = firstNonBlank(
                request.getDeviceId(),
                stringFrom(payload.get("deviceId")),
                stringFrom(payload.get("machineId")),
                "milk-analyzer"
        );
        String eventType = firstNonBlank(request.getEventType(), "MILK_ANALYZER_READING");

        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("fat", fat);
        normalized.put("snf", snf);
        normalized.put("temperature", numberFrom(payload.get("temperature")));
        normalized.put("lactometer", numberFrom(payload.get("lactometer")));
        normalized.put("sampleId", stringFrom(payload.get("sampleId")));
        normalized.put("antibioticResidue", booleanFrom(payload.get("antibioticResidue")));
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

    private Boolean booleanFrom(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        String text = String.valueOf(value).trim().toLowerCase();
        if ("true".equals(text) || "yes".equals(text) || "1".equals(text)) {
            return true;
        }
        if ("false".equals(text) || "no".equals(text) || "0".equals(text)) {
            return false;
        }
        return null;
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
