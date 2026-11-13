package com.dbclasshw.document;

import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.transaction.TransactionException;

@RestControllerAdvice
public class DocumentExceptionHandler {
    public record ApiError(String code, String message) { }

    @ExceptionHandler(DocumentNotFoundException.class)
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiError notFound() {
        return new ApiError("DOCUMENT_NOT_FOUND", "Document not found");
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class, IllegalArgumentException.class})
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError invalidRequest() {
        return new ApiError("INVALID_REQUEST", "Invalid input or unsupported fields; check field formats and lengths");
    }

    @ExceptionHandler({DataAccessException.class, TransactionException.class})
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ApiError databaseUnavailable() {
        return new ApiError("DATABASE_UNAVAILABLE", "Document storage is temporarily unavailable");
    }

    @ExceptionHandler(org.springframework.dao.DuplicateKeyException.class)
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.CONFLICT)
    public ApiError conflict() { return new ApiError("ACCOUNT_CONFLICT", "Username or email already in use"); }

    @ExceptionHandler(org.springframework.security.core.AuthenticationException.class)
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ApiError authenticationFailed() {
        return new ApiError("INVALID_CREDENTIALS", "Invalid username/email or password");
    }
}
