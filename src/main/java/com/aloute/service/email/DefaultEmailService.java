package com.aloute.service.email;

import jakarta.annotation.PostConstruct;
import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Properties;

@Service
@Slf4j
public class DefaultEmailService implements EmailService {

    private final Environment environment;

    @Value("${aloute.mail.host:}")
    private String smtpHost;

    @Value("${aloute.mail.port:587}")
    private int smtpPort;

    @Value("${aloute.mail.username:}")
    private String smtpUsername;

    @Value("${aloute.mail.password:}")
    private String smtpPassword;

    @Value("${aloute.mail.from:noreply@aloute.edu.vn}")
    private String smtpFrom;

    @Value("${aloute.mail.starttls.enable:true}")
    private boolean starttlsEnable;

    @Value("${aloute.mail.ssl.checkserveridentity:true}")
    private boolean sslCheckServerIdentity;

    @Autowired
    public DefaultEmailService(Environment environment) {
        this.environment = Objects.requireNonNull(environment, "Environment must not be null");
    }

    private boolean isProduction() {
        return environment.acceptsProfiles(Profiles.of("prod", "production"));
    }

    @PostConstruct
    public void validateConfiguration() {
        if (isProduction()) {
            if (smtpHost == null || smtpHost.isBlank()) {
                throw new IllegalStateException("Production startup failed: SMTP host (aloute.mail.host) is required and cannot be blank in production profile");
            }
            if (smtpUsername == null || smtpUsername.isBlank() || smtpPassword == null || smtpPassword.isBlank()) {
                throw new IllegalStateException("Production startup failed: SMTP credentials (aloute.mail.username, aloute.mail.password) must be configured in production profile");
            }
            if (smtpPort != 465 && (!starttlsEnable || !sslCheckServerIdentity)) {
                throw new IllegalStateException("Production startup failed: Unsafe SMTP configuration rejected: STARTTLS and SSL server identity verification must be enabled for authenticated transport");
            }
            if (smtpPort == 465 && !sslCheckServerIdentity) {
                throw new IllegalStateException("Production startup failed: Unsafe SMTP configuration rejected: SSL server identity verification must be enabled for authenticated transport");
            }
        } else if (smtpHost != null && !smtpHost.isBlank()) {
            if (smtpPort != 465 && (!starttlsEnable || !sslCheckServerIdentity)) {
                throw new IllegalStateException("Unsafe SMTP configuration rejected: STARTTLS and SSL server identity verification must be enabled for authenticated transport");
            }
            if (smtpUsername == null || smtpUsername.isBlank() || smtpPassword == null || smtpPassword.isBlank()) {
                throw new IllegalStateException("Invalid SMTP configuration: username and password must be configured when SMTP host is specified");
            }
        }
    }

    @Override
    public void sendPasswordResetEmail(String toEmail, String resetUrl) {
        log.info("Password reset email dispatch requested for recipient {}", sanitizeForLog(toEmail));

        if (smtpHost != null && !smtpHost.isBlank()) {
            sendViaSmtp(toEmail, resetUrl);
        } else if (isProduction()) {
            log.error("Email delivery attempted in production without configured SMTP host for recipient {}", sanitizeForLog(toEmail));
            throw new IllegalStateException("Email delivery simulation is prohibited in production profile: aloute.mail.host must be configured");
        } else {
            log.info("SMTP host not configured; email delivery simulated for recipient {}", sanitizeForLog(toEmail));
        }
    }

    @Override
    public void sendRegistrationOtpEmail(String toEmail, String otpCode) {
        log.info("Registration OTP email dispatch requested for recipient {}", sanitizeForLog(toEmail));

        if (smtpHost != null && !smtpHost.isBlank()) {
            sendOtpViaSmtp(toEmail, otpCode);
        } else if (isProduction()) {
            log.error("Email delivery attempted in production without configured SMTP host for recipient {}", sanitizeForLog(toEmail));
            throw new IllegalStateException("Email delivery simulation is prohibited in production profile: aloute.mail.host must be configured");
        } else {
            log.info("SMTP host not configured; email delivery simulated for recipient {}", sanitizeForLog(toEmail));
        }
    }

    @Override
    public void sendTwoFactorFallbackOtpEmail(String toEmail, String otpCode) {
        log.info("2FA Fallback OTP email dispatch requested for recipient {}", sanitizeForLog(toEmail));

        if (smtpHost != null && !smtpHost.isBlank()) {
            sendTwoFactorOtpViaSmtp(toEmail, otpCode);
        } else if (isProduction()) {
            log.error("Email delivery attempted in production without configured SMTP host for recipient {}", sanitizeForLog(toEmail));
            throw new IllegalStateException("Email delivery simulation is prohibited in production profile: aloute.mail.host must be configured");
        } else {
            log.info("SMTP host not configured; 2FA fallback email delivery simulated for recipient {}", sanitizeForLog(toEmail));
        }
    }

    private void sendTwoFactorOtpViaSmtp(String toEmail, String otpCode) {
        if (!starttlsEnable || !sslCheckServerIdentity) {
            log.error("Unsafe SMTP configuration rejected");
            throw new IllegalStateException("Unsafe SMTP configuration rejected: STARTTLS and SSL server identity verification must be enabled for authenticated transport");
        }
        if (smtpUsername == null || smtpUsername.isBlank() || smtpPassword == null || smtpPassword.isBlank()) {
            log.error("Missing SMTP credentials for configured host {}", smtpHost);
            throw new IllegalStateException("Missing SMTP credentials: username and password are required for authenticated transport");
        }
        try {
            Properties props = new Properties();
            props.put("mail.transport.protocol", "smtp");
            props.put("mail.smtp.host", smtpHost);
            props.put("mail.smtp.port", String.valueOf(smtpPort));
            props.put("mail.smtp.user", smtpUsername);
            props.put("mail.smtp.connectiontimeout", "10000");
            props.put("mail.smtp.timeout", "10000");
            props.put("mail.smtp.writetimeout", "10000");

            props.put("mail.smtp.auth", "true");
            if (smtpPort == 465) {
                props.put("mail.smtp.ssl.enable", "true");
                props.put("mail.smtp.socketFactory.port", "465");
                props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
                props.put("mail.smtp.socketFactory.fallback", "false");
                props.put("mail.smtp.starttls.enable", "false");
            } else {
                props.put("mail.smtp.starttls.enable", "true");
                props.put("mail.smtp.starttls.required", "true");
                props.put("mail.smtp.ssl.enable", "false");
                props.put("mail.smtp.ssl.protocols", "TLSv1.2");
                props.put("mail.smtp.ssl.trust", "*");
                props.put("mail.smtp.ssl.checkserveridentity", "true");
            }

            Session session = Session.getInstance(props, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(smtpUsername, smtpPassword);
                }
            });

            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(smtpFrom, "AloUTE Campus", StandardCharsets.UTF_8.name()));
            message.setRecipient(Message.RecipientType.TO, new InternetAddress(toEmail));
            message.setSubject("[AloUTE] Mã xác thực bảo mật 2 lớp (2FA)", StandardCharsets.UTF_8.name());

            MimeMultipart multipart = new MimeMultipart("alternative");

            MimeBodyPart textPart = new MimeBodyPart();
            textPart.setText("Mã xác thực bảo mật 2 lớp của bạn trên AloUTE là: " + otpCode + "\n\nMã này có hiệu lực trong 5 phút. Tuyệt đối không chia sẻ mã này với bất kỳ ai.", StandardCharsets.UTF_8.name());

            MimeBodyPart htmlPart = new MimeBodyPart();
            htmlPart.setContent(buildTwoFactorOtpHtml(otpCode), "text/html; charset=UTF-8");

            multipart.addBodyPart(textPart);
            multipart.addBodyPart(htmlPart);

            message.setContent(multipart);
            Transport.send(message, smtpUsername, smtpPassword);
            log.info("2FA OTP notification dispatched via SMTP host {}:{}", smtpHost, smtpPort);
        } catch (Exception ex) {
            log.error("Failed to deliver external email notification via SMTP host {}: {} - {}", smtpHost, ex.getClass().getSimpleName(), ex.getMessage(), ex);
            throw new MailDeliveryException("Failed to deliver external email notification via SMTP host " + smtpHost + ": " + ex.getClass().getSimpleName(), ex);
        }
    }

    private void sendViaSmtp(String toEmail, String resetUrl) {
        if (!starttlsEnable || !sslCheckServerIdentity) {
            log.error("Unsafe SMTP configuration rejected");
            throw new IllegalStateException("Unsafe SMTP configuration rejected: STARTTLS and SSL server identity verification must be enabled for authenticated transport");
        }
        if (smtpUsername == null || smtpUsername.isBlank() || smtpPassword == null || smtpPassword.isBlank()) {
            log.error("Missing SMTP credentials for configured host {}", smtpHost);
            throw new IllegalStateException("Missing SMTP credentials: username and password are required for authenticated transport");
        }
        try {
            Properties props = new Properties();
            props.put("mail.transport.protocol", "smtp");
            props.put("mail.smtp.host", smtpHost);
            props.put("mail.smtp.port", String.valueOf(smtpPort));
            props.put("mail.smtp.user", smtpUsername);
            props.put("mail.smtp.connectiontimeout", "10000");
            props.put("mail.smtp.timeout", "10000");
            props.put("mail.smtp.writetimeout", "10000");

            props.put("mail.smtp.auth", "true");
            if (smtpPort == 465) {
                props.put("mail.smtp.ssl.enable", "true");
                props.put("mail.smtp.socketFactory.port", "465");
                props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
                props.put("mail.smtp.socketFactory.fallback", "false");
                props.put("mail.smtp.starttls.enable", "false");
            } else {
                props.put("mail.smtp.starttls.enable", "true");
                props.put("mail.smtp.starttls.required", "true");
                props.put("mail.smtp.ssl.enable", "false");
                props.put("mail.smtp.ssl.protocols", "TLSv1.2");
                props.put("mail.smtp.ssl.trust", "*");
                props.put("mail.smtp.ssl.checkserveridentity", "true");
            }

            Session session = Session.getInstance(props, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(smtpUsername, smtpPassword);
                }
            });

            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(smtpFrom, "AloUTE Campus", StandardCharsets.UTF_8.name()));
            message.setRecipient(Message.RecipientType.TO, new InternetAddress(toEmail));
            message.setSubject("[AloUTE] Yêu cầu đặt lại mật khẩu", StandardCharsets.UTF_8.name());

            MimeMultipart multipart = new MimeMultipart("alternative");

            MimeBodyPart textPart = new MimeBodyPart();
            textPart.setText("Truy cập liên kết sau để đặt lại mật khẩu tài khoản AloUTE của bạn:\n" + resetUrl + "\n\nLiên kết có hiệu lực trong 15 phút.", StandardCharsets.UTF_8.name());

            MimeBodyPart htmlPart = new MimeBodyPart();
            htmlPart.setContent(buildPasswordResetHtml(resetUrl), "text/html; charset=UTF-8");

            multipart.addBodyPart(textPart);
            multipart.addBodyPart(htmlPart);

            message.setContent(multipart);
            Transport.send(message, smtpUsername, smtpPassword);
            log.info("Reset notification dispatched via SMTP host {}:{}", smtpHost, smtpPort);
        } catch (Exception ex) {
            log.error("Failed to deliver external email notification via SMTP host {}: {} - {}", smtpHost, ex.getClass().getSimpleName(), ex.getMessage(), ex);
            throw new MailDeliveryException("Failed to deliver external email notification via SMTP host " + smtpHost + ": " + ex.getClass().getSimpleName(), ex);
        }
    }

    private void sendOtpViaSmtp(String toEmail, String otpCode) {
        if (!starttlsEnable || !sslCheckServerIdentity) {
            log.error("Unsafe SMTP configuration rejected");
            throw new IllegalStateException("Unsafe SMTP configuration rejected: STARTTLS and SSL server identity verification must be enabled for authenticated transport");
        }
        if (smtpUsername == null || smtpUsername.isBlank() || smtpPassword == null || smtpPassword.isBlank()) {
            log.error("Missing SMTP credentials for configured host {}", smtpHost);
            throw new IllegalStateException("Missing SMTP credentials: username and password are required for authenticated transport");
        }
        try {
            Properties props = new Properties();
            props.put("mail.transport.protocol", "smtp");
            props.put("mail.smtp.host", smtpHost);
            props.put("mail.smtp.port", String.valueOf(smtpPort));
            props.put("mail.smtp.user", smtpUsername);
            props.put("mail.smtp.connectiontimeout", "10000");
            props.put("mail.smtp.timeout", "10000");
            props.put("mail.smtp.writetimeout", "10000");

            props.put("mail.smtp.auth", "true");
            if (smtpPort == 465) {
                props.put("mail.smtp.ssl.enable", "true");
                props.put("mail.smtp.socketFactory.port", "465");
                props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
                props.put("mail.smtp.socketFactory.fallback", "false");
                props.put("mail.smtp.starttls.enable", "false");
            } else {
                props.put("mail.smtp.starttls.enable", "true");
                props.put("mail.smtp.starttls.required", "true");
                props.put("mail.smtp.ssl.enable", "false");
                props.put("mail.smtp.ssl.protocols", "TLSv1.2");
                props.put("mail.smtp.ssl.trust", "*");
                props.put("mail.smtp.ssl.checkserveridentity", "true");
            }

            Session session = Session.getInstance(props, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(smtpUsername, smtpPassword);
                }
            });

            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(smtpFrom, "AloUTE Campus", StandardCharsets.UTF_8.name()));
            message.setRecipient(Message.RecipientType.TO, new InternetAddress(toEmail));
            message.setSubject("[AloUTE] Mã xác thực đăng ký tài khoản", StandardCharsets.UTF_8.name());

            MimeMultipart multipart = new MimeMultipart("alternative");

            MimeBodyPart textPart = new MimeBodyPart();
            textPart.setText("Mã xác thực đăng ký tài khoản AloUTE của bạn là: " + otpCode + "\n\nMã này có hiệu lực trong 5 phút.", StandardCharsets.UTF_8.name());

            MimeBodyPart htmlPart = new MimeBodyPart();
            htmlPart.setContent(buildRegistrationOtpHtml(otpCode), "text/html; charset=UTF-8");

            multipart.addBodyPart(textPart);
            multipart.addBodyPart(htmlPart);

            message.setContent(multipart);
            Transport.send(message, smtpUsername, smtpPassword);
            log.info("Registration OTP notification dispatched via SMTP host {}:{}", smtpHost, smtpPort);
        } catch (Exception ex) {
            log.error("Failed to deliver external email notification via SMTP host {}: {} - {}", smtpHost, ex.getClass().getSimpleName(), ex.getMessage(), ex);
            throw new MailDeliveryException("Failed to deliver external email notification via SMTP host " + smtpHost + ": " + ex.getClass().getSimpleName(), ex);
        }
    }

    private String renderOtpDigitBoxes(String otpCode) {
        if (otpCode == null || otpCode.isBlank()) {
            return "<div style=\"font-family: ui-monospace, 'SF Mono', Consolas, monospace; font-size: 32px; font-weight: 800; letter-spacing: 12px; color: #1d4ed8;\">------</div>";
        }
        String clean = otpCode.trim();
        if (clean.length() != 6) {
            return "<div style=\"font-family: ui-monospace, 'SF Mono', Consolas, monospace; font-size: 34px; font-weight: 800; letter-spacing: 12px; color: #1d4ed8;\">" + clean + "</div>";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("<table role=\"presentation\" cellspacing=\"0\" cellpadding=\"0\" style=\"margin: 0 auto; border-collapse: separate;\"><tr>");
        for (int i = 0; i < 6; i++) {
            char digit = clean.charAt(i);
            sb.append("<td style=\"padding: 0 4px;\">")
              .append("<div style=\"width: 48px; height: 58px; line-height: 58px; text-align: center; background-color: #ffffff; border: 2px solid #3b82f6; border-radius: 12px; font-size: 32px; font-weight: 800; color: #1e40af; font-family: ui-monospace, 'SF Mono', Monaco, Consolas, monospace; box-shadow: 0 4px 10px -2px rgba(37, 99, 235, 0.18);\">")
              .append(digit)
              .append("</div></td>");
        }
        sb.append("</tr></table>");
        return sb.toString();
    }

    private String buildTwoFactorOtpHtml(String otpCode) {
        String digitBoxesHtml = renderOtpDigitBoxes(otpCode);
        return """
            <!DOCTYPE html>
            <html lang="vi">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Mã xác thực bảo mật 2 lớp AloUTE</title>
            </head>
            <body style="margin: 0; padding: 0; background-color: #f1f5f9; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; -webkit-font-smoothing: antialiased;">
                <table role="presentation" width="100%" cellspacing="0" cellpadding="0" style="background-color: #f1f5f9; padding: 36px 16px;">
                    <tr>
                        <td align="center">
                            <table role="presentation" width="100%" cellspacing="0" cellpadding="0" style="max-width: 580px; background-color: #ffffff; border-radius: 20px; overflow: hidden; box-shadow: 0 20px 30px -10px rgba(15, 23, 42, 0.08), 0 10px 12px -6px rgba(15, 23, 42, 0.04); border: 1px solid #e2e8f0;">
                                <!-- Header Banner -->
                                <tr>
                                    <td style="background: linear-gradient(135deg, #1e3a8a 0%, #2563eb 55%, #4f46e5 100%); padding: 36px 32px 30px 32px; text-align: center;">
                                        <div style="display: inline-block; background-color: rgba(255, 255, 255, 0.18); border: 1px solid rgba(255, 255, 255, 0.3); border-radius: 9999px; padding: 6px 18px; margin-bottom: 14px;">
                                            <span style="font-size: 22px; font-weight: 800; color: #ffffff; letter-spacing: -0.5px;">Alo<span style="color: #93c5fd;">UTE</span></span>
                                            <span style="display: inline-block; background-color: #ffffff; color: #1d4ed8; font-size: 10px; font-weight: 800; padding: 2px 8px; border-radius: 12px; margin-left: 8px; vertical-align: middle; letter-spacing: 0.5px;">CAMPUS</span>
                                        </div>
                                        <h1 style="margin: 0 0 6px 0; color: #ffffff; font-size: 22px; font-weight: 800; letter-spacing: -0.3px;">Mã Xác Thực Bảo Mật 2 Lớp (2FA)</h1>
                                        <p style="margin: 0; color: #bfdbfe; font-size: 13px; font-weight: 500;">Bảo vệ tài khoản sinh viên Trường Đại học Sư phạm Kỹ thuật TP.HCM</p>
                                    </td>
                                </tr>
                                <!-- Main Body -->
                                <tr>
                                    <td style="padding: 36px 36px 28px 36px;">
                                        <p style="margin: 0 0 12px 0; font-size: 15px; font-weight: 600; color: #0f172a;">
                                            Xin chào sinh viên UTE,
                                        </p>
                                        <p style="margin: 0 0 24px 0; font-size: 14px; line-height: 1.6; color: #475569;">
                                            Hệ thống AloUTE ghi nhận yêu cầu đăng nhập hoặc thiết lập bảo mật 2 lớp trên tài khoản của bạn. Vui lòng sử dụng mã một lần (OTP) gồm 6 chữ số dưới đây để xác nhận:
                                        </p>
                                        
                                        <!-- OTP Display Box -->
                                        <table role="presentation" width="100%" cellspacing="0" cellpadding="0" style="margin: 28px 0;">
                                            <tr>
                                                <td align="center" style="background: linear-gradient(180deg, #f0f7ff 0%, #e0f2fe 100%); border: 2px dashed #60a5fa; border-radius: 16px; padding: 28px 20px;">
                                                    <div style="font-size: 11px; font-weight: 700; text-transform: uppercase; letter-spacing: 2px; color: #0369a1; margin-bottom: 14px;">
                                                        🔐 Mã xác thực của bạn (OTP)
                                                    </div>
                                                    {{OTP_DIGIT_BOXES}}
                                                    <div style="margin-top: 16px;">
                                                        <span style="display: inline-block; background-color: #dbeafe; color: #1e40af; font-size: 12px; font-weight: 700; padding: 5px 14px; border-radius: 20px; border: 1px solid #bfdbfe;">
                                                            ⏱️ Hiệu lực trong 5 phút • Dùng 1 lần duy nhất
                                                        </span>
                                                    </div>
                                                </td>
                                            </tr>
                                        </table>

                                        <!-- Quick Instructions -->
                                        <table role="presentation" width="100%" cellspacing="0" cellpadding="0" style="margin-bottom: 20px;">
                                            <tr>
                                                <td style="background-color: #f8fafc; border: 1px solid #e2e8f0; border-radius: 10px; padding: 14px 18px;">
                                                    <div style="font-size: 12px; font-weight: 700; color: #334155; text-transform: uppercase; letter-spacing: 0.5px; margin-bottom: 6px;">
                                                        📋 Các bước thực hiện:
                                                    </div>
                                                    <ol style="margin: 0; padding-left: 18px; font-size: 13px; line-height: 1.6; color: #64748b;">
                                                        <li>Nhập mã 6 chữ số ở trên vào ô xác thực của AloUTE.</li>
                                                        <li>Không chia sẻ hoặc cung cấp mã này cho bất kỳ ai.</li>
                                                        <li>Nếu bạn không thực hiện yêu cầu này, hãy đổi mật khẩu ngay lập tức.</li>
                                                    </ol>
                                                </td>
                                            </tr>
                                        </table>

                                        <!-- Security Warning -->
                                        <table role="presentation" width="100%" cellspacing="0" cellpadding="0" style="margin-bottom: 8px;">
                                            <tr>
                                                <td style="background-color: #fffbeb; border-left: 4px solid #f59e0b; border-radius: 6px; padding: 14px 18px;">
                                                    <p style="margin: 0; font-size: 13px; line-height: 1.5; color: #92400e;">
                                                        ⚠️ <strong>Cảnh báo an toàn:</strong> Cán bộ nhà trường và ban quản trị AloUTE không bao giờ yêu cầu bạn cung cấp mã OTP dưới mọi hình thức.
                                                    </p>
                                                </td>
                                            </tr>
                                        </table>
                                    </td>
                                </tr>
                                <!-- Footer -->
                                <tr>
                                    <td style="background-color: #f8fafc; border-top: 1px solid #e2e8f0; padding: 26px 36px; text-align: center;">
                                        <p style="margin: 0 0 4px 0; font-size: 13px; color: #334155; font-weight: 700;">
                                            TRƯỜNG ĐẠI HỌC SƯ PHẠM KỸ THUẬT TP. HỒ CHÍ MINH (HCMUTE)
                                        </p>
                                        <p style="margin: 0 0 8px 0; font-size: 12px; color: #64748b;">
                                            Số 1 Võ Văn Ngân, Phường Linh Chiểu, TP. Thủ Đức, TP. Hồ Chí Minh
                                        </p>
                                        <p style="margin: 0 0 10px 0; font-size: 11px; color: #94a3b8;">
                                            AloUTE — Mạng xã hội & Cổng thông tin Sinh viên UTE
                                        </p>
                                        <p style="margin: 0; font-size: 11px; color: #cbd5e1;">
                                            Thư điện tử được gửi tự động từ hệ thống AloUTE. Vui lòng không trả lời thư này.
                                        </p>
                                    </td>
                                </tr>
                            </table>
                        </td>
                    </tr>
                </table>
            </body>
            </html>
            """.replace("{{OTP_DIGIT_BOXES}}", digitBoxesHtml);
    }

    private String buildRegistrationOtpHtml(String otpCode) {
        String digitBoxesHtml = renderOtpDigitBoxes(otpCode);
        return """
            <!DOCTYPE html>
            <html lang="vi">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Mã xác thực đăng ký AloUTE</title>
            </head>
            <body style="margin: 0; padding: 0; background-color: #f1f5f9; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; -webkit-font-smoothing: antialiased;">
                <table role="presentation" width="100%" cellspacing="0" cellpadding="0" style="background-color: #f1f5f9; padding: 36px 16px;">
                    <tr>
                        <td align="center">
                            <table role="presentation" width="100%" cellspacing="0" cellpadding="0" style="max-width: 580px; background-color: #ffffff; border-radius: 20px; overflow: hidden; box-shadow: 0 20px 30px -10px rgba(15, 23, 42, 0.08), 0 10px 12px -6px rgba(15, 23, 42, 0.04); border: 1px solid #e2e8f0;">
                                <!-- Header Banner -->
                                <tr>
                                    <td style="background: linear-gradient(135deg, #1e3a8a 0%, #2563eb 55%, #4f46e5 100%); padding: 36px 32px 30px 32px; text-align: center;">
                                        <div style="display: inline-block; background-color: rgba(255, 255, 255, 0.18); border: 1px solid rgba(255, 255, 255, 0.3); border-radius: 9999px; padding: 6px 18px; margin-bottom: 14px;">
                                            <span style="font-size: 22px; font-weight: 800; color: #ffffff; letter-spacing: -0.5px;">Alo<span style="color: #93c5fd;">UTE</span></span>
                                            <span style="display: inline-block; background-color: #ffffff; color: #1d4ed8; font-size: 10px; font-weight: 800; padding: 2px 8px; border-radius: 12px; margin-left: 8px; vertical-align: middle; letter-spacing: 0.5px;">CAMPUS</span>
                                        </div>
                                        <h1 style="margin: 0 0 6px 0; color: #ffffff; font-size: 22px; font-weight: 800; letter-spacing: -0.3px;">Chào mừng bạn đến với AloUTE!</h1>
                                        <p style="margin: 0; color: #bfdbfe; font-size: 13px; font-weight: 500;">Mạng xã hội & Cộng đồng Sinh viên UTE</p>
                                    </td>
                                </tr>
                                <!-- Main Body -->
                                <tr>
                                    <td style="padding: 36px 36px 28px 36px;">
                                        <p style="margin: 0 0 12px 0; font-size: 15px; font-weight: 600; color: #0f172a;">
                                            Chào mừng tân sinh viên / bạn học,
                                        </p>
                                        <p style="margin: 0 0 24px 0; font-size: 14px; line-height: 1.6; color: #475569;">
                                            Cảm ơn bạn đã đăng ký tài khoản tại mạng xã hội sinh viên AloUTE. Dưới đây là mã xác thực dùng một lần (OTP) để kích hoạt tài khoản của bạn:
                                        </p>
                                        
                                        <!-- OTP Display Box -->
                                        <table role="presentation" width="100%" cellspacing="0" cellpadding="0" style="margin: 28px 0;">
                                            <tr>
                                                <td align="center" style="background: linear-gradient(180deg, #f0f7ff 0%, #e0f2fe 100%); border: 2px dashed #60a5fa; border-radius: 16px; padding: 28px 20px;">
                                                    <div style="font-size: 11px; font-weight: 700; text-transform: uppercase; letter-spacing: 2px; color: #0369a1; margin-bottom: 14px;">
                                                        🎓 Mã kích hoạt tài khoản
                                                    </div>
                                                    {{OTP_DIGIT_BOXES}}
                                                    <div style="margin-top: 16px;">
                                                        <span style="display: inline-block; background-color: #dbeafe; color: #1e40af; font-size: 12px; font-weight: 700; padding: 5px 14px; border-radius: 20px; border: 1px solid #bfdbfe;">
                                                            ⏱️ Hiệu lực trong 5 phút
                                                        </span>
                                                    </div>
                                                </td>
                                            </tr>
                                        </table>

                                        <table role="presentation" width="100%" cellspacing="0" cellpadding="0">
                                            <tr>
                                                <td style="background-color: #eff6ff; border-left: 4px solid #3b82f6; border-radius: 6px; padding: 12px 16px;">
                                                    <p style="margin: 0; font-size: 13px; line-height: 1.5; color: #1e40af;">
                                                        💡 <strong>Mẹo:</strong> Nhập mã này trên trang đăng ký để hoàn tất tạo tài khoản và bắt đầu kết nối với bạn bè trường HCMUTE!
                                                    </p>
                                                </td>
                                            </tr>
                                        </table>
                                    </td>
                                </tr>
                                <!-- Footer -->
                                <tr>
                                    <td style="background-color: #f8fafc; border-top: 1px solid #e2e8f0; padding: 26px 36px; text-align: center;">
                                        <p style="margin: 0 0 4px 0; font-size: 13px; color: #334155; font-weight: 700;">
                                            TRƯỜNG ĐẠI HỌC SƯ PHẠM KỸ THUẬT TP. HỒ CHÍ MINH (HCMUTE)
                                        </p>
                                        <p style="margin: 0 0 8px 0; font-size: 12px; color: #64748b;">
                                            Số 1 Võ Văn Ngân, Phường Linh Chiểu, TP. Thủ Đức, TP. Hồ Chí Minh
                                        </p>
                                        <p style="margin: 0 0 10px 0; font-size: 11px; color: #94a3b8;">
                                            AloUTE — Mạng xã hội & Cổng thông tin Sinh viên UTE
                                        </p>
                                    </td>
                                </tr>
                            </table>
                        </td>
                    </tr>
                </table>
            </body>
            </html>
            """.replace("{{OTP_DIGIT_BOXES}}", digitBoxesHtml);
    }

    private String buildPasswordResetHtml(String resetUrl) {
        return """
            <!DOCTYPE html>
            <html lang="vi">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Đặt lại mật khẩu AloUTE</title>
            </head>
            <body style="margin: 0; padding: 0; background-color: #f1f5f9; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; -webkit-font-smoothing: antialiased;">
                <table role="presentation" width="100%" cellspacing="0" cellpadding="0" style="background-color: #f1f5f9; padding: 36px 16px;">
                    <tr>
                        <td align="center">
                            <table role="presentation" width="100%" cellspacing="0" cellpadding="0" style="max-width: 580px; background-color: #ffffff; border-radius: 20px; overflow: hidden; box-shadow: 0 20px 30px -10px rgba(15, 23, 42, 0.08), 0 10px 12px -6px rgba(15, 23, 42, 0.04); border: 1px solid #e2e8f0;">
                                <!-- Header Banner -->
                                <tr>
                                    <td style="background: linear-gradient(135deg, #1e3a8a 0%, #2563eb 55%, #4f46e5 100%); padding: 36px 32px 30px 32px; text-align: center;">
                                        <div style="display: inline-block; background-color: rgba(255, 255, 255, 0.18); border: 1px solid rgba(255, 255, 255, 0.3); border-radius: 9999px; padding: 6px 18px; margin-bottom: 14px;">
                                            <span style="font-size: 22px; font-weight: 800; color: #ffffff; letter-spacing: -0.5px;">Alo<span style="color: #93c5fd;">UTE</span></span>
                                        </div>
                                        <h1 style="margin: 0 0 6px 0; color: #ffffff; font-size: 22px; font-weight: 800; letter-spacing: -0.3px;">Yêu cầu đặt lại mật khẩu</h1>
                                        <p style="margin: 0; color: #bfdbfe; font-size: 13px; font-weight: 500;">Bảo vệ an toàn tài khoản sinh viên</p>
                                    </td>
                                </tr>
                                <!-- Main Body -->
                                <tr>
                                    <td style="padding: 36px 36px 28px 36px;">
                                        <p style="margin: 0 0 12px 0; font-size: 15px; font-weight: 600; color: #0f172a;">
                                            Xin chào bạn,
                                        </p>
                                        <p style="margin: 0 0 24px 0; font-size: 14px; line-height: 1.6; color: #475569;">
                                            Hệ thống nhận được yêu cầu đặt lại mật khẩu cho tài khoản AloUTE của bạn. Vui lòng bấm vào nút bên dưới để tạo mật khẩu mới:
                                        </p>
                                        <table role="presentation" width="100%" cellspacing="0" cellpadding="0" style="margin: 28px 0;">
                                            <tr>
                                                <td align="center">
                                                    <a href="{{RESET_URL}}" style="display: inline-block; background: linear-gradient(135deg, #1d4ed8 0%, #2563eb 100%); color: #ffffff; font-size: 15px; font-weight: 700; text-decoration: none; padding: 15px 36px; border-radius: 50px; box-shadow: 0 6px 16px -2px rgba(29, 78, 216, 0.35); letter-spacing: 0.3px;">Đặt lại mật khẩu ngay</a>
                                                </td>
                                            </tr>
                                        </table>
                                        <table role="presentation" width="100%" cellspacing="0" cellpadding="0" style="margin-bottom: 20px;">
                                            <tr>
                                                <td style="background-color: #eff6ff; border-left: 4px solid #3b82f6; border-radius: 6px; padding: 12px 16px;">
                                                    <p style="margin: 0; font-size: 13px; line-height: 1.5; color: #1e40af;">
                                                        ⏱️ Liên kết đặt lại mật khẩu có hiệu lực trong <strong>15 phút</strong>.
                                                    </p>
                                                </td>
                                            </tr>
                                        </table>
                                        <p style="margin: 0; font-size: 12px; color: #64748b; line-height: 1.6;">
                                            Nếu nút bấm không hoạt động, bạn có thể sao chép liên kết sau vào trình duyệt:<br>
                                            <a href="{{RESET_URL}}" style="color: #1d4ed8; word-break: break-all;">{{RESET_URL}}</a>
                                        </p>
                                    </td>
                                </tr>
                                <!-- Footer -->
                                <tr>
                                    <td style="background-color: #f8fafc; border-top: 1px solid #e2e8f0; padding: 26px 36px; text-align: center;">
                                        <p style="margin: 0 0 4px 0; font-size: 13px; color: #334155; font-weight: 700;">
                                            TRƯỜNG ĐẠI HỌC SƯ PHẠM KỸ THUẬT TP. HỒ CHÍ MINH (HCMUTE)
                                        </p>
                                        <p style="margin: 0 0 8px 0; font-size: 12px; color: #64748b;">
                                            Số 1 Võ Văn Ngân, Phường Linh Chiểu, TP. Thủ Đức, TP. Hồ Chí Minh
                                        </p>
                                        <p style="margin: 0; font-size: 11px; color: #94a3b8;">
                                            AloUTE — Mạng xã hội & Cổng thông tin Sinh viên UTE
                                        </p>
                                    </td>
                                </tr>
                            </table>
                        </td>
                    </tr>
                </table>
            </body>
            </html>
            """.replace("{{RESET_URL}}", resetUrl);
    }

    public static String sanitizeForLog(String email) {
        if (email == null) return "null";
        int atIndex = email.indexOf('@');
        if (atIndex <= 1) return "***" + (atIndex >= 0 ? email.substring(atIndex) : "");
        return email.charAt(0) + "***" + email.substring(atIndex);
    }
}
