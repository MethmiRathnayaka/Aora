package lk.aora.equipmentmanagement.dto.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lk.aora.equipmentmanagement.entity.Role;

public record CreateUserRequest(
        String authUserId,
        @NotBlank String firstName,
        @NotBlank String lastName,
        @Email @NotBlank String email,
        String phone,
        @NotNull Role role,
        Long worksiteId) {
}
