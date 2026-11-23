package com.carcare;

import com.carcare.user.UserRepository;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.*;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;
import org.springframework.test.context.web.ServletTestExecutionListener;
import org.springframework.test.web.servlet.*;
import org.testcontainers.containers.PostgreSQLContainer;
import java.time.Instant;
import java.util.Map;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.math.BigDecimal;
import java.util.concurrent.*;
import org.flywaydb.core.Flyway;
import java.sql.DriverManager;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"app.jwt.secret=integration-test-secret-at-least-32-bytes-long", "spring.profiles.active=dev"})
@AutoConfigureMockMvc
// These tests use real services and repositories; no Mockito instrumentation is needed.
@TestExecutionListeners(listeners = {ServletTestExecutionListener.class, DependencyInjectionTestExecutionListener.class}, mergeMode = TestExecutionListeners.MergeMode.REPLACE_DEFAULTS)
class FoundationIntegrationTest {
    // A dedicated external test database may be supplied for machines without Docker.
    // This database is truncated before each test; never point it at development/user data.
    static PostgreSQLContainer<?> postgres;
    @DynamicPropertySource static void database(DynamicPropertyRegistry properties) {
        String external = System.getenv("TEST_DATABASE_URL");
        if (external == null || external.isBlank()) {
            postgres = new PostgreSQLContainer<>("postgres:17-alpine");
            postgres.start();
            properties.add("spring.datasource.url", postgres::getJdbcUrl);
            properties.add("spring.datasource.username", postgres::getUsername);
            properties.add("spring.datasource.password", postgres::getPassword);
        } else {
            properties.add("spring.datasource.url", () -> external);
            properties.add("spring.datasource.username", () -> System.getenv("TEST_DATABASE_USER"));
            properties.add("spring.datasource.password", () -> System.getenv().getOrDefault("TEST_DATABASE_PASSWORD", ""));
        }
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwords;
    @Autowired JwtEncoder encoder;
    @BeforeEach void reset() { jdbc.execute("TRUNCATE TABLE vehicles, users RESTART IDENTITY CASCADE"); }
    @AfterAll static void stop() { if (postgres != null) postgres.stop(); }
    private String token(String email) throws Exception {
        return json.readTree(mvc.perform(post("/api/v1/auth/register").contentType("application/json")
            .content(json.writeValueAsString(Map.of("email", email, "password", "Password123!"))))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("accessToken").asText();
    }
    private String vehicle() { return """
        {"make":"Toyota","model":"Corolla","year":2020,"fuelType":"HYBRID","currentOdometerKm":72000,
        "generation":"E210","engineCapacityLiters":"1.80","vin":"JTDBR32E720012345","registrationNumber":"WA 12345","purchaseDate":"2024-01-10"}
        """; }
    private long create(String token) throws Exception {
        return json.readTree(mvc.perform(post("/api/v1/vehicles").header("Authorization", "Bearer " + token).contentType("application/json").content(vehicle()))
            .andExpect(status().isCreated()).andExpect(header().exists("Location")).andReturn().getResponse().getContentAsString()).get("id").asLong();
    }
    @Test void registrationLoginAndProfile() throws Exception {
        String token = token("  OWNER@Example.com  ");
        mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token)).andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value("owner@example.com")).andExpect(jsonPath("$.passwordHash").doesNotExist());
        var user = users.findByEmail("owner@example.com").orElseThrow();
        assertThat(user.getPasswordHash()).isNotEqualTo("Password123!");
        assertThat(passwords.matches("Password123!", user.getPasswordHash())).isTrue();
        mvc.perform(post("/api/v1/auth/login").contentType("application/json").content("{\"email\":\"OWNER@example.com\",\"password\":\"Password123!\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.tokenType").value("Bearer")).andExpect(jsonPath("$.expiresAt").exists());
    }
    @Test void duplicateEmailIsCaseInsensitive() throws Exception {
        token("owner@example.com");
        mvc.perform(post("/api/v1/auth/register").contentType("application/json").content("{\"email\":\"OWNER@example.com\",\"password\":\"Password123!\"}"))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EMAIL_EXISTS"));
    }
    @Test void invalidCredentialsAreUnauthorized() throws Exception {
        token("owner@example.com");
        for (String email : new String[]{"owner@example.com", "missing@example.com"}) {
            mvc.perform(post("/api/v1/auth/login").contentType("application/json").content(json.writeValueAsString(Map.of("email", email, "password", "wrong-password"))))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        }
    }
    @Test void anonymousAndMalformedTokenAreUnauthorized() throws Exception {
        mvc.perform(get("/api/v1/vehicles")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.fieldErrors").isMap()).andExpect(jsonPath("$.path").value("/api/v1/vehicles"));
        mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer invalid")).andExpect(status().isUnauthorized());
    }
    @Test void expiredAndWrongIssuerTokensAreUnauthorized() throws Exception {
        for (String issuer : new String[]{"carcare-api", "another-issuer"}) {
            Instant expiry = issuer.equals("carcare-api") ? Instant.now().minusSeconds(120) : Instant.now().plusSeconds(3600);
            var claims = JwtClaimsSet.builder().issuer(issuer).subject("1").issuedAt(Instant.now().minusSeconds(7200)).expiresAt(expiry).build();
            var jwt = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
            mvc.perform(get("/api/v1/vehicles").header("Authorization", "Bearer " + jwt)).andExpect(status().isUnauthorized());
        }
    }
    @Test void modifiedSignatureIsRejected() throws Exception {
        String jwt = token("owner@example.com");
        int pos = jwt.lastIndexOf('.') + 1;
        String tampered = jwt.substring(0, pos) + (jwt.charAt(pos) == 'A' ? "B" : "A") + jwt.substring(pos + 1);
        mvc.perform(get("/api/v1/vehicles").header("Authorization", "Bearer " + tampered)).andExpect(status().isUnauthorized());
    }
    @Test void completeVehicleCrudAndNullClearing() throws Exception {
        String jwt = token("owner@example.com"); long id = create(jwt);
        mvc.perform(get("/api/v1/vehicles").header("Authorization", "Bearer " + jwt)).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(patch("/api/v1/vehicles/" + id).header("Authorization", "Bearer " + jwt).contentType("application/json").content("{\"model\":\"Yaris\",\"vin\":null}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.model").value("Yaris")).andExpect(jsonPath("$.make").value("Toyota")).andExpect(jsonPath("$.vin").isEmpty());
        mvc.perform(get("/api/v1/vehicles/" + id).header("Authorization", "Bearer " + jwt)).andExpect(status().isOk()).andExpect(jsonPath("$.currentOdometerKm").value(72000));
        mvc.perform(delete("/api/v1/vehicles/" + id).header("Authorization", "Bearer " + jwt)).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/vehicles/" + id).header("Authorization", "Bearer " + jwt)).andExpect(status().isNotFound());
    }
    @Test void otherUserCannotReadUpdateDeleteOrListVehicle() throws Exception {
        String a = token("a@example.com"); long id = create(a); String b = token("b@example.com");
        mvc.perform(get("/api/v1/vehicles/" + id).header("Authorization", "Bearer " + b)).andExpect(status().isNotFound());
        mvc.perform(patch("/api/v1/vehicles/" + id).header("Authorization", "Bearer " + b).contentType("application/json").content("{\"make\":\"BMW\"}"))
            .andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/vehicles/" + id).header("Authorization", "Bearer " + b)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/vehicles").header("Authorization", "Bearer " + b)).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/v1/vehicles/" + id).header("Authorization", "Bearer " + a)).andExpect(jsonPath("$.make").value("Toyota"));
    }
    @Test void badVehicleFieldsReturn422WithoutChangingData() throws Exception {
        String jwt = token("owner@example.com"); long id = create(jwt);
        mvc.perform(patch("/api/v1/vehicles/" + id).header("Authorization", "Bearer " + jwt).contentType("application/json")
            .content("{\"year\":1800,\"currentOdometerKm\":-1,\"engineCapacityLiters\":0,\"vin\":\"bad\",\"purchaseDate\":\"2099-01-01\"}"))
            .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.fieldErrors.year").exists()).andExpect(jsonPath("$.fieldErrors.currentOdometerKm").exists())
            .andExpect(jsonPath("$.fieldErrors.engineCapacityLiters").exists()).andExpect(jsonPath("$.fieldErrors.purchaseDate").exists());
        mvc.perform(get("/api/v1/vehicles/" + id).header("Authorization", "Bearer " + jwt)).andExpect(jsonPath("$.year").value(2020));
    }
    @Test void futureYearAndMissingFieldsAreRejected() throws Exception {
        String jwt = token("owner@example.com");
        mvc.perform(post("/api/v1/vehicles").header("Authorization", "Bearer " + jwt).contentType("application/json").content("{}"))
            .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.fieldErrors.make").exists());
        mvc.perform(post("/api/v1/vehicles").header("Authorization", "Bearer " + jwt).contentType("application/json").content(vehicle().replace("2020", "2099")))
            .andExpect(status().isUnprocessableEntity());
    }
    @Test void unknownFieldsInvalidEnumAndFractionalIntegersAreRejected() throws Exception {
        String jwt = token("owner@example.com"); long id = create(jwt);
        for (String body : new String[]{"{\"userId\":2}", "{\"fuelType\":\"INVALID\"}", "{\"year\":2020.5}", "{}", "null"}) {
            mvc.perform(patch("/api/v1/vehicles/" + id).header("Authorization", "Bearer " + jwt).contentType("application/json").content(body)).andExpect(status().isBadRequest());
        }
    }
    @Test void paginationAndSortAreBounded() throws Exception {
        String jwt = token("owner@example.com"); create(jwt); create(jwt);
        mvc.perform(get("/api/v1/vehicles?page=1&size=1&sort=year,asc").header("Authorization", "Bearer " + jwt)).andExpect(status().isOk())
            .andExpect(jsonPath("$.content.length()").value(1)).andExpect(jsonPath("$.totalPages").value(2));
        for (String query : new String[]{"size=101", "size=0", "page=-1", "sort=owner,asc", "sort=make,invalid"}) {
            mvc.perform(get("/api/v1/vehicles?" + query).header("Authorization", "Bearer " + jwt)).andExpect(status().isBadRequest());
        }
    }
    @Test void unicodePasswordByteLimitIsEnforced() throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType("application/json").content(json.writeValueAsString(Map.of("email", "owner@example.com", "password", "я".repeat(40)))))
            .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.fieldErrors.password").exists());
    }
    @Test void flywayMigrationAndOpenApiAreAvailable() throws Exception {
        assertThat(jdbc.queryForList("SELECT version FROM flyway_schema_history WHERE success = true", String.class))
            .contains("1", "2", "3", "4", "5");
        assertThat(jdbc.queryForObject("""
            SELECT data_type FROM information_schema.columns
            WHERE table_schema = current_schema() AND table_name = 'vehicle_photos' AND column_name = 'jpeg'
            """, String.class)).isEqualTo("bytea");
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
            .andExpect(jsonPath("$.paths['/api/v1/vehicles']").exists())
            .andExpect(jsonPath("$.paths['/api/v1/vehicles/{id}/photo']").exists());
    }
    private String day(int offset) { return LocalDate.now(ZoneOffset.UTC).plusDays(offset).toString(); }
    private JsonNode add(String jwt,long vehicle,String resource,String body) throws Exception {
        return json.readTree(mvc.perform(post("/api/v1/vehicles/"+vehicle+"/"+resource).header("Authorization","Bearer "+jwt).contentType("application/json").content(body))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }
    private String fuel(String date,int km,String amount) {
        return "{\"date\":\""+date+"\",\"odometerKm\":"+km+",\"liters\":25,\"totalCostPln\":"+amount+",\"isFullTank\":true}";
    }
    private String service(String cost) {
        return "{\"date\":\""+day(0)+"\",\"serviceType\":\"REPAIR\",\"odometerKm\":73000,\"description\":\"Замена тормозов\",\"costPln\":"+cost+"}";
    }
    private String authorization(String jwt) { return "Bearer "+jwt; }
    @Test void odometerCrudRecalculatesVehicle() throws Exception {
        String jwt=token("owner@example.com"); long v=create(jwt);
        var previous=add(jwt,v,"odometer","{\"date\":\""+day(-1)+"\",\"odometerKm\":71000}");
        var latest=add(jwt,v,"odometer","{\"date\":\""+day(0)+"\",\"odometerKm\":74000}");
        mvc.perform(patch("/api/v1/odometer/"+latest.get("id").asLong()).header("Authorization",authorization(jwt)).contentType("application/json").content("{\"odometerKm\":75000,\"note\":\"Исправлено\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.note").value("Исправлено"));
        mvc.perform(get("/api/v1/vehicles/"+v).header("Authorization",authorization(jwt))).andExpect(jsonPath("$.currentOdometerKm").value(75000));
        mvc.perform(delete("/api/v1/odometer/"+latest.get("id").asLong()).header("Authorization",authorization(jwt))).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/vehicles/"+v).header("Authorization",authorization(jwt))).andExpect(jsonPath("$.currentOdometerKm").value(72000));
        mvc.perform(get("/api/v1/odometer/"+previous.get("id").asLong()).header("Authorization",authorization(jwt))).andExpect(status().isOk());
    }
    @Test void chronologicalMileageConflictRollsBackRefuelingAndExpense() throws Exception {
        String jwt=token("owner@example.com"); long v=create(jwt);
        add(jwt,v,"odometer","{\"date\":\""+day(-1)+"\",\"odometerKm\":71000}");
        mvc.perform(post("/api/v1/vehicles/"+v+"/refuelings").header("Authorization",authorization(jwt)).contentType("application/json").content(fuel(day(-2),71500,"150")))
            .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.fieldErrors.odometerKm").exists());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM expenses",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM refuelings",Integer.class)).isZero();
        mvc.perform(patch("/api/v1/vehicles/"+v).header("Authorization",authorization(jwt)).contentType("application/json").content("{\"currentOdometerKm\":90000}"))
            .andExpect(status().isUnprocessableEntity());
    }
    @Test void conflictingMileageEditLeavesOriginalValues() throws Exception {
        String jwt=token("owner@example.com"); long v=create(jwt);
        var r=add(jwt,v,"refuelings",fuel(day(-1),71000,"150"));
        mvc.perform(patch("/api/v1/refuelings/"+r.get("id").asLong()).header("Authorization",authorization(jwt)).contentType("application/json").content("{\"odometerKm\":80000,\"totalCostPln\":999}"))
            .andExpect(status().isUnprocessableEntity());
        assertThat(jdbc.queryForObject("SELECT amount_pln FROM expenses",BigDecimal.class)).isEqualByComparingTo("150.00");
        assertThat(jdbc.queryForObject("SELECT odometer_km FROM refuelings",Integer.class)).isEqualTo(71000);
    }
    @Test void refuelingHasExactlyOneExpenseAcrossEditAndDelete() throws Exception {
        String jwt=token("owner@example.com"); long v=create(jwt); var r=add(jwt,v,"refuelings",fuel(day(0),73000,"150")); long id=r.get("id").asLong();
        assertThat(r.get("pricePerLiterPln").decimalValue()).isEqualByComparingTo("6.00");
        mvc.perform(patch("/api/v1/refuelings/"+id).header("Authorization",authorization(jwt)).contentType("application/json").content("{\"totalCostPln\":175,\"station\":\"АЗС\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.expenseId").value(r.get("expenseId").asLong()));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM expenses",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT sum(amount_pln) FROM expenses",BigDecimal.class)).isEqualByComparingTo("175");
        mvc.perform(delete("/api/v1/refuelings/"+id).header("Authorization",authorization(jwt))).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM expenses",Integer.class)).isZero();
        mvc.perform(get("/api/v1/vehicles/"+v).header("Authorization",authorization(jwt))).andExpect(jsonPath("$.currentOdometerKm").value(72000));
    }
    @Test void sourceManagedExpenseAndOdometerCannotBeEditedDirectly() throws Exception {
        String jwt=token("owner@example.com"); long v=create(jwt); var r=add(jwt,v,"refuelings",fuel(day(0),73000,"150"));
        long e=r.get("expenseId").asLong(); long o=jdbc.queryForObject("SELECT odometer_entry_id FROM refuelings",Long.class);
        for(String path:new String[]{"expenses/"+e,"odometer/"+o}) {
            mvc.perform(patch("/api/v1/"+path).header("Authorization",authorization(jwt)).contentType("application/json").content("{\"note\":\"x\"}"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("SOURCE_MANAGED"));
            mvc.perform(delete("/api/v1/"+path).header("Authorization",authorization(jwt))).andExpect(status().isUnprocessableEntity());
        }
    }
    @Test void serviceCostTransitionsCreateUpdateAndRemoveOneExpense() throws Exception {
        String jwt=token("owner@example.com"); long v=create(jwt); var s=add(jwt,v,"services",service("0")); long id=s.get("id").asLong();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM expenses",Integer.class)).isZero();
        for(String amount:new String[]{"100","200"}) {
            mvc.perform(patch("/api/v1/services/"+id).header("Authorization",authorization(jwt)).contentType("application/json").content("{\"costPln\":"+amount+"}"))
                .andExpect(status().isOk());
            assertThat(jdbc.queryForObject("SELECT count(*) FROM expenses",Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT sum(amount_pln) FROM expenses",BigDecimal.class)).isEqualByComparingTo(amount);
        }
        mvc.perform(patch("/api/v1/services/"+id).header("Authorization",authorization(jwt)).contentType("application/json").content("{\"costPln\":null,\"odometerKm\":null}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.expenseId").isEmpty());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM expenses",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM odometer_entries WHERE source_type='SERVICE'",Integer.class)).isZero();
    }
    @Test void deletingServiceRemovesItsExpenseAndMeasurement() throws Exception {
        String jwt=token("owner@example.com"); long v=create(jwt); var s=add(jwt,v,"services",service("300"));
        mvc.perform(delete("/api/v1/services/"+s.get("id").asLong()).header("Authorization",authorization(jwt))).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM expenses",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM odometer_entries",Integer.class)).isEqualTo(1);
    }
    @Test void manualExpensesUseExactDecimalsAndRejectFuelCategory() throws Exception {
        String jwt=token("owner@example.com"); long v=create(jwt);
        for(String amount:new String[]{"0.10","0.20"}) add(jwt,v,"expenses","{\"date\":\""+day(0)+"\",\"category\":\"PARKING\",\"amountPln\":"+amount+",\"title\":\"Парковка\"}");
        assertThat(jdbc.queryForObject("SELECT sum(amount_pln) FROM expenses",BigDecimal.class)).isEqualByComparingTo("0.30");
        mvc.perform(post("/api/v1/vehicles/"+v+"/expenses").header("Authorization",authorization(jwt)).contentType("application/json").content("{\"date\":\""+day(0)+"\",\"category\":\"FUEL\",\"amountPln\":10,\"title\":\"Нельзя\"}"))
            .andExpect(status().isUnprocessableEntity());
    }
    @Test void timelineAndFiltersDoNotDoubleCountGeneratedExpenses() throws Exception {
        String jwt=token("owner@example.com"); long v=create(jwt);
        add(jwt,v,"refuelings",fuel(day(0),73000,"150")); add(jwt,v,"services",service("200"));
        add(jwt,v,"expenses","{\"date\":\""+day(-1)+"\",\"category\":\"PARKING\",\"amountPln\":20,\"title\":\"Парковка\"}");
        var result=mvc.perform(get("/api/v1/vehicles/"+v+"/timeline").header("Authorization",authorization(jwt))).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(4)).andReturn();
        BigDecimal total=BigDecimal.ZERO; for(JsonNode row:json.readTree(result.getResponse().getContentAsString()).get("content")) if(!row.get("amountPln").isNull()) total=total.add(row.get("amountPln").decimalValue());
        assertThat(total).isEqualByComparingTo("370");
        mvc.perform(get("/api/v1/vehicles/"+v+"/timeline?kind=EXPENSE&category=PARKING&dateFrom="+day(-1)+"&dateTo="+day(-1)).header("Authorization",authorization(jwt)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/v1/vehicles/"+v+"/timeline?size=1&page=1").header("Authorization",authorization(jwt))).andExpect(jsonPath("$.content.length()").value(1)).andExpect(jsonPath("$.totalPages").value(4));
        mvc.perform(get("/api/v1/vehicles/"+v+"/expenses").header("Authorization",authorization(jwt))).andExpect(jsonPath("$.totalElements").value(3));
    }
    @Test void documentsSelectLatestUnexpiredAndKeepHistory() throws Exception {
        String jwt=token("owner@example.com"); long v=create(jwt);
        var expired=add(jwt,v,"documents","{\"type\":\"INSURANCE\",\"validUntil\":\""+day(-1)+"\"}");
        var earlier=add(jwt,v,"documents","{\"type\":\"INSURANCE\",\"validUntil\":\""+day(7)+"\"}");
        var latest=add(jwt,v,"documents","{\"type\":\"INSURANCE\",\"validUntil\":\""+day(365)+"\"}");
        mvc.perform(get("/api/v1/documents/"+expired.get("id").asLong()).header("Authorization",authorization(jwt))).andExpect(jsonPath("$.active").value(false)).andExpect(jsonPath("$.status").value("OVERDUE"));
        mvc.perform(get("/api/v1/documents/"+earlier.get("id").asLong()).header("Authorization",authorization(jwt))).andExpect(jsonPath("$.active").value(false)).andExpect(jsonPath("$.status").value("UPCOMING"));
        mvc.perform(delete("/api/v1/documents/"+latest.get("id").asLong()).header("Authorization",authorization(jwt))).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/documents/"+earlier.get("id").asLong()).header("Authorization",authorization(jwt))).andExpect(jsonPath("$.active").value(true));
    }
    @Test void everyJournalResourceEnforcesOwnership() throws Exception {
        String a=token("a@example.com"); long v=create(a); String b=token("b@example.com");
        var rows=Map.of("odometer",add(a,v,"odometer","{\"date\":\""+day(0)+"\",\"odometerKm\":73000}"),"refuelings",add(a,v,"refuelings",fuel(day(0),73000,"150")),"services",add(a,v,"services",service("200")),"expenses",add(a,v,"expenses","{\"date\":\""+day(0)+"\",\"category\":\"WASH\",\"amountPln\":20,\"title\":\"Мойка\"}"),"documents",add(a,v,"documents","{\"type\":\"INSPECTION\",\"validUntil\":\""+day(7)+"\"}"));
        for(var row:rows.entrySet()) {
            String path="/api/v1/"+row.getKey()+"/"+row.getValue().get("id").asLong();
            mvc.perform(get(path).header("Authorization",authorization(b))).andExpect(status().isNotFound());
            mvc.perform(patch(path).header("Authorization",authorization(b)).contentType("application/json").content("{\"note\":\"x\"}")).andExpect(status().isNotFound());
            mvc.perform(delete(path).header("Authorization",authorization(b))).andExpect(status().isNotFound());
            mvc.perform(get("/api/v1/vehicles/"+v+"/"+row.getKey()).header("Authorization",authorization(b))).andExpect(status().isNotFound());
        }
        mvc.perform(get("/api/v1/vehicles/"+v+"/timeline").header("Authorization",authorization(b))).andExpect(status().isNotFound());
    }
    @Test void deletingVehicleCascadesThroughAllJournalTables() throws Exception {
        String jwt=token("owner@example.com"); long v=create(jwt); add(jwt,v,"refuelings",fuel(day(0),73000,"150")); add(jwt,v,"services",service("200")); add(jwt,v,"documents","{\"type\":\"INSPECTION\",\"validUntil\":\""+day(7)+"\"}");
        mvc.perform(delete("/api/v1/vehicles/"+v).header("Authorization",authorization(jwt))).andExpect(status().isNoContent());
        for(String table:new String[]{"odometer_entries","expenses","refuelings","service_records","document_deadlines","journal_commands"}) assertThat(jdbc.queryForObject("SELECT count(*) FROM "+table,Integer.class)).isZero();
    }
    @Test void idempotencyPreventsDuplicatesAndRejectsReusedChangedBody() throws Exception {
        String jwt=token("owner@example.com"); long v=create(jwt); String path="/api/v1/vehicles/"+v+"/refuelings";
        long id=0;
        for(int i=0;i<2;i++) {
            var result=mvc.perform(post(path).header("Authorization",authorization(jwt)).header("Idempotency-Key","repeat-key").contentType("application/json").content(fuel(day(0),73000,"150"))).andExpect(status().isCreated()).andReturn();
            long next=json.readTree(result.getResponse().getContentAsString()).get("id").asLong(); if(i>0) assertThat(next).isEqualTo(id); id=next;
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM expenses",Integer.class)).isEqualTo(1);
        mvc.perform(post(path).header("Authorization",authorization(jwt)).header("Idempotency-Key","repeat-key").contentType("application/json").content(fuel(day(0),73000,"151"))).andExpect(status().isConflict());
        mvc.perform(delete("/api/v1/refuelings/"+id).header("Authorization",authorization(jwt))).andExpect(status().isNoContent());
        mvc.perform(post(path).header("Authorization",authorization(jwt)).header("Idempotency-Key","repeat-key").contentType("application/json").content(fuel(day(0),73000,"150"))).andExpect(status().isConflict());
    }
    @Test void concurrentRetriesCreateOneRefueling() throws Exception {
        String jwt=token("owner@example.com"); long v=create(jwt);
        try(var pool=Executors.newFixedThreadPool(2)) {
            Callable<Integer> call=()->mvc.perform(post("/api/v1/vehicles/"+v+"/refuelings").header("Authorization",authorization(jwt)).header("Idempotency-Key","concurrent").contentType("application/json").content(fuel(day(0),73000,"150"))).andReturn().getResponse().getStatus();
            var first=pool.submit(call); var second=pool.submit(call);
            assertThat(first.get(10,TimeUnit.SECONDS)).isEqualTo(201); assertThat(second.get(10,TimeUnit.SECONDS)).isEqualTo(201);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM refuelings",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM expenses",Integer.class)).isEqualTo(1);
    }
    @Test void journalValidationRejectsInvalidAmountsDatesAndDueRules() throws Exception {
        String jwt=token("owner@example.com"); long v=create(jwt);
        // @PastOrPresent uses the validation provider's local calendar, which can differ from UTC near midnight.
        for(String body:new String[]{fuel(day(0),73000,"0"),fuel(day(0),73000,"1.001"),fuel(LocalDate.now().plusDays(1).toString(),73000,"150"),fuel(day(0),73000,"150").replace("\"liters\":25","\"liters\":-1")})
            mvc.perform(post("/api/v1/vehicles/"+v+"/refuelings").header("Authorization",authorization(jwt)).contentType("application/json").content(body)).andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/api/v1/vehicles/"+v+"/services").header("Authorization",authorization(jwt)).contentType("application/json").content(service("10").replace("}",",\"nextDueOdometerKm\":70000}"))).andExpect(status().isUnprocessableEntity());
        mvc.perform(get("/api/v1/vehicles/"+v+"/timeline?dateFrom="+day(0)+"&dateTo="+day(-1)).header("Authorization",authorization(jwt))).andExpect(status().isUnprocessableEntity());
    }
    @Test void upgradingV1PreservesExistingVehicleAndCreatesInitialMeasurement() throws Exception {
        String schema="upgrade_"+UUID.randomUUID().toString().replace("-","");
        var source=jdbc.getDataSource();
        assertThat(source).isNotNull();
        try(var connection=source.getConnection()) {
            String url=connection.getMetaData().getURL(); String user=connection.getMetaData().getUserName();
            String password=System.getenv().getOrDefault("TEST_DATABASE_PASSWORD",postgres==null?"":postgres.getPassword());
            try {
                Flyway.configure().dataSource(url,user,password).schemas(schema).defaultSchema(schema).target("1").load().migrate();
                try(var isolated=DriverManager.getConnection(url,user,password); var statement=isolated.createStatement()) {
                    statement.execute("SET search_path TO "+schema);
                    statement.execute("INSERT INTO users(email,password_hash,created_at) VALUES ('migration@example.com','test-hash',now())");
                    statement.execute("INSERT INTO vehicles(user_id,make,model,year,fuel_type,current_odometer_km,created_at,updated_at) VALUES (1,'Toyota','Corolla',2020,'PETROL',12345,now(),now())");
                }
                Flyway.configure().dataSource(url,user,password).schemas(schema).defaultSchema(schema).load().migrate();
                assertThat(jdbc.queryForObject("SELECT current_odometer_km FROM "+schema+".vehicles",Integer.class)).isEqualTo(12345);
                assertThat(jdbc.queryForObject("SELECT odometer_km FROM "+schema+".odometer_entries",Integer.class)).isEqualTo(12345);
                assertThat(jdbc.queryForObject("SELECT source_type FROM "+schema+".odometer_entries",String.class)).isEqualTo("MANUAL");
            } finally { jdbc.execute("DROP SCHEMA IF EXISTS "+schema+" CASCADE"); }
        }
    }
    @Test void databaseFailureAfterLinkedWritesRollsBackWholeRefueling() throws Exception {
        String jwt=token("owner@example.com"); long v=create(jwt);
        jdbc.execute("CREATE FUNCTION test_fail_refueling() RETURNS trigger AS $$ BEGIN RAISE EXCEPTION 'forced test failure' USING ERRCODE='23514'; END; $$ LANGUAGE plpgsql");
        jdbc.execute("CREATE TRIGGER test_refueling_rollback BEFORE INSERT ON refuelings FOR EACH ROW WHEN (NEW.station='ROLLBACK_TEST') EXECUTE FUNCTION test_fail_refueling()");
        try {
            String body=fuel(day(0),73000,"150").replace("}",",\"station\":\"ROLLBACK_TEST\"}");
            mvc.perform(post("/api/v1/vehicles/"+v+"/refuelings").header("Authorization",authorization(jwt)).header("Idempotency-Key","rollback-key").contentType("application/json").content(body))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("Конфликт сохраняемых данных"));
            assertThat(jdbc.queryForObject("SELECT count(*) FROM expenses",Integer.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM refuelings",Integer.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM journal_commands",Integer.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM odometer_entries",Integer.class)).isEqualTo(1);
            mvc.perform(get("/api/v1/vehicles/"+v).header("Authorization",authorization(jwt))).andExpect(jsonPath("$.currentOdometerKm").value(72000));
        } finally { jdbc.execute("DROP TRIGGER test_refueling_rollback ON refuelings"); jdbc.execute("DROP FUNCTION test_fail_refueling()"); }
    }
    private long analyticVehicle(String jwt) throws Exception {
        return json.readTree(mvc.perform(post("/api/v1/vehicles").header("Authorization",authorization(jwt)).contentType("application/json").content(vehicle().replace("72000","200000")))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
    }
    private JsonNode analytics(String jwt,long vehicle,String section,String period) throws Exception {
        return json.readTree(mvc.perform(get("/api/v1/vehicles/"+vehicle+"/analytics/"+section+period).header("Authorization",authorization(jwt))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    @Test void analyticsTotalsCategoriesMonthlyAndCostPerKmMatchControlData() throws Exception {
        String jwt=token("owner@example.com"); long v=analyticVehicle(jwt);
        add(jwt,v,"odometer","{\"date\":\"2026-01-01\",\"odometerKm\":100000}");
        add(jwt,v,"odometer","{\"date\":\"2026-02-28\",\"odometerKm\":101000}");
        add(jwt,v,"expenses","{\"date\":\"2026-01-01\",\"category\":\"PARKING\",\"amountPln\":100,\"title\":\"Parking\"}");
        add(jwt,v,"expenses","{\"date\":\"2026-01-31\",\"category\":\"WASH\",\"amountPln\":200,\"title\":\"Wash\"}");
        String period="?dateFrom=2026-01-01&dateTo=2026-02-28";
        var summary=analytics(jwt,v,"summary",period);
        assertThat(summary.get("totalExpensesPln").decimalValue()).isEqualByComparingTo("300");
        assertThat(summary.get("monthAveragePln").decimalValue()).isEqualByComparingTo("150");
        assertThat(summary.get("costPerKmPln").decimalValue()).isEqualByComparingTo("0.3000"); assertThat(summary.get("distanceKm").asLong()).isEqualTo(1000);
        var categories=analytics(jwt,v,"categories",period).get("content"); BigDecimal sum=BigDecimal.ZERO;
        for(var row:categories) sum=sum.add(row.get("amountPln").decimalValue()); assertThat(sum).isEqualByComparingTo("300");
        var months=analytics(jwt,v,"monthly",period).get("content"); assertThat(months.size()).isEqualTo(2);
        assertThat(months.get(0).get("amountPln").decimalValue()).isEqualByComparingTo("300"); assertThat(months.get(1).get("amountPln").decimalValue()).isZero();
    }
    @Test void analyticsFuelIncludesPartialFillsWithoutCountingStartingTank() throws Exception {
        String jwt=token("owner@example.com"); long v=analyticVehicle(jwt);
        add(jwt,v,"refuelings",fuel("2026-01-01",100000,"150"));
        add(jwt,v,"refuelings",fuel("2026-01-10",100200,"120").replace("\"liters\":25","\"liters\":20").replace("\"isFullTank\":true","\"isFullTank\":false"));
        add(jwt,v,"refuelings",fuel("2026-01-31",100600,"180").replace("\"liters\":25","\"liters\":30"));
        var result=analytics(jwt,v,"fuel","?dateFrom=2026-01-01&dateTo=2026-01-31");
        assertThat(result.get("averageLitersPer100Km").decimalValue()).isEqualByComparingTo("8.33");
        assertThat(result.get("averagePricePerLiterPln").decimalValue()).isEqualByComparingTo("6");
        assertThat(result.get("measuredLiters").decimalValue()).isEqualByComparingTo("50"); assertThat(result.get("cycleCount").asInt()).isEqualTo(1);
    }
    @Test void emptyAndZeroMileageAreExplicitlyUnavailable() throws Exception {
        String jwt=token("owner@example.com"); long v=create(jwt);
        var empty=analytics(jwt,v,"summary","?dateFrom=2025-01-01&dateTo=2025-01-31");
        assertThat(empty.get("costPerKmPln").isNull()).isTrue(); assertThat(empty.get("distanceKm").isNull()).isTrue();
        assertThat(analytics(jwt,v,"fuel","").get("averageLitersPer100Km").isNull()).isTrue();
        add(jwt,v,"odometer","{\"date\":\""+day(0)+"\",\"odometerKm\":72000}");
        var zero=analytics(jwt,v,"summary","?dateFrom="+day(0)+"&dateTo="+day(0)); assertThat(zero.get("distanceKm").asInt()).isZero(); assertThat(zero.get("costPerKmPln").isNull()).isTrue();
    }
    @Test void dashboardUsesOneLedgerAndReflectsSourceChanges() throws Exception {
        String jwt=token("owner@example.com"); long v=create(jwt); var f=add(jwt,v,"refuelings",fuel(day(0),73000,"150")); add(jwt,v,"services",service("200"));
        var dashboard=analytics(jwt,v,"dashboard",""); assertThat(dashboard.get("summary").get("totalExpensesPln").decimalValue()).isEqualByComparingTo("350"); assertThat(dashboard.get("lifetimeExpensesPln").decimalValue()).isEqualByComparingTo("350");
        mvc.perform(patch("/api/v1/refuelings/"+f.get("id").asLong()).header("Authorization",authorization(jwt)).contentType("application/json").content("{\"totalCostPln\":160}")).andExpect(status().isOk());
        assertThat(analytics(jwt,v,"summary","").get("totalExpensesPln").decimalValue()).isEqualByComparingTo("360");
        mvc.perform(delete("/api/v1/refuelings/"+f.get("id").asLong()).header("Authorization",authorization(jwt))).andExpect(status().isNoContent());
        assertThat(analytics(jwt,v,"summary","").get("totalExpensesPln").decimalValue()).isEqualByComparingTo("200");
    }
    @Test void remindersCompleteReopenAndResetWhenRuleChanges() throws Exception {
        String jwt=token("owner@example.com"); long v=create(jwt);
        var s=add(jwt,v,"services",service("0").replace("73000","72000").replace("}",",\"nextDueDate\":\""+day(7)+"\",\"nextDueOdometerKm\":72500}")); long id=s.get("id").asLong();
        String url="/api/v1/vehicles/"+v+"/reminders";
        mvc.perform(get(url).header("Authorization",authorization(jwt))).andExpect(jsonPath("$.content[0].status").value("UPCOMING")).andExpect(jsonPath("$.content[0].notificationStage").value("D7|K500"));
        mvc.perform(patch("/api/v1/services/"+id+"/reminder").header("Authorization",authorization(jwt)).contentType("application/json").content("{\"done\":true}")).andExpect(status().isNoContent());
        mvc.perform(get(url).header("Authorization",authorization(jwt))).andExpect(jsonPath("$.content.length()").value(0));
        mvc.perform(get(url+"?includeDone=true").header("Authorization",authorization(jwt))).andExpect(jsonPath("$.content[0].status").value("DONE"));
        mvc.perform(patch("/api/v1/services/"+id).header("Authorization",authorization(jwt)).contentType("application/json").content("{\"nextDueDate\":\""+day(6)+"\"}")).andExpect(status().isOk());
        mvc.perform(get(url).header("Authorization",authorization(jwt))).andExpect(jsonPath("$.content[0].status").value("UPCOMING"));
        mvc.perform(patch("/api/v1/services/"+id+"/reminder").header("Authorization",authorization(jwt)).contentType("application/json").content("{\"done\":false}")).andExpect(status().isNoContent());
    }
    @Test void renewedDocumentDoesNotLeaveFalseOverdueReminder() throws Exception {
        String jwt=token("owner@example.com"); long v=create(jwt);
        add(jwt,v,"documents","{\"type\":\"INSURANCE\",\"validUntil\":\""+day(-1)+"\"}"); add(jwt,v,"documents","{\"type\":\"INSURANCE\",\"validUntil\":\""+day(365)+"\"}");
        mvc.perform(get("/api/v1/vehicles/"+v+"/reminders").header("Authorization",authorization(jwt))).andExpect(jsonPath("$.content.length()").value(1)).andExpect(jsonPath("$.content[0].status").value("SCHEDULED"));
        mvc.perform(get("/api/v1/vehicles/"+v+"/reminders?includeDone=true").header("Authorization",authorization(jwt))).andExpect(jsonPath("$.content.length()").value(2));
    }
    @Test void reminderDatesUseRequestedTimeZoneAtMidnightBoundary() throws Exception {
        String jwt=token("owner@example.com"); long v=create(jwt);
        add(jwt,v,"documents","{\"type\":\"INSPECTION\",\"validUntil\":\"2026-11-12\"}");
        var fixed=new com.carcare.reminder.ReminderService(contextVehicles(),jdbc,java.time.Clock.fixed(Instant.parse("2026-11-11T23:30:00Z"),ZoneOffset.UTC));
        long user=users.findByEmail("owner@example.com").orElseThrow().getId();
        assertThat(fixed.list(user,v,"UTC",false).content().getFirst().status()).isEqualTo(com.carcare.reminder.ReminderDtos.Status.UPCOMING);
        assertThat(fixed.list(user,v,"Europe/Warsaw",false).content().getFirst().status()).isEqualTo(com.carcare.reminder.ReminderDtos.Status.DUE);
    }
    @Autowired com.carcare.vehicle.VehicleRepository analyticsVehicles;
    private com.carcare.vehicle.VehicleRepository contextVehicles() { return analyticsVehicles; }
    @Test void analyticsAndRemindersValidatePeriodAndOwnership() throws Exception {
        String a=token("a@example.com"); long v=create(a); String b=token("b@example.com"); var s=add(a,v,"services",service("0"));
        for(String section:new String[]{"summary","categories","monthly","fuel","dashboard"}) mvc.perform(get("/api/v1/vehicles/"+v+"/analytics/"+section).header("Authorization",authorization(b))).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/vehicles/"+v+"/reminders").header("Authorization",authorization(b))).andExpect(status().isNotFound());
        mvc.perform(patch("/api/v1/services/"+s.get("id").asLong()+"/reminder").header("Authorization",authorization(b)).contentType("application/json").content("{\"done\":true}")).andExpect(status().isNotFound());
        for(String query:new String[]{"?dateFrom=2026-02-01&dateTo=2026-01-01","?timeZone=Unknown/Zone","?dateFrom=1000-01-01&dateTo=2026-01-01"}) mvc.perform(get("/api/v1/vehicles/"+v+"/analytics/summary"+query).header("Authorization",authorization(a))).andExpect(status().isUnprocessableEntity());
    }
    private void monthlyExpense(long v, String month, String amount) {
        jdbc.update("INSERT INTO expenses(vehicle_id,date,category,amount_pln,title,source_type,created_at,updated_at) VALUES (?,?,'PARKING',?,'Monthly synthetic expense','MANUAL',now(),now())", v, java.time.YearMonth.parse(month).atDay(15), new BigDecimal(amount));
    }
    @Test void forecastAggregatesLedgerFillsZerosAndExcludesCurrentMonth() throws Exception {
        String jwt=token("owner@example.com"); long v=create(jwt);
        var current=java.time.YearMonth.now(ZoneOffset.UTC);
        monthlyExpense(v,current.minusMonths(3).toString(),"100"); monthlyExpense(v,current.minusMonths(1).toString(),"300");
        add(jwt,v,"expenses","{\"date\":\""+day(0)+"\",\"category\":\"PARKING\",\"amountPln\":999999,\"title\":\"Excluded partial month\"}");
        var result=json.readTree(mvc.perform(get("/api/v1/vehicles/"+v+"/forecast").header("Authorization",authorization(jwt))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(result.get("history").size()).isEqualTo(3); assertThat(result.get("history").get(1).get("amountPln").decimalValue()).isZero();
        assertThat(result.get("targetMonth").asText()).isEqualTo(current.plusMonths(1).toString());
        assertThat(result.get("methods").get(0).get("forecastPln").decimalValue()).isEqualByComparingTo("133.33");
        assertThat(result.get("methods").get(1).get("forecastPln").decimalValue()).isEqualByComparingTo("166.67");
        assertThat(result.get("warnings").toString()).contains("ZERO_MONTHS_ASSUMED_NO_EXPENSES");
    }
    @Test void forecastEmptyHistoryIsSuccessfulButUnavailable() throws Exception {
        String jwt=token("owner@example.com"); long v=create(jwt);
        mvc.perform(get("/api/v1/vehicles/"+v+"/forecast").header("Authorization",authorization(jwt))).andExpect(status().isOk())
            .andExpect(jsonPath("$.sufficientData").value(false)).andExpect(jsonPath("$.availableMonths").value(0))
            .andExpect(jsonPath("$.methods[0].forecastPln").isEmpty()).andExpect(jsonPath("$.methods[1].maePln").isEmpty());
    }
    @Test void forecastTruncatesEvaluationHistoryAndPreservesTrainingWindow() throws Exception {
        String jwt=token("owner@example.com"); long v=create(jwt); var current=java.time.YearMonth.now(ZoneOffset.UTC);
        for(int i=1;i<=15;i++) monthlyExpense(v,current.minusMonths(i).toString(),Integer.toString(1600-i*100));
        var result=json.readTree(mvc.perform(get("/api/v1/vehicles/"+v+"/forecast?historyMonths=6").header("Authorization",authorization(jwt))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(result.get("availableMonths").asInt()).isEqualTo(6); assertThat(result.get("evaluationCount").asInt()).isEqualTo(2);
        assertThat(result.get("methods").get(0).get("forecastPln").decimalValue()).isEqualByComparingTo("1400");
        assertThat(result.get("methods").get(0).get("maePln").decimalValue()).isEqualByComparingTo("300");
    }
    @Test void forecastTimeZoneControlsMonthBoundary() throws Exception {
        String jwt=token("owner@example.com"); long v=create(jwt); monthlyExpense(v,"2026-08","100"); monthlyExpense(v,"2026-09","200"); monthlyExpense(v,"2026-10","300");
        var service=new com.carcare.forecast.ForecastService(contextVehicles(),jdbc,java.time.Clock.fixed(Instant.parse("2026-10-31T23:30:00Z"),ZoneOffset.UTC));
        long user=users.findByEmail("owner@example.com").orElseThrow().getId();
        assertThat(service.forecast(user,v,"UTC",12).sufficientData()).isFalse();
        assertThat(service.forecast(user,v,"Europe/Warsaw",12).sufficientData()).isTrue();
        assertThat(service.forecast(user,v,"Europe/Warsaw",12).targetMonth()).isEqualTo("2026-12");
    }
    @Test void forecastAndCsvRejectForeignAnonymousAndInvalidInputs() throws Exception {
        String a=token("a@example.com"); long v=create(a); String b=token("b@example.com");
        for(String suffix:new String[]{"forecast","expenses/export.csv"}) {
            mvc.perform(get("/api/v1/vehicles/"+v+"/"+suffix)).andExpect(status().isUnauthorized());
            mvc.perform(get("/api/v1/vehicles/"+v+"/"+suffix).header("Authorization",authorization(b))).andExpect(status().isNotFound());
        }
        for(String query:new String[]{"historyMonths=2","historyMonths=61","timeZone=Unknown/Zone"}) mvc.perform(get("/api/v1/vehicles/"+v+"/forecast?"+query).header("Authorization",authorization(a))).andExpect(status().isUnprocessableEntity());
        mvc.perform(get("/api/v1/vehicles/"+v+"/expenses/export.csv?dateFrom=2026-02-01&dateTo=2026-01-01").header("Authorization",authorization(a))).andExpect(status().isUnprocessableEntity());
    }
    @Test void csvExportsSingleLedgerWithBomExactAmountsAndInclusiveFilters() throws Exception {
        String jwt=token("owner@example.com"); long v=create(jwt);
        var fuel=add(jwt,v,"refuelings",fuel(day(0),73000,"150.25")); add(jwt,v,"services",service("200.50"));
        add(jwt,v,"expenses",json.writeValueAsString(Map.of("date",day(0),"category","PARKING","amountPln","0.10","title","=SUM(1,2)","description","Масло, \"фильтр\"\nстрока")));
        String url="/api/v1/vehicles/"+v+"/expenses/export.csv?dateFrom="+day(0)+"&dateTo="+day(0);
        var response=mvc.perform(get(url).header("Authorization",authorization(jwt))).andExpect(status().isOk()).andExpect(content().contentType("text/csv;charset=UTF-8"))
            .andExpect(header().string("Cache-Control","no-store")).andExpect(header().string("Content-Disposition",org.hamcrest.Matchers.containsString("attachment"))).andReturn().getResponse();
        assertThat(response.getContentAsByteArray()).startsWith((byte)0xef,(byte)0xbb,(byte)0xbf);
        String csv=response.getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(csv).contains("\"150.25\"","\"200.50\"","\"0.10\"","\"'=SUM(1,2)\"","Масло, \"\"фильтр\"\"\nстрока");
        assertThat(csv.split("\"PLN\"",-1)).hasSize(4);
        var filtered=mvc.perform(get(url+"&category=FUEL").header("Authorization",authorization(jwt))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(filtered.split("\"PLN\"",-1)).hasSize(2);
        mvc.perform(patch("/api/v1/refuelings/"+fuel.get("id").asLong()).header("Authorization",authorization(jwt)).contentType("application/json").content("{\"totalCostPln\":175.25}")).andExpect(status().isOk());
        assertThat(mvc.perform(get(url+"&category=FUEL").header("Authorization",authorization(jwt))).andReturn().getResponse().getContentAsString()).contains("175.25").doesNotContain("150.25");
    }
    @Test void csvRejectsOversizedExportInsteadOfSilentlyTruncating() throws Exception {
        String jwt=token("owner@example.com"); long v=create(jwt);
        jdbc.update("INSERT INTO expenses(vehicle_id,date,category,amount_pln,title,source_type,created_at,updated_at) SELECT ?,?::date,'PARKING',0.01,'Synthetic','MANUAL',now(),now() FROM generate_series(1,25001)",v,day(0));
        mvc.perform(get("/api/v1/vehicles/"+v+"/expenses/export.csv").header("Authorization",authorization(jwt))).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("EXPORT_TOO_LARGE"));
    }
    @Test void forecastChangesAfterEditingAndDeletingLinkedSourceWithoutDoubleCounting() throws Exception {
        String jwt=token("owner@example.com"); long v=create(jwt); var current=java.time.YearMonth.now(ZoneOffset.UTC);
        monthlyExpense(v,current.minusMonths(3).toString(),"100"); monthlyExpense(v,current.minusMonths(2).toString(),"200");
        var f=add(jwt,v,"refuelings",fuel(current.minusMonths(1).atDay(15).toString(),72000,"300"));
        String url="/api/v1/vehicles/"+v+"/forecast";
        mvc.perform(get(url).header("Authorization",authorization(jwt))).andExpect(jsonPath("$.methods[0].forecastPln").value(200));
        mvc.perform(patch("/api/v1/refuelings/"+f.get("id").asLong()).header("Authorization",authorization(jwt)).contentType("application/json").content("{\"totalCostPln\":600}")).andExpect(status().isOk());
        mvc.perform(get(url).header("Authorization",authorization(jwt))).andExpect(jsonPath("$.methods[0].forecastPln").value(300));
        mvc.perform(delete("/api/v1/refuelings/"+f.get("id").asLong()).header("Authorization",authorization(jwt))).andExpect(status().isNoContent());
        mvc.perform(get(url).header("Authorization",authorization(jwt))).andExpect(jsonPath("$.methods[0].forecastPln").value(100));
    }
    @Test void malformedSignedAccountClaimsAreUnauthorizedInsteadOfServerErrors() throws Exception {
        for(String subject:new String[]{"abc","-1","0","9223372036854775808"}) {
            var claims=JwtClaimsSet.builder().issuer("carcare-api").subject(subject).issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(3600)).build();
            String signed=encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),claims)).getTokenValue();
            mvc.perform(get("/api/v1/vehicles").header("Authorization",authorization(signed))).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        }
    }
    @Test void signedTokenWithoutExpiryIsUnauthorized() throws Exception {
        var claims=JwtClaimsSet.builder().issuer("carcare-api").subject("1").issuedAt(Instant.now()).build();
        String signed=encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),claims)).getTokenValue();
        mvc.perform(get("/api/v1/users/me").header("Authorization",authorization(signed))).andExpect(status().isUnauthorized());
    }
    @Test void mediaTypesAndMethodsReturnClientErrorsWithoutInternalDetails() throws Exception {
        String jwt=token("owner@example.com"); long v=create(jwt);
        mvc.perform(post("/api/v1/vehicles").header("Authorization",authorization(jwt)).contentType("text/plain").content(vehicle())).andExpect(status().isUnsupportedMediaType()).andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE")).andExpect(jsonPath("$.stackTrace").doesNotExist());
        mvc.perform(get("/api/v1/vehicles/"+v).header("Authorization",authorization(jwt)).accept("text/plain")).andExpect(status().isNotAcceptable());
        mvc.perform(put("/api/v1/vehicles/"+v).header("Authorization",authorization(jwt)).contentType("application/json").content("{}")).andExpect(status().isMethodNotAllowed());
    }
    private void databaseRejects(Runnable action,String expectedState) {
        try { action.run(); fail("Database constraint must reject invalid write"); }
        catch(org.springframework.dao.DataIntegrityViolationException ex) { assertThat(((java.sql.SQLException)ex.getMostSpecificCause()).getSQLState()).isEqualTo(expectedState); }
    }
    @Test void databaseRejectsCrossVehicleExpenseAndMileageLinks() throws Exception {
        String a=token("a@example.com"); long first=create(a); String b=token("b@example.com"); long second=create(b);
        var fuel=add(a,first,"refuelings",fuel(day(0),73000,"150")); var s=add(a,first,"services",service("200"));
        var expense=add(b,second,"expenses","{\"date\":\""+day(0)+"\",\"category\":\"OTHER\",\"amountPln\":10,\"title\":\"Second owner\"}");
        Long odometer=jdbc.queryForObject("SELECT id FROM odometer_entries WHERE vehicle_id=? AND source_type='MANUAL'",Long.class,second);
        for(String table:new String[]{"refuelings","service_records"}) {
            long id=table.equals("refuelings")?fuel.get("id").asLong():s.get("id").asLong();
            databaseRejects(()->jdbc.update("UPDATE "+table+" SET expense_id=? WHERE id=?",expense.get("id").asLong(),id),"23503");
            databaseRejects(()->jdbc.update("UPDATE "+table+" SET odometer_entry_id=? WHERE id=?",odometer,id),"23503");
        }
        mvc.perform(get("/api/v1/refuelings/"+fuel.get("id").asLong()).header("Authorization",authorization(a))).andExpect(jsonPath("$.totalCostPln").value(150));
    }
    @Test void databaseRejectsBrokenReferencesNonpositiveAmountsAndManualSourceIds() throws Exception {
        String jwt=token("owner@example.com"); long v=create(jwt); var f=add(jwt,v,"refuelings",fuel(day(0),73000,"150"));
        long expense=f.get("expenseId").asLong();
        databaseRejects(()->jdbc.update("DELETE FROM expenses WHERE id=?",expense),"23503");
        databaseRejects(()->jdbc.update("UPDATE expenses SET amount_pln=-1 WHERE id=?",expense),"23514");
        var manual=add(jwt,v,"expenses","{\"date\":\""+day(0)+"\",\"category\":\"OTHER\",\"amountPln\":10,\"title\":\"Manual\"}");
        databaseRejects(()->jdbc.update("UPDATE expenses SET source_id=99 WHERE id=?",manual.get("id").asLong()),"23514");
        assertThat(jdbc.queryForObject("SELECT amount_pln FROM expenses WHERE id=?",BigDecimal.class,expense)).isEqualByComparingTo("150");
    }
    @Test void databaseRejectsDuplicateAutomaticExpenseSource() throws Exception {
        String jwt=token("owner@example.com"); long v=create(jwt); var f=add(jwt,v,"refuelings",fuel(day(0),73000,"150"));
        databaseRejects(()->jdbc.update("INSERT INTO expenses(vehicle_id,date,category,amount_pln,title,source_type,source_id,created_at,updated_at) SELECT vehicle_id,date,category,amount_pln,title,source_type,source_id,created_at,updated_at FROM expenses WHERE id=?",f.get("expenseId").asLong()),"23505");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM expenses WHERE vehicle_id=?",Integer.class,v)).isEqualTo(1);
    }
    @Test void sqlLikeTextRemainsDataAndInvalidSortIsRejected() throws Exception {
        String jwt=token("owner@example.com"); long v=create(jwt); String title="O'Brien; DROP TABLE users; --";
        add(jwt,v,"expenses",json.writeValueAsString(Map.of("date",day(0),"category","OTHER","amountPln","10.00","title",title)));
        mvc.perform(get("/api/v1/vehicles?sort=make;DROP%20TABLE%20users,asc").header("Authorization",authorization(jwt))).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT title FROM expenses WHERE vehicle_id=?",String.class,v)).isEqualTo(title);
        mvc.perform(get("/api/v1/users/me").header("Authorization",authorization(jwt))).andExpect(status().isOk());
    }
    @Test void emptyCsvContainsOnlyBomAndHeader() throws Exception {
        String jwt=token("owner@example.com"); long v=create(jwt);
        byte[] csv=mvc.perform(get("/api/v1/vehicles/"+v+"/expenses/export.csv").header("Authorization",authorization(jwt))).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(csv).startsWith((byte)0xef,(byte)0xbb,(byte)0xbf);
        assertThat(new String(csv,java.nio.charset.StandardCharsets.UTF_8).split("\r\n")).hasSize(1);
    }
    @Test void analyticsPerformanceSampleOnTenThousandExpenses() throws Exception {
        String jwt=token("owner@example.com"); long v=create(jwt);
        jdbc.update("INSERT INTO expenses(vehicle_id,date,category,amount_pln,title,source_type,created_at,updated_at) SELECT ?,'2026-01-15'::date,'PARKING',0.01,'Synthetic expense','MANUAL',now(),now() FROM generate_series(1,10000)",v);
        String p="?dateFrom=2026-01-01&dateTo=2026-01-31"; analytics(jwt,v,"summary",p);
        long start=System.nanoTime();
        for(int i=0;i<20;i++) assertThat(analytics(jwt,v,"summary",p).get("totalExpensesPln").decimalValue()).isEqualByComparingTo("100.00");
        double averageMs=(System.nanoTime()-start)/20_000_000.0;
        System.out.printf(java.util.Locale.ROOT,"ANALYTICS_PERFORMANCE average_ms=%.2f requests=20 rows=10000%n",averageMs);
    }
}
