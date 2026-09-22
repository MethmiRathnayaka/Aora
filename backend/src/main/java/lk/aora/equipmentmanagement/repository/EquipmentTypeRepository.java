package lk.aora.equipmentmanagement.repository;

import java.util.Optional;

import lk.aora.equipmentmanagement.entity.EquipmentType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EquipmentTypeRepository extends JpaRepository<EquipmentType, Long> {
    Optional<EquipmentType> findByCodePrefix(String codePrefix);
    Optional<EquipmentType> findFirstByNameIgnoreCase(String name);
}
