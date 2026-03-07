package com.xbleey.goldpricealert.service;

import com.xbleey.goldpricealert.config.GoldAlertMailProperties;
import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;

import java.math.BigDecimal;
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

class GoldAlertEmailServiceSubjectTest {

    @Test
    void notifyAlertBuildsExpectedSubject() throws Exception {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        GoldAlertMailProperties properties = new GoldAlertMailProperties();
        properties.setSender("sender@example.com");
        properties.setMinLevel("P1");
        GoldMailRecipientService recipientService = mock(GoldMailRecipientService.class);
        when(recipientService.listEnabledEmails()).thenReturn(List.of("receiver@example.com"));
        Clock clock = Clock.fixed(Instant.parse("2026-01-05T12:00:00Z"), ZoneOffset.UTC);
        GoldAlertEmailService service = new GoldAlertEmailService(mailSender, properties, clock, recipientService);

        GoldAlertMessage message = new GoldAlertMessage(
                "P2",
                2,
                "price moved",
                Instant.parse("2026-01-05T12:00:00Z"),
                Duration.ofMinutes(5),
                new BigDecimal("0.50"),
                new BigDecimal("1.25"),
                new BigDecimal("650.10"),
                new BigDecimal("658.88"),
                List.of()
        );

        service.notifyAlert(message);

        verify(mailSender).send(mimeMessage);
        assertThat(mimeMessage.getSubject()).isEqualTo("Price Alert [↑] - P2 - 658.88");
        String content = readContent(mimeMessage.getContent());
        assertThat(content).doesNotContain("WARNING!!WARNING!!WARNING!!");
        assertThat(content).doesNotContain("Gold Price Chart");
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
