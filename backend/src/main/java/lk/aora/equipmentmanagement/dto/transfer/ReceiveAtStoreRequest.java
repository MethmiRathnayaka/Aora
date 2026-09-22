package lk.aora.equipmentmanagement.dto.transfer;

import jakarta.validation.constraints.*;
import lk.aora.equipmentmanagement.entity.EquipmentCondition;

public record ReceiveAtStoreRequest(
        @NotBlank String equipmentCode,
        @NotNull @Positive Long fromWorksiteId,
        @NotBlank String deliveryPersonName,
        String deliveryPersonPhone,
        @NotNull EquipmentCondition conditionAfter,
        @NotNull @AssertTrue Boolean receiptConfirmed,
        String notes,
        @NotNull @DecimalMin("0.00") @Digits(integer = 12, fraction = 2) java.math.BigDecimal unitPrice) {
}
