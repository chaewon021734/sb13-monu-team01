package com.project.monu.domain.interest.exception;

import com.project.monu.domain.interest.controller.InterestController;
import com.project.monu.global.exception.BusinessException;
import com.project.monu.global.exception.ErrorCode;
import com.project.monu.global.exception.ErrorResponse;
import java.time.Instant;
import java.util.Collections;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 관심사(Interest) 도메인 전용 예외 처리기입니다.
 *
 * InterestController에서 발생한 예외만 이 클래스가 처리하며,
 * 다른 도메인의 예외는 기존과 동일하게 GlobalHandlerException(공통)이 처리합니다.
 * (BusinessException, Bean Validation 같은 진짜 공통 예외는 global/exception에 그대로 둡니다.)
 */
@RestControllerAdvice(assignableTypes = InterestController.class)
public class InterestExceptionHandler {

    // 관심사 등록/수정/삭제/구독 등에서 발생하는 도메인 예외
    // (INTEREST_NOT_FOUND, INTEREST_ALREADY_EXISTS, INVALID_INTEREST_CURSOR,
    //  SUBSCRIPTION_NOT_FOUND, SUBSCRIPTION_ALREADY_EXISTS 등)
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

    // 낙관적 락(@Version) 충돌 - 관심사 구독자 수 동시 갱신 시 발생
    // InterestService(subscribe/unsubscribe)에서 놓친 경우를 대비한 도메인 안전망
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLockingFailureException(
        ObjectOptimisticLockingFailureException exception
    ) {
        ErrorCode errorCode = ErrorCode.INTEREST_CONCURRENT_UPDATE;

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
}
