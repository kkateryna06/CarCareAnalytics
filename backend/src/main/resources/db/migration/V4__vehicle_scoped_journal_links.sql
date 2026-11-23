-- Related amounts and measurements must belong to the same vehicle even for direct SQL writes.
-- Keep the links deferred so source/ledger updates and cascading deletes remain atomic.
ALTER TABLE expenses ADD CONSTRAINT uq_expenses_vehicle_id UNIQUE (vehicle_id, id);
ALTER TABLE odometer_entries ADD CONSTRAINT uq_odometer_vehicle_id UNIQUE (vehicle_id, id);

ALTER TABLE refuelings DROP CONSTRAINT refuelings_expense_id_fkey;
ALTER TABLE refuelings DROP CONSTRAINT refuelings_odometer_entry_id_fkey;
ALTER TABLE service_records DROP CONSTRAINT service_records_expense_id_fkey;
ALTER TABLE service_records DROP CONSTRAINT service_records_odometer_entry_id_fkey;

ALTER TABLE refuelings ADD CONSTRAINT fk_refueling_expense_vehicle
    FOREIGN KEY (vehicle_id, expense_id) REFERENCES expenses(vehicle_id, id) DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE refuelings ADD CONSTRAINT fk_refueling_odometer_vehicle
    FOREIGN KEY (vehicle_id, odometer_entry_id) REFERENCES odometer_entries(vehicle_id, id) DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE service_records ADD CONSTRAINT fk_service_expense_vehicle
    FOREIGN KEY (vehicle_id, expense_id) REFERENCES expenses(vehicle_id, id) DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE service_records ADD CONSTRAINT fk_service_odometer_vehicle
    FOREIGN KEY (vehicle_id, odometer_entry_id) REFERENCES odometer_entries(vehicle_id, id) DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE expenses ADD CONSTRAINT ck_manual_expense_source CHECK (source_type <> 'MANUAL' OR source_id IS NULL);
