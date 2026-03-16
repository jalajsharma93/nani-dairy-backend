package net.nani.dairy.integration.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import net.nani.dairy.integration.ConnectorType;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateIntegrationConnectorRequest {

    @NotBlank
    private String name;

    @NotNull
    private ConnectorType connectorType;

    private String connectorKey;
    private String allowedSource;
}
