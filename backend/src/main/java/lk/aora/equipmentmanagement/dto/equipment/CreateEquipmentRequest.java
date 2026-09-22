package lk.aora.equipmentmanagement.dto.equipment;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import lk.aora.equipmentmanagement.entity.EquipmentCondition;

public record CreateEquipmentRequest(
        @NotNull @Positive Long equipmentTypeId,
        @NotBlank String brand,
        String model,
        @NotBlank String serialNumber,
        @NotNull EquipmentCondition condition,
        @NotNull @PastOrPresent LocalDate purchaseDate) {
}