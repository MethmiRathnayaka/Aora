package lk.aora.equipmentmanagement.controller;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthTestController {

    @GetMapping("/api/v1/auth-test")
    public String testAuthentication(Authentication authentication) {
        return "Authenticated as: " + authentication.getName();
    }
}

