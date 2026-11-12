package com.dbclasshw.document;

import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.transaction.TransactionException;

@RestControllerAdvice(assignableTypes = DocumentController.class)
public class DocumentExceptionHandler {
    public record ApiError(String code, String message) { }

    @ExceptionHandler(DocumentNotFoundException.class)
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiError notFound() {
        return new ApiError("DOCUMENT_NOT_FOUND", "Document not found");
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class})
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError invalidRequest() {
        return new ApiError("INVALID_REQUEST", "Title must be non-blank and at most 255 characters; only title and content are accepted");
    }

    @ExceptionHandler({DataAccessException.class, TransactionException.class})
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ApiError databaseUnavailable() {
        return new ApiError("DATABASE_UNAVAILABLE", "Document storage is temporarily unavailable");
    }
}
