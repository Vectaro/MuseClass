package ua.museclass.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 401 і 403 від Spring Security у тому самому форматі problem+json, що й решта
 * помилок. Заголовок WWW-Authenticate ставлять стандартні обробники, ми лише
 * дописуємо тіло з detail українською.
 */
public class SecurityProblems implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final BearerTokenAuthenticationEntryPoint entryPoint = new BearerTokenAuthenticationEntryPoint();
    private final BearerTokenAccessDeniedHandler deniedHandler = new BearerTokenAccessDeniedHandler();

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException e)
            throws IOException {
        entryPoint.commence(request, response, e);
        write(request, response, HttpStatus.UNAUTHORIZED, unauthorizedDetail(e));
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException e)
            throws IOException {
        deniedHandler.handle(request, response, e);
        write(request, response, HttpStatus.FORBIDDEN, "Немає доступу.");
    }

    static String unauthorizedDetail(AuthenticationException e) {
        if (e instanceof InvalidBearerTokenException) {
            String m = String.valueOf(e.getMessage());
            return m.contains("expired")
                    ? "Сесія закінчилась. Увійди знову."
                    : "Недійсний токен. Увійди знову.";
        }
        return "Потрібно увійти.";
    }

    private static void write(HttpServletRequest request, HttpServletResponse response, HttpStatus status,
                              String detail) throws IOException {
        response.setStatus(status.value());
        response.setContentType("application/problem+json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"title\":\"" + json(status.getReasonPhrase())
                + "\",\"status\":" + status.value()
                + ",\"detail\":\"" + json(detail)
                + "\",\"instance\":\"" + json(request.getRequestURI()) + "\"}");
    }

    static String json(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.toString();
    }
}
