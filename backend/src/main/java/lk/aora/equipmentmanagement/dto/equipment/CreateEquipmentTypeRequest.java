package lk.aora.equipmentmanagement.dto.equipment;

import jakarta.validation.constraints.NotBlank;

public record CreateEquipmentTypeRequest(@NotBlank @jakarta.validation.constraints.Size(max = 255) String name, @jakarta.validation.constraints.Size(max = 10) String codePrefix, String description) {
}
