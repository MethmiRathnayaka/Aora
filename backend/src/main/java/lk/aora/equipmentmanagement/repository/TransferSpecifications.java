package lk.aora.equipmentmanagement.repository;

import jakarta.persistence.criteria.Predicate;
import lk.aora.equipmentmanagement.entity.EquipmentTransfer;
import lk.aora.equipmentmanagement.entity.TransferStatus;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class TransferSpecifications {

    private TransferSpecifications() {
    }

    public static Specification<EquipmentTransfer> filter(
            Optional<TransferStatus> status,
            Optional<String> equipmentCode,
            Optional<Long> fromWorksiteId,
            Optional<Long> toWorksiteId
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            status.ifPresent(s -> predicates.add(cb.equal(root.get("status"), s)));
            fromWorksiteId.ifPresent(id -> predicates.add(cb.equal(root.get("fromWorksite").get("id"), id)));
            toWorksiteId.ifPresent(id -> predicates.add(cb.equal(root.get("toWorksite").get("id"), id)));

            equipmentCode.ifPresent(code -> predicates.add(cb.equal(root.get("equipment").get("equipmentCode"), code)));

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
