package lk.aora.equipmentmanagement.service;

import lk.aora.equipmentmanagement.dto.equipment.CreateEquipmentTypeRequest;
import lk.aora.equipmentmanagement.dto.equipment.EquipmentTypeDto;
import lk.aora.equipmentmanagement.dto.equipment.UpdateEquipmentTypeRequest;
import lk.aora.equipmentmanagement.entity.EquipmentType;
import lk.aora.equipmentmanagement.exception.BusinessRuleException;
import lk.aora.equipmentmanagement.exception.ResourceNotFoundException;
import lk.aora.equipmentmanagement.repository.EquipmentTypeRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EquipmentTypeService {

    private final EquipmentTypeRepository repo;

    public EquipmentTypeService(EquipmentTypeRepository repo) {
        this.repo = repo;
    }

    @Transactional(readOnly = true)
    public Page<EquipmentTypeDto> list(Pageable pageable) {
        return repo.findAll(pageable).map(t -> new EquipmentTypeDto(t.getId(), t.getName(), t.getCodePrefix()));
    }

    @Transactional(readOnly = true)
    public EquipmentTypeDto get(Long id) {
        EquipmentType t = repo.findById(id).orElseThrow(() -> new ResourceNotFoundException("EquipmentType not found"));
        return new EquipmentTypeDto(t.getId(), t.getName(), t.getCodePrefix());
    }

    @Transactional
    public EquipmentTypeDto create(CreateEquipmentTypeRequest req) {
        String name = req.name().trim();
        var existing = repo.findFirstByNameIgnoreCase(name);
        if (existing.isPresent()) {
            EquipmentType t = existing.get();
            return new EquipmentTypeDto(t.getId(), t.getName(), t.getCodePrefix());
        }
        String prefix = req.codePrefix();
        if (prefix == null || prefix.isBlank()) {
            String base = name.toUpperCase(java.util.Locale.ROOT).replaceAll("[^A-Z0-9]", "");
            if (base.isEmpty()) base = "EQ";
            base = base.substring(0, Math.min(4, base.length()));
            prefix = base;
            int suffix = 1;
            while (repo.findByCodePrefix(prefix).isPresent()) prefix = base + suffix++;
        } else if (repo.findByCodePrefix(prefix).isPresent()) {
            throw new BusinessRuleException("codePrefix must be unique");
        }
        EquipmentType t = new EquipmentType();
        t.setName(name);
        t.setCodePrefix(prefix);
        t.setDescription(req.description());
        repo.save(t);
        return new EquipmentTypeDto(t.getId(), t.getName(), t.getCodePrefix());
    }

    @Transactional
    public EquipmentTypeDto update(Long id, UpdateEquipmentTypeRequest req) {
        EquipmentType t = repo.findById(id).orElseThrow(() -> new ResourceNotFoundException("EquipmentType not found"));
        if (req.name() != null) t.setName(req.name());
        if (req.description() != null) t.setDescription(req.description());
        repo.save(t);
        return new EquipmentTypeDto(t.getId(), t.getName(), t.getCodePrefix());
    }
}
