package lk.aora.equipmentmanagement.service;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.stream.IntStream;
import lk.aora.equipmentmanagement.entity.*;
import lk.aora.equipmentmanagement.exception.*;
import lk.aora.equipmentmanagement.repository.*;
import lk.aora.equipmentmanagement.security.CurrentUserService;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RentalBillServiceTest {
    private final EquipmentTransferRepository transfers = mock(EquipmentTransferRepository.class);
    private final WorksiteRepository sites = mock(WorksiteRepository.class);
    private final CurrentUserService users = mock(CurrentUserService.class);
    private final RentalBillService service = new RentalBillService(transfers, sites, users);
    private final LocalDate start = LocalDate.of(2026, 9, 1);
    private final LocalDate end = LocalDate.of(2026, 9, 30);

    private void setup(Role role) {
        AppUser user = new AppUser(); user.setRole(role);
        when(users.requireCurrentUser()).thenReturn(user);
        Worksite site = new Worksite(); site.setId(7L); site.setName("Test site"); site.setProjectCode("P-7");
        when(sites.findById(7L)).thenReturn(Optional.of(site));
    }

    @Test
    void bothManagerRolesCanFetchAllLinesAndExactTotal() {
        for (Role role : List.of(Role.MANAGER, Role.STORE_MANAGER)) {
            setup(role);
            var rows = IntStream.range(0, 125).mapToObj(i -> {
                Equipment eq = new Equipment(); eq.setEquipmentCode("EQ-" + i);
                EquipmentTransfer t = new EquipmentTransfer(); t.setId((long)i); t.setEquipment(eq);
                t.setRentalStartedAt(Instant.parse("2026-08-30T00:00:00Z"));
                t.setReceivedAt(Instant.parse("2026-09-02T00:00:00Z"));
                t.setRentalDays(3L); t.setUnitPrice(new BigDecimal("0.10")); t.setRentTotal(new BigDecimal("0.30"));
                return t;
            }).toList();
            when(transfers.findAll(org.mockito.ArgumentMatchers.<Specification<EquipmentTransfer>>any(), any(Sort.class))).thenReturn(rows);
            var bill = service.bill(7L, start, end);
            assertEquals(125, bill.lines().size());
            assertEquals(new BigDecimal("37.50"), bill.total());
            assertEquals("P-7", bill.projectCode());
            assertEquals(start, bill.fromDate());
            assertEquals(end, bill.toDate());
            assertEquals(Instant.parse("2026-08-30T00:00:00Z"), bill.lines().get(0).dispatchedAt());
            assertEquals(3L, bill.lines().get(0).days());
        }
    }

    @Test
    void rejectsSiteAdminsBeforeReadingBillingData() {
        setup(Role.SITE_ADMIN);
        assertThrows(BusinessRuleException.class, () -> service.bill(7L, start, end));
        verifyNoInteractions(transfers);
        verify(sites, never()).findById(anyLong());
    }

    @Test
    void rejectsInvalidFiltersAndUnknownWorksite() {
        setup(Role.MANAGER);
        assertThrows(BusinessRuleException.class, () -> service.bill(7L, end, start));
        assertThrows(BusinessRuleException.class, () -> service.bill(null, start, end));
        assertThrows(BusinessRuleException.class, () -> service.bill(7L, null, end));
        assertThrows(ResourceNotFoundException.class, () -> service.bill(99L, start, end));
        verifyNoInteractions(transfers);
    }

    @Test
    void emptyAndSingleDayBillsAreSupported() {
        setup(Role.STORE_MANAGER);
        when(transfers.findAll(org.mockito.ArgumentMatchers.<Specification<EquipmentTransfer>>any(), any(Sort.class))).thenReturn(List.of());
        var bill = service.bill(7L, start, start);
        assertTrue(bill.lines().isEmpty());
        assertEquals(new BigDecimal("0.00"), bill.total());
    }
}
