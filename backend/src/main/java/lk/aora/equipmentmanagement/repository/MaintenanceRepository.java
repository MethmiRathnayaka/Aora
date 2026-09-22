package lk.aora.equipmentmanagement.repository;

import java.util.List;

import lk.aora.equipmentmanagement.entity.MaintenanceRecord;
import lk.aora.equipmentmanagement.entity.MaintenanceStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MaintenanceRepository extends JpaRepository<MaintenanceRecord, Long> {
    List<MaintenanceRecord> findByStatus(MaintenanceStatus status);
    java.util.Optional<MaintenanceRecord> findFirstByEquipmentEquipmentCodeAndStatusOrderByIdDesc(String equipmentCode, MaintenanceStatus status);
}
