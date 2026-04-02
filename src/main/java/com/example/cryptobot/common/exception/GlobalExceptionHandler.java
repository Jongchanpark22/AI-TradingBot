package com.example.cryptobot.common.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException e) {
        ErrorResponse response = ErrorResponse.builder()
                .errorCode(e.getErrorCode())
                .errorMessage(e.getErrorMessage())
                .status(HttpStatus.BAD_REQUEST.value())
                .build();
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneralException(Exception e) {
        ErrorResponse response = ErrorResponse.builder()
                .errorCode("INTERNAL_ERROR")
                .errorMessage(e.getMessage())
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .build();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    @Getter
    @AllArgsConstructor
    public static class ErrorResponse {
        private String errorCode;
        private String errorMessage;
        private int status;

        public static ErrorResponseBuilder builder() {
            return new ErrorResponseBuilder();
        }

        public static class ErrorResponseBuilder {
            private String errorCode;
            private String errorMessage;
            private int status;

            public ErrorResponseBuilder errorCode(String errorCode) {
                this.errorCode = errorCode;
                return this;
            }

            public ErrorResponseBuilder errorMessage(String errorMessage) {
                this.errorMessage = errorMessage;
                return this;
            }

            public ErrorResponseBuilder status(int status) {
                this.status = status;
                return this;
            }

            public ErrorResponse build() {
                return new ErrorResponse(errorCode, errorMessage, status);
            }
        }
    }

}

