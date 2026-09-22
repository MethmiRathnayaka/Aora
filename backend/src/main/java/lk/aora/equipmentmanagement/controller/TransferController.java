package lk.aora.equipmentmanagement.controller;

import jakarta.validation.Valid;
import lk.aora.equipmentmanagement.dto.PageResponse;
import lk.aora.equipmentmanagement.dto.transfer.CreateTransferRequest;
import lk.aora.equipmentmanagement.dto.transfer.TransferDto;
import lk.aora.equipmentmanagement.dto.transfer.UpdateTransferRequest;
import lk.aora.equipmentmanagement.dto.transfer.ReceiveAtStoreRequest;
import lk.aora.equipmentmanagement.entity.TransferStatus;
import lk.aora.equipmentmanagement.service.TransferService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/v1/transfers")
public class TransferController {

    private final TransferService service;

    public TransferController(TransferService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<PageResponse<TransferDto>> list(
            @RequestParam(name = "status", required = false) TransferStatus status,
            @RequestParam(name = "equipmentCode", required = false) String equipmentCode,
            @RequestParam(name = "fromWorksiteId", required = false) Long fromWorksiteId,
            @RequestParam(name = "toWorksiteId", required = false) Long toWorksiteId,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable
    ) {
        var page = service.list(Optional.ofNullable(status), Optional.ofNullable(equipmentCode), Optional.ofNullable(fromWorksiteId), Optional.ofNullable(toWorksiteId), pageable);
        var resp = new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TransferDto> get(@PathVariable Long id) {
        return ResponseEntity.ok(service.get(id));
    }

    @PostMapping
    public ResponseEntity<TransferDto> create(@Valid @RequestBody CreateTransferRequest req) {
        var t = service.create(req);
        return ResponseEntity.status(201).body(t);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<TransferDto> update(@PathVariable Long id, @RequestBody UpdateTransferRequest req) {
        return ResponseEntity.ok(service.update(id, req));
    }

    @PostMapping("/receive-at-store")
    public ResponseEntity<TransferDto> receiveAtStore(@Valid @RequestBody ReceiveAtStoreRequest req) {
        return ResponseEntity.status(201).body(service.receiveAtStore(req));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
