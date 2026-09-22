package lk.aora.equipmentmanagement.service;

import java.time.format.DateTimeFormatter;
import java.util.Optional;

import lk.aora.equipmentmanagement.dto.PageResponse;
import lk.aora.equipmentmanagement.dto.equipment.CreateEquipmentRequest;
import lk.aora.equipmentmanagement.dto.equipment.EquipmentDetailDto;
import lk.aora.equipmentmanagement.dto.equipment.EquipmentSummaryDto;
import lk.aora.equipmentmanagement.dto.equipment.EquipmentTypeDto;
import lk.aora.equipmentmanagement.dto.equipment.UpdateEquipmentRequest;
import lk.aora.equipmentmanagement.dto.worksite.WorksiteSummaryDto;
import lk.aora.equipmentmanagement.entity.*;
import lk.aora.equipmentmanagement.exception.BusinessRuleException;
import lk.aora.equipmentmanagement.exception.ResourceNotFoundException;
import lk.aora.equipmentmanagement.repository.*;
import lk.aora.equipmentmanagement.security.CurrentUserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EquipmentService {

    private final EquipmentRepository equipmentRepository;
    private final EquipmentTypeRepository equipmentTypeRepository;
    private final WorksiteRepository worksiteRepository;
    private final CurrentUserService currentUserService;
    private final AuditLogService auditLogService;

    public EquipmentService(EquipmentRepository equipmentRepository, EquipmentTypeRepository equipmentTypeRepository, WorksiteRepository worksiteRepository, CurrentUserService currentUserService, AuditLogService auditLogService) {
        this.equipmentRepository = equipmentRepository;
        this.equipmentTypeRepository = equipmentTypeRepository;
        this.worksiteRepository = worksiteRepository;
        this.currentUserService = currentUserService;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public Page<EquipmentSummaryDto> list(Optional<EquipmentStatus> status, Optional<Long> currentWorksiteId, Optional<Long> equipmentTypeId, Optional<String> brand, Optional<EquipmentCondition> condition, Optional<String> search, Pageable pageable) {
        // All roles can browse the fleet; worksite scope is an optional filter.
        var spec = lk.aora.equipmentmanagement.repository.EquipmentSpecifications.filter(status, currentWorksiteId, equipmentTypeId, brand, condition, search);
        Page<Equipment> page = equipmentRepository.findAll(spec, pageable);
        return page.map(this::toSummaryDto);
    }

    @Transactional(readOnly = true)
    public EquipmentDetailDto getByCode(String equipmentCode) {
        Equipment e = equipmentRepository.findByEquipmentCode(equipmentCode).orElseThrow(() -> new ResourceNotFoundException("Equipment not found"));
        return toDetailDto(e);
    }

    @Transactional
    public EquipmentDetailDto create(CreateEquipmentRequest req) {
        AppUser actor = currentUserService.requireCurrentUser();
        if (!(actor.getRole() == Role.MANAGER || actor.getRole() == Role.STORE_MANAGER)) {
            throw new BusinessRuleException("Not authorized to create equipment");
        }
        EquipmentType type = equipmentTypeRepository.findById(req.equipmentTypeId()).orElseThrow(() -> new ResourceNotFoundException("EquipmentType not found"));
        String prefix = type.getCodePrefix();
        long count = equipmentRepository.countByEquipmentCodeStartingWith(prefix + "-");
        String code = String.format("%s-%03d", prefix, count + 1);
        Equipment e = new Equipment();
        e.setEquipmentCode(code);
        e.setEquipmentType(type);
        e.setBrand(req.brand());
        e.setModel(req.model());
        e.setSerialNumber(req.serialNumber());
        e.setCondition(req.condition());
        e.setPurchaseDate(req.purchaseDate());
        e.setStatus(EquipmentStatus.ON_STORE);
        equipmentRepository.save(e);
        auditLogService.createAudit(actor, AuditAction.CREATE_EQUIPMENT.name(), "equipment", e.getId(), null, "{\"equipmentCode\":\"" + code + "\"}");
        return toDetailDto(e);
    }

    @Transactional
    public EquipmentDetailDto update(String equipmentCode, UpdateEquipmentRequest req) {
        AppUser actor = currentUserService.requireCurrentUser();
        Equipment e = equipmentRepository.findByEquipmentCode(equipmentCode).orElseThrow(() -> new ResourceNotFoundException("Equipment not found"));
        // SITE_ADMIN are not allowed to modify equipment
        if (actor.getRole() == Role.SITE_ADMIN) {
            throw new BusinessRuleException("SITE_ADMIN cannot update equipment");
        }

        if (req.status() != null) {
            if (req.status() == EquipmentStatus.RETIRED) {
                if (actor.getRole() != Role.MANAGER) throw new BusinessRuleException("Only MANAGER can retire equipment");
                if (e.getStatus() != EquipmentStatus.ON_STORE) throw new BusinessRuleException("Equipment must be ON_STORE to retire");
                e.setStatus(EquipmentStatus.RETIRED);
                auditLogService.createAudit(actor, AuditAction.RETIRE_EQUIPMENT.name(), "equipment", e.getId(), "{\"status\":\"ON_STORE\"}", "{\"status\":\"RETIRED\"}");
                equipmentRepository.save(e);
                return toDetailDto(e);
            }

            if (req.status() == EquipmentStatus.UNDER_MAINTENANCE) {
                // Disallow setting UNDER_MAINTENANCE directly via equipment update. Maintenance workflow must be used.
                throw new BusinessRuleException("Cannot set status to UNDER_MAINTENANCE via equipment update; use maintenance workflow");
            }

            // other status changes must follow business rules; not implemented yet
            throw new BusinessRuleException("Status transition not supported in this endpoint");
        }
        // Only MANAGER and STORE_MANAGER can update equipment attributes
        if (!(actor.getRole() == Role.MANAGER || actor.getRole() == Role.STORE_MANAGER)) {
            throw new BusinessRuleException("Only MANAGER or STORE_MANAGER can update equipment");
        }

        if (req.brand() != null) e.setBrand(req.brand());
        if (req.model() != null) e.setModel(req.model());
        if (req.serialNumber() != null) e.setSerialNumber(req.serialNumber());
        if (req.condition() != null) e.setCondition(req.condition());
        if (req.purchaseDate() != null) e.setPurchaseDate(req.purchaseDate());
        equipmentRepository.save(e);
        auditLogService.createAudit(actor, AuditAction.UPDATE_EQUIPMENT.name(), "equipment", e.getId(), null, "{\"brand\":\"" + e.getBrand() + "\"}");
        return toDetailDto(e);
    }

    private EquipmentSummaryDto toSummaryDto(Equipment e) {
        EquipmentTypeDto type = new EquipmentTypeDto(e.getEquipmentType().getId(), e.getEquipmentType().getName(), e.getEquipmentType().getCodePrefix());
        WorksiteSummaryDto ws = e.getCurrentWorksite() == null ? null : new WorksiteSummaryDto(
            e.getCurrentWorksite().getId(),
            e.getCurrentWorksite().getName(),
            e.getCurrentWorksite().getProjectCode(),
            e.getCurrentWorksite().getStatus(),
            e.getCurrentWorksite().getSiteAdmin() == null ? null : e.getCurrentWorksite().getSiteAdmin().getId(),
            e.getCurrentWorksite().getAddress());
        return new EquipmentSummaryDto(e.getId(), e.getEquipmentCode(), type, e.getBrand(), e.getModel(), e.getSerialNumber(), e.getStatus(), e.getCondition(), ws, e.getPurchaseDate(), e.getCreatedAt(), e.getUpdatedAt());
    }

    private EquipmentDetailDto toDetailDto(Equipment e) {
        Long worksiteId = e.getCurrentWorksite() == null ? null : e.getCurrentWorksite().getId();
        DateTimeFormatter fmt = DateTimeFormatter.ISO_INSTANT;
        return new EquipmentDetailDto(e.getId(), e.getEquipmentCode(), e.getEquipmentType().getId(), e.getBrand(), e.getModel(), e.getSerialNumber(), e.getStatus().name(), e.getCondition().name(), worksiteId, e.getPurchaseDate() == null ? null : e.getPurchaseDate().toString(), e.getCreatedAt().toString(), e.getUpdatedAt().toString());
    }
}
