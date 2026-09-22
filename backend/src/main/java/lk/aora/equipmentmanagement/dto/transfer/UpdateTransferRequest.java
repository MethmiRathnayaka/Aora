package lk.aora.equipmentmanagement.dto.transfer;

import java.util.List;

import lk.aora.equipmentmanagement.entity.EquipmentCondition;
import lk.aora.equipmentmanagement.entity.TransferStatus;

public record UpdateTransferRequest(TransferStatus status, TransferEquipmentUpdateRequest equipment, String notes, java.math.BigDecimal unitPrice) {

    public record TransferEquipmentUpdateRequest(String equipmentCode, EquipmentCondition conditionAfter) {
    }
}