package ua.museclass.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import ua.museclass.musicxml.MusicXmlException;

/**
 * Усі помилки — у форматі RFC 9457 (application/problem+json).
 * Клієнт показує поле detail як є, тому воно людською мовою.
 */
@RestControllerAdvice
public class ErrorHandler {

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

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail invalid(MethodArgumentNotValidException e) {
        FieldError fe = e.getBindingResult().getFieldError();
        String detail = fe != null && fe.getDefaultMessage() != null
                ? fe.getDefaultMessage()
                : "Некоректні дані запиту.";
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ProblemDetail tooBig(MaxUploadSizeExceededException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(413),
                "Файл завеликий. Максимум — 5 МБ.");
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    ProblemDetail noFile(MissingServletRequestPartException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Не передано файл партитури.");
    }
}
