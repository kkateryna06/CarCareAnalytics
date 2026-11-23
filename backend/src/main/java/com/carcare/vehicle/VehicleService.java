package com.carcare.vehicle;

import com.carcare.exception.ApiException;
import com.carcare.user.UserRepository;
import com.carcare.journal.MileageService;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.validation.Validator;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

@Service
@Transactional
public class VehicleService {
    private static final Set<String> SORT_FIELDS = Set.of("createdAt", "updatedAt", "make", "model", "year", "currentOdometerKm");
    private static final Set<String> WRITE_FIELDS = Set.of("make", "model", "year", "generation", "fuelType", "engineCapacityLiters", "vin", "registrationNumber", "purchaseDate", "currentOdometerKm", "photoBase64");
    private final VehicleRepository vehicles;
    private final UserRepository users;
    private final Validator validator;
    private final ObjectMapper json;
    private final Clock clock;
    private final MileageService mileage;
    private final JdbcTemplate jdbc;
    public VehicleService(VehicleRepository vehicles, UserRepository users, Validator validator, ObjectMapper json, Clock clock, MileageService mileage, JdbcTemplate jdbc) {
        this.vehicles = vehicles; this.users = users; this.validator = validator; this.json = json; this.clock = clock;
        this.mileage=mileage; this.jdbc=jdbc;
    }
    @Transactional(readOnly = true)
    public VehiclePage list(long userId, int page, int size, String sort) {
        var parts = sort.split(",", -1);
        if (page < 0 || size < 1 || size > 100 || parts.length != 2 || !SORT_FIELDS.contains(parts[0]) || !Set.of("asc", "desc").contains(parts[1])) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAGINATION", "page >= 0, size от 1 до 100; sort: поле,asc или поле,desc");
        }
        return VehiclePage.from(vehicles.findAllByOwnerId(userId, PageRequest.of(page, size, Sort.by(Sort.Direction.fromString(parts[1]), parts[0]).and(Sort.by("id")))));
    }
    @Transactional(readOnly = true)
    public VehicleResponse get(long userId, long id) { return owned(userId, id).response(); }
    @Transactional(readOnly = true)
    public byte[] photo(long userId, long id, UUID version) {
        return jdbc.query("""
                SELECT p.jpeg FROM vehicle_photos p JOIN vehicles v ON v.id = p.vehicle_id
                WHERE v.id = ? AND v.user_id = ? AND v.photo_version = ?
                """, (rs, row) -> rs.getBytes(1), id, userId, version).stream().findFirst().orElseThrow(ApiException::notFound);
    }
    public VehicleResponse create(long userId, VehicleWrite data) {
        validate(data);
        byte[] photo = VehiclePhoto.decode(data.photoBase64());
        var owner = users.findById(userId).orElseThrow(ApiException::notFound);
        var vehicle=vehicles.saveAndFlush(new Vehicle(owner, data, clock.instant()));
        mileage.initialize(vehicle, data.currentOdometerKm());
        if (photo != null) storePhoto(vehicle, photo);
        return vehicle.response();
    }
    public VehicleResponse patch(long userId, long id, JsonNode patch) {
        var vehicle = vehicles.lockOwned(id,userId).orElseThrow(ApiException::notFound);
        if (!patch.isObject() || patch.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "Укажите изменяемые поля");
        ObjectNode merged = json.valueToTree(vehicle.data());
        patch.fields().forEachRemaining(field -> {
            if (!WRITE_FIELDS.contains(field.getKey())) throw new ApiException(HttpStatus.BAD_REQUEST, "UNKNOWN_FIELD", "Неизвестное поле: " + field.getKey());
            merged.set(field.getKey(), field.getValue());
        });
        VehicleWrite data;
        try { data = json.treeToValue(merged, VehicleWrite.class); }
        catch (Exception ex) { throw new ApiException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "Некорректный тип поля"); }
        validate(data);
        if (data.currentOdometerKm()!=vehicle.getCurrentOdometerKm()) throw ApiException.invalid(Map.of("currentOdometerKm", "Изменяйте пробег через записи журнала"));
        vehicle.update(data, clock.instant());
        if (patch.has("photoBase64")) storePhoto(vehicle, VehiclePhoto.decode(data.photoBase64()));
        return vehicles.saveAndFlush(vehicle).response();
    }
    private void storePhoto(Vehicle vehicle, byte[] photo) {
        if (photo == null) {
            jdbc.update("DELETE FROM vehicle_photos WHERE vehicle_id = ?", vehicle.getId());
            vehicle.setPhotoVersion(null);
        } else {
            jdbc.update("""
                    INSERT INTO vehicle_photos (vehicle_id, jpeg) VALUES (?, ?)
                    ON CONFLICT (vehicle_id) DO UPDATE SET jpeg = EXCLUDED.jpeg
                    """, vehicle.getId(), photo);
            vehicle.setPhotoVersion(UUID.randomUUID());
        }
    }
    public void delete(long userId, long id) { vehicles.delete(vehicles.lockOwned(id,userId).orElseThrow(ApiException::notFound)); }
    private Vehicle owned(long userId, long id) { return vehicles.findByIdAndOwnerId(id, userId).orElseThrow(ApiException::notFound); }
    private void validate(VehicleWrite data) {
        var fields = new TreeMap<String, String>();
        validator.validate(data).forEach(v -> fields.put(v.getPropertyPath().toString(), v.getMessage()));
        if (data.year() != null && data.year() > Year.now(clock).getValue() + 1) fields.put("year", "Год не может быть позднее следующего календарного года");
        if (!fields.isEmpty()) throw ApiException.invalid(fields);
    }
}
