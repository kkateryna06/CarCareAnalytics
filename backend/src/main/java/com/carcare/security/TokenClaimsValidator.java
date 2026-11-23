package com.carcare.security;

import java.time.Clock;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.Jwt;

/** Validate the application's account identifier and mandatory token lifetime before controllers run. */
public final class TokenClaimsValidator implements OAuth2TokenValidator<Jwt> {
    private final Clock clock;
    public TokenClaimsValidator(Clock clock) { this.clock = clock; }
    @Override public OAuth2TokenValidatorResult validate(Jwt token) {
        try {
            String subject = token.getSubject();
            if (subject == null || !subject.matches("[1-9][0-9]{0,18}") || Long.parseLong(subject) <= 0 ||
                token.getIssuedAt() == null || token.getExpiresAt() == null ||
                !token.getExpiresAt().isAfter(token.getIssuedAt()) || token.getIssuedAt().isAfter(clock.instant().plusSeconds(60)))
                return invalid();
            return OAuth2TokenValidatorResult.success();
        } catch (RuntimeException ex) { return invalid(); }
    }
    private OAuth2TokenValidatorResult invalid() {
        return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Invalid account or token lifetime", null));
    }
}
