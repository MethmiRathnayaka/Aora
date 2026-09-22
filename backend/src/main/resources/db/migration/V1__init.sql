create table equipment_types (
    equipment_type_id bigserial primary key,
    name varchar(255) not null unique,
    code_prefix varchar(10) not null unique,
    description varchar(1000),
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create table stores (
    store_id bigserial primary key,
    name varchar(255) not null,
    address varchar(1000),
    store_manager_id bigint,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create table worksites (
    worksite_id bigserial primary key,
    name varchar(255) not null,
    project_code varchar(255) not null unique,
    address varchar(1000),
    status varchar(30) not null,
    site_admin_id bigint,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create table app_users (
    user_id bigserial primary key,
    auth_user_id varchar(255) not null unique,
    first_name varchar(255) not null,
    last_name varchar(255) not null,
    email varchar(255) not null unique,
    phone varchar(255),
    role varchar(50) not null,
    is_active boolean not null,
    current_worksite_id bigint,
    current_store_id bigint,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

alter table stores add constraint fk_stores_manager foreign key (store_manager_id) references app_users (user_id);
alter table worksites add constraint fk_worksites_admin foreign key (site_admin_id) references app_users (user_id);
alter table app_users add constraint fk_users_worksite foreign key (current_worksite_id) references worksites (worksite_id);
alter table app_users add constraint fk_users_store foreign key (current_store_id) references stores (store_id);

create table equipment (
    equipment_id bigserial primary key,
    equipment_code varchar(255) not null unique,
    equipment_type_id bigint not null,
    brand varchar(255) not null,
    model varchar(255),
    serial_number varchar(255) not null unique,
    status varchar(50) not null,
    condition varchar(50) not null,
    current_worksite_id bigint,
    purchase_date date,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint fk_equipment_type foreign key (equipment_type_id) references equipment_types (equipment_type_id),
    constraint fk_equipment_worksite foreign key (current_worksite_id) references worksites (worksite_id)
);

create table equipment_transfers (
    transfer_id bigserial primary key,
    from_location_type varchar(50) not null,
    from_worksite_id bigint,
    to_location_type varchar(50) not null,
    to_worksite_id bigint,
    delivery_person_name varchar(255) not null,
    delivery_person_phone varchar(255),
    status varchar(50) not null,
    dispatched_by bigint,
    dispatched_at timestamptz,
    received_by bigint,
    received_at timestamptz,
    notes varchar(4000),
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint fk_transfer_from_worksite foreign key (from_worksite_id) references worksites (worksite_id),
    constraint fk_transfer_to_worksite foreign key (to_worksite_id) references worksites (worksite_id),
    constraint fk_transfer_dispatched_by foreign key (dispatched_by) references app_users (user_id),
    constraint fk_transfer_received_by foreign key (received_by) references app_users (user_id)
);

create table transfer_items (
    transfer_item_id bigserial primary key,
    transfer_id bigint not null,
    equipment_id bigint not null,
    condition_before varchar(50) not null,
    condition_after varchar(50),
    constraint fk_transfer_items_transfer foreign key (transfer_id) references equipment_transfers (transfer_id) on delete cascade,
    constraint fk_transfer_items_equipment foreign key (equipment_id) references equipment (equipment_id)
);

create table maintenance_records (
    maintenance_id bigserial primary key,
    equipment_id bigint not null,
    reason varchar(1000) not null,
    description varchar(4000),
    status varchar(50) not null,
    reported_by bigint,
    approved_by bigint,
    started_by bigint,
    completed_by bigint,
    reported_at timestamptz,
    approved_at timestamptz,
    started_at timestamptz,
    completed_at timestamptz,
    condition_after varchar(50),
    cost numeric(19,2),
    notes varchar(4000),
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint fk_maintenance_equipment foreign key (equipment_id) references equipment (equipment_id),
    constraint fk_maintenance_reported_by foreign key (reported_by) references app_users (user_id),
    constraint fk_maintenance_approved_by foreign key (approved_by) references app_users (user_id),
    constraint fk_maintenance_started_by foreign key (started_by) references app_users (user_id),
    constraint fk_maintenance_completed_by foreign key (completed_by) references app_users (user_id)
);

create table audit_logs (
    audit_id bigserial primary key,
    user_id bigint,
    action varchar(255) not null,
    entity_type varchar(255) not null,
    entity_id bigint not null,
    old_values text,
    new_values text,
    created_at timestamptz not null,
    constraint fk_audit_user foreign key (user_id) references app_users (user_id)
);

insert into stores (name, address, created_at, updated_at)
values ('Aora Main Equipment Store', 'Colombo, Sri Lanka', now(), now());
