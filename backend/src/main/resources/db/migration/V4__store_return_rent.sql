ALTER TABLE equipment_transfers
    ADD COLUMN rental_started_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN rental_days BIGINT,
    ADD COLUMN unit_price NUMERIC(14, 2),
    ADD COLUMN rent_total NUMERIC(24, 2);
