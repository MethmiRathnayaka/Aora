package lk.aora.equipmentmanagement.service;

import lk.aora.equipmentmanagement.dto.user.CreateUserRequest;
import lk.aora.equipmentmanagement.dto.worksite.UpdateWorksiteRequest;
import lk.aora.equipmentmanagement.dto.worksite.WorksiteSummaryDto;
import lk.aora.equipmentmanagement.entity.AppUser;
import lk.aora.equipmentmanagement.entity.Role;
import lk.aora.equipmentmanagement.entity.Worksite;
import lk.aora.equipmentmanagement.entity.WorksiteStatus;
import lk.aora.equipmentmanagement.repository.AppUserRepository;
import lk.aora.equipmentmanagement.repository.WorksiteRepository;
import lk.aora.equipmentmanagement.security.CurrentUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserServiceTest {
    @Test
    void managerDeletesUserAndReleasesWorksite() {
        AppUser user = new AppUser();
        user.setId(8L);
        Worksite site = new Worksite();
        site.setSiteAdmin(user);
        user.setCurrentWorksite(site);
        when(users.findById(8L)).thenReturn(Optional.of(user));
        when(sites.findBySiteAdminId(8L)).thenReturn(java.util.List.of(site));
        service.delete(8L);
        assertTrue(user.isDeleted());
        assertFalse(user.isActive());
        assertNull(user.getCurrentWorksite());
        assertNull(site.getSiteAdmin());
        verify(users).save(user);
        verify(users, never()).delete(any());
    }

    @Test
    void cannotDeleteSelfOrDeleteAsNonManager() {
        AppUser actor = new AppUser();
        actor.setId(1L);
        actor.setRole(Role.MANAGER);
        when(currentUser.requireCurrentUser()).thenReturn(actor);
        assertThrows(lk.aora.equipmentmanagement.exception.BusinessRuleException.class, () -> service.delete(1L));
        actor.setRole(Role.STORE_MANAGER);
        assertThrows(lk.aora.equipmentmanagement.exception.BusinessRuleException.class, () -> service.delete(2L));
        actor.setRole(Role.SITE_ADMIN);
        assertThrows(lk.aora.equipmentmanagement.exception.BusinessRuleException.class, () -> service.delete(2L));
        verifyNoInteractions(users, sites);
    }

    private final AppUserRepository users = mock(AppUserRepository.class);
    private final CurrentUserService currentUser = mock(CurrentUserService.class);
    private final Auth0ManagementService auth0 = mock(Auth0ManagementService.class);
    private final WorksiteRepository sites = mock(WorksiteRepository.class);
    private final WorksiteService worksiteService = mock(WorksiteService.class);
    private final UserService service = new UserService(users, currentUser, auth0, sites, worksiteService);

    @BeforeEach
    void setUp() {
        AppUser manager = new AppUser();
        manager.setRole(Role.MANAGER);
        when(currentUser.requireCurrentUser()).thenReturn(manager);
    }

    private CreateUserRequest request(Role role, Long siteId) {
        return new CreateUserRequest("auth0|existing", "Site", "Admin", "admin@example.com", null, role, siteId);
    }

    @Test
    void siteAdminRequiresWorksiteBeforeAccountCreation() {
        assertThrows(IllegalArgumentException.class, () -> service.create(request(Role.SITE_ADMIN, null)));
        verifyNoInteractions(auth0, users, worksiteService);
    }

    @Test
    void occupiedWorksiteIsRejectedBeforeAccountCreation() {
        Worksite site = new Worksite();
        site.setSiteAdmin(new AppUser());
        when(sites.findForAssignmentById(3L)).thenReturn(Optional.of(site));
        assertThrows(IllegalArgumentException.class, () -> service.create(request(Role.SITE_ADMIN, 3L)));
        verifyNoInteractions(auth0, users, worksiteService);
    }

    @Test
    void assignsCreatedSiteAdminAndReturnsWorksite() {
        Worksite site = new Worksite();
        site.setId(3L);
        when(sites.findForAssignmentById(3L)).thenReturn(Optional.of(site));
        when(users.save(any(AppUser.class))).thenAnswer(invocation -> {
            AppUser user = invocation.getArgument(0);
            user.setId(8L);
            return user;
        });
        WorksiteSummaryDto summary = new WorksiteSummaryDto(3L, "Site", "S1", WorksiteStatus.ACTIVE, 8L, null);
        when(worksiteService.updateWorksite(3L, new UpdateWorksiteRequest(null, null, null, 8L))).thenReturn(summary);
        assertEquals(summary, service.create(request(Role.SITE_ADMIN, 3L)).currentWorksite());
        verify(worksiteService).updateWorksite(3L, new UpdateWorksiteRequest(null, null, null, 8L));
    }

    @Test
    void managerDoesNotRequireWorksite() {
        assertNull(service.create(request(Role.MANAGER, null)).currentWorksite());
        verifyNoInteractions(sites, worksiteService);
    }
}
