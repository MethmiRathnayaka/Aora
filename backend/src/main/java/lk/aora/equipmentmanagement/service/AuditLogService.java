package lk.aora.equipmentmanagement.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import lk.aora.equipmentmanagement.entity.AppUser;
import lk.aora.equipmentmanagement.entity.AuditLog;
import lk.aora.equipmentmanagement.repository.AuditLogRepository;

@Service
public class AuditLogService {

	private final AuditLogRepository auditLogRepository;

	public AuditLogService(AuditLogRepository auditLogRepository) {
		this.auditLogRepository = auditLogRepository;
	}

	@Transactional
	public void createAudit(AppUser user, String action, String entityType, Long entityId, String oldValues, String newValues) {
		AuditLog a = new AuditLog();
		a.setUser(user);
		a.setAction(action);
		a.setEntityType(entityType);
		a.setEntityId(entityId == null ? 0L : entityId);
		a.setOldValues(oldValues);
		a.setNewValues(newValues);
		a.setCreatedAt(Instant.now());
		auditLogRepository.save(a);
	}
}

