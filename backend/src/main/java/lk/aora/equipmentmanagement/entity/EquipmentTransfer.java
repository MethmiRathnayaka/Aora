package lk.aora.equipmentmanagement.entity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "equipment_transfers")
public class EquipmentTransfer extends TimestampedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "transfer_id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_location_type", nullable = false)
    private LocationType fromLocationType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_worksite_id")
    private Worksite fromWorksite;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_location_type", nullable = false)
    private LocationType toLocationType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_worksite_id")
    private Worksite toWorksite;

    @Column(nullable = false)
    private String deliveryPersonName;

    private String deliveryPersonPhone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransferStatus status = TransferStatus.IN_TRANSIT;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dispatched_by")
    private AppUser dispatchedBy;

    private Instant dispatchedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "received_by")
    private AppUser receivedBy;

    private Instant receivedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "equipment_id", nullable = false)
    private Equipment equipment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EquipmentCondition conditionBefore;

    @Enumerated(EnumType.STRING)
    private EquipmentCondition conditionAfter;

    private Instant rentalStartedAt;

    private Long rentalDays;

    @Column(precision = 14, scale = 2)
    private java.math.BigDecimal unitPrice;

    @Column(precision = 24, scale = 2)
    private java.math.BigDecimal rentTotal;

    @Column(length = 4000)
    private String notes;
}