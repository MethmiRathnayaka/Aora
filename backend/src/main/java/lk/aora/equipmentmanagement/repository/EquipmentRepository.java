package lk.aora.equipmentmanagement.repository;

import java.util.List;
import java.util.Optional;

import lk.aora.equipmentmanagement.entity.Equipment;
import lk.aora.equipmentmanagement.entity.EquipmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface EquipmentRepository extends JpaRepository<Equipment, Long>, JpaSpecificationExecutor<Equipment> {
    Optional<Equipment> findByEquipmentCode(String equipmentCode);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select e from Equipment e where e.equipmentCode = :code")
    Optional<Equipment> findForTransferByCode(@org.springframework.data.repository.query.Param("code") String code);

    boolean existsByEquipmentCode(String equipmentCode);

    long countByEquipmentCodeStartingWith(String prefix);

    List<Equipment> findByStatus(EquipmentStatus status);
}
