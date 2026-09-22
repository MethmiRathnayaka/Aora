package lk.aora.equipmentmanagement.dto.worksite;

import lk.aora.equipmentmanagement.entity.WorksiteStatus;

public record UpdateWorksiteRequest(String name, String address, WorksiteStatus status, Long siteAdminId) {
}