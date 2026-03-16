package net.nani.dairy.integration.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import net.nani.dairy.integration.ConnectorStatus;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateIntegrationConnectorStatusRequest {

    @NotNull
    private ConnectorStatus status;

    private String reason;
}
