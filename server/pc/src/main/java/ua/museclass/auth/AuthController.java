package ua.museclass.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ua.museclass.common.ApiException;
import ua.museclass.user.UserRepository;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    public record RegisterRequest(
            @NotBlank(message = "Вкажи пошту.") @Email(message = "Пошта виглядає неправильно.")
            @Size(max = 254, message = "Пошта задовга.") String email,
            @NotBlank(message = "Вкажи пароль.") @Size(min = 8, max = 128, message = "Пароль — від 8 до 128 символів.")
            String password,
            @NotBlank(message = "Вкажи ім'я.") @Size(max = 60, message = "Ім'я — до 60 символів.")
            String displayName) {}

    public record LoginRequest(
            @NotBlank(message = "Вкажи пошту.") String email,
            @NotBlank(message = "Вкажи пароль.") String password) {}

    public record AuthResponse(String token, Instant expiresAt, UUID userId, String displayName) {}

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final TokenService tokens;
    /** Хеш-пустушка: вхід з неіснуючою поштою займає стільки ж часу, скільки з існуючою. */
    private final String dummyHash;

    public AuthController(UserRepository users, PasswordEncoder encoder, TokenService tokens) {
        this.users = users;
        this.encoder = encoder;
        this.tokens = tokens;
        this.dummyHash = encoder.encode("museclass-timing-equalizer");
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest req) {
        String email = req.email().trim().toLowerCase(Locale.ROOT);
        String name = req.displayName().trim();
        UUID id;
        try {
            id = users.insert(email, encoder.encode(req.password()), name);
        } catch (DuplicateKeyException e) {
            throw ApiException.conflict("Акаунт з такою поштою вже є.");
        }
        TokenService.IssuedToken t = tokens.issue(id);
        return new AuthResponse(t.token(), t.expiresAt(), id, name);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest req) {
        var user = users.findByEmail(req.email().trim());
        String hash = user.map(UserRepository.UserRow::passwordHash).orElse(dummyHash);
        boolean ok = encoder.matches(req.password(), hash);
        if (user.isEmpty() || !ok) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Невірна пошта або пароль.");
        }
        TokenService.IssuedToken t = tokens.issue(user.get().id());
        return new AuthResponse(t.token(), t.expiresAt(), user.get().id(), user.get().displayName());
    }
}
