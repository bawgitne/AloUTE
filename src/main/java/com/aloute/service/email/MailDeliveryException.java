package com.aloute.service.email;

/**
 * Thrown when an external SMTP email delivery attempt fails.
 * Ensures SMTP transport failures are observable to callers and operational logging.
 */
public class MailDeliveryException extends RuntimeException {

    public MailDeliveryException(String message) {
        super(message);
    }

    public MailDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
