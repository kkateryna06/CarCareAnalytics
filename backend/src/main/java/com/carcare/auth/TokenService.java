package com.carcare.auth;

import com.carcare.user.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import java.time.*;

@Service
public class TokenService {
    private final JwtEncoder encoder;
    private final Clock clock;
    private final String issuer;
    private final Duration ttl;
    public TokenService(JwtEncoder encoder, Clock clock, @Value("${app.jwt.issuer}") String issuer, @Value("${app.jwt.ttl}") Duration ttl) {
        this.encoder = encoder; this.clock = clock; this.issuer = issuer; this.ttl = ttl;
    }
    public AuthResponse issue(User user) {
        var now = clock.instant(); var expires = now.plus(ttl);
        var claims = JwtClaimsSet.builder().issuer(issuer).subject(user.getId().toString()).issuedAt(now).expiresAt(expires).build();
        var token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        return new AuthResponse(token, "Bearer", expires, UserResponse.from(user));
    }
}
