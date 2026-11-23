package com.carcare.auth;

import com.carcare.exception.ApiException;
import com.carcare.user.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Map;

@Service
public class AuthService {
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final TokenService tokens;
    private final Clock clock;
    private final String dummyHash;
    public AuthService(UserRepository users, PasswordEncoder passwords, TokenService tokens, Clock clock) {
        this.users = users; this.passwords = passwords; this.tokens = tokens; this.clock = clock;
        this.dummyHash = passwords.encode("timing-equalization-password");
    }
    @Transactional
    public AuthResponse register(AuthRequest request) {
        validatePassword(request.password());
        if (users.existsByEmail(request.email())) throw new ApiException(HttpStatus.CONFLICT, "EMAIL_EXISTS", "Этот e-mail уже зарегистрирован");
        var user = users.saveAndFlush(new User(request.email(), passwords.encode(request.password()), clock.instant()));
        return tokens.issue(user);
    }
    @Transactional(readOnly = true)
    public AuthResponse login(AuthRequest request) {
        validatePassword(request.password());
        var user = users.findByEmail(request.email());
        boolean valid = passwords.matches(request.password(), user.map(User::getPasswordHash).orElse(dummyHash));
        if (!valid || user.isEmpty()) throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Неверный e-mail или пароль");
        return tokens.issue(user.orElseThrow());
    }
    private void validatePassword(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) throw ApiException.invalid(Map.of("password", "Пароль должен занимать не более 72 байт UTF-8"));
    }
}
