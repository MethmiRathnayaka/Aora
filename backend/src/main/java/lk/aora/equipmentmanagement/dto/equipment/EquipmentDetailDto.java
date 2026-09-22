package lk.aora.equipmentmanagement.dto.equipment;

public record EquipmentDetailDto(
        Long equipmentId,
        String equipmentCode,
        Long equipmentTypeId,
        String brand,
        String model,
        String serialNumber,
        String status,
        String condition,
        Long currentWorksiteId,
        String purchaseDate,
        String createdAt,
        String updatedAt) {
}