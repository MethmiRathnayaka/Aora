package lk.aora.equipmentmanagement.service;

import lk.aora.equipmentmanagement.dto.user.CreateUserRequest;
import lk.aora.equipmentmanagement.exception.BusinessRuleException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class Auth0ManagementService {

    public record Auth0CreatedUser(String userId, String temporaryPassword) {
    }

    @Value("${auth0.domain}")
    private String domain;

    @Value("${auth0.mgmtClientId}")
    private String clientId;

    @Value("${auth0.mgmtClientSecret}")
    private String clientSecret;

    @Value("${auth0.connection:Username-Password-Authentication}")
    private String connection;

    @Value("${auth0.mgmtAudience:https://${auth0.domain}/api/v2/}")
    private String mgmtAudience;

    private final RestTemplate restTemplate = new RestTemplate();

    private String getMgmtToken() {
        String tokenUrl = "https://" + domain + "/oauth/token";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> body = new HashMap<>();
        body.put("client_id", clientId);
        body.put("client_secret", clientSecret);
        body.put("audience", mgmtAudience == null || mgmtAudience.isBlank() ? "https://" + domain + "/api/v2/" : mgmtAudience);
        body.put("grant_type", "client_credentials");

        HttpEntity<Map<String, Object>> req = new HttpEntity<>(body, headers);
        try {
            ResponseEntity<Map> resp = restTemplate.postForEntity(tokenUrl, req, Map.class);
            if (resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null) {
                Object at = resp.getBody().get("access_token");
                return at == null ? null : at.toString();
            }
            return null;
        } catch (HttpClientErrorException.Forbidden ex) {
            throw new BusinessRuleException("Auth0 Management API access denied. Create a Machine-to-Machine application grant for the Auth0 Management API and authorize the required scopes (for example create:users, read:users). Check that auth0.mgmtClientId/auth0.mgmtClientSecret belong to that M2M app.");
        }
    }

    public Auth0CreatedUser createUser(CreateUserRequest req) {
        String token = getMgmtToken();
        if (token == null) throw new RuntimeException("Failed to obtain Auth0 management token");

        String url = "https://" + domain + "/api/v2/users";
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);

        // generate a temporary password
        String tempPassword = generateTempPassword();

        Map<String, Object> body = new HashMap<>();
        body.put("connection", connection);
        body.put("email", req.email());
        body.put("password", tempPassword);
        body.put("email_verified", true);
        body.put("given_name", req.firstName());
        body.put("family_name", req.lastName());

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        ResponseEntity<Map> resp;
        try {
            resp = restTemplate.postForEntity(url, entity, Map.class);
        } catch (HttpClientErrorException.Conflict ex) {
            throw new BusinessRuleException("An account with this email address already exists. Check the Users page or use a different email address.");
        }
        if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
            throw new RuntimeException("Failed to create Auth0 user: " + resp.getStatusCode());
        }
        Object userId = resp.getBody().get("user_id");
        if (userId == null) throw new RuntimeException("Auth0 response missing user_id");
        return new Auth0CreatedUser(userId.toString(), tempPassword);
    }

    private String generateTempPassword() {
        // simple random password: UUID + timestamp (you may replace with stronger generator)
        return "Tmp@" + UUID.randomUUID().toString().replaceAll("-", "").substring(0, 12) + Instant.now().getEpochSecond();
    }
}
