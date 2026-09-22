alter table equipment_transfers add column equipment_id bigint;
alter table equipment_transfers add column condition_before varchar(50);
alter table equipment_transfers add column condition_after varchar(50);

with ranked_items as (
    select distinct on (transfer_id)
        transfer_id,
        equipment_id,
        condition_before,
        condition_after
    from transfer_items
    order by transfer_id, transfer_item_id
)
update equipment_transfers t
set equipment_id = r.equipment_id,
    condition_before = r.condition_before,
    condition_after = r.condition_after
from ranked_items r
where r.transfer_id = t.transfer_id;

alter table equipment_transfers
    alter column equipment_id set not null,
    alter column condition_before set not null;

alter table equipment_transfers
    add constraint fk_transfer_equipment foreign key (equipment_id) references equipment (equipment_id);

create index idx_equipment_current_worksite on equipment (current_worksite_id);
create index idx_equipment_status on equipment (status);
create index idx_transfer_equipment on equipment_transfers (equipment_id);
create index idx_transfer_from_worksite on equipment_transfers (from_worksite_id);
create index idx_transfer_to_worksite on equipment_transfers (to_worksite_id);
create index idx_transfer_status on equipment_transfers (status);

drop table transfer_items;
