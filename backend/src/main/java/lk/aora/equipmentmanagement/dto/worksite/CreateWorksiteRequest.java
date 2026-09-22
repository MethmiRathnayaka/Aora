package lk.aora.equipmentmanagement.dto.worksite;

import jakarta.validation.constraints.NotBlank;

public record CreateWorksiteRequest(@NotBlank String name, @NotBlank String projectCode, String address) {
}