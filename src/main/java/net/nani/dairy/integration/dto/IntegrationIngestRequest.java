package net.nani.dairy.integration.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IntegrationIngestRequest {
    private String externalEventId;
    private String deviceId;
    private String eventType;
    private OffsetDateTime occurredAt;

    @NotNull
    private Map<String, Object> payload;
}
