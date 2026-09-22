package lk.aora.equipmentmanagement.service;

import lk.aora.equipmentmanagement.dto.equipment.CreateEquipmentTypeRequest;
import lk.aora.equipmentmanagement.entity.EquipmentType;
import lk.aora.equipmentmanagement.repository.EquipmentTypeRepository;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EquipmentTypeServiceTest {
    private final EquipmentTypeRepository repo = mock(EquipmentTypeRepository.class);
    private final EquipmentTypeService service = new EquipmentTypeService(repo);

    @Test
    void reusesExistingName() {
        EquipmentType existing = new EquipmentType();
        existing.setId(7L);
        existing.setName("Drill");
        existing.setCodePrefix("DRIL");
        when(repo.findFirstByNameIgnoreCase("drill")).thenReturn(Optional.of(existing));
        var result = service.create(new CreateEquipmentTypeRequest(" drill ", null, null));
        assertEquals(7L, result.equipmentTypeId());
        verify(repo, never()).save(any());
    }

    @Test
    void generatesAvailablePrefix() {
        when(repo.findByCodePrefix("DRIL")).thenReturn(Optional.of(new EquipmentType()));
        when(repo.save(any())).thenAnswer(invocation -> {
            EquipmentType type = invocation.getArgument(0);
            type.setId(8L);
            return type;
        });
        var result = service.create(new CreateEquipmentTypeRequest(" Drill press ", null, null));
        assertEquals("Drill press", result.name());
        assertEquals("DRIL1", result.codePrefix());
        assertEquals(8L, result.equipmentTypeId());
    }
}
