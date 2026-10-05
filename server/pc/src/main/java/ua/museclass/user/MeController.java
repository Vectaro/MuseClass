package ua.museclass.user;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ua.museclass.auth.TokenService;
import ua.museclass.common.ApiException;
import ua.museclass.musicxml.InstrumentDetector;
import ua.museclass.score.ScoreRepository;
import ua.museclass.score.ScoreService;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/me")
public class MeController {

    public record Profile(UUID id, String email, String displayName, List<String> instruments, Instant createdAt) {}

    public record UpdateProfileRequest(
            @NotBlank(message = "Вкажи ім'я.") @Size(max = 60, message = "Ім'я — до 60 символів.") String displayName) {}

    public record InstrumentsRequest(
            @NotNull(message = "Передай список інструментів.")
            @Size(max = 10, message = "Забагато інструментів.") List<String> instruments) {}

    private final UserRepository users;
    private final ScoreService scores;

    public MeController(UserRepository users, ScoreService scores) {
        this.users = users;
        this.scores = scores;
    }

    @GetMapping
    public Profile me(@AuthenticationPrincipal Jwt jwt) {
        return profile(TokenService.userId(jwt));
    }

    @PatchMapping
    public Profile update(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UpdateProfileRequest req) {
        UUID me = TokenService.userId(jwt);
        users.updateName(me, req.displayName().trim());
        return profile(me);
    }

    /** Замінює список цілком. Коди: piano, guitar, voice, violin, trumpet, flute, bass_guitar, drums, saxophone, bandura. */
    @PutMapping("/instruments")
    @Transactional
    public Profile instruments(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody InstrumentsRequest req) {
        UUID me = TokenService.userId(jwt);
        Set<String> codes = new LinkedHashSet<>();
        for (String raw : req.instruments()) {
            String c = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
            if (!InstrumentDetector.CODES.contains(c)) {
                throw ApiException.badRequest("Невідомий інструмент: " + raw);
            }
            codes.add(c);
        }
        users.replaceInstruments(me, List.copyOf(codes));
        return profile(me);
    }

    /** Усе, що видали в моїх класах. */
    @GetMapping("/library")
    public List<ScoreRepository.LibraryEntry> library(@AuthenticationPrincipal Jwt jwt) {
        return scores.myLibrary(TokenService.userId(jwt));
    }

    @GetMapping("/saved")
    public List<ScoreRepository.Summary> saved(@AuthenticationPrincipal Jwt jwt) {
        return scores.saved(TokenService.userId(jwt));
    }

    @PutMapping("/saved/{scoreId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void save(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID scoreId) {
        scores.save(TokenService.userId(jwt), scoreId);
    }

    @DeleteMapping("/saved/{scoreId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unsave(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID scoreId) {
        scores.unsave(TokenService.userId(jwt), scoreId);
    }

    private Profile profile(UUID me) {
        UserRepository.UserRow u = users.findById(me)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Акаунт не знайдено. Увійди знову."));
        return new Profile(u.id(), u.email(), u.displayName(), users.instruments(me), u.createdAt());
    }
}
