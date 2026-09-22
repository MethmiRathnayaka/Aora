package lk.aora.equipmentmanagement.dto.worksite;

import lk.aora.equipmentmanagement.entity.WorksiteStatus;

public record WorksiteSummaryDto(
	Long worksiteId,
	String name,
	String projectCode,
	WorksiteStatus status,
	Long siteAdminId,
	String address
) {
}