package lk.aora.equipmentmanagement.service;

import java.time.Instant;

import lk.aora.equipmentmanagement.dto.maintenance.CreateMaintenanceRequest;
import lk.aora.equipmentmanagement.dto.maintenance.UpdateMaintenanceRequest;
import lk.aora.equipmentmanagement.entity.*;
import lk.aora.equipmentmanagement.exception.BusinessRuleException;
import lk.aora.equipmentmanagement.exception.ResourceNotFoundException;
import lk.aora.equipmentmanagement.repository.MaintenanceRepository;
import lk.aora.equipmentmanagement.repository.EquipmentRepository;
import lk.aora.equipmentmanagement.security.CurrentUserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MaintenanceService {

    private final MaintenanceRepository maintenanceRepository;
    private final EquipmentRepository equipmentRepository;
    private final CurrentUserService currentUserService;
    private final AuditLogService auditLogService;

    public MaintenanceService(MaintenanceRepository maintenanceRepository, EquipmentRepository equipmentRepository, CurrentUserService currentUserService, AuditLogService auditLogService) {
        this.maintenanceRepository = maintenanceRepository;
        this.equipmentRepository = equipmentRepository;
        this.currentUserService = currentUserService;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public Page<MaintenanceRecord> list(Pageable pageable) {
        return maintenanceRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public MaintenanceRecord get(Long id) {
        return maintenanceRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Maintenance not found"));
    }

    @Transactional
    public MaintenanceRecord create(CreateMaintenanceRequest req) {
        AppUser actor = currentUserService.requireCurrentUser();

        Equipment eq = equipmentRepository.findByEquipmentCode(req.equipmentCode()).orElseThrow(() -> new ResourceNotFoundException("Equipment not found"));

        // Allow SITE_ADMIN and STORE_MANAGER to create maintenance requests with location-specific rules
        if (actor.getRole() == Role.SITE_ADMIN) {
            // Equipment must be at worksite and belong to actor's assigned worksite
            if (eq.getStatus() != EquipmentStatus.ON_SITE) throw new BusinessRuleException("Equipment must be ON_SITE to report maintenance");
            if (actor.getCurrentWorksite() == null || eq.getCurrentWorksite() == null || !actor.getCurrentWorksite().getId().equals(eq.getCurrentWorksite().getId())) throw new BusinessRuleException("SITE_ADMIN can only report maintenance for equipment at their assigned worksite");
        } else if (actor.getRole() == Role.STORE_MANAGER) {
            // Store manager can report maintenance for equipment at the store
            if (eq.getStatus() != EquipmentStatus.ON_STORE) throw new BusinessRuleException("STORE_MANAGER can only report maintenance for equipment at the store");
        } else {
            throw new BusinessRuleException("Only SITE_ADMIN or STORE_MANAGER can create maintenance requests");
        }

        MaintenanceRecord m = new MaintenanceRecord();
        m.setEquipment(eq);
        m.setReason(req.reason());
        m.setDescription(req.description());
        m.setReportedBy(actor);
        m.setStatus(MaintenanceStatus.REQUESTED);
        m.setReportedAt(Instant.now());
        maintenanceRepository.save(m);
        auditLogService.createAudit(actor, AuditAction.CREATE_MAINTENANCE.name(), "maintenance", m.getId(), null, "{\"status\":\"REQUESTED\"}");
        return m;
    }

    @Transactional
    public Long start(CreateMaintenanceRequest req) {
        AppUser actor = currentUserService.requireCurrentUser();
        if (actor.getRole() != Role.MANAGER) {
            throw new BusinessRuleException("Only MANAGER can send equipment for maintenance");
        }
        Equipment eq = equipmentRepository.findByEquipmentCode(req.equipmentCode())
                .orElseThrow(() -> new ResourceNotFoundException("Equipment not found"));
        if (eq.getStatus() != EquipmentStatus.ON_STORE) {
            throw new BusinessRuleException("Equipment must be ON_STORE before maintenance can start");
        }
        Instant now = Instant.now();
        MaintenanceRecord m = new MaintenanceRecord();
        m.setEquipment(eq);
        m.setReason(req.reason().trim());
        m.setDescription(req.description());
        m.setReportedBy(actor);
        m.setApprovedBy(actor);
        m.setStartedBy(actor);
        m.setReportedAt(now);
        m.setApprovedAt(now);
        m.setStartedAt(now);
        m.setStatus(MaintenanceStatus.IN_PROGRESS);
        maintenanceRepository.save(m);
        eq.setStatus(EquipmentStatus.UNDER_MAINTENANCE);
        equipmentRepository.save(eq);
        auditLogService.createAudit(actor, AuditAction.CREATE_MAINTENANCE.name(), "maintenance", m.getId(), null, "{\"status\":\"IN_PROGRESS\"}");
        auditLogService.createAudit(actor, AuditAction.UPDATE_EQUIPMENT.name(), "equipment", eq.getId(), "{\"status\":\"ON_STORE\"}", "{\"status\":\"UNDER_MAINTENANCE\"}");
        return m.getId();
    }

    @Transactional
    public Long completeForEquipment(String equipmentCode) {
        AppUser actor = currentUserService.requireCurrentUser();
        if (actor.getRole() != Role.MANAGER && actor.getRole() != Role.STORE_MANAGER) {
            throw new BusinessRuleException("Only MANAGER or STORE_MANAGER can return equipment from maintenance");
        }
        MaintenanceRecord record = maintenanceRepository
                .findFirstByEquipmentEquipmentCodeAndStatusOrderByIdDesc(equipmentCode, MaintenanceStatus.IN_PROGRESS)
                .orElseThrow(() -> new BusinessRuleException("No maintenance in progress for this equipment"));
        update(record.getId(), new UpdateMaintenanceRequest(MaintenanceStatus.COMPLETED, null, null, null));
        return record.getId();
    }

    @Transactional
    public MaintenanceRecord update(Long id, UpdateMaintenanceRequest req) {
        AppUser actor = currentUserService.requireCurrentUser();
        MaintenanceRecord m = maintenanceRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Maintenance not found"));

        if (req.status() != null) {
            boolean completing = m.getStatus() == MaintenanceStatus.IN_PROGRESS && req.status() == MaintenanceStatus.COMPLETED;
            if (actor.getRole() != Role.MANAGER && !(actor.getRole() == Role.STORE_MANAGER && completing)) {
                throw new BusinessRuleException("Only MANAGER can change maintenance status; STORE_MANAGER can complete maintenance");
            }
            if (m.getStatus() == MaintenanceStatus.REQUESTED && req.status() == MaintenanceStatus.APPROVED) {
                m.setStatus(MaintenanceStatus.APPROVED);
                m.setApprovedBy(actor);
                m.setApprovedAt(Instant.now());
            } else if (m.getStatus() == MaintenanceStatus.APPROVED && req.status() == MaintenanceStatus.IN_PROGRESS) {
                // equipment must be at store before IN_PROGRESS
                Equipment eq = m.getEquipment();
                if (eq.getStatus() != EquipmentStatus.ON_STORE) throw new BusinessRuleException("Equipment must be ON_STORE before maintenance can start");
                m.setStatus(MaintenanceStatus.IN_PROGRESS);
                m.setStartedBy(actor);
                m.setStartedAt(Instant.now());
                eq.setStatus(EquipmentStatus.UNDER_MAINTENANCE);
                equipmentRepository.save(eq);
            } else if (m.getStatus() == MaintenanceStatus.IN_PROGRESS && req.status() == MaintenanceStatus.COMPLETED) {
                Equipment eq = m.getEquipment();
                if (eq.getStatus() != EquipmentStatus.UNDER_MAINTENANCE) {
                    throw new BusinessRuleException("Equipment must be UNDER_MAINTENANCE before returning to store");
                }
                m.setStatus(MaintenanceStatus.COMPLETED);
                m.setCompletedBy(actor);
                m.setCompletedAt(Instant.now());
                m.setConditionAfter(req.conditionAfter());
                m.setCost(req.cost());
                m.setNotes(req.notes());
                eq.setStatus(EquipmentStatus.ON_STORE);
                eq.setCurrentWorksite(null);
                if (req.conditionAfter() != null) eq.setCondition(req.conditionAfter());
                equipmentRepository.save(eq);
                auditLogService.createAudit(actor, AuditAction.UPDATE_EQUIPMENT.name(), "equipment", eq.getId(), "{\"status\":\"UNDER_MAINTENANCE\"}", "{\"status\":\"ON_STORE\"}");
            } else {
                throw new BusinessRuleException("Invalid maintenance state transition");
            }
            maintenanceRepository.save(m);
            auditLogService.createAudit(actor, AuditAction.UPDATE_MAINTENANCE.name(), "maintenance", m.getId(), null, "{\"status\":\"" + m.getStatus().name() + "\"}");
            return m;
        }
        throw new BusinessRuleException("Unsupported update");
    }
}
