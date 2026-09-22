package lk.aora.equipmentmanagement.dto.equipment;

import java.time.LocalDate;

import lk.aora.equipmentmanagement.entity.EquipmentCondition;
import lk.aora.equipmentmanagement.entity.EquipmentStatus;

public record UpdateEquipmentRequest(
        String brand,
        String model,
        String serialNumber,
        EquipmentCondition condition,
        LocalDate purchaseDate,
        EquipmentStatus status) {
}