package lk.aora.equipmentmanagement.security;

import lk.aora.equipmentmanagement.entity.AppUser;
import lk.aora.equipmentmanagement.entity.Role;

public record CurrentUser(Long userId, String authUserId, Role role, AppUser user) {
}