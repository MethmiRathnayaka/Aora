package lk.aora.equipmentmanagement.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import lk.aora.equipmentmanagement.dto.transfer.CreateTransferRequest;
import lk.aora.equipmentmanagement.dto.transfer.UpdateTransferRequest;
import lk.aora.equipmentmanagement.dto.transfer.TransferDto;
import lk.aora.equipmentmanagement.dto.transfer.ReceiveAtStoreRequest;
import lk.aora.equipmentmanagement.entity.*;
import lk.aora.equipmentmanagement.exception.BusinessRuleException;
import lk.aora.equipmentmanagement.exception.ResourceNotFoundException;
import lk.aora.equipmentmanagement.repository.*;
import lk.aora.equipmentmanagement.security.CurrentUserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransferService {

    private static final Logger logger = LoggerFactory.getLogger(TransferService.class);

    private final EquipmentTransferRepository transferRepository;
    private final EquipmentRepository equipmentRepository;
    private final WorksiteRepository worksiteRepository;
    private final StoreRepository storeRepository;
    private final CurrentUserService currentUserService;
    private final AuditLogService auditLogService;

    public TransferService(EquipmentTransferRepository transferRepository, EquipmentRepository equipmentRepository, WorksiteRepository worksiteRepository, StoreRepository storeRepository, CurrentUserService currentUserService, AuditLogService auditLogService) {
        this.transferRepository = transferRepository;
        this.equipmentRepository = equipmentRepository;
        this.worksiteRepository = worksiteRepository;
        this.storeRepository = storeRepository;
        this.currentUserService = currentUserService;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public Page<TransferDto> list(Optional<TransferStatus> status, Optional<String> equipmentCode, Optional<Long> fromWorksiteId, Optional<Long> toWorksiteId, Pageable pageable) {
        return transferRepository.findAll(
                lk.aora.equipmentmanagement.repository.TransferSpecifications.filter(status, equipmentCode, fromWorksiteId, toWorksiteId),
                pageable
        ).map(this::toDto);
    }

    @Transactional(readOnly = true)
    public TransferDto get(Long id) {
        EquipmentTransfer transfer = transferRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Transfer not found"));
        return toDto(transfer);
    }

    @Transactional
    public TransferDto create(CreateTransferRequest req) {
        AppUser actor = currentUserService.requireCurrentUser();

        // Validate from/to
        LocationType fromType = req.from().type();
        LocationType toType = req.to().type();
        if (fromType == toType && fromType == LocationType.WORKSITE && req.from().worksiteId().equals(req.to().worksiteId())) {
            throw new BusinessRuleException("From and To cannot be the same worksite");
        }

        // Authorization: store-origin: MANAGER or STORE_MANAGER; worksite-origin: SITE_ADMIN of that worksite
        if (fromType == LocationType.STORE) {
            if (!(actor.getRole() == Role.MANAGER || actor.getRole() == Role.STORE_MANAGER)) throw new BusinessRuleException("Not authorized to create store-origin transfer");
        } else {
            // worksite-origin, actor must be site admin of from worksite
            if (actor.getRole() != Role.SITE_ADMIN) throw new BusinessRuleException("Only SITE_ADMIN of the source worksite can create worksite-origin transfers");
            if (actor.getCurrentWorksite() == null || !actor.getCurrentWorksite().getId().equals(req.from().worksiteId())) throw new BusinessRuleException("SITE_ADMIN is not assigned to the source worksite");
        }

        EquipmentTransfer t = new EquipmentTransfer();
        t.setFromLocationType(fromType);
        if (fromType == LocationType.WORKSITE) t.setFromWorksite(worksiteRepository.findById(req.from().worksiteId()).orElseThrow(() -> new ResourceNotFoundException("From worksite not found")));
        t.setToLocationType(toType);
        if (toType == LocationType.WORKSITE) t.setToWorksite(worksiteRepository.findById(req.to().worksiteId()).orElseThrow(() -> new ResourceNotFoundException("To worksite not found")));
        t.setDeliveryPersonName(req.deliveryPerson().name());
        t.setDeliveryPersonPhone(req.deliveryPerson().phone());
        t.setStatus(TransferStatus.IN_TRANSIT);
        t.setDispatchedBy(actor);
        t.setDispatchedAt(Instant.now());
        t.setNotes(req.notes());


        CreateTransferRequest.TransferEquipmentRequest er = req.equipment();
        Equipment eq = equipmentRepository.findForTransferByCode(er.equipmentCode()).orElseThrow(() -> new ResourceNotFoundException("Equipment not found: " + er.equipmentCode()));
        logger.info("Transfer create: equipmentCode={} equipmentId={} status={} currentWorksiteId={}", er.equipmentCode(), eq.getId(), eq.getStatus(), eq.getCurrentWorksite() == null ? null : eq.getCurrentWorksite().getId());
        if (eq.getStatus() == EquipmentStatus.RETIRED || eq.getStatus() == EquipmentStatus.UNDER_MAINTENANCE) throw new BusinessRuleException("Equipment " + er.equipmentCode() + " cannot be transferred");
        if (fromType == LocationType.STORE) {
    
            if (eq.getStatus() != EquipmentStatus.ON_STORE) throw new BusinessRuleException("Equipment " + er.equipmentCode() + " is not on store");
        } else {
            if (eq.getStatus() != EquipmentStatus.ON_SITE) throw new BusinessRuleException("Equipment " + er.equipmentCode() + " is not on site");
            if (eq.getCurrentWorksite() == null || !eq.getCurrentWorksite().getId().equals(req.from().worksiteId())) throw new BusinessRuleException("Equipment " + er.equipmentCode() + " is not at the specified source worksite");
        }
        if (eq.getStatus() == EquipmentStatus.TRANSFER) throw new BusinessRuleException("Equipment " + er.equipmentCode() + " is already in transfer");

        t.setEquipment(eq);
        t.setConditionBefore(er.condition());

        eq.setStatus(EquipmentStatus.TRANSFER);
        equipmentRepository.save(eq);

        transferRepository.save(t);
        auditLogService.createAudit(actor, AuditAction.CREATE_TRANSFER.name(), "transfer", t.getId(), null, "{\"status\":\"IN_TRANSIT\"}");
        return toDto(t);
    }

    @Transactional
    public TransferDto receiveAtStore(ReceiveAtStoreRequest req) {
        AppUser actor = currentUserService.requireCurrentUser();
        if (actor.getRole() != Role.MANAGER && actor.getRole() != Role.STORE_MANAGER) {
            throw new BusinessRuleException("Only a manager or store manager can receive equipment at the store");
        }
        if (!Boolean.TRUE.equals(req.receiptConfirmed()) || req.conditionAfter() == null
                || req.deliveryPersonName() == null || req.deliveryPersonName().isBlank()) {
            throw new BusinessRuleException("Confirm physical receipt, equipment condition, and delivery contact");
        }
        Equipment equipment = equipmentRepository.findForTransferByCode(req.equipmentCode())
                .orElseThrow(() -> new ResourceNotFoundException("Equipment not found"));
        if (transferRepository.existsByEquipmentIdAndStatus(equipment.getId(), TransferStatus.IN_TRANSIT)) {
            throw new BusinessRuleException("An active transfer already exists. Check the equipment again and confirm its existing store transfer");
        }
        if (equipment.getStatus() != EquipmentStatus.ON_SITE || equipment.getCurrentWorksite() == null) {
            throw new BusinessRuleException("Only equipment currently at a worksite can be returned directly to the store");
        }
        if (!equipment.getCurrentWorksite().getId().equals(req.fromWorksiteId())) {
            throw new BusinessRuleException("The equipment worksite has changed. Check the equipment again before confirming");
        }
        EquipmentTransfer transfer = new EquipmentTransfer();
        transfer.setEquipment(equipment);
        transfer.setFromLocationType(LocationType.WORKSITE);
        transfer.setFromWorksite(equipment.getCurrentWorksite());
        transfer.setToLocationType(LocationType.STORE);
        transfer.setDeliveryPersonName(req.deliveryPersonName().trim());
        transfer.setDeliveryPersonPhone(req.deliveryPersonPhone());
        transfer.setConditionBefore(equipment.getCondition());
        transfer.setConditionAfter(req.conditionAfter());
        transfer.setDispatchedBy(actor);
        transfer.setReceivedBy(actor);
        Instant now = Instant.now();
        transfer.setDispatchedAt(now);
        transfer.setReceivedAt(now);
        transfer.setStatus(TransferStatus.COMPLETED);
        transfer.setNotes(req.notes());
        calculateRent(transfer, req.unitPrice(), now);
        equipment.setStatus(EquipmentStatus.ON_STORE);
        equipment.setCurrentWorksite(null);
        equipment.setCondition(req.conditionAfter());
        equipmentRepository.save(equipment);
        transferRepository.save(transfer);
        auditLogService.createAudit(actor, AuditAction.CREATE_TRANSFER.name(), "transfer", transfer.getId(), null, "{\"status\":\"COMPLETED\",\"receiptAtStore\":true}");
        auditLogService.createAudit(actor, AuditAction.COMPLETE_TRANSFER.name(), "transfer", transfer.getId(), null, "{\"status\":\"COMPLETED\"}");
        return toDto(transfer);
    }

    @Transactional
    public void delete(Long id) {
        AppUser actor = currentUserService.requireCurrentUser();
        if (actor.getRole() != Role.MANAGER) throw new BusinessRuleException("Only MANAGER can delete transfers");
        EquipmentTransfer transfer = transferRepository.findForUpdateById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transfer not found"));
        if (transfer.getStatus() == TransferStatus.IN_TRANSIT && transfer.getEquipment() != null) {
            Equipment equipment = equipmentRepository.findForTransferByCode(transfer.getEquipment().getEquipmentCode())
                    .orElseThrow(() -> new ResourceNotFoundException("Equipment not found"));
            if (equipment.getStatus() == EquipmentStatus.TRANSFER) {
                equipment.setStatus(transfer.getFromLocationType() == LocationType.STORE ? EquipmentStatus.ON_STORE : EquipmentStatus.ON_SITE);
                equipment.setCurrentWorksite(transfer.getFromLocationType() == LocationType.STORE ? null : transfer.getFromWorksite());
                equipmentRepository.save(equipment);
            }
        }
        auditLogService.createAudit(actor, AuditAction.DELETE_TRANSFER.name(), "transfer", id,
                "{\"status\":\"" + transfer.getStatus().name() + "\",\"equipmentId\":" + transfer.getEquipment().getId() + "}", null);
        transferRepository.delete(transfer);
    }

    @Transactional
    public TransferDto update(Long id, UpdateTransferRequest req) {
        AppUser actor = currentUserService.requireCurrentUser();
        EquipmentTransfer t = transferRepository.findForUpdateById(id).orElseThrow(() -> new ResourceNotFoundException("Transfer not found"));

        if (req.status() != null && req.status().name().equals(TransferStatus.COMPLETED.name())) {
            if (t.getStatus() != TransferStatus.IN_TRANSIT) throw new BusinessRuleException("Transfer is not in transit");
            // Authorization: if destination is worksite -> SITE_ADMIN of that worksite; if destination is store -> STORE_MANAGER or MANAGER
            if (t.getToLocationType() == LocationType.WORKSITE) {
                if (actor.getRole() != Role.SITE_ADMIN) throw new BusinessRuleException("Only SITE_ADMIN can confirm delivery to worksite");
                if (actor.getCurrentWorksite() == null || !actor.getCurrentWorksite().getId().equals(t.getToWorksite().getId())) throw new BusinessRuleException("SITE_ADMIN not assigned to destination");
            } else {
                if (!(actor.getRole() == Role.MANAGER || actor.getRole() == Role.STORE_MANAGER)) throw new BusinessRuleException("Only store manager or manager can confirm delivery to store");
            }

            if (t.getEquipment() == null) throw new BusinessRuleException("No equipment in transfer");
            if (req.equipment() != null) {
                if (!t.getEquipment().getEquipmentCode().equals(req.equipment().equipmentCode())) {
                    throw new BusinessRuleException("Equipment in completion payload does not match transfer equipment");
                }
                t.setConditionAfter(req.equipment().conditionAfter());
            }
            Instant receivedAt = Instant.now();
            if (t.getToLocationType() == LocationType.STORE) calculateRent(t, req.unitPrice(), receivedAt);
            Equipment eq = t.getEquipment();
            if (t.getConditionAfter() != null) eq.setCondition(t.getConditionAfter());
            if (t.getToLocationType() == LocationType.WORKSITE) {
                eq.setStatus(EquipmentStatus.ON_SITE);
                eq.setCurrentWorksite(t.getToWorksite());
            } else {
                eq.setStatus(EquipmentStatus.ON_STORE);
                eq.setCurrentWorksite(null);
            }
            equipmentRepository.save(eq);

            t.setStatus(TransferStatus.COMPLETED);
            t.setReceivedBy(actor);
            t.setReceivedAt(receivedAt);
            if (req.notes() != null) t.setNotes(req.notes());
            transferRepository.save(t);
            auditLogService.createAudit(actor, AuditAction.COMPLETE_TRANSFER.name(), "transfer", t.getId(), "{\"status\":\"IN_TRANSIT\"}", "{\"status\":\"COMPLETED\"}");
            return toDto(t);
        }

        if (req.status() != null && req.status().name().equals(TransferStatus.CANCELLED.name())) {
            if (t.getStatus() != TransferStatus.IN_TRANSIT) throw new BusinessRuleException("Only IN_TRANSIT transfers can be cancelled");
            // authorization: allow dispatcher or manager
            if (!(actor.getRole() == Role.MANAGER || actor.getId().equals(t.getDispatchedBy() == null ? null : t.getDispatchedBy().getId()))) {
                throw new BusinessRuleException("Not authorized to cancel transfer");
            }
            Equipment eq = t.getEquipment();
            if (eq != null) {
                if (t.getFromLocationType() == LocationType.STORE) {
                    eq.setStatus(EquipmentStatus.ON_STORE);
                    eq.setCurrentWorksite(null);
                } else {
                    eq.setStatus(EquipmentStatus.ON_SITE);
                    eq.setCurrentWorksite(t.getFromWorksite());
                }
                equipmentRepository.save(eq);
            }
            t.setStatus(TransferStatus.CANCELLED);
            if (req.notes() != null) t.setNotes(req.notes());
            transferRepository.save(t);
            auditLogService.createAudit(actor, AuditAction.CANCEL_TRANSFER.name(), "transfer", t.getId(), "{\"status\":\"IN_TRANSIT\"}", "{\"status\":\"CANCELLED\"}");
            return toDto(t);
        }

        // allow updating notes only while IN_TRANSIT
        if (req.notes() != null) {
            t.setNotes(req.notes());
            transferRepository.save(t);
            return toDto(t);
        }
        throw new BusinessRuleException("Unsupported update");
    }

    private void calculateRent(EquipmentTransfer transfer, java.math.BigDecimal price, Instant returnedAt) {
        if (price == null || price.signum() < 0 || price.scale() > 2
                || price.compareTo(new java.math.BigDecimal("999999999999.99")) > 0) {
            throw new BusinessRuleException("Enter a non-negative daily unit price with at most two decimal places");
        }
        List<EquipmentTransfer> history = transferRepository.findByEquipmentId(transfer.getEquipment().getId());
        Instant startedAt = history.stream()
                .filter(t -> t.getStatus() == TransferStatus.COMPLETED && t.getFromLocationType() == LocationType.STORE
                        && t.getToLocationType() == LocationType.WORKSITE && t.getDispatchedAt() != null)
                .map(EquipmentTransfer::getDispatchedAt).max(Instant::compareTo)
                .orElseThrow(() -> new BusinessRuleException("Cannot calculate rent: original store dispatch record is missing"));
        boolean alreadyReturned = history.stream().anyMatch(t -> t != transfer
                && t.getStatus() == TransferStatus.COMPLETED && t.getToLocationType() == LocationType.STORE
                && t.getReceivedAt() != null && !t.getReceivedAt().isBefore(startedAt));
        if (alreadyReturned || returnedAt.isBefore(startedAt)) {
            throw new BusinessRuleException("Cannot calculate rent: no valid store dispatch exists for this rental period");
        }
        long days = rentalDays(startedAt, returnedAt);
        transfer.setRentalStartedAt(startedAt);
        transfer.setRentalDays(days);
        transfer.setUnitPrice(price.setScale(2));
        transfer.setRentTotal(price.multiply(java.math.BigDecimal.valueOf(days)).setScale(2));
    }

    static long rentalDays(Instant startedAt, Instant returnedAt) {
        java.time.Duration elapsed = java.time.Duration.between(startedAt, returnedAt);
        long wholeDays = elapsed.toDays();
        return Math.max(1, wholeDays + (elapsed.minusDays(wholeDays).isZero() ? 0 : 1));
    }

    private TransferDto toDto(EquipmentTransfer transfer) {
        Equipment equipment = transfer.getEquipment();
        return new TransferDto(
                transfer.getId(),
                transfer.getFromLocationType() == null ? null : transfer.getFromLocationType().name(),
                transfer.getFromWorksite() == null ? null : transfer.getFromWorksite().getId(),
                transfer.getToLocationType() == null ? null : transfer.getToLocationType().name(),
                transfer.getToWorksite() == null ? null : transfer.getToWorksite().getId(),
                transfer.getDeliveryPersonName(),
                transfer.getDeliveryPersonPhone(),
                transfer.getStatus() == null ? null : transfer.getStatus().name(),
                transfer.getDispatchedBy() == null ? null : transfer.getDispatchedBy().getId(),
                transfer.getDispatchedAt(),
                transfer.getReceivedBy() == null ? null : transfer.getReceivedBy().getId(),
                transfer.getReceivedAt(),
                equipment == null ? List.of() : List.of(new TransferDto.TransferItemDto(
                        equipment.getEquipmentCode(),
                        transfer.getConditionBefore() == null ? null : transfer.getConditionBefore().name(),
                        transfer.getConditionAfter() == null ? null : transfer.getConditionAfter().name())),
                transfer.getNotes(), transfer.getRentalStartedAt(), transfer.getRentalDays(), transfer.getUnitPrice(), transfer.getRentTotal());
    }
}
