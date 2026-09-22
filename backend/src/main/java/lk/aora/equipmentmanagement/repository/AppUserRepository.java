package lk.aora.equipmentmanagement.repository;

import java.util.Optional;

import lk.aora.equipmentmanagement.entity.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {
    org.springframework.data.domain.Page<AppUser> findByDeletedFalse(org.springframework.data.domain.Pageable pageable);

    Optional<AppUser> findByAuthUserId(String authUserId);

    boolean existsByAuthUserId(String authUserId);

    boolean existsByEmail(String email);
}