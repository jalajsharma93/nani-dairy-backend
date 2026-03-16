package net.nani.dairy.integration;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NormalizedIntegrationData {
    private String eventType;
    private String deviceId;
    private Map<String, Object> normalizedPayload;
}
