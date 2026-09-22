package lk.aora.equipmentmanagement.dto.audit;

import java.time.Instant;

public record AuditLogDto(Long auditId, Long userId, String userName, String action, String entityType, Long entityId, String oldValues, String newValues, Instant createdAt) {
}