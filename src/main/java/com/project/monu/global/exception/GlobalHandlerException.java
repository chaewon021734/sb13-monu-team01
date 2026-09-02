package com.project.monu.global.exception;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalHandlerException {

  // User Email 중복 확인
  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<ErrorResponse> handleBusinessException(
      BusinessException exception
  ) {
    ErrorCode errorCode = exception.getErrorCode();

    ErrorResponse errorResponse = new ErrorResponse(
        Instant.now(),
        errorCode.getCode(),
        errorCode.getMessage(),
        Collections.emptyMap(),
        exception.getClass().getSimpleName(),
        errorCode.getStatus().value()
    );

    return ResponseEntity
        .status(errorCode.getStatus())
        .body(errorResponse);
  }

  // Bean Validation 검증 실패
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> handleValidationException(
      MethodArgumentNotValidException exception
  ) {
    ErrorCode errorCode = ErrorCode.INVALID_INPUT_VALUE;

    Map<String, Object> details = exception.getBindingResult()
        .getFieldErrors()
        .stream()
        .collect(Collectors.toMap(
            FieldError::getField,
            FieldError::getDefaultMessage,
            (existing, replacement) -> existing
        ));

    ErrorResponse errorResponse = new ErrorResponse(
        Instant.now(),
        errorCode.getCode(),
        errorCode.getMessage(),
        details,
        "DomainException",
        errorCode.getStatus().value()
    );

    return ResponseEntity
        .status(errorCode.getStatus())
        .body(errorResponse);
  }

}