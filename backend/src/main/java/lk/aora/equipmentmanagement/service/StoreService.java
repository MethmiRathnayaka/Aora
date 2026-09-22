package lk.aora.equipmentmanagement.service;

import lk.aora.equipmentmanagement.dto.PageResponse;
import lk.aora.equipmentmanagement.dto.store.UpdateStoreRequest;
import lk.aora.equipmentmanagement.dto.worksite.WorksiteSummaryDto;
import lk.aora.equipmentmanagement.entity.AppUser;
import lk.aora.equipmentmanagement.entity.Store;
import lk.aora.equipmentmanagement.entity.Role;
import lk.aora.equipmentmanagement.exception.ResourceNotFoundException;
import lk.aora.equipmentmanagement.repository.AppUserRepository;
import lk.aora.equipmentmanagement.repository.StoreRepository;
import lk.aora.equipmentmanagement.security.CurrentUserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StoreService {

    private final StoreRepository repo;
    private final AppUserRepository appUserRepository;
    private final CurrentUserService currentUserService;
    private final AuditLogService auditLogService;

    public StoreService(StoreRepository repo, AppUserRepository appUserRepository, CurrentUserService currentUserService, AuditLogService auditLogService) {
        this.repo = repo;
        this.appUserRepository = appUserRepository;
        this.currentUserService = currentUserService;
        this.auditLogService = auditLogService;
    }
    @Transactional(readOnly = true)
    public Page<Store> list(Pageable pageable) {
        return repo.findAll(pageable);
    }
    @Transactional(readOnly = true)
    public Store get(Long id) {
        return repo.findById(id).orElseThrow(() -> new ResourceNotFoundException("Store not found"));
    }

    @Transactional
    public Store update(Long id, UpdateStoreRequest req) {
        AppUser actor = currentUserService.requireCurrentUser();
        if (actor.getRole() != Role.MANAGER) throw new IllegalArgumentException("Only MANAGER can update store");
        Store s = repo.findById(id).orElseThrow(() -> new ResourceNotFoundException("Store not found"));
        if (req.name() != null) s.setName(req.name());
        if (req.address() != null) s.setAddress(req.address());
        if (req.storeManagerId() != null) {
            AppUser newManager = appUserRepository.findById(req.storeManagerId()).orElseThrow(() -> new ResourceNotFoundException("User not found"));
            if (newManager.getRole() != Role.STORE_MANAGER) throw new IllegalArgumentException("User is not STORE_MANAGER");
            AppUser previous = s.getStoreManager();
            if (previous != null && !previous.getId().equals(newManager.getId())) {
                previous.setCurrentStore(null);
                appUserRepository.save(previous);
            }
            newManager.setCurrentStore(s);
            appUserRepository.save(newManager);
            s.setStoreManager(newManager);
            auditLogService.createAudit(actor, "ASSIGN_STORE_MANAGER", "store", s.getId(), previous == null ? null : previous.getId().toString(), newManager.getId().toString());
        }
        repo.save(s);
        auditLogService.createAudit(actor, "UPDATE_STORE", "store", s.getId(), null, "{\"name\":\"" + s.getName() + "\"}");
        return s;
    }
}