package lk.aora.equipmentmanagement.dto.maintenance;

import jakarta.validation.constraints.NotBlank;

public record CreateMaintenanceRequest(@NotBlank String equipmentCode, @NotBlank String reason, String description) {
}