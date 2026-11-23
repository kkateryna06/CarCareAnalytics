package com.carcare.security;

import com.carcare.exception.ApiError;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.SecurityFilterChain;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;

@Configuration
public class SecurityConfig {
    @Bean Clock clock() { return Clock.systemUTC(); }
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }
    @Bean SecretKey jwtKey(@Value("${app.jwt.secret}") String secret) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) throw new IllegalArgumentException("JWT_SECRET must contain at least 32 bytes");
        return new SecretKeySpec(bytes, "HmacSHA256");
    }
    @Bean JwtEncoder encoder(SecretKey key) { return new NimbusJwtEncoder(new ImmutableSecret<>(key)); }
    @Bean JwtDecoder decoder(SecretKey key, @Value("${app.jwt.issuer}") String issuer, Clock clock) {
        var decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(new org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator<>(
            JwtValidators.createDefaultWithIssuer(issuer), new TokenClaimsValidator(clock)));
        return decoder;
    }
    @Bean SecurityFilterChain security(HttpSecurity http, ObjectMapper json, Environment environment) throws Exception {
        http.csrf(csrf -> csrf.disable()).sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        http.authorizeHttpRequests(auth -> {
            auth.requestMatchers(HttpMethod.POST, "/api/v1/auth/register", "/api/v1/auth/login").permitAll();
            if (environment.acceptsProfiles(Profiles.of("dev"))) auth.requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll();
            auth.anyRequest().authenticated();
        });
        var entryPoint = (org.springframework.security.web.AuthenticationEntryPoint) (req, res, ex) -> {
            res.setStatus(401); res.setContentType("application/json"); res.setCharacterEncoding("UTF-8");
            json.writeValue(res.getOutputStream(), ApiError.of("UNAUTHORIZED", "Войдите в аккаунт", req.getRequestURI()));
        };
        http.exceptionHandling(ex -> ex.authenticationEntryPoint(entryPoint).accessDeniedHandler((req, res, error) -> {
            res.setStatus(403); res.setContentType("application/json"); res.setCharacterEncoding("UTF-8");
            json.writeValue(res.getOutputStream(), ApiError.of("FORBIDDEN", "Доступ запрещён", req.getRequestURI()));
        }));
        http.oauth2ResourceServer(oauth -> oauth.jwt(jwt -> {}).authenticationEntryPoint(entryPoint));
        return http.build();
    }
}
