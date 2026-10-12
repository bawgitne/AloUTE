package com.aloute.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class AppException extends RuntimeException {
    private final ErrorCode errorCode;
    private final HttpStatus status;
    private final String code;

    public AppException(ErrorCode errorCode) {
        super(errorCode != null ? errorCode.getDefaultMessage() : "Application error");
        this.errorCode = errorCode;
        this.status = errorCode != null ? errorCode.getStatus() : HttpStatus.INTERNAL_SERVER_ERROR;
        this.code = errorCode != null ? errorCode.getCode() : "INTERNAL_ERROR";
    }

    public AppException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.status = errorCode != null ? errorCode.getStatus() : HttpStatus.INTERNAL_SERVER_ERROR;
        this.code = errorCode != null ? errorCode.getCode() : "INTERNAL_ERROR";
    }

    public AppException(HttpStatus status, String code, String message) {
        super(message);
        this.errorCode = null;
        this.status = status != null ? status : HttpStatus.INTERNAL_SERVER_ERROR;
        this.code = code != null ? code : "INTERNAL_ERROR";
    }

    public AppException(HttpStatus status, String code, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = null;
        this.status = status != null ? status : HttpStatus.INTERNAL_SERVER_ERROR;
        this.code = code != null ? code : "INTERNAL_ERROR";
    }
}
