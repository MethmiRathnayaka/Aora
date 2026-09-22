package lk.aora.equipmentmanagement.dto.user;

public record UserCreateResponse(UserSummaryDto user, String temporaryPassword) {
}