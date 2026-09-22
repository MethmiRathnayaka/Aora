package lk.aora.equipmentmanagement.service;

import java.math.BigDecimal;
import java.time.*;
import lk.aora.equipmentmanagement.dto.transfer.RentalBillDto;
import lk.aora.equipmentmanagement.entity.*;
import lk.aora.equipmentmanagement.exception.*;
import lk.aora.equipmentmanagement.repository.*;
import lk.aora.equipmentmanagement.security.CurrentUserService;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RentalBillService {
    private final EquipmentTransferRepository transfers;
    private final WorksiteRepository worksites;
    private final CurrentUserService users;

    public RentalBillService(EquipmentTransferRepository transfers, WorksiteRepository worksites, CurrentUserService users) {
        this.transfers = transfers;
        this.worksites = worksites;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public RentalBillDto bill(Long worksiteId, LocalDate fromDate, LocalDate toDate) {
        Role role = users.requireCurrentUser().getRole();
        if (role != Role.MANAGER && role != Role.STORE_MANAGER) {
            throw new BusinessRuleException("Only managers and store managers can view rental bills");
        }
        if (worksiteId == null || worksiteId <= 0 || fromDate == null || toDate == null || toDate.isBefore(fromDate)
                || toDate.getYear() > 9999 || fromDate.getYear() < 1) {
            throw new BusinessRuleException("Select a worksite and a valid start and end date");
        }
        Worksite site = worksites.findById(worksiteId).orElseThrow(() -> new ResourceNotFoundException("Worksite not found"));
        ZoneId zone = ZoneId.of("Asia/Colombo");
        Instant start = fromDate.atStartOfDay(zone).toInstant();
        Instant end = toDate.plusDays(1).atStartOfDay(zone).toInstant();
        var lines = transfers.findAll(billFilter(worksiteId, start, end), Sort.by("receivedAt", "id")).stream()
                .map(t -> new RentalBillDto.Line(t.getId(), t.getEquipment().getEquipmentCode(), t.getRentalStartedAt(),
                        t.getReceivedAt(), t.getRentalDays(), t.getUnitPrice(), t.getRentTotal())).toList();
        BigDecimal total = lines.stream().map(RentalBillDto.Line::amount).reduce(new BigDecimal("0.00"), BigDecimal::add);
        return new RentalBillDto(site.getId(), site.getName(), site.getProjectCode(), site.getAddress(),
                fromDate, toDate, Instant.now(), lines, total);
    }

    static Specification<EquipmentTransfer> billFilter(Long worksiteId, Instant start, Instant end) {
        return (root, query, cb) -> cb.and(
                cb.equal(root.get("status"), TransferStatus.COMPLETED),
                cb.equal(root.get("toLocationType"), LocationType.STORE),
                cb.equal(root.get("fromLocationType"), LocationType.WORKSITE),
                cb.equal(root.get("fromWorksite").get("id"), worksiteId),
                cb.isNotNull(root.get("rentTotal")),
                cb.greaterThanOrEqualTo(root.<Instant>get("receivedAt"), start),
                cb.lessThan(root.<Instant>get("receivedAt"), end));
    }
}
