package lk.aora.equipmentmanagement.controller;

import jakarta.validation.Valid;
import lk.aora.equipmentmanagement.dto.PageResponse;
import lk.aora.equipmentmanagement.dto.equipment.CreateEquipmentTypeRequest;
import lk.aora.equipmentmanagement.dto.equipment.EquipmentTypeDto;
import lk.aora.equipmentmanagement.dto.equipment.UpdateEquipmentTypeRequest;
import lk.aora.equipmentmanagement.service.EquipmentTypeService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/equipment-types")
public class EquipmentTypeController {

    private final EquipmentTypeService service;

    public EquipmentTypeController(EquipmentTypeService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<PageResponse<EquipmentTypeDto>> list(@ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        var page = service.list(pageable);
        var resp = new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/{id}")
    public ResponseEntity<EquipmentTypeDto> get(@PathVariable Long id) {
        return ResponseEntity.ok(service.get(id));
    }

    @PostMapping
    public ResponseEntity<EquipmentTypeDto> create(@Valid @RequestBody CreateEquipmentTypeRequest req) {
        var dto = service.create(req);
        return ResponseEntity.created(URI.create("/api/v1/equipment-types/" + dto.equipmentTypeId())).body(dto);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<EquipmentTypeDto> update(@PathVariable Long id, @RequestBody UpdateEquipmentTypeRequest req) {
        return ResponseEntity.ok(service.update(id, req));
    }
}