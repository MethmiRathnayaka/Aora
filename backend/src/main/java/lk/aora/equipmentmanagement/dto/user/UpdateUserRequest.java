package lk.aora.equipmentmanagement.dto.user;

import lk.aora.equipmentmanagement.entity.Role;

public record UpdateUserRequest(String firstName, String lastName, String phone, Boolean isActive, Role role) {
}