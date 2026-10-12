package com.aloute.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class ApiException extends AppException {

    public ApiException(HttpStatus status, String code, String message) {
        super(status, code, message);
    }

    public ApiException(ErrorCode errorCode) {
        super(errorCode);
    }

    public ApiException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
