package com.carcare.user;

import com.carcare.exception.ApiException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {
    private final UserRepository users;
    public UserController(UserRepository users) { this.users = users; }
    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal Jwt jwt) {
        return UserResponse.from(users.findById(Long.parseLong(jwt.getSubject())).orElseThrow(ApiException::notFound));
    }
}
