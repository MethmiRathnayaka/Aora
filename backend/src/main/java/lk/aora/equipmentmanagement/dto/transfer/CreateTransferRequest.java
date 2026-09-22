package lk.aora.equipmentmanagement.dto.transfer;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lk.aora.equipmentmanagement.entity.LocationType;

public record CreateTransferRequest(
        @NotNull TransferLocationDto from,
        @NotNull TransferLocationDto to,
        @NotNull DeliveryPersonDto deliveryPerson,
        @NotNull TransferEquipmentRequest equipment,
        String notes) {

    public record TransferLocationDto(@NotNull LocationType type, @Positive Long worksiteId) {
    }

    public record DeliveryPersonDto(@NotBlank String name, String phone) {
    }

    public record TransferEquipmentRequest(@NotBlank String equipmentCode, @NotNull lk.aora.equipmentmanagement.entity.EquipmentCondition condition) {
    }
}