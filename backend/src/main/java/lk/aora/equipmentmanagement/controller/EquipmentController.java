package lk.aora.equipmentmanagement.controller;

import jakarta.validation.Valid;
import lk.aora.equipmentmanagement.dto.PageResponse;
import lk.aora.equipmentmanagement.dto.equipment.CreateEquipmentRequest;
import lk.aora.equipmentmanagement.dto.equipment.EquipmentDetailDto;
import lk.aora.equipmentmanagement.dto.equipment.EquipmentSummaryDto;
import lk.aora.equipmentmanagement.dto.equipment.UpdateEquipmentRequest;
import lk.aora.equipmentmanagement.entity.EquipmentCondition;
import lk.aora.equipmentmanagement.entity.EquipmentStatus;
import lk.aora.equipmentmanagement.service.EquipmentService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/v1/equipment")
public class EquipmentController {

    private final EquipmentService service;

    public EquipmentController(EquipmentService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<PageResponse<EquipmentSummaryDto>> list(
            @RequestParam(name = "status", required = false) EquipmentStatus status,
            @RequestParam(name = "currentWorksiteId", required = false) Long currentWorksiteId,
            @RequestParam(name = "equipmentTypeId", required = false) Long equipmentTypeId,
            @RequestParam(name = "brand", required = false) String brand,
            @RequestParam(name = "condition", required = false) EquipmentCondition condition,
            @RequestParam(name = "search", required = false) String search,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable
    ) {
        var page = service.list(Optional.ofNullable(status), Optional.ofNullable(currentWorksiteId), Optional.ofNullable(equipmentTypeId), Optional.ofNullable(brand), Optional.ofNullable(condition), Optional.ofNullable(search), pageable);
        var resp = new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/{code}")
    public ResponseEntity<EquipmentDetailDto> getByCode(@PathVariable String code) {
        return ResponseEntity.ok(service.getByCode(code));
    }

    @PostMapping
    public ResponseEntity<EquipmentDetailDto> create(@Valid @RequestBody CreateEquipmentRequest req) {
        var dto = service.create(req);
        return ResponseEntity.status(201).body(dto);
    }

    @PatchMapping("/{code}")
    public ResponseEntity<EquipmentDetailDto> patch(@PathVariable String code, @RequestBody UpdateEquipmentRequest req) {
        return ResponseEntity.ok(service.update(code, req));
    }
}