package lk.aora.equipmentmanagement.service;

import java.util.Optional;
import lk.aora.equipmentmanagement.dto.transfer.ReceiveAtStoreRequest;
import lk.aora.equipmentmanagement.dto.transfer.UpdateTransferRequest;
import lk.aora.equipmentmanagement.entity.*;
import lk.aora.equipmentmanagement.exception.BusinessRuleException;
import lk.aora.equipmentmanagement.repository.*;
import lk.aora.equipmentmanagement.security.CurrentUserService;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StoreReceiptTest {
    private final EquipmentRepository equipment = mock(EquipmentRepository.class);
    private final EquipmentTransferRepository transfers = mock(EquipmentTransferRepository.class);
    private final CurrentUserService currentUser = mock(CurrentUserService.class);
    private final AuditLogService audit = mock(AuditLogService.class);
    private final TransferService service = new TransferService(transfers, equipment, mock(WorksiteRepository.class), mock(StoreRepository.class), currentUser, audit);

    private ReceiveAtStoreRequest request(Long site, boolean confirmed) {
        return new ReceiveAtStoreRequest("EQ-001", site, "Driver", null, EquipmentCondition.FAIR, confirmed, "Returned to store", new java.math.BigDecimal("125.50"));
    }

    private Equipment setup(Role role) {
        AppUser actor = new AppUser();
        actor.setRole(role);
        when(currentUser.requireCurrentUser()).thenReturn(actor);
        Worksite site = new Worksite();
        site.setId(2L);
        Equipment eq = new Equipment();
        eq.setId(3L);
        eq.setEquipmentCode("EQ-001");
        eq.setStatus(EquipmentStatus.ON_SITE);
        eq.setCondition(EquipmentCondition.GOOD);
        eq.setCurrentWorksite(site);
        when(equipment.findForTransferByCode("EQ-001")).thenReturn(Optional.of(eq));
        doAnswer(invocation -> {
            EquipmentTransfer transfer = invocation.getArgument(0);
            transfer.setId(4L);
            return transfer;
        }).when(transfers).save(any());
        EquipmentTransfer dispatch = new EquipmentTransfer();
        dispatch.setFromLocationType(LocationType.STORE);
        dispatch.setToLocationType(LocationType.WORKSITE);
        dispatch.setStatus(TransferStatus.COMPLETED);
        dispatch.setDispatchedAt(java.time.Instant.now().minusSeconds(180000));
        when(transfers.findByEquipmentId(3L)).thenReturn(java.util.List.of(dispatch));
        return eq;
    }

    @Test
    void bothManagerRolesCanCreateAndCompleteReturnFromCurrentSite() {
        for (Role role : new Role[] {Role.MANAGER, Role.STORE_MANAGER}) {
            Equipment eq = setup(role);
            var result = service.receiveAtStore(request(2L, true));
            assertEquals("COMPLETED", result.status());
            assertEquals(3L, result.rentalDays());
            assertEquals(new java.math.BigDecimal("376.50"), result.rentTotal());
            assertEquals(2L, result.fromWorksiteId());
            assertEquals("STORE", result.toType());
            assertEquals("GOOD", result.equipment().get(0).conditionBefore());
            assertEquals("FAIR", result.equipment().get(0).conditionAfter());
            assertEquals(EquipmentStatus.ON_STORE, eq.getStatus());
            assertEquals(EquipmentCondition.FAIR, eq.getCondition());
            assertNull(eq.getCurrentWorksite());
            assertThrows(BusinessRuleException.class, () -> service.receiveAtStore(request(2L, true)));
        }
        verify(transfers, times(2)).save(any());
    }

    @Test
    void activeTransferMustBeConfirmedInsteadOfCreatingAnother() {
        Equipment eq = setup(Role.MANAGER);
        when(transfers.existsByEquipmentIdAndStatus(3L, TransferStatus.IN_TRANSIT)).thenReturn(true);
        assertThrows(BusinessRuleException.class, () -> service.receiveAtStore(request(2L, true)));
        assertEquals(EquipmentStatus.ON_SITE, eq.getStatus());
        verify(transfers, never()).save(any());
    }

    @Test
    void refusesUnauthorizedUnconfirmedOrChangedWorksiteReturns() {
        setup(Role.SITE_ADMIN);
        assertThrows(BusinessRuleException.class, () -> service.receiveAtStore(request(2L, true)));
        setup(Role.STORE_MANAGER);
        assertThrows(BusinessRuleException.class, () -> service.receiveAtStore(request(2L, false)));
        assertThrows(BusinessRuleException.class, () -> service.receiveAtStore(request(99L, true)));
        verify(transfers, never()).save(any());
    }

    @Test
    void existingStoreTransferIsCompletedWithoutCreatingAnother() {
        Equipment eq = setup(Role.STORE_MANAGER);
        eq.setStatus(EquipmentStatus.TRANSFER);
        EquipmentTransfer transfer = new EquipmentTransfer();
        transfer.setId(4L);
        transfer.setEquipment(eq);
        transfer.setFromLocationType(LocationType.WORKSITE);
        transfer.setFromWorksite(eq.getCurrentWorksite());
        transfer.setToLocationType(LocationType.STORE);
        transfer.setStatus(TransferStatus.IN_TRANSIT);
        when(transfers.findForUpdateById(4L)).thenReturn(Optional.of(transfer));
        var request = new UpdateTransferRequest(TransferStatus.COMPLETED,
                new UpdateTransferRequest.TransferEquipmentUpdateRequest("EQ-001", EquipmentCondition.FAIR), null, new java.math.BigDecimal("125.50"));
        var result = service.update(4L, request);
        assertEquals("COMPLETED", result.status());
        assertEquals(3L, result.rentalDays());
        assertEquals(new java.math.BigDecimal("376.50"), result.rentTotal());
        assertEquals(EquipmentStatus.ON_STORE, eq.getStatus());
        assertEquals(EquipmentCondition.FAIR, eq.getCondition());
        assertNull(eq.getCurrentWorksite());
        verify(transfers).save(same(transfer));
        assertThrows(BusinessRuleException.class, () -> service.update(4L, request));
    }

    @Test
    void countsStarted24HourPeriodsWithOneDayMinimum() {
        var start = java.time.Instant.parse("2026-09-20T23:59:00Z");
        assertEquals(1, TransferService.rentalDays(start, start));
        assertEquals(1, TransferService.rentalDays(start, start.plusSeconds(120)));
        assertEquals(1, TransferService.rentalDays(start, start.plusSeconds(86400)));
        assertEquals(2, TransferService.rentalDays(start, start.plusSeconds(86400).plusNanos(1)));
        assertEquals(2, TransferService.rentalDays(start, start.plusSeconds(172800)));
    }

    @Test
    void missingDispatchAndInvalidPricesDoNotCompleteReturn() {
        Equipment eq = setup(Role.MANAGER);
        for (java.math.BigDecimal price : new java.math.BigDecimal[] {null, new java.math.BigDecimal("-1"), new java.math.BigDecimal("1.001")}) {
            var invalid = new ReceiveAtStoreRequest("EQ-001", 2L, "Driver", null, EquipmentCondition.FAIR, true, null, price);
            assertThrows(BusinessRuleException.class, () -> service.receiveAtStore(invalid));
        }
        when(transfers.findByEquipmentId(3L)).thenReturn(java.util.List.of());
        assertThrows(BusinessRuleException.class, () -> service.receiveAtStore(request(2L, true)));
        assertEquals(EquipmentStatus.ON_SITE, eq.getStatus());
        verify(transfers, never()).save(any());
    }

    @Test
    void siteMovesAndCancelledDispatchesDoNotRestartRent() {
        setup(Role.MANAGER);
        EquipmentTransfer original = transfers.findByEquipmentId(3L).get(0);
        original.setDispatchedAt(java.time.Instant.now().minusSeconds(90000));
        EquipmentTransfer siteMove = new EquipmentTransfer();
        siteMove.setFromLocationType(LocationType.WORKSITE);
        siteMove.setToLocationType(LocationType.WORKSITE);
        siteMove.setStatus(TransferStatus.COMPLETED);
        siteMove.setDispatchedAt(java.time.Instant.now().minusSeconds(3600));
        EquipmentTransfer cancelled = new EquipmentTransfer();
        cancelled.setFromLocationType(LocationType.STORE);
        cancelled.setToLocationType(LocationType.WORKSITE);
        cancelled.setStatus(TransferStatus.CANCELLED);
        cancelled.setDispatchedAt(java.time.Instant.now().minusSeconds(60));
        when(transfers.findByEquipmentId(3L)).thenReturn(java.util.List.of(original, siteMove, cancelled));
        var result = service.receiveAtStore(request(2L, true));
        assertEquals(original.getDispatchedAt(), result.rentalStartedAt());
        assertEquals(2L, result.rentalDays());
        assertEquals(new java.math.BigDecimal("251.00"), result.rentTotal());
    }

    @Test
    void previouslyReturnedRentalCannotBeChargedAgain() {
        setup(Role.MANAGER);
        EquipmentTransfer original = transfers.findByEquipmentId(3L).get(0);
        EquipmentTransfer previousReturn = new EquipmentTransfer();
        previousReturn.setStatus(TransferStatus.COMPLETED);
        previousReturn.setToLocationType(LocationType.STORE);
        previousReturn.setReceivedAt(java.time.Instant.now().minusSeconds(3600));
        when(transfers.findByEquipmentId(3L)).thenReturn(java.util.List.of(original, previousReturn));
        assertThrows(BusinessRuleException.class, () -> service.receiveAtStore(request(2L, true)));
        verify(transfers, never()).save(any());
    }

    @Test
    void refusesUnavailableEquipment() {
        Equipment eq = setup(Role.MANAGER);
        for (EquipmentStatus status : new EquipmentStatus[] {EquipmentStatus.ON_STORE, EquipmentStatus.TRANSFER, EquipmentStatus.RETIRED, EquipmentStatus.UNDER_MAINTENANCE}) {
            eq.setStatus(status);
            assertThrows(BusinessRuleException.class, () -> service.receiveAtStore(request(2L, true)));
        }
        verify(transfers, never()).save(any());
    }
}
