package lk.aora.equipmentmanagement.dto.equipment;

import java.time.Instant;
import java.time.LocalDate;

import lk.aora.equipmentmanagement.dto.worksite.WorksiteSummaryDto;
import lk.aora.equipmentmanagement.entity.EquipmentCondition;
import lk.aora.equipmentmanagement.entity.EquipmentStatus;

public record EquipmentSummaryDto(
        Long equipmentId,
        String equipmentCode,
        EquipmentTypeDto equipmentType,
        String brand,
        String model,
        String serialNumber,
        EquipmentStatus status,
        EquipmentCondition condition,
        WorksiteSummaryDto currentWorksite,
        LocalDate purchaseDate,
        Instant createdAt,
        Instant updatedAt) {
}