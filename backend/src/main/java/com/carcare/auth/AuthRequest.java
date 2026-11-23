package com.carcare.auth;

import jakarta.validation.constraints.*;
import java.util.Locale;

public record AuthRequest(
        @NotBlank(message = "Укажите e-mail") @Email(message = "Некорректный e-mail") @Size(max = 254) String email,
        @NotBlank(message = "Укажите пароль") @Size(min = 8, max = 72, message = "Пароль: от 8 до 72 символов") String password) {
    public AuthRequest { if (email != null) email = email.strip().toLowerCase(Locale.ROOT); }
}
