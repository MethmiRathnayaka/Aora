package lk.aora.equipmentmanagement.controller;

import lk.aora.equipmentmanagement.dto.PageResponse;
import lk.aora.equipmentmanagement.dto.store.UpdateStoreRequest;
import lk.aora.equipmentmanagement.entity.Store;
import lk.aora.equipmentmanagement.service.StoreService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/stores")
public class StoreController {

    private final StoreService service;

    public StoreController(StoreService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<PageResponse<Store>> list(@ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        var page = service.list(pageable);
        var resp = new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Store> get(@PathVariable Long id) {
        return ResponseEntity.ok(service.get(id));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<Store> update(@PathVariable Long id, @RequestBody UpdateStoreRequest req) {
        return ResponseEntity.ok(service.update(id, req));
    }
}
