package com.solvergto.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Map;

@RestControllerAdvice
public class GlobalApiExceptionHandler {
    @ExceptionHandler({
            IllegalArgumentException.class,
            SQLException.class,
            IOException.class,
            HttpMessageNotReadableException.class
    })
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleKnownExceptions(Exception exception) {
        return Map.of("error", exception.getMessage());
    }
}
