package lk.aora.equipmentmanagement.dto.transfer;

import java.time.Instant;
import java.util.List;

public record TransferDto(Long transferId, String fromType, Long fromWorksiteId, String toType, Long toWorksiteId, String deliveryPersonName, String deliveryPersonPhone, String status, Long dispatchedById, Instant dispatchedAt, Long receivedById, Instant receivedAt, List<TransferItemDto> equipment, String notes, Instant rentalStartedAt, Long rentalDays, java.math.BigDecimal unitPrice, java.math.BigDecimal rentTotal) {
    public record TransferItemDto(String equipmentCode, String conditionBefore, String conditionAfter) {}
}
