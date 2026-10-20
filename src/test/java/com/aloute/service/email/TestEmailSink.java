package com.aloute.service.email;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@Primary
public class TestEmailSink implements EmailService {

    public record SentEmail(String toEmail, String resetUrl) {}
    public record SentOtp(String toEmail, String otpCode) {}

    private final List<SentEmail> emailSink = Collections.synchronizedList(new ArrayList<>());
    private final List<SentOtp> otpSink = Collections.synchronizedList(new ArrayList<>());
    private final AtomicInteger invocationCount = new AtomicInteger(0);
    private volatile CountDownLatch deliveryHoldLatch = null;
    private volatile CountDownLatch deliveryStartedLatch = null;
    private volatile CountDownLatch deliveryCompletedLatch = null;
    private volatile RuntimeException deliveryFailureException = null;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private DefaultEmailService defaultEmailService;

    @Override
    public void sendPasswordResetEmail(String toEmail, String resetUrl) {
        invocationCount.incrementAndGet();

        CountDownLatch started = this.deliveryStartedLatch;
        CountDownLatch hold = this.deliveryHoldLatch;
        CountDownLatch completed = this.deliveryCompletedLatch;
        RuntimeException failure = this.deliveryFailureException;

        try {
            if (started != null) {
                started.countDown();
            }
            if (failure != null) {
                throw failure;
            }
            if (hold != null) {
                try {
                    hold.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            emailSink.add(new SentEmail(toEmail, resetUrl));
            if (defaultEmailService != null) {
                try {
                    defaultEmailService.sendPasswordResetEmail(toEmail, resetUrl);
                } catch (Exception ignored) {}
            }
        } finally {
            if (completed != null) {
                completed.countDown();
            }
        }
    }

    @Override
    public void sendRegistrationOtpEmail(String toEmail, String otpCode) {
        invocationCount.incrementAndGet();

        CountDownLatch started = this.deliveryStartedLatch;
        CountDownLatch hold = this.deliveryHoldLatch;
        CountDownLatch completed = this.deliveryCompletedLatch;
        RuntimeException failure = this.deliveryFailureException;

        try {
            if (started != null) {
                started.countDown();
            }
            if (failure != null) {
                throw failure;
            }
            if (hold != null) {
                try {
                    hold.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            otpSink.add(new SentOtp(toEmail, otpCode));
            if (defaultEmailService != null) {
                try {
                    defaultEmailService.sendRegistrationOtpEmail(toEmail, otpCode);
                } catch (Exception ignored) {}
            }
        } finally {
            if (completed != null) {
                completed.countDown();
            }
        }
    }

    public List<SentEmail> getEmailSink() {
        return List.copyOf(emailSink);
    }

    public List<SentOtp> getOtpSink() {
        return List.copyOf(otpSink);
    }

    public void clearSink() {
        emailSink.clear();
        otpSink.clear();
        deliveryHoldLatch = null;
        deliveryStartedLatch = null;
        deliveryCompletedLatch = null;
        deliveryFailureException = null;
        invocationCount.set(0);
    }

    public void setDeliveryHoldLatch(CountDownLatch holdLatch) {
        this.deliveryHoldLatch = holdLatch;
    }

    public void setDeliveryStartedLatch(CountDownLatch startedLatch) {
        this.deliveryStartedLatch = startedLatch;
    }

    public void setDeliveryCompletedLatch(CountDownLatch completedLatch) {
        this.deliveryCompletedLatch = completedLatch;
    }

    public void setDeliveryFailureException(RuntimeException ex) {
        this.deliveryFailureException = ex;
    }

    public int getInvocationCount() {
        return invocationCount.get();
    }

    public boolean awaitEmailCount(int expectedCount, long timeout, TimeUnit unit) {
        long deadline = System.currentTimeMillis() + unit.toMillis(timeout);
        while (emailSink.size() < expectedCount && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return emailSink.size() >= expectedCount;
    }

    public boolean awaitOtpCount(int expectedCount, long timeout, TimeUnit unit) {
        long deadline = System.currentTimeMillis() + unit.toMillis(timeout);
        while (otpSink.size() < expectedCount && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return otpSink.size() >= expectedCount;
    }
}
