package com.aloute.common.exception;

import org.springframework.http.HttpStatus;

public class ConflictException extends AppException {

    public ConflictException() {
        super(ErrorCode.CONFLICT);
    }

    public ConflictException(ErrorCode errorCode) {
        super(errorCode);
    }

    public ConflictException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public ConflictException(String message) {
        super(HttpStatus.CONFLICT, ErrorCode.CONFLICT.getCode(), message);
    }
}
