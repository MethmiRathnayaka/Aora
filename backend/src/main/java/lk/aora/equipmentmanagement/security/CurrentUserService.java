
package lk.aora.equipmentmanagement.security;

import java.util.Optional;

import lk.aora.equipmentmanagement.entity.AppUser;
import lk.aora.equipmentmanagement.exception.UnauthorizedException;
import lk.aora.equipmentmanagement.repository.AppUserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

@Service
public class CurrentUserService {

    private final AppUserRepository appUserRepository;

    public CurrentUserService(AppUserRepository appUserRepository) {
        this.appUserRepository = appUserRepository;
    }

    public Optional<AppUser> getCurrentUser() {

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null ||
                !(authentication.getPrincipal() instanceof Jwt jwt)) {
            return Optional.empty();
        }

        // Auth0 stores the user's unique identifier in the standard "sub" claim
        String authUserId = jwt.getSubject();

        if (authUserId == null || authUserId.isBlank()) {
            return Optional.empty();
        }

        return appUserRepository.findByAuthUserId(authUserId).filter(user -> user.isActive() && !user.isDeleted());
    }

    public AppUser requireCurrentUser() {
        return getCurrentUser()
                .orElseThrow(() ->
                        new UnauthorizedException(
                                "Authenticated user was not found in the application database."
                        )
                );
    }
}
