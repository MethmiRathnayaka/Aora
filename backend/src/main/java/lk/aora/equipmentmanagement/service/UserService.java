package lk.aora.equipmentmanagement.service;

import lk.aora.equipmentmanagement.dto.PageResponse;
import lk.aora.equipmentmanagement.dto.user.CreateUserRequest;
import lk.aora.equipmentmanagement.dto.user.CreateUserResponseDto;
import lk.aora.equipmentmanagement.dto.user.UpdateUserRequest;
import lk.aora.equipmentmanagement.dto.user.UserSummaryDto;
import lk.aora.equipmentmanagement.entity.AppUser;
import lk.aora.equipmentmanagement.entity.Role;
import lk.aora.equipmentmanagement.entity.Worksite;
import lk.aora.equipmentmanagement.entity.WorksiteStatus;
import lk.aora.equipmentmanagement.dto.worksite.UpdateWorksiteRequest;
import lk.aora.equipmentmanagement.dto.worksite.WorksiteSummaryDto;
import lk.aora.equipmentmanagement.repository.WorksiteRepository;
import lk.aora.equipmentmanagement.exception.ResourceNotFoundException;
import lk.aora.equipmentmanagement.repository.AppUserRepository;
import lk.aora.equipmentmanagement.security.CurrentUserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final AppUserRepository repo;
    private final CurrentUserService currentUserService;
    private final Auth0ManagementService auth0ManagementService;
    private final WorksiteRepository worksiteRepository;
    private final WorksiteService worksiteService;

    public UserService(AppUserRepository repo, CurrentUserService currentUserService, Auth0ManagementService auth0ManagementService, WorksiteRepository worksiteRepository, WorksiteService worksiteService) {
        this.repo = repo;
        this.currentUserService = currentUserService;
        this.auth0ManagementService = auth0ManagementService;
        this.worksiteRepository = worksiteRepository;
        this.worksiteService = worksiteService;
    }
    @Transactional(readOnly = true)
    public Page<UserSummaryDto> list(Role role, Boolean isActive, String search, Pageable pageable) {
        Page<AppUser> page = repo.findByDeletedFalse(pageable);
        return page.map(u -> new UserSummaryDto(u.getId(), u.getAuthUserId(), u.getFirstName(), u.getLastName(), u.getEmail(), u.getPhone(), u.getRole(), u.isActive(), u.getCurrentWorksite() == null ? null : new lk.aora.equipmentmanagement.dto.worksite.WorksiteSummaryDto(u.getCurrentWorksite().getId(), u.getCurrentWorksite().getName(), u.getCurrentWorksite().getProjectCode(), u.getCurrentWorksite().getStatus(), u.getCurrentWorksite().getSiteAdmin() == null ? null : u.getCurrentWorksite().getSiteAdmin().getId(), u.getCurrentWorksite().getAddress())));
    }
    @Transactional(readOnly = true)
    public UserSummaryDto get(Long id) {
        AppUser u = repo.findById(id).filter(user -> !user.isDeleted()).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return new UserSummaryDto(u.getId(), u.getAuthUserId(), u.getFirstName(), u.getLastName(), u.getEmail(), u.getPhone(), u.getRole(), u.isActive(), u.getCurrentWorksite() == null ? null : new lk.aora.equipmentmanagement.dto.worksite.WorksiteSummaryDto(u.getCurrentWorksite().getId(), u.getCurrentWorksite().getName(), u.getCurrentWorksite().getProjectCode(), u.getCurrentWorksite().getStatus(), u.getCurrentWorksite().getSiteAdmin() == null ? null : u.getCurrentWorksite().getSiteAdmin().getId(), u.getCurrentWorksite().getAddress()));
    }

    @Transactional(readOnly = true)
    public UserSummaryDto getCurrentUser() {
        AppUser u = currentUserService.requireCurrentUser();
        return new UserSummaryDto(u.getId(), u.getAuthUserId(), u.getFirstName(), u.getLastName(), u.getEmail(), u.getPhone(), u.getRole(), u.isActive(), u.getCurrentWorksite() == null ? null : new lk.aora.equipmentmanagement.dto.worksite.WorksiteSummaryDto(u.getCurrentWorksite().getId(), u.getCurrentWorksite().getName(), u.getCurrentWorksite().getProjectCode(), u.getCurrentWorksite().getStatus(), u.getCurrentWorksite().getSiteAdmin() == null ? null : u.getCurrentWorksite().getSiteAdmin().getId(), u.getCurrentWorksite().getAddress()));
    }

    @Transactional
    public CreateUserResponseDto create(CreateUserRequest req) {
        AppUser actor = currentUserService.requireCurrentUser();
        if (actor.getRole() != Role.MANAGER) throw new IllegalArgumentException("Only MANAGER can create users");
        Worksite worksite = null;
        if (req.role() == Role.SITE_ADMIN) {
            if (req.worksiteId() == null) throw new IllegalArgumentException("A worksite is required for a site admin");
            worksite = worksiteRepository.findForAssignmentById(req.worksiteId())
                    .orElseThrow(() -> new ResourceNotFoundException("Worksite not found"));
            if (worksite.getStatus() != WorksiteStatus.ACTIVE) throw new IllegalArgumentException("Select an active worksite");
            if (worksite.getSiteAdmin() != null) throw new IllegalArgumentException("This worksite already has a site admin. Select another worksite.");
        } else if (req.worksiteId() != null) {
            throw new IllegalArgumentException("Only site admins can be assigned to a worksite during user creation");
        }
        AppUser u = new AppUser();
        String temporaryPassword = null;
        // If authUserId not provided, create the user in Auth0 and use returned id
        if (req.authUserId() == null || req.authUserId().isBlank()) {
            Auth0ManagementService.Auth0CreatedUser created = auth0ManagementService.createUser(req);
            u.setAuthUserId(created.userId());
            temporaryPassword = created.temporaryPassword();
        } else {
            u.setAuthUserId(req.authUserId());
        }
        u.setFirstName(req.firstName());
        u.setLastName(req.lastName());
        u.setEmail(req.email());
        u.setPhone(req.phone());
        u.setRole(req.role());
        u.setActive(true);
        repo.save(u);
        WorksiteSummaryDto assignedWorksite = worksite == null ? null : worksiteService.updateWorksite(
                worksite.getId(), new UpdateWorksiteRequest(null, null, null, u.getId()));
        return new CreateUserResponseDto(u.getId(), u.getAuthUserId(), u.getFirstName(), u.getLastName(), u.getEmail(), u.getPhone(), u.getRole(), u.isActive(), assignedWorksite, temporaryPassword);
    }

    @Transactional
    public void delete(Long id) {
        AppUser actor = currentUserService.requireCurrentUser();
        if (actor.getRole() != Role.MANAGER) throw new lk.aora.equipmentmanagement.exception.BusinessRuleException("Only MANAGER can delete users");
        if (id.equals(actor.getId())) throw new lk.aora.equipmentmanagement.exception.BusinessRuleException("You cannot delete your own account");
        AppUser user = repo.findById(id).filter(u -> !u.isDeleted()).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        for (Worksite site : worksiteRepository.findBySiteAdminId(id)) {
            site.setSiteAdmin(null);
            worksiteRepository.save(site);
        }
        user.setCurrentWorksite(null);
        user.setCurrentStore(null);
        user.setActive(false);
        user.setDeleted(true);
        repo.save(user);
    }

    @Transactional
    public UserSummaryDto update(Long id, UpdateUserRequest req) {
        AppUser actor = currentUserService.requireCurrentUser();
        AppUser u = repo.findById(id).filter(user -> !user.isDeleted()).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (req.role() != null && actor.getRole() != Role.MANAGER) throw new IllegalArgumentException("Only MANAGER can change roles");
        if (req.firstName() != null) u.setFirstName(req.firstName());
        if (req.lastName() != null) u.setLastName(req.lastName());
        if (req.phone() != null) u.setPhone(req.phone());
        if (req.isActive() != null) u.setActive(req.isActive());
        if (req.role() != null) u.setRole(req.role());
        repo.save(u);
        return new UserSummaryDto(u.getId(), u.getAuthUserId(), u.getFirstName(), u.getLastName(), u.getEmail(), u.getPhone(), u.getRole(), u.isActive(), u.getCurrentWorksite() == null ? null : new lk.aora.equipmentmanagement.dto.worksite.WorksiteSummaryDto(u.getCurrentWorksite().getId(), u.getCurrentWorksite().getName(), u.getCurrentWorksite().getProjectCode(), u.getCurrentWorksite().getStatus(), u.getCurrentWorksite().getSiteAdmin() == null ? null : u.getCurrentWorksite().getSiteAdmin().getId(), u.getCurrentWorksite().getAddress()));
    }
}
