package com.carcare;

import com.carcare.security.TokenClaimsValidator;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import static org.assertj.core.api.Assertions.*;

class TokenClaimsValidatorTest {
    private final Instant now = Instant.parse("2026-11-12T10:00:00Z");
    private final TokenClaimsValidator validator = new TokenClaimsValidator(Clock.fixed(now, ZoneOffset.UTC));
    private Jwt token(String subject, Instant issued, Instant expires) {
        var jwt = Jwt.withTokenValue("local-test").header("alg", "HS256").claim("iss", "carcare-api");
        if(subject != null) jwt.subject(subject);
        if(issued != null) jwt.issuedAt(issued);
        if(expires != null) jwt.expiresAt(expires);
        return jwt.build();
    }
    @Test void normalAndMaximumPositiveAccountIdsAreValid() {
        for(String subject : List.of("1", Long.toString(Long.MAX_VALUE)))
            assertThat(validator.validate(token(subject, now, now.plusSeconds(3600))).hasErrors()).isFalse();
    }
    @Test void malformedNegativeZeroAndOverflowSubjectsAreRejected() {
        for(String subject : List.of("", "0", "-1", "+1", "01", "abc", "1.0", "9223372036854775808", "99999999999999999999999"))
            assertThat(validator.validate(token(subject, now, now.plusSeconds(3600))).hasErrors()).isTrue();
    }
    @Test void missingAccountIsRejected() { assertThat(validator.validate(token(null, now, now.plusSeconds(3600))).hasErrors()).isTrue(); }
    @Test void missingExpiryAndIssueTimeAreRejected() {
        assertThat(validator.validate(token("1", now, null)).hasErrors()).isTrue();
        assertThat(validator.validate(token("1", null, now.plusSeconds(3600))).hasErrors()).isTrue();
    }
    @Test void FutureIssueBeyondClockSkewIsRejected() {
        assertThat(validator.validate(token("1", now.plusSeconds(61), now.plusSeconds(3600))).hasErrors()).isTrue();
        assertThat(validator.validate(token("1", now.plusSeconds(60), now.plusSeconds(3600))).hasErrors()).isFalse();
    }
}
