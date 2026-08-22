package io.github.chalsense.server.http;

import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public final class ApiExceptionHandler {
    @ExceptionHandler({ IllegalArgumentException.class, HttpMessageNotReadableException.class,
            MissingRequestHeaderException.class })
    ResponseEntity<ApiError> invalidRequest(Exception exception) {
        return ApiResponses.error(400, "INVALID_REQUEST");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ApiError> unsupportedMedia(HttpMediaTypeNotSupportedException exception) {
        return ApiResponses.error(415, "INVALID_REQUEST");
    }
}
