package net.nani.dairy.auth;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthUserAdminSecurityTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @LocalServerPort
    private int port;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Test
    void adminCanListUsers() throws Exception {
        String token = login("admin", "admin123");

        HttpResponse<String> response = request("GET", "/api/auth/users", null, token);
        assertEquals(HttpStatus.OK.value(), response.statusCode());
    }

    @Test
    void managerCannotListUsers() throws Exception {
        String token = login("manager", "manager123");

        HttpResponse<String> response = request("GET", "/api/auth/users", null, token);
        assertEquals(HttpStatus.FORBIDDEN.value(), response.statusCode());
    }

    @Test
    void adminCanCreateUser() throws Exception {
        String token = login("admin", "admin123");
        String username = "test_user_" + System.currentTimeMillis();

        String payload = """
                {
                  "username": "%s",
                  "fullName": "Test User",
                  "role": "WORKER",
                  "password": "test1234",
                  "active": true
                }
                """.formatted(username);
        HttpResponse<String> response = request("POST", "/api/auth/users", payload, token);
        assertEquals(HttpStatus.OK.value(), response.statusCode());
        JsonNode body = objectMapper.readTree(response.body());
        assertEquals(username, body.get("username").asText());
        assertEquals("WORKER", body.get("role").asText());
    }

    @Test
    void workerCannotCreateUser() throws Exception {
        String token = login("worker", "worker123");

        String payload = """
                {
                  "username": "worker_forbidden_user",
                  "fullName": "Forbidden User",
                  "role": "WORKER",
                  "password": "test1234",
                  "active": true
                }
                """;
        HttpResponse<String> response = request("POST", "/api/auth/users", payload, token);
        assertEquals(HttpStatus.FORBIDDEN.value(), response.statusCode());
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
        } else {
            builder.GET();
        }

        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
}
