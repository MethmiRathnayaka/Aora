package lk.aora.equipmentmanagement.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import lk.aora.equipmentmanagement.dto.PageResponse;
import lk.aora.equipmentmanagement.dto.worksite.CreateWorksiteRequest;
import lk.aora.equipmentmanagement.dto.worksite.UpdateWorksiteRequest;
import lk.aora.equipmentmanagement.dto.worksite.WorksiteSummaryDto;
import lk.aora.equipmentmanagement.entity.WorksiteStatus;
import lk.aora.equipmentmanagement.service.WorksiteService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/worksites")
public class WorksiteController {

	private final WorksiteService worksiteService;

	public WorksiteController(WorksiteService worksiteService) {
		this.worksiteService = worksiteService;
	}

	@GetMapping
	public ResponseEntity<PageResponse<WorksiteSummaryDto>> listWorksites(
			@RequestParam(name = "status", required = false) WorksiteStatus status,
			@RequestParam(name = "search", required = false) String search,
			@ParameterObject @PageableDefault(size = 20) Pageable pageable
	) {
		var page = worksiteService.listWorksites(Optional.ofNullable(status), Optional.ofNullable(search), pageable);
		var resp = new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
		return ResponseEntity.ok(resp);
	}

	@GetMapping("/{id}")
	public ResponseEntity<WorksiteSummaryDto> getWorksite(@PathVariable Long id) {
		var dto = worksiteService.getWorksite(id);
		return ResponseEntity.ok(dto);
	}

	@PostMapping
	public ResponseEntity<WorksiteSummaryDto> createWorksite(@Valid @RequestBody CreateWorksiteRequest req) {
		var dto = worksiteService.createWorksite(req);
		URI location = URI.create(String.format("/api/v1/worksites/%d", dto.worksiteId()));
		return ResponseEntity.created(location).body(dto);
	}

	@PatchMapping("/{id}")
	public ResponseEntity<WorksiteSummaryDto> updateWorksite(@PathVariable Long id, @RequestBody UpdateWorksiteRequest req) {
		var dto = worksiteService.updateWorksite(id, req);
		return ResponseEntity.ok(dto);
	}
}

