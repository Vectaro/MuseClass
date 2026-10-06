package ua.museclass.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import ua.museclass.musicxml.MusicXmlException;

import java.util.UUID;

/**
 * Усі помилки — у форматі RFC 9457 (application/problem+json) з detail
 * українською: клієнт показує його як є.
 *
 * Стандартні помилки Spring MVC (зламаний JSON, не той метод, не той
 * Content-Type, неіснуючий шлях...) проходять через handleExceptionInternal,
 * де їм підставляється український detail; заголовки на кшталт Allow лишаються.
 * 401/403 від Spring Security — у {@link SecurityProblems}.
 */
@RestControllerAdvice
public class ErrorHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ErrorHandler.class);

    @ExceptionHandler(ApiException.class)
    ProblemDetail api(ApiException e) {
        return ProblemDetail.forStatusAndDetail(e.status(), e.getMessage());
    }

    @ExceptionHandler(MusicXmlException.class)
    ProblemDetail musicXml(MusicXmlException e) {
        if (e.getCause() != null) log.info("MusicXML rejected: {}", e.getCause().toString());
        return ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(422), e.getMessage());
    }

    /** Зламаний multipart (обрізане тіло, кривий boundary). Завеликий файл — окремо, 413. */
    @ExceptionHandler(MultipartException.class)
    ProblemDetail multipart(MultipartException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Не вдалося прочитати завантажений файл.");
    }

    /** Усе непередбачене: 500 без подробиць назовні, подробиці — в лог. */
    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception e) throws Exception {
        // їх обробляє Spring Security, не ми
        if (e instanceof AccessDeniedException || e instanceof AuthenticationException) throw e;
        log.error("Unhandled exception", e);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "Помилка на сервері. Спробуй пізніше.");
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
                                                             HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = body instanceof ProblemDetail p ? p : ProblemDetail.forStatus(status);
        problem.setDetail(detail(ex, status));
        return super.handleExceptionInternal(ex, problem, headers, status, request);
    }

    static String detail(Exception ex, HttpStatusCode status) {
        return switch (ex) {
            case MethodArgumentNotValidException e -> {
                FieldError fe = e.getBindingResult().getFieldError();
                yield fe != null && fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "Некоректні дані запиту.";
            }
            case MaxUploadSizeExceededException e -> "Файл завеликий. Максимум — 5 МБ.";
            case MissingServletRequestPartException e -> "file".equals(e.getRequestPartName())
                    ? "Не передано файл партитури."
                    : "Не передано частину запиту «" + e.getRequestPartName() + "».";
            case MissingServletRequestParameterException e -> "Не передано параметр «" + e.getParameterName() + "».";
            case TypeMismatchException e -> e.getRequiredType() == UUID.class
                    ? "Некоректний ідентифікатор: " + e.getValue()
                    : "Параметр «" + e.getPropertyName() + "» має неправильний формат: " + e.getValue();
            case HttpMessageNotReadableException e -> "Тіло запиту не читається: потрібен коректний JSON.";
            case HttpRequestMethodNotSupportedException e -> "Метод " + e.getMethod() + " тут не підтримується.";
            case HttpMediaTypeNotSupportedException e -> "Непідтримуваний тип тіла запиту (Content-Type).";
            case HttpMediaTypeNotAcceptableException e -> "Сервер не може віддати відповідь у запитаному форматі.";
            case NoResourceFoundException e -> "Такого шляху в API немає.";
            case NoHandlerFoundException e -> "Такого шляху в API немає.";
            default -> status.is5xxServerError() ? "Помилка на сервері. Спробуй пізніше." : "Некоректний запит.";
        };
    }
}
