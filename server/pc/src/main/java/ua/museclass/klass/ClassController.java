package ua.museclass.klass;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ua.museclass.auth.TokenService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/classes")
public class ClassController {

    public record CreateClassRequest(
            @NotBlank(message = "Вкажи назву класу.") @Size(max = 80, message = "Назва — до 80 символів.") String name,
            String codePrefix) {}

    public record JoinRequest(@NotBlank(message = "Вкажи код класу.") String code) {}

    public record RenameRequest(
            @NotBlank(message = "Вкажи назву класу.") @Size(max = 80, message = "Назва — до 80 символів.") String name) {}

    public record NewCodeRequest(String codePrefix) {}

    private final ClassService service;

    public ClassController(ClassService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ClassRepository.ClassRow create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateClassRequest req) {
        return service.create(TokenService.userId(jwt), req.name(), req.codePrefix());
    }

    @PostMapping("/join")
    public ClassRepository.ClassRow join(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody JoinRequest req) {
        return service.join(TokenService.userId(jwt), req.code());
    }

    @GetMapping
    public List<ClassRepository.ClassRow> mine(@AuthenticationPrincipal Jwt jwt) {
        return service.mine(TokenService.userId(jwt));
    }

    @GetMapping("/{id}")
    public ClassRepository.ClassRow view(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return service.view(TokenService.userId(jwt), id);
    }

    @PatchMapping("/{id}")
    public ClassRepository.ClassRow rename(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                           @Valid @RequestBody RenameRequest req) {
        return service.rename(TokenService.userId(jwt), id, req.name());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        service.delete(TokenService.userId(jwt), id);
    }

    /** Перевипустити код: старий перестає працювати. */
    @PostMapping("/{id}/code")
    public ClassRepository.ClassRow newCode(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                            @RequestBody(required = false) NewCodeRequest req) {
        return service.regenerateCode(TokenService.userId(jwt), id, req == null ? null : req.codePrefix());
    }

    @GetMapping("/{id}/members")
    public List<ClassRepository.MemberRow> members(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return service.members(TokenService.userId(jwt), id);
    }

    /** Викладач виключає учня; учень може передати свій id, щоб вийти з класу. */
    @DeleteMapping("/{id}/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMember(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @PathVariable UUID userId) {
        service.removeMember(TokenService.userId(jwt), id, userId);
    }
}
