package lk.aora.equipmentmanagement.dto.transfer;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record RentalBillDto(Long worksiteId, String worksiteName, String projectCode, String address,
        LocalDate fromDate, LocalDate toDate, Instant generatedAt, List<Line> lines, BigDecimal total) {
    public record Line(Long transferId, String equipmentCode, Instant dispatchedAt, Instant returnedAt,
            Long days, BigDecimal unitPrice, BigDecimal amount) {}
}
