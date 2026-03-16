package net.nani.dairy.integration;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.nani.dairy.integration.dto.CreateIntegrationConnectorRequest;
import net.nani.dairy.integration.dto.IntegrationConnectorResponse;
import net.nani.dairy.integration.dto.IntegrationEventResponse;
import net.nani.dairy.integration.dto.IntegrationIngestRequest;
import net.nani.dairy.integration.dto.IntegrationIngestResponse;
import net.nani.dairy.integration.dto.IntegrationMonitoringResponse;
import net.nani.dairy.integration.dto.RotateConnectorTokenResponse;
import net.nani.dairy.integration.dto.UpdateIntegrationConnectorStatusRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/integrations")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class IntegrationController {

    private final IntegrationService integrationService;

    @GetMapping("/connectors")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public List<IntegrationConnectorResponse> listConnectors(
            @RequestParam(required = false) ConnectorStatus status,
            @RequestParam(required = false) ConnectorType connectorType
    ) {
        return integrationService.listConnectors(status, connectorType);
    }

    @PostMapping("/connectors")
    @PreAuthorize("hasRole('ADMIN')")
    public IntegrationConnectorResponse createConnector(
            @Valid @RequestBody CreateIntegrationConnectorRequest req,
            Authentication authentication
    ) {
        return integrationService.createConnector(req, actor(authentication));
    }

    @PostMapping("/connectors/{connectorId}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public IntegrationConnectorResponse updateConnectorStatus(
            @PathVariable String connectorId,
            @Valid @RequestBody UpdateIntegrationConnectorStatusRequest req,
            Authentication authentication
    ) {
        return integrationService.updateConnectorStatus(connectorId, req, actor(authentication));
    }

    @PostMapping("/connectors/{connectorId}/rotate-token")
    @PreAuthorize("hasRole('ADMIN')")
    public RotateConnectorTokenResponse rotateToken(
            @PathVariable String connectorId,
            Authentication authentication
    ) {
        return integrationService.rotateToken(connectorId, actor(authentication));
    }

    @GetMapping("/events")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public List<IntegrationEventResponse> listEvents(
            @RequestParam(required = false) String connectorId,
            @RequestParam(required = false) IntegrationIngestStatus status,
            @RequestParam(required = false) Integer sinceHours,
            @RequestParam(required = false) Integer limit
    ) {
        return integrationService.listEvents(connectorId, status, sinceHours, limit);
    }

    @GetMapping("/monitoring")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public IntegrationMonitoringResponse monitoring(@RequestParam(required = false) Integer windowHours) {
        return integrationService.monitoring(windowHours);
    }

    @PostMapping("/ingest/{connectorKey}")
    public IntegrationIngestResponse ingest(
            @PathVariable String connectorKey,
            @RequestHeader(name = "X-Connector-Token", required = false) String connectorToken,
            @Valid @RequestBody IntegrationIngestRequest req,
            HttpServletRequest servletRequest
    ) {
        return integrationService.ingest(connectorKey, connectorToken, req, servletRequest.getRemoteAddr());
    }

    private String actor(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new IllegalArgumentException("Unauthorized");
        }
        return authentication.getName();
    }
}
