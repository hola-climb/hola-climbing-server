package com.holaclimbing.server.infrastructure.mail;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class SmtpVerificationEmailSender implements VerificationEmailSender {

    private final JavaMailSender mailSender;
    private final String from;
    private final String frontendBaseUrl;

    public SmtpVerificationEmailSender(JavaMailSender mailSender, String from, String frontendBaseUrl) {
        this.mailSender = mailSender;
        this.from = from;
        this.frontendBaseUrl = trimTrailingSlash(frontendBaseUrl);
    }

    @Override
    public void send(String toEmail, String token) {
        String actionUrl = buildActionUrl("/verify-email", token);
        sendMail(toEmail, MailTemplate.verification(actionUrl));
    }

    @Override
    public void sendPasswordReset(String toEmail, String token) {
        String actionUrl = buildActionUrl("/reset-password", token);
        sendMail(toEmail, MailTemplate.passwordReset(actionUrl));
    }

    private void sendMail(String toEmail, MailTemplate template) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    message,
                    MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED,
                    StandardCharsets.UTF_8.name());
            if (from != null && !from.isBlank()) {
                helper.setFrom(from);
            }
            helper.setTo(toEmail);
            helper.setSubject(template.subject());
            helper.setText(template.plainText(), template.html());
            mailSender.send(message);
        } catch (MessagingException e) {
            throw new MailPreparationException("메일 본문 생성에 실패했습니다.", e);
        }
    }

    private String buildActionUrl(String path, String token) {
        return frontendBaseUrl + path + "?token=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
    }

    private String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private record MailTemplate(
            String subject,
            String preheader,
            String title,
            String lead,
            String buttonText,
            String actionUrl,
            String expiryText
    ) {

        static MailTemplate verification(String actionUrl) {
            return new MailTemplate(
                    "Hola 이메일 인증",
                    "Hola 계정 보호를 위해 이메일 인증을 완료해 주세요.",
                    "이메일 인증을 완료해 주세요",
                    "Hola Climbing을 안전하게 이용할 수 있도록 아래 버튼을 눌러 이메일 주소를 확인해 주세요.",
                    "이메일 인증 완료하기",
                    actionUrl,
                    "이 링크는 발급 후 24시간 동안 유효합니다.");
        }

        static MailTemplate passwordReset(String actionUrl) {
            return new MailTemplate(
                    "Hola 비밀번호 재설정",
                    "요청하신 비밀번호 재설정 링크를 보내드립니다.",
                    "비밀번호를 재설정해 주세요",
                    "계정 보안을 위해 아래 버튼을 눌러 새 비밀번호를 설정해 주세요.",
                    "비밀번호 재설정하기",
                    actionUrl,
                    "이 링크는 발급 후 30분 동안 유효합니다.");
        }

        String plainText() {
            return """
                    %s

                    %s

                    %s
                    %s

                    %s
                    본인이 요청하지 않았다면 이 메일을 무시해 주세요.
                    """.formatted(title, lead, buttonText, actionUrl, expiryText);
        }

        String html() {
            return """
                    <!doctype html>
                    <html lang="ko">
                    <head>
                      <meta charset="UTF-8">
                      <meta name="viewport" content="width=device-width, initial-scale=1.0">
                      <title>%s</title>
                    </head>
                    <body style="margin:0;padding:0;background:#f4f7fb;color:#172033;font-family:Arial,'Apple SD Gothic Neo','Malgun Gothic',sans-serif;">
                      <span style="display:none!important;visibility:hidden;opacity:0;color:transparent;height:0;width:0;overflow:hidden;">%s</span>
                      <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="background:#f4f7fb;padding:32px 12px;">
                        <tr>
                          <td align="center">
                            <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="max-width:600px;background:#ffffff;border:1px solid #e7eaf0;border-radius:24px;overflow:hidden;box-shadow:0 10px 34px rgba(23,32,51,0.08);">
                              <tr>
                                <td style="padding:34px 32px 20px;text-align:center;background:#151515;">
                                  <div style="font-size:30px;font-weight:800;letter-spacing:4px;color:#ffffff;line-height:1;">HOLA</div>
                                  <div style="margin-top:10px;font-size:13px;font-weight:600;color:#c8ff00;letter-spacing:0.12em;text-transform:uppercase;">Climbing Account</div>
                                </td>
                              </tr>
                              <tr>
                                <td style="padding:36px 34px 16px;text-align:center;">
                                  <div style="display:inline-block;padding:7px 12px;border-radius:999px;background:#eeffb8;color:#405900;font-size:12px;font-weight:700;letter-spacing:0.04em;">SECURE LINK</div>
                                  <h1 style="margin:20px 0 12px;font-size:28px;line-height:1.3;font-weight:800;color:#151515;">%s</h1>
                                  <p style="margin:0 auto;font-size:15px;line-height:1.7;color:#667085;max-width:440px;">%s</p>
                                </td>
                              </tr>
                              <tr>
                                <td align="center" style="padding:20px 34px 30px;">
                                  <a href="%s" style="display:inline-block;background:#151515;color:#ffffff;text-decoration:none;border-radius:14px;padding:15px 28px;font-size:15px;font-weight:800;">%s</a>
                                </td>
                              </tr>
                              <tr>
                                <td style="padding:0 34px 30px;">
                                  <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="background:#f7f8fb;border:1px solid #e7eaf0;border-radius:16px;">
                                    <tr>
                                      <td style="padding:18px 18px;">
                                        <p style="margin:0 0 8px;font-size:13px;font-weight:700;color:#151515;">버튼이 열리지 않나요?</p>
                                        <a href="%s" style="font-size:13px;line-height:1.6;color:#475467;word-break:break-all;text-decoration:underline;">%s</a>
                                      </td>
                                    </tr>
                                  </table>
                                </td>
                              </tr>
                              <tr>
                                <td style="padding:0 34px 34px;">
                                  <p style="margin:0;font-size:13px;line-height:1.7;color:#8d96a8;">%s<br>본인이 요청하지 않았다면 이 메일을 무시해 주세요.</p>
                                </td>
                              </tr>
                            </table>
                            <p style="margin:18px 0 0;font-size:12px;line-height:1.6;color:#98a2b3;">© Hola Climbing. This is an automated message.</p>
                          </td>
                        </tr>
                      </table>
                    </body>
                    </html>
                    """.formatted(
                    escape(subject),
                    escape(preheader),
                    escape(title),
                    escape(lead),
                    escape(actionUrl),
                    escape(buttonText),
                    escape(actionUrl),
                    escape(actionUrl),
                    escape(expiryText));
        }

        private String escape(String value) {
            if (value == null) {
                return "";
            }
            return value
                    .replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;")
                    .replace("'", "&#39;");
        }
    }
}
