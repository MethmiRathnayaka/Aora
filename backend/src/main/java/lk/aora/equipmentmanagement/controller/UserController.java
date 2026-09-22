package lk.aora.equipmentmanagement.controller;

import jakarta.validation.Valid;
import lk.aora.equipmentmanagement.dto.PageResponse;
import lk.aora.equipmentmanagement.dto.user.CreateUserRequest;
import lk.aora.equipmentmanagement.dto.user.CreateUserResponseDto;
import lk.aora.equipmentmanagement.dto.user.UpdateUserRequest;
import lk.aora.equipmentmanagement.dto.user.UserSummaryDto;
import lk.aora.equipmentmanagement.entity.Role;
import lk.aora.equipmentmanagement.service.UserService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService service;

    public UserController(UserService service) {
        this.service = service;
    }

    @GetMapping("/me")
    public ResponseEntity<UserSummaryDto> me() {
        return ResponseEntity.ok(service.getCurrentUser());
    }

    @GetMapping
    public ResponseEntity<PageResponse<UserSummaryDto>> list(@RequestParam(name = "role", required = false) Role role,
                                                             @RequestParam(name = "isActive", required = false) Boolean isActive,
                                                             @RequestParam(name = "search", required = false) String search,
                                                             @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        var page = service.list(role, isActive, search, pageable);
        var resp = new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/{id:\\d+}")
    public ResponseEntity<UserSummaryDto> get(@PathVariable Long id) {
        return ResponseEntity.ok(service.get(id));
    }

    @PostMapping
    public ResponseEntity<CreateUserResponseDto> create(@Valid @RequestBody CreateUserRequest req) {
        var dto = service.create(req);
        return ResponseEntity.status(201).body(dto);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}")
    public ResponseEntity<UserSummaryDto> update(@PathVariable Long id, @RequestBody UpdateUserRequest req) {
        return ResponseEntity.ok(service.update(id, req));
    }
}