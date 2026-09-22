package lk.aora.equipmentmanagement.repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import lk.aora.equipmentmanagement.entity.Equipment;
import lk.aora.equipmentmanagement.entity.EquipmentCondition;
import lk.aora.equipmentmanagement.entity.EquipmentStatus;
import org.springframework.data.jpa.domain.Specification;

public final class EquipmentSpecifications {

    private EquipmentSpecifications() {}

    public static Specification<Equipment> filter(Optional<EquipmentStatus> status,
                                                  Optional<Long> currentWorksiteId,
                                                  Optional<Long> equipmentTypeId,
                                                  Optional<String> brand,
                                                  Optional<EquipmentCondition> condition,
                                                  Optional<String> search) {
        return (root, query, cb) -> {
            List<Predicate> preds = new ArrayList<>();

            status.ifPresent(s -> preds.add(cb.equal(root.get("status"), s)));

            currentWorksiteId.ifPresent(wid -> preds.add(cb.equal(root.get("currentWorksite").get("id"), wid)));

            equipmentTypeId.ifPresent(tid -> preds.add(cb.equal(root.get("equipmentType").get("id"), tid)));

            brand.ifPresent(b -> preds.add(cb.like(cb.lower(root.get("brand")), "%" + b.toLowerCase() + "%")));

            condition.ifPresent(c -> preds.add(cb.equal(root.get("condition"), c)));

            if (search.isPresent() && !search.get().isBlank()) {
                String s = "%" + search.get().toLowerCase() + "%";
                Path<String> code = root.get("equipmentCode");
                Path<String> serial = root.get("serialNumber");
                Path<String> model = root.get("model");
                Path<String> br = root.get("brand");
                Predicate pcode = cb.like(cb.lower(code), s);
                Predicate pserial = cb.like(cb.lower(serial), s);
                Predicate pmodel = cb.like(cb.lower(model), s);
                Predicate pbrand = cb.like(cb.lower(br), s);
                preds.add(cb.or(pcode, pserial, pmodel, pbrand));
            }

            return preds.isEmpty() ? cb.conjunction() : cb.and(preds.toArray(new Predicate[0]));
        };
    }
}
