package lk.aora.equipmentmanagement.service;

import java.util.Optional;
import lk.aora.equipmentmanagement.entity.*;
import lk.aora.equipmentmanagement.exception.BusinessRuleException;
import lk.aora.equipmentmanagement.repository.*;
import lk.aora.equipmentmanagement.security.CurrentUserService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TransferDeleteTest {
    private final EquipmentTransferRepository transfers = mock(EquipmentTransferRepository.class);
    private final EquipmentRepository equipment = mock(EquipmentRepository.class);
    private final CurrentUserService users = mock(CurrentUserService.class);
    private final AuditLogService audit = mock(AuditLogService.class);
    private final TransferService service = new TransferService(transfers, equipment, mock(WorksiteRepository.class), mock(StoreRepository.class), users, audit);

    @ParameterizedTest
    @EnumSource(TransferStatus.class)
    void managerCanDeleteEveryStatus(TransferStatus status) {
        AppUser manager = new AppUser();
        manager.setRole(Role.MANAGER);
        when(users.requireCurrentUser()).thenReturn(manager);
        Equipment eq = new Equipment();
        eq.setId(3L);
        eq.setEquipmentCode("EQ-001");
        eq.setStatus(status == TransferStatus.IN_TRANSIT ? EquipmentStatus.TRANSFER : EquipmentStatus.UNDER_MAINTENANCE);
        Worksite site = new Worksite();
        EquipmentTransfer transfer = new EquipmentTransfer();
        transfer.setId(1L);
        transfer.setStatus(status);
        transfer.setEquipment(eq);
        transfer.setFromLocationType(LocationType.WORKSITE);
        transfer.setFromWorksite(site);
        when(transfers.findForUpdateById(1L)).thenReturn(Optional.of(transfer));
        when(equipment.findForTransferByCode("EQ-001")).thenReturn(Optional.of(eq));
        service.delete(1L);
        verify(transfers).delete(transfer);
        if (status == TransferStatus.IN_TRANSIT) {
            assertEquals(EquipmentStatus.ON_SITE, eq.getStatus());
            assertSame(site, eq.getCurrentWorksite());
        } else {
            assertEquals(EquipmentStatus.UNDER_MAINTENANCE, eq.getStatus());
            verifyNoInteractions(equipment);
        }
        verify(audit).createAudit(eq(manager), eq("DELETE_TRANSFER"), eq("transfer"), eq(1L), anyString(), isNull());
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"SITE_ADMIN", "STORE_MANAGER"})
    void otherRolesCannotDelete(Role role) {
        AppUser user = new AppUser();
        user.setRole(role);
        when(users.requireCurrentUser()).thenReturn(user);
        assertThrows(BusinessRuleException.class, () -> service.delete(1L));
        verifyNoInteractions(transfers, equipment, audit);
    }
}
