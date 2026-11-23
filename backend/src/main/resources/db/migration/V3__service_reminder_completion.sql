ALTER TABLE service_records ADD COLUMN reminder_done BOOLEAN NOT NULL DEFAULT FALSE;
CREATE INDEX idx_service_vehicle_due ON service_records(vehicle_id, next_due_date, next_due_odometer_km);
