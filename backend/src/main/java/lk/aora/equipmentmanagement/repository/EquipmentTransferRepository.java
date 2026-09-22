package lk.aora.equipmentmanagement.repository;

import java.util.List;

import lk.aora.equipmentmanagement.entity.EquipmentTransfer;
import lk.aora.equipmentmanagement.entity.TransferStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface EquipmentTransferRepository extends JpaRepository<EquipmentTransfer, Long>, JpaSpecificationExecutor<EquipmentTransfer> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select t from EquipmentTransfer t where t.id = :id")
    java.util.Optional<EquipmentTransfer> findForUpdateById(@org.springframework.data.repository.query.Param("id") Long id);
    List<EquipmentTransfer> findByStatus(TransferStatus status);
    boolean existsByEquipmentIdAndStatus(Long equipmentId, TransferStatus status);
    List<EquipmentTransfer> findByToWorksiteId(Long toWorksiteId);
    List<EquipmentTransfer> findByEquipmentId(Long equipmentId);
    List<EquipmentTransfer> findByEquipmentEquipmentCode(String equipmentCode);
}
