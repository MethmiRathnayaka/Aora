package lk.aora.equipmentmanagement.dto.maintenance;

import java.math.BigDecimal;

import lk.aora.equipmentmanagement.entity.EquipmentCondition;
import lk.aora.equipmentmanagement.entity.MaintenanceStatus;

public record UpdateMaintenanceRequest(MaintenanceStatus status, EquipmentCondition conditionAfter, BigDecimal cost, String notes) {
}