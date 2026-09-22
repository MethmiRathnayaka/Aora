package lk.aora.equipmentmanagement.controller;

import jakarta.validation.Valid;
import lk.aora.equipmentmanagement.dto.PageResponse;
import lk.aora.equipmentmanagement.dto.maintenance.CreateMaintenanceRequest;
import lk.aora.equipmentmanagement.dto.maintenance.UpdateMaintenanceRequest;
import lk.aora.equipmentmanagement.entity.MaintenanceRecord;
import lk.aora.equipmentmanagement.service.MaintenanceService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/maintenance")
public class MaintenanceController {

    private final MaintenanceService service;

    public MaintenanceController(MaintenanceService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<PageResponse<MaintenanceRecord>> list(@ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        var page = service.list(pageable);
        var resp = new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/{id}")
    public ResponseEntity<MaintenanceRecord> get(@PathVariable Long id) {
        return ResponseEntity.ok(service.get(id));
    }

    @PostMapping
    public ResponseEntity<MaintenanceRecord> create(@Valid @RequestBody CreateMaintenanceRequest req) {
        var m = service.create(req);
        return ResponseEntity.status(201).body(m);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<MaintenanceRecord> update(@PathVariable Long id, @RequestBody UpdateMaintenanceRequest req) {
        return ResponseEntity.ok(service.update(id, req));
    }

    @PostMapping("/start")
    public ResponseEntity<java.util.Map<String, Long>> start(@Valid @RequestBody CreateMaintenanceRequest req) {
        Long id = service.start(req);
        return ResponseEntity.created(java.net.URI.create("/api/v1/maintenance/" + id)).body(java.util.Map.of("maintenanceId", id));
    }

    @PostMapping("/equipment/{equipmentCode}/complete")
    public ResponseEntity<java.util.Map<String, Long>> complete(@PathVariable String equipmentCode) {
        return ResponseEntity.ok(java.util.Map.of("maintenanceId", service.completeForEquipment(equipmentCode)));
    }
}
