package com.carcare.journal;

public final class JournalTypes {
    private JournalTypes() {}
    public enum SourceType { MANUAL, FUEL, SERVICE }
    public enum Category { FUEL, SERVICE, INSURANCE, TAXES, PARKING, WASH, OTHER }
    public enum ServiceType { REPAIR, OIL_CHANGE, TIRES, BRAKES, INSPECTION, OTHER }
    public enum DocumentType { INSURANCE, INSPECTION }
    public enum Kind {
        ODOMETER("odometer"), REFUELING("refuelings"), EXPENSE("expenses"), SERVICE("services"), DOCUMENT("documents");
        public final String resource;
        Kind(String resource) { this.resource = resource; }
    }
}
