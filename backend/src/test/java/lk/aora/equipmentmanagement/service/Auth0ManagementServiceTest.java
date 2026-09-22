package lk.aora.equipmentmanagement.service;

import lk.aora.equipmentmanagement.dto.user.CreateUserRequest;
import lk.aora.equipmentmanagement.entity.Role;
import lk.aora.equipmentmanagement.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class Auth0ManagementServiceTest {
    @Test
    void duplicateAccountReturnsFriendlyMessage() {
        Auth0ManagementService service = new Auth0ManagementService();
        ReflectionTestUtils.setField(service, "domain", "example.auth0.com");
        RestTemplate client = (RestTemplate) ReflectionTestUtils.getField(service, "restTemplate");
        MockRestServiceServer server = MockRestServiceServer.bindTo(client).build();
        server.expect(requestTo("https://example.auth0.com/oauth/token"))
                .andRespond(withSuccess("{\"access_token\":\"test-token\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://example.auth0.com/api/v2/users"))
                .andRespond(withStatus(HttpStatus.CONFLICT).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"message\":\"The user already exists.\"}"));
        BusinessRuleException error = assertThrows(BusinessRuleException.class, () -> service.createUser(
                new CreateUserRequest(null, "Test", "User", "test@example.com", null, Role.MANAGER, null)));
        assertEquals("An account with this email address already exists. Check the Users page or use a different email address.", error.getMessage());
        server.verify();
    }
}
