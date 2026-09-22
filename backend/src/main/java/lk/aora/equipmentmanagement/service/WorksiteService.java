package lk.aora.equipmentmanagement.service;

import java.util.Optional;
import java.util.stream.Collectors;

import lk.aora.equipmentmanagement.dto.worksite.CreateWorksiteRequest;
import lk.aora.equipmentmanagement.dto.worksite.UpdateWorksiteRequest;
import lk.aora.equipmentmanagement.dto.worksite.WorksiteSummaryDto;
import lk.aora.equipmentmanagement.entity.AppUser;
import lk.aora.equipmentmanagement.entity.AuditAction;
import lk.aora.equipmentmanagement.entity.Worksite;
import lk.aora.equipmentmanagement.entity.WorksiteStatus;
import lk.aora.equipmentmanagement.exception.ResourceNotFoundException;
import lk.aora.equipmentmanagement.exception.UnauthorizedException;
import lk.aora.equipmentmanagement.repository.AppUserRepository;
import lk.aora.equipmentmanagement.repository.WorksiteRepository;
import lk.aora.equipmentmanagement.security.CurrentUserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorksiteService {

    private final WorksiteRepository worksiteRepository;
    private final AppUserRepository appUserRepository;
    private final CurrentUserService currentUserService;
    private final AuditLogService auditLogService;

    public WorksiteService(WorksiteRepository worksiteRepository, AppUserRepository appUserRepository, CurrentUserService currentUserService, AuditLogService auditLogService) {
        this.worksiteRepository = worksiteRepository;
        this.appUserRepository = appUserRepository;
        this.currentUserService = currentUserService;
        this.auditLogService = auditLogService;
    }

    public Page<WorksiteSummaryDto> listWorksites(Optional<WorksiteStatus> status, Optional<String> search, Pageable pageable) {
        Page<Worksite> page;
        if (status.isPresent()) {
            page = worksiteRepository.findByStatus(status.get(), pageable);
        } else if (search.isPresent() && !search.get().isBlank()) {
            page = worksiteRepository.findByNameContainingIgnoreCaseOrProjectCodeContainingIgnoreCase(search.get(), search.get(), pageable);
        } else {
            page = worksiteRepository.findAll(pageable);
        }
        return page.map(this::toSummaryDto);
    }

    public WorksiteSummaryDto getWorksite(Long id) {
        Worksite w = worksiteRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Worksite not found"));
        return toSummaryDto(w);
    }

    @Transactional
    public WorksiteSummaryDto createWorksite(CreateWorksiteRequest req) {
        AppUser actor = currentUserService.requireCurrentUser();
        if (actor.getRole() == null || actor.getRole().name() == null || actor.getRole().name().isEmpty()) {
            throw new UnauthorizedException("User role is not set");
        }
        if (!actor.getRole().name().equals("MANAGER")) {
            throw new UnauthorizedException("Only MANAGER can create worksites");
        }
        Worksite w = new Worksite();
        w.setName(req.name());
        w.setProjectCode(req.projectCode());
        w.setAddress(req.address());
        w.setStatus(WorksiteStatus.ACTIVE);
        worksiteRepository.save(w);
        auditLogService.createAudit(actor, AuditAction.CREATE_WORKSITE.name(), "worksite", w.getId(), null, "{\"name\":\"" + w.getName() + "\"}");
        return toSummaryDto(w);
    }

    @Transactional
    public WorksiteSummaryDto updateWorksite(Long id, UpdateWorksiteRequest req) {
        AppUser actor = currentUserService.requireCurrentUser();
        if (!actor.getRole().name().equals("MANAGER")) {
            throw new UnauthorizedException("Only MANAGER can update worksites");
        }
        Worksite w = worksiteRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Worksite not found"));
        String oldValues = "{" + (w.getSiteAdmin() != null ? ("\"siteAdminId\":\"" + w.getSiteAdmin().getId() + "\"") : "\"siteAdminId\":null") + "}" ;
        if (req.name() != null) w.setName(req.name());
        if (req.address() != null) w.setAddress(req.address());
        if (req.status() != null) w.setStatus(req.status());

        if (req.siteAdminId() != null) {
            Long newAdminId = req.siteAdminId();
            AppUser newAdmin = appUserRepository.findById(newAdminId).orElseThrow(() -> new ResourceNotFoundException("User not found"));
            if (newAdmin.getRole() == null || !newAdmin.getRole().name().equals("SITE_ADMIN")) {
                throw new IllegalArgumentException("User is not SITE_ADMIN");
            }
            AppUser previous = w.getSiteAdmin();
            if (previous != null && !previous.getId().equals(newAdmin.getId())) {
                // end previous assignment
                previous.setCurrentWorksite(null);
                appUserRepository.save(previous);
            }
            // assign new admin
            newAdmin.setCurrentWorksite(w);
            appUserRepository.save(newAdmin);
            w.setSiteAdmin(newAdmin);
            // audit assignment
            String newValues = "{\"siteAdminId\":\"" + newAdmin.getId() + "\"}";
            auditLogService.createAudit(actor, AuditAction.ASSIGN_SITE_ADMIN.name(), "worksite", w.getId(), oldValues, newValues);
        }

        worksiteRepository.save(w);
        auditLogService.createAudit(actor, AuditAction.UPDATE_WORKSITE.name(), "worksite", w.getId(), oldValues, "{\"name\":\"" + w.getName() + "\"}");
        return toSummaryDto(w);
    }

    private WorksiteSummaryDto toSummaryDto(Worksite w) {
        Long siteAdminId = w.getSiteAdmin() == null ? null : w.getSiteAdmin().getId();
        return new WorksiteSummaryDto(w.getId(), w.getName(), w.getProjectCode(), w.getStatus(), siteAdminId, w.getAddress());
    }
}
