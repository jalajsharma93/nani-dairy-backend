package net.nani.dairy.tasks;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TaskApiRoleSecurityTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @LocalServerPort
    private int port;

    @Test
    void workerCanCreateTaskAndUpdateOwnStatus() throws Exception {
        String workerToken = login("worker", "worker123");

        HttpResponse<String> createResponse = request(
                "POST",
                "/api/tasks",
                """
                        {
                          "taskDate": "%s",
                          "taskType": "OTHER",
                          "title": "Worker checklist task",
                          "details": "Clean milking area",
                          "assignedRole": "WORKER",
                          "priority": "MEDIUM"
                        }
                        """.formatted(LocalDate.now()),
                workerToken
        );
        assertEquals(HttpStatus.OK.value(), createResponse.statusCode());
        String taskId = objectMapper.readTree(createResponse.body()).get("taskId").asText();

        HttpResponse<String> statusResponse = request(
                "POST",
                "/api/tasks/" + taskId + "/status",
                """
                        {
                          "status": "DONE"
                        }
                        """,
                workerToken
        );
        assertEquals(HttpStatus.OK.value(), statusResponse.statusCode());
        JsonNode statusBody = objectMapper.readTree(statusResponse.body());
        assertEquals("DONE", statusBody.get("status").asText());
    }

    @Test
    void managerCanUpdateTask() throws Exception {
        String adminToken = login("admin", "admin123");
        String managerToken = login("manager", "manager123");
        String taskId = createTaskAs(
                adminToken,
                """
                        {
                          "taskDate": "%s",
                          "taskType": "FARM",
                          "title": "Initial manager task",
                          "details": "Initial details",
                          "assignedRole": "WORKER",
                          "assignedToUsername": "worker",
                          "priority": "MEDIUM"
                        }
                        """.formatted(LocalDate.now())
        );

        HttpResponse<String> updateResponse = request(
                "PUT",
                "/api/tasks/" + taskId,
                """
                        {
                          "taskDate": "%s",
                          "taskType": "FARM",
                          "title": "Updated by manager",
                          "details": "Updated details",
                          "assignedRole": "WORKER",
                          "assignedToUsername": "worker",
                          "priority": "HIGH",
                          "status": "IN_PROGRESS"
                        }
                        """.formatted(LocalDate.now()),
                managerToken
        );
        assertEquals(HttpStatus.OK.value(), updateResponse.statusCode());
        JsonNode body = objectMapper.readTree(updateResponse.body());
        assertEquals("Updated by manager", body.get("title").asText());
        assertEquals("IN_PROGRESS", body.get("status").asText());
    }

    @Test
    void workerAndDeliveryCannotUseUpdateEndpoint() throws Exception {
        String adminToken = login("admin", "admin123");
        String workerToken = login("worker", "worker123");
        String deliveryToken = login("delivery", "delivery123");

        String taskId = createTaskAs(
                adminToken,
                """
                        {
                          "taskDate": "%s",
                          "taskType": "OTHER",
                          "title": "Protected update task",
                          "assignedRole": "WORKER",
                          "assignedToUsername": "worker",
                          "priority": "MEDIUM"
                        }
                        """.formatted(LocalDate.now())
        );

        String updatePayload = """
                {
                  "taskDate": "%s",
                  "taskType": "OTHER",
                  "title": "Forbidden update",
                  "details": "forbidden",
                  "assignedRole": "WORKER",
                  "assignedToUsername": "worker",
                  "priority": "LOW",
                  "status": "PENDING"
                }
                """.formatted(LocalDate.now());

        HttpResponse<String> workerUpdate = request("PUT", "/api/tasks/" + taskId, updatePayload, workerToken);
        HttpResponse<String> deliveryUpdate = request("PUT", "/api/tasks/" + taskId, updatePayload, deliveryToken);

        assertEquals(HttpStatus.FORBIDDEN.value(), workerUpdate.statusCode());
        assertEquals(HttpStatus.FORBIDDEN.value(), deliveryUpdate.statusCode());
    }

    @Test
    void adminCannotEditDeliveryLinkedTaskViaGenericUpdate() throws Exception {
        String adminToken = login("admin", "admin123");
        String taskId = createTaskAs(
                adminToken,
                """
                        {
                          "taskDate": "%s",
                          "taskType": "DELIVERY",
                          "title": "Delivery linked task",
                          "details": "Linked delivery task",
                          "assignedRole": "DELIVERY",
                          "assignedToUsername": "delivery",
                          "priority": "MEDIUM",
                          "sourceRefId": "DELIVERY_TASK:DT_TEST_001"
                        }
                        """.formatted(LocalDate.now())
        );

        HttpResponse<String> updateResponse = request(
                "PUT",
                "/api/tasks/" + taskId,
                """
                        {
                          "taskDate": "%s",
                          "taskType": "DELIVERY",
                          "title": "Should fail",
                          "details": "Should fail",
                          "assignedRole": "DELIVERY",
                          "assignedToUsername": "delivery",
                          "priority": "HIGH",
                          "status": "PENDING",
                          "sourceRefId": "DELIVERY_TASK:DT_TEST_001"
                        }
                        """.formatted(LocalDate.now()),
                adminToken
        );

        assertEquals(HttpStatus.FORBIDDEN.value(), updateResponse.statusCode());
    }

    private String createTaskAs(String token, String payload) throws Exception {
        HttpResponse<String> response = request("POST", "/api/tasks", payload, token);
        assertEquals(HttpStatus.OK.value(), response.statusCode());
        JsonNode body = objectMapper.readTree(response.body());
        assertNotNull(body.get("taskId"));
        return body.get("taskId").asText();
    }

    private String login(String username, String password) throws Exception {
        String payload = """
                {
                  "username": "%s",
                  "password": "%s"
                }
                """.formatted(username, password);
        HttpResponse<String> response = request("POST", "/api/auth/login", payload, null);
        assertEquals(HttpStatus.OK.value(), response.statusCode());
        JsonNode node = objectMapper.readTree(response.body());
        assertNotNull(node.get("token"));
        return node.get("token").asText();
    }

    private HttpResponse<String> request(String method, String path, String body, String bearerToken) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json");

        if (bearerToken != null && !bearerToken.isBlank()) {
            builder.header("Authorization", "Bearer " + bearerToken);
        }

        if ("POST".equals(method)) {
            builder.POST(HttpRequest.BodyPublishers.ofString(body == null ? "" : body));
        } else if ("PUT".equals(method)) {
            builder.PUT(HttpRequest.BodyPublishers.ofString(body == null ? "" : body));
        } else {
            builder.GET();
        }

        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
}
