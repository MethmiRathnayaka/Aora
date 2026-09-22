package lk.aora.equipmentmanagement.repository;

import java.util.Optional;

import lk.aora.equipmentmanagement.entity.Worksite;
import lk.aora.equipmentmanagement.entity.WorksiteStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorksiteRepository extends JpaRepository<Worksite, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select w from Worksite w where w.id = :id")
    Optional<Worksite> findForAssignmentById(@org.springframework.data.repository.query.Param("id") Long id);

    java.util.List<Worksite> findBySiteAdminId(Long userId);

    Optional<Worksite> findByProjectCode(String projectCode);

    Page<Worksite> findByStatus(WorksiteStatus status, Pageable pageable);

    Page<Worksite> findByNameContainingIgnoreCaseOrProjectCodeContainingIgnoreCase(String name, String projectCode, Pageable pageable);
}
