package com.xbleey.goldpricealert.service;

import com.xbleey.goldpricealert.config.GoldAlertMailProperties;
import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GoldAlertEmailServiceApiTemplateTest {

    @Test
    void notifyApiErrorBuildsExpectedTemplate() throws Exception {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        GoldAlertMailProperties properties = new GoldAlertMailProperties();
        properties.setSender("sender@example.com");
        properties.setRecipients(List.of("receiver@example.com"));
        Clock clock = Clock.fixed(Instant.parse("2026-01-05T12:00:00Z"), ZoneOffset.UTC);
        GoldAlertEmailService service = new GoldAlertEmailService(mailSender, properties, clock);

        GoldApiErrorMessage message = new GoldApiErrorMessage(
                Instant.parse("2026-01-05T12:00:00Z"),
                "https://api.gold.example/price",
                "timeout",
                Duration.ofMinutes(12)
        );

        service.notifyApiError(message);

        verify(mailSender).send(mimeMessage);
        assertThat(mimeMessage.getSubject()).isEqualTo("Gold Price API ERROR");
        String content = readContent(mimeMessage.getContent());
        assertThat(content).contains("Gold Price API ERROR");
        assertThat(content).contains("api=https://api.gold.example/price");
        assertThat(content).contains("error=timeout");
        assertThat(content).contains("已连续失败时长");
        assertThat(content).contains("color:#d32f2f");
    }

    @Test
    void notifyApiResumeBuildsExpectedTemplate() throws Exception {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        GoldAlertMailProperties properties = new GoldAlertMailProperties();
        properties.setSender("sender@example.com");
        properties.setRecipients(List.of("receiver@example.com"));
        Clock clock = Clock.fixed(Instant.parse("2026-01-05T12:30:00Z"), ZoneOffset.UTC);
        GoldAlertEmailService service = new GoldAlertEmailService(mailSender, properties, clock);

        GoldApiResumeMessage message = new GoldApiResumeMessage(
                Instant.parse("2026-01-05T12:30:00Z"),
                Instant.parse("2026-01-05T12:00:00Z"),
                Duration.ofMinutes(30),
                "https://api.gold.example/price"
        );

        service.notifyApiResume(message);

        verify(mailSender).send(mimeMessage);
        assertThat(mimeMessage.getSubject()).isEqualTo("Gold Price API RESUME");
        String content = readContent(mimeMessage.getContent());
        assertThat(content).contains("Gold Price API RESUME");
        assertThat(content).contains("resumeTime=2026-01-05 12:30:00");
        assertThat(content).contains("firstFailureTime=2026-01-05 12:00:00");
        assertThat(content).contains("api=https://api.gold.example/price");
    }

    private String readContent(Object content) throws Exception {
        if (content == null) {
            return "";
        }
        if (content instanceof String text) {
            return text;
        }
        if (content instanceof Multipart multipart) {
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < multipart.getCount(); i++) {
                BodyPart part = multipart.getBodyPart(i);
                builder.append(readContent(part.getContent()));
            }
            return builder.toString();
        }
        return content.toString();
    }
}
