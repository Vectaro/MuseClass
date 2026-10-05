package ua.museclass.auth;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * museclass.jwt.secret — щонайменше 32 байти (HS256). Береться з JWT_SECRET.
 * Без нього сервер не стартує: краще впасти одразу, ніж видавати токени,
 * підписані порожнім ключем.
 */
@Validated
@ConfigurationProperties("museclass.jwt")
public record JwtProperties(@NotBlank(message = "Задай JWT_SECRET (32+ символи)") String secret, Duration ttl) {

    public JwtProperties {
        if (secret != null && !secret.isBlank() && secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("JWT_SECRET закороткий: потрібно щонайменше 32 байти.");
        }
        if (ttl == null) ttl = Duration.ofDays(30);
    }
}
