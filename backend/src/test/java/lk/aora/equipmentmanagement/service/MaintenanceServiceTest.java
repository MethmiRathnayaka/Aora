package lk.aora.equipmentmanagement.service;

import lk.aora.equipmentmanagement.dto.maintenance.CreateMaintenanceRequest;
import lk.aora.equipmentmanagement.entity.*;
import lk.aora.equipmentmanagement.exception.BusinessRuleException;
import lk.aora.equipmentmanagement.repository.EquipmentRepository;
import lk.aora.equipmentmanagement.repository.MaintenanceRepository;
import lk.aora.equipmentmanagement.security.CurrentUserService;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MaintenanceServiceTest {
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value = Role.class, names = {"MANAGER", "STORE_MANAGER"})
    void managerAndStoreManagerCanReturnEquipment(Role role) {
        AppUser user = actor(role);
        Equipment eq = new Equipment();
        eq.setId(4L);
        eq.setStatus(EquipmentStatus.UNDER_MAINTENANCE);
        eq.setCondition(EquipmentCondition.FAIR);
        MaintenanceRecord record = new MaintenanceRecord();
        record.setId(8L);
        record.setEquipment(eq);
        record.setStatus(MaintenanceStatus.IN_PROGRESS);
        when(maintenance.findFirstByEquipmentEquipmentCodeAndStatusOrderByIdDesc("EQ-001", MaintenanceStatus.IN_PROGRESS)).thenReturn(Optional.of(record));
        when(maintenance.findById(8L)).thenReturn(Optional.of(record));

        assertEquals(8L, service.completeForEquipment("EQ-001"));
        assertEquals(EquipmentStatus.ON_STORE, eq.getStatus());
        assertEquals(EquipmentCondition.FAIR, eq.getCondition());
        assertNull(eq.getCurrentWorksite());
        assertEquals(MaintenanceStatus.COMPLETED, record.getStatus());
        assertSame(user, record.getCompletedBy());
        assertNotNull(record.getCompletedAt());
        verify(equipment).save(eq);
        verify(maintenance).save(record);
        verify(audit, times(2)).createAudit(eq(user), anyString(), anyString(), anyLong(), nullable(String.class), anyString());
        assertThrows(BusinessRuleException.class, () -> service.completeForEquipment("EQ-001"));
    }

    @Test
    void siteAdminCannotReturnEquipment() {
        actor(Role.SITE_ADMIN);
        assertThrows(BusinessRuleException.class, () -> service.completeForEquipment("EQ-001"));
        verifyNoInteractions(maintenance, equipment, audit);
    }

    @Test
    void returnRequiresActiveMaintenance() {
        actor(Role.MANAGER);
        assertThrows(BusinessRuleException.class, () -> service.completeForEquipment("EQ-001"));
        verify(equipment, never()).save(any());
        verify(maintenance, never()).save(any());
    }

    @Test
    void storeManagerCannotApproveMaintenance() {
        actor(Role.STORE_MANAGER);
        MaintenanceRecord record = new MaintenanceRecord();
        record.setStatus(MaintenanceStatus.REQUESTED);
        when(maintenance.findById(8L)).thenReturn(Optional.of(record));
        assertThrows(BusinessRuleException.class, () -> service.update(8L,
                new lk.aora.equipmentmanagement.dto.maintenance.UpdateMaintenanceRequest(MaintenanceStatus.APPROVED, null, null, null)));
        verify(maintenance, never()).save(any());
    }

    @Test
    void completionRejectsEquipmentThatIsNotUnderMaintenance() {
        actor(Role.MANAGER);
        Equipment eq = new Equipment();
        eq.setStatus(EquipmentStatus.TRANSFER);
        MaintenanceRecord record = new MaintenanceRecord();
        record.setEquipment(eq);
        record.setStatus(MaintenanceStatus.IN_PROGRESS);
        when(maintenance.findById(8L)).thenReturn(Optional.of(record));
        assertThrows(BusinessRuleException.class, () -> service.update(8L,
                new lk.aora.equipmentmanagement.dto.maintenance.UpdateMaintenanceRequest(MaintenanceStatus.COMPLETED, null, null, null)));
        assertEquals(MaintenanceStatus.IN_PROGRESS, record.getStatus());
        assertEquals(EquipmentStatus.TRANSFER, eq.getStatus());
        verify(maintenance, never()).save(any());
        verifyNoInteractions(equipment, audit);
    }

    private final EquipmentRepository equipment = mock(EquipmentRepository.class);
    private final MaintenanceRepository maintenance = mock(MaintenanceRepository.class);
    private final CurrentUserService currentUser = mock(CurrentUserService.class);
    private final AuditLogService audit = mock(AuditLogService.class);
    private final MaintenanceService service = new MaintenanceService(maintenance, equipment, currentUser, audit);
    private final CreateMaintenanceRequest request = new CreateMaintenanceRequest("EQ-001", " Repair motor ", "Motor stopped");

    private AppUser actor(Role role) {
        AppUser user = new AppUser();
        user.setRole(role);
        when(currentUser.requireCurrentUser()).thenReturn(user);
        return user;
    }

    @Test
    void managerStartsMaintenanceWithApprovalAndEquipmentStatus() {
        AppUser manager = actor(Role.MANAGER);
        Equipment eq = new Equipment();
        eq.setId(4L);
        when(equipment.findByEquipmentCode("EQ-001")).thenReturn(Optional.of(eq));
        when(maintenance.save(any(MaintenanceRecord.class))).thenAnswer(invocation -> {
            MaintenanceRecord record = invocation.getArgument(0);
            assertEquals(MaintenanceStatus.IN_PROGRESS, record.getStatus());
            assertSame(eq, record.getEquipment());
            assertSame(manager, record.getReportedBy());
            assertSame(manager, record.getApprovedBy());
            assertSame(manager, record.getStartedBy());
            assertNotNull(record.getReportedAt());
            assertEquals(record.getReportedAt(), record.getApprovedAt());
            assertEquals(record.getApprovedAt(), record.getStartedAt());
            assertEquals("Repair motor", record.getReason());
            assertEquals("Motor stopped", record.getDescription());
            record.setId(8L);
            return record;
        });
        assertEquals(8L, service.start(request));
        assertEquals(EquipmentStatus.UNDER_MAINTENANCE, eq.getStatus());
        verify(equipment).save(eq);
        verify(audit, times(2)).createAudit(eq(manager), anyString(), anyString(), anyLong(), nullable(String.class), anyString());
        assertThrows(BusinessRuleException.class, () -> service.start(request));
        verify(maintenance, times(1)).save(any());
    }

    @Test
    void otherRolesCannotDirectlyStartMaintenance() {
        for (Role role : new Role[] { Role.SITE_ADMIN, Role.STORE_MANAGER }) {
            actor(role);
            assertThrows(BusinessRuleException.class, () -> service.start(request));
        }
        verifyNoInteractions(equipment, maintenance, audit);
    }

    @Test
    void equipmentOutsideStoreCannotStartMaintenance() {
        actor(Role.MANAGER);
        for (EquipmentStatus status : EquipmentStatus.values()) {
            if (status == EquipmentStatus.ON_STORE) continue;
            Equipment eq = new Equipment();
            eq.setStatus(status);
            when(equipment.findByEquipmentCode("EQ-001")).thenReturn(Optional.of(eq));
            assertThrows(BusinessRuleException.class, () -> service.start(request));
            assertEquals(status, eq.getStatus());
        }
        verifyNoInteractions(maintenance, audit);
        verify(equipment, never()).save(any());
    }
}
