package net.nani.dairy.integration.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RotateConnectorTokenResponse {
    private String connectorId;
    private String connectorKey;
    private String provisioningToken;
    private OffsetDateTime rotatedAt;
}
