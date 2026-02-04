package com.xbleey.goldpricealert.service;

import com.xbleey.goldpricealert.config.GoldAlertMailProperties;
import com.xbleey.goldpricealert.enums.GoldAlertLevel;
import com.xbleey.goldpricealert.model.GoldApiResponse;
import com.xbleey.goldpricealert.model.GoldPriceSnapshot;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class GoldAlertEmailService implements GoldAlertNotifier {

    private static final Logger log = LoggerFactory.getLogger(GoldAlertEmailService.class);
    private static final DateTimeFormatter REPORT_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final JavaMailSender mailSender;
    private final GoldAlertMailProperties properties;
    private final Clock clock;
    private final Object sendLock = new Object();
    private final Map<GoldAlertLevel, Instant> lastSentAtByLevel = new EnumMap<>(GoldAlertLevel.class);
    private Instant lastSentAt;
    private GoldAlertLevel lastSentLevel;

    public GoldAlertEmailService(JavaMailSender mailSender, GoldAlertMailProperties properties, Clock clock) {
        this.mailSender = mailSender;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public void notifyAlert(GoldAlertMessage message) {
        if (message == null) {
            return;
        }
        if (!shouldSend(message)) {
            return;
        }
        EmailTargets targets = resolveEmailTargets();
        if (targets == null) {
            return;
        }
        sendEmail(targets, buildSubject(message), buildPlainText(message), buildHtmlBody(message));
    }

    public void notifyThresholdAlert(GoldThresholdAlertMessage message) {
        if (message == null) {
            return;
        }
        EmailTargets targets = resolveEmailTargets();
        if (targets == null) {
            return;
        }
        sendEmail(targets, buildThresholdSubject(message), buildThresholdPlainText(message), buildThresholdHtmlBody(message));
    }

    public void notifyApiError(GoldApiErrorMessage message) {
        if (message == null) {
            return;
        }
        EmailTargets targets = resolveEmailTargets();
        if (targets == null) {
            return;
        }
        sendEmail(targets, buildApiErrorSubject(), buildApiErrorPlainText(message), buildApiErrorHtmlBody(message));
    }

    public void notifyApiResume(GoldApiResumeMessage message) {
        if (message == null) {
            return;
        }
        EmailTargets targets = resolveEmailTargets();
        if (targets == null) {
            return;
        }
        sendEmail(targets, buildApiResumeSubject(), buildApiResumePlainText(message), buildApiResumeHtmlBody(message));
    }

    private boolean shouldSend(GoldAlertMessage message) {
        if (message == null || message.level() == null) {
            return false;
        }
        GoldAlertLevel minLevel = properties.getMinLevel();
        if (minLevel == null) {
            return canSendWithCooldown(message);
        }
        if (message.level().ordinal() < minLevel.ordinal()) {
            return false;
        }
        return canSendWithCooldown(message);
    }

    private boolean canSendWithCooldown(GoldAlertMessage message) {
        Instant now = message.alertTime() == null ? Instant.now(clock) : message.alertTime();
        synchronized (sendLock) {
            boolean isLevelUp = lastSentLevel == null
                    || message.level().ordinal() > lastSentLevel.ordinal();
            if (isLevelUp || lastSentAt == null) {
                recordSent(message.level(), now);
                return true;
            }
            Duration cooldown = properties.cooldownFor(message.level());
            if (cooldown == null || cooldown.isZero() || cooldown.isNegative()) {
                recordSent(message.level(), now);
                return true;
            }
            Instant lastAtForLevel = lastSentAtByLevel.get(message.level());
            Instant baseline = lastAtForLevel == null ? lastSentAt : lastAtForLevel;
            Duration elapsed = Duration.between(baseline, now);
            if (elapsed.compareTo(cooldown) >= 0) {
                recordSent(message.level(), now);
                return true;
            }
            return false;
        }
    }

    private void recordSent(GoldAlertLevel level, Instant now) {
        lastSentAt = now;
        lastSentLevel = level;
        if (level != null) {
            lastSentAtByLevel.put(level, now);
        }
    }

    public String previewHtml(GoldAlertMessage message) {
        if (message == null) {
            return "";
        }
        return buildHtmlBody(message);
    }

    private EmailTargets resolveEmailTargets() {
        String sender = normalize(properties.getSender());
        List<String> recipients = normalizeRecipients(properties.getRecipients());
        if (sender == null) {
            log.warn("Skip alert email: sender not configured");
            return null;
        }
        if (recipients.isEmpty()) {
            log.debug("Skip alert email: recipients not configured");
            return null;
        }
        return new EmailTargets(sender, recipients);
    }

    private void sendEmail(EmailTargets targets, String subject, String plainText, String htmlBody) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, StandardCharsets.UTF_8.name());
            helper.setFrom(targets.sender());
            helper.setTo(targets.recipients().toArray(new String[0]));
            helper.setSubject(subject);
            helper.setText(plainText, htmlBody);
            mailSender.send(mimeMessage);
        } catch (Exception ex) {
            log.warn("Failed to send alert email", ex);
        }
    }

    private String buildSubject(GoldAlertMessage message) {
        return "Gold Price Alert " + resolveDirectionTag(message) + " - " + message.level().getLevelName();
    }

    private String buildThresholdSubject(GoldThresholdAlertMessage message) {
        String direction = resolveThresholdDirection(message);
        return "Gold Price Alert " + direction + " " + formatPrice(message == null ? null : message.threshold());
    }

    private String buildApiErrorSubject() {
        return "Gold Price API ERROR";
    }

    private String buildApiResumeSubject() {
        return "Gold Price API RESUME";
    }

    private String resolveDirectionTag(GoldAlertMessage message) {
        if (message == null || message.changePercent() == null) {
            return "[?]";
        }
        int sign = message.changePercent().signum();
        if (sign > 0) {
            return "[↑]";
        }
        if (sign < 0) {
            return "[↓]";
        }
        return "[?]";
    }

    private String resolveThresholdDirection(GoldThresholdAlertMessage message) {
        if (message == null || message.direction() == null) {
            return "UNKNOWN";
        }
        return message.direction().subjectTag();
    }

    private String buildPlainText(GoldAlertMessage message) {
        StringBuilder builder = new StringBuilder();
        ZoneId zone = clock.getZone();
        ZoneId updatedAtZone = ZoneId.of("UTC+08:00");
        builder.append("WARNING!!WARNING!!WARNING!!").append('\n');
        builder.append("level: ").append(message.level().getLevelName()).append('\n');
        builder.append("window=").append(formatDuration(message.window())).append('\n');
        builder.append("threshold=").append(formatPercent(message.thresholdPercent())).append("%").append('\n');
        builder.append("change=").append(formatPercent(message.changePercent())).append("%").append('\n');
        builder.append("price ").append(formatPrice(message.baselinePrice()))
                .append(" -> ").append(formatPrice(message.latestPrice())).append('\n');
        builder.append("time=").append(formatInstant(message.alertTime(), zone)).append('\n');
        builder.append("time (UTC+8)=")
                .append(formatInstant(message.alertTime(), ZoneId.of("UTC+08:00")))
                .append('\n');
        builder.append("Generated at: ")
                .append(formatInstant(message.alertTime(), zone))
                .append(' ')
                .append(zone.getId())
                .append('\n')
                .append('\n');
        builder.append("Recent GoldPriceSnapshot (last ")
                .append(message.recentSnapshots() == null ? 0 : message.recentSnapshots().size())
                .append(")")
                .append('\n');
        appendPlainTextTable(builder, message.recentSnapshots(), zone, updatedAtZone);
        return builder.toString();
    }

    private String buildThresholdPlainText(GoldThresholdAlertMessage message) {
        StringBuilder builder = new StringBuilder();
        ZoneId zone = clock.getZone();
        ZoneId updatedAtZone = ZoneId.of("UTC+08:00");
        builder.append("Gold Price Threshold Alert").append('\n');
        builder.append("direction=").append(formatThresholdDirection(message)).append('\n');
        builder.append("price=").append(formatPrice(message == null ? null : message.price())).append('\n');
        builder.append("time=").append(formatInstant(message == null ? null : message.alertTime(), zone)).append('\n');
        builder.append("time (UTC+8)=")
                .append(formatInstant(message == null ? null : message.alertTime(), ZoneId.of("UTC+08:00")))
                .append('\n')
                .append('\n');
        builder.append("Recent GoldPriceSnapshot (last ")
                .append(message == null || message.recentSnapshots() == null ? 0 : message.recentSnapshots().size())
                .append(")")
                .append('\n');
        appendPlainTextTable(builder, message == null ? null : message.recentSnapshots(), zone, updatedAtZone);
        return builder.toString();
    }

    private String buildApiErrorPlainText(GoldApiErrorMessage message) {
        StringBuilder builder = new StringBuilder();
        ZoneId zone = clock.getZone();
        builder.append("Gold Price API ERROR").append('\n');
        builder.append("time=").append(formatInstant(message.failureTime(), zone)).append('\n');
        builder.append("api=").append(safeValue(message.apiUrl())).append('\n');
        builder.append("error=").append(safeValue(message.errorDetail())).append('\n');
        builder.append("downtime=").append(formatDuration(message.downtime())).append('\n');
        builder.append("note=API 长时间未恢复").append('\n');
        return builder.toString();
    }

    private String buildApiResumePlainText(GoldApiResumeMessage message) {
        StringBuilder builder = new StringBuilder();
        ZoneId zone = clock.getZone();
        builder.append("Gold Price API RESUME").append('\n');
        builder.append("resumeTime=").append(formatInstant(message.resumeTime(), zone)).append('\n');
        builder.append("firstFailureTime=").append(formatInstant(message.firstFailureTime(), zone)).append('\n');
        builder.append("downtime=").append(formatDuration(message.downtime())).append('\n');
        builder.append("api=").append(safeValue(message.apiUrl())).append('\n');
        return builder.toString();
    }

    private String buildHtmlBody(GoldAlertMessage message) {
        StringBuilder builder = new StringBuilder();
        ZoneId zone = clock.getZone();
        ZoneId updatedAtZone = ZoneId.of("UTC+08:00");
        builder.append("<html><body>");
        builder.append("<h2>WARNING!!WARNING!!WARNING!!</h2>");
        builder.append("<h3><span style=\"color:#d32f2f;font-weight:bold;\">level: ")
                .append(escapeHtml(message.level().getLevelName()))
                .append("</span></h3>");
        builder.append("<h3>window=").append(escapeHtml(formatDuration(message.window()))).append("</h3>");
        builder.append("<h3>threshold=").append(escapeHtml(formatPercent(message.thresholdPercent())))
                .append("%</h3>");
        builder.append("<h3>change=").append(escapeHtml(formatPercent(message.changePercent()))).append("%</h3>");
        builder.append("<h3>price ")
                .append(escapeHtml(formatPrice(message.baselinePrice())))
                .append(" --&gt; ")
                .append(escapeHtml(formatPrice(message.latestPrice())))
                .append("</h3>");
        builder.append("<h3>time=")
                .append(escapeHtml(formatInstant(message.alertTime(), zone)))
                .append("</h3>");
        builder.append("<h3>time (UTC+8)=")
                .append(escapeHtml(formatInstant(message.alertTime(), ZoneId.of("UTC+08:00"))))
                .append("</h3>");
        builder.append("<p>Generated at: ")
                .append(escapeHtml(formatInstant(message.alertTime(), zone)))
                .append(' ')
                .append(escapeHtml(zone.getId()))
                .append("</p>");
        builder.append("<h3>Recent GoldPriceSnapshot (last ")
                .append(message.recentSnapshots() == null ? 0 : message.recentSnapshots().size())
                .append(")</h3>");
        appendHtmlTable(builder, message.recentSnapshots(), zone, updatedAtZone);
        builder.append("</body></html>");
        return builder.toString();
    }

    private String buildThresholdHtmlBody(GoldThresholdAlertMessage message) {
        StringBuilder builder = new StringBuilder();
        ZoneId zone = clock.getZone();
        ZoneId updatedAtZone = ZoneId.of("UTC+08:00");
        builder.append("<html><body>");
        builder.append("<h2>Gold Price Threshold Alert</h2>");
        builder.append("<h3>direction=")
                .append(escapeHtml(formatThresholdDirection(message)))
                .append("</h3>");
        builder.append("<h3>price=")
                .append(escapeHtml(formatPrice(message == null ? null : message.price())))
                .append("</h3>");
        builder.append("<h3>time=")
                .append(escapeHtml(formatInstant(message == null ? null : message.alertTime(), zone)))
                .append("</h3>");
        builder.append("<h3>time (UTC+8)=")
                .append(escapeHtml(formatInstant(message == null ? null : message.alertTime(), ZoneId.of("UTC+08:00"))))
                .append("</h3>");
        builder.append("<h3>Recent GoldPriceSnapshot (last ")
                .append(message == null || message.recentSnapshots() == null ? 0 : message.recentSnapshots().size())
                .append(")</h3>");
        appendHtmlTable(builder, message == null ? null : message.recentSnapshots(), zone, updatedAtZone);
        builder.append("</body></html>");
        return builder.toString();
    }

    private String buildApiErrorHtmlBody(GoldApiErrorMessage message) {
        StringBuilder builder = new StringBuilder();
        ZoneId zone = clock.getZone();
        builder.append("<html><body>");
        builder.append("<h2>Gold Price API ERROR</h2>");
        builder.append("<p>time=")
                .append(escapeHtml(formatInstant(message.failureTime(), zone)))
                .append("</p>");
        builder.append("<p>api=").append(escapeHtml(safeValue(message.apiUrl()))).append("</p>");
        builder.append("<p>error=").append(escapeHtml(safeValue(message.errorDetail()))).append("</p>");
        builder.append("<p><span style=\"color:#d32f2f;font-weight:bold;\">")
                .append("已连续失败时长: ")
                .append(escapeHtml(formatDuration(message.downtime())))
                .append("</span></p>");
        builder.append("</body></html>");
        return builder.toString();
    }

    private String buildApiResumeHtmlBody(GoldApiResumeMessage message) {
        StringBuilder builder = new StringBuilder();
        ZoneId zone = clock.getZone();
        builder.append("<html><body>");
        builder.append("<h2>Gold Price API RESUME</h2>");
        builder.append("<p>resumeTime=")
                .append(escapeHtml(formatInstant(message.resumeTime(), zone)))
                .append("</p>");
        builder.append("<p>firstFailureTime=")
                .append(escapeHtml(formatInstant(message.firstFailureTime(), zone)))
                .append("</p>");
        builder.append("<p>downtime=")
                .append(escapeHtml(formatDuration(message.downtime())))
                .append("</p>");
        builder.append("<p>api=").append(escapeHtml(safeValue(message.apiUrl()))).append("</p>");
        builder.append("</body></html>");
        return builder.toString();
    }

    private void appendPlainTextTable(
            StringBuilder builder,
            List<GoldPriceSnapshot> recentSnapshots,
            ZoneId zone,
            ZoneId updatedAtZone
    ) {
        builder.append(String.format(
                "# | price | updatedAt (%s) | fetchedAt (Z) | symbol | name",
                updatedAtZone.getId()
        )).append('\n');
        builder.append("---|---|---|---|---|---").append('\n');
        if (recentSnapshots != null) {
            int index = 1;
            for (GoldPriceSnapshot snapshot : recentSnapshots) {
                GoldApiResponse response = snapshot == null ? null : snapshot.response();
                builder.append(index).append(" | ")
                        .append(formatPrice(snapshot)).append(" | ")
                        .append(formatInstant(response == null ? null : response.updatedAt(), updatedAtZone)).append(" | ")
                        .append(formatInstant(snapshot == null ? null : snapshot.fetchedAt(), zone)).append(" | ")
                        .append(safeValue(response == null ? null : response.symbol())).append(" | ")
                        .append(safeValue(response == null ? null : response.name()))
                        .append('\n');
                index++;
            }
        }
    }

    private void appendHtmlTable(
            StringBuilder builder,
            List<GoldPriceSnapshot> recentSnapshots,
            ZoneId zone,
            ZoneId updatedAtZone
    ) {
        builder.append("<table style=\"border-collapse:collapse;width:100%;\">");
        builder.append("<thead><tr style=\"background-color:#e0e0e0;\">")
                .append("<th style=\"border:1px solid #ddd;padding:8px;\">#</th>")
                .append("<th style=\"border:1px solid #ddd;padding:8px;\">price</th>")
                .append("<th style=\"border:1px solid #ddd;padding:8px;\">updatedAt (")
                .append(escapeHtml(updatedAtZone.getId()))
                .append(")</th>")
                .append("<th style=\"border:1px solid #ddd;padding:8px;\">fetchedAt (Z)</th>")
                .append("<th style=\"border:1px solid #ddd;padding:8px;\">symbol</th>")
                .append("<th style=\"border:1px solid #ddd;padding:8px;\">name</th>")
                .append("</tr></thead><tbody>");
        if (recentSnapshots != null) {
            int index = 1;
            for (GoldPriceSnapshot snapshot : recentSnapshots) {
                String rowColor = (index % 2 == 0) ? "#f5f5f5" : "#ffffff";
                GoldApiResponse response = snapshot == null ? null : snapshot.response();
                builder.append("<tr style=\"background-color:").append(rowColor).append(";\">")
                        .append("<td style=\"border:1px solid #ddd;padding:8px;\">").append(index).append("</td>")
                        .append("<td style=\"border:1px solid #ddd;padding:8px;\">")
                        .append(escapeHtml(formatPrice(snapshot)))
                        .append("</td>")
                        .append("<td style=\"border:1px solid #ddd;padding:8px;\">")
                        .append(escapeHtml(formatInstant(response == null ? null : response.updatedAt(), updatedAtZone)))
                        .append("</td>")
                        .append("<td style=\"border:1px solid #ddd;padding:8px;\">")
                        .append(escapeHtml(formatInstant(snapshot == null ? null : snapshot.fetchedAt(), zone)))
                        .append("</td>")
                        .append("<td style=\"border:1px solid #ddd;padding:8px;\">")
                        .append(escapeHtml(safeValue(response == null ? null : response.symbol())))
                        .append("</td>")
                        .append("<td style=\"border:1px solid #ddd;padding:8px;\">")
                        .append(escapeHtml(safeValue(response == null ? null : response.name())))
                        .append("</td>")
                        .append("</tr>");
                index++;
            }
        }
        builder.append("</tbody></table>");
    }

    private String formatInstant(Instant instant, ZoneId zone) {
        if (instant == null) {
            return "-";
        }
        return REPORT_TIME_FORMATTER.withZone(zone).format(instant);
    }

    private String formatPrice(GoldPriceSnapshot snapshot) {
        if (snapshot == null) {
            return "-";
        }
        return formatPrice(snapshot.price());
    }

    private String formatPrice(BigDecimal price) {
        return price == null ? "-" : price.toPlainString();
    }

    private String formatThresholdDirection(GoldThresholdAlertMessage message) {
        if (message == null) {
            return "UNKNOWN";
        }
        String direction = resolveThresholdDirection(message);
        return direction + " " + formatPrice(message.threshold());
    }

    private String safeValue(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "-";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private String formatPercent(BigDecimal value) {
        if (value == null) {
            return "-";
        }
        return value.setScale(4, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    private String formatDuration(java.time.Duration duration) {
        return duration == null ? "-" : duration.toString();
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private List<String> normalizeRecipients(List<String> recipients) {
        if (recipients == null) {
            return List.of();
        }
        return recipients.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
    }

    private record EmailTargets(String sender, List<String> recipients) {
    }

}
