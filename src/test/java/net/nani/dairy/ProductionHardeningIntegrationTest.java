package net.nani.dairy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ProductionHardeningIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void actuatorEndpointSecurityPolicy_shouldExposeHealthPubliclyAndProtectMetrics() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());

        MvcResult infoEndpoint = mockMvc.perform(get("/actuator/info"))
                .andReturn();
        int infoStatus = infoEndpoint.getResponse().getStatus();
        assertThat(infoStatus).isIn(200, 404);

        MvcResult protectedEndpoint = mockMvc.perform(get("/actuator/prometheus"))
                .andReturn();
        int status = protectedEndpoint.getResponse().getStatus();
        assertThat(status).isIn(401, 403);
    }

    @Test
    void integrationConnectorIngestFlow_shouldCreateAndNormalizeEvents() throws Exception {
        String adminToken = loginAndGetToken("admin", "admin123");
        String connectorKey = "hardening-" + UUID.randomUUID().toString().substring(0, 8);

        Map<String, Object> createConnector = Map.of(
                "name", "Hardening Custom Connector",
                "connectorType", "CUSTOM",
                "connectorKey", connectorKey
        );

        MvcResult createResult = mockMvc.perform(post("/api/integrations/connectors")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createConnector)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connectorId").exists())
                .andExpect(jsonPath("$.provisioningToken").exists())
                .andReturn();

        JsonNode connector = objectMapper.readTree(createResult.getResponse().getContentAsString());
        String connectorId = connector.get("connectorId").asText();
        String provisioningToken = connector.get("provisioningToken").asText();

        Map<String, Object> ingestPayload = Map.of(
                "externalEventId", "evt-" + UUID.randomUUID(),
                "deviceId", "device-001",
                "eventType", "SAMPLE_EVENT",
                "payload", Map.of("weightKg", 505.6, "tag", "RFID-12345")
        );

        mockMvc.perform(post("/api/integrations/ingest/{connectorKey}", connectorKey)
                        .header("X-Connector-Token", provisioningToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ingestPayload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NORMALIZED"));

        mockMvc.perform(get("/api/integrations/events")
                        .param("connectorId", connectorId)
                        .param("limit", "10")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].connectorId").value(connectorId))
                .andExpect(jsonPath("$[0].status").value("NORMALIZED"));

        mockMvc.perform(get("/api/integrations/monitoring")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalEvents").isNumber())
                .andExpect(jsonPath("$.connectors").isArray());
    }

    @Test
    void approvalWorkflow_shouldEnforceRequiredApproverRole() throws Exception {
        String workerToken = loginAndGetToken("worker", "worker123");
        String managerToken = loginAndGetToken("manager", "manager123");
        String adminToken = loginAndGetToken("admin", "admin123");

        Map<String, Object> approvalRequest = Map.of(
                "module", "sales",
                "actionType", "price_edit",
                "targetRefId", "SALE_TEST_01",
                "requiredApproverRole", "ADMIN",
                "requestReason", "Need approval for exceptional price correction",
                "requestPayloadJson", "{\"priceDelta\":5.0}"
        );

        MvcResult requestResult = mockMvc.perform(post("/api/governance/approvals/request")
                        .header("Authorization", bearer(workerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(approvalRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.requestedByUsername").value("worker"))
                .andReturn();

        String approvalRequestId = objectMapper.readTree(requestResult.getResponse().getContentAsString())
                .get("approvalRequestId")
                .asText();

        Map<String, Object> approveBody = Map.of("decisionNote", "Approved after manual verification");

        mockMvc.perform(post("/api/governance/approvals/{approvalRequestId}/approve", approvalRequestId)
                        .header("Authorization", bearer(managerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(approveBody)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/governance/approvals/{approvalRequestId}/approve", approvalRequestId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(approveBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.approvedByUsername").value("admin"));

        mockMvc.perform(get("/api/governance/audits")
                        .param("module", "SALES")
                        .param("limit", "20")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    private String loginAndGetToken(String username, String password) throws Exception {
        Map<String, Object> login = Map.of(
                "username", username,
                "password", password
        );

        MvcResult response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists())
                .andReturn();

        JsonNode payload = objectMapper.readTree(response.getResponse().getContentAsString());
        return payload.get("token").asText();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
