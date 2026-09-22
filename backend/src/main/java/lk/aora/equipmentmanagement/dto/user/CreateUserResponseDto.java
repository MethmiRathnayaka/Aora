package lk.aora.equipmentmanagement.dto.user;

import lk.aora.equipmentmanagement.dto.worksite.WorksiteSummaryDto;
import lk.aora.equipmentmanagement.entity.Role;

public record CreateUserResponseDto(
        Long userId,
        String authUserId,
        String firstName,
        String lastName,
        String email,
        String phone,
        Role role,
        boolean isActive,
        WorksiteSummaryDto currentWorksite,
        String temporaryPassword) {
}