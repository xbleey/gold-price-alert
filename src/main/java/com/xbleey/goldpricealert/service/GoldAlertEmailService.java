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
    private static final Duration CHART_WINDOW = Duration.ofMinutes(20);
    private static final Duration CHART_EXPECTED_INTERVAL = Duration.ofSeconds(20);
    private static final Duration CHART_GAP_THRESHOLD = CHART_EXPECTED_INTERVAL.multipliedBy(2);
    private static final int CHART_MAX_POINTS = 60;
    private static final int CHART_WIDTH = 600;
    private static final int CHART_HEIGHT = 160;
    private static final int CHART_PADDING = 24;

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
        builder.append("Gold Price Chart (20m)").append('\n');
        builder.append(buildPlainTextChart(message.recentSnapshots(), message.alertTime()));
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
        builder.append("Gold Price Chart (20m)").append('\n');
        builder.append(buildPlainTextChart(message == null ? null : message.recentSnapshots(),
                message == null ? null : message.alertTime()));
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
        builder.append(buildHtmlChartSection(message.recentSnapshots(), message.alertTime(), zone));
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
        builder.append(buildHtmlChartSection(message == null ? null : message.recentSnapshots(),
                message == null ? null : message.alertTime(), zone));
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

    private String buildHtmlChartSection(List<GoldPriceSnapshot> recentSnapshots, Instant anchorTime, ZoneId zone) {
        StringBuilder builder = new StringBuilder();
        builder.append("<h3>Gold Price Chart (20m)</h3>");
        builder.append(buildHtmlPriceChart(recentSnapshots, anchorTime, zone));
        return builder.toString();
    }

    private String buildHtmlPriceChart(List<GoldPriceSnapshot> recentSnapshots, Instant anchorTime, ZoneId zone) {
        ChartData chartData = buildChartData(recentSnapshots, anchorTime);
        if (chartData.points().isEmpty()) {
            return "<p>chart=无可用数据</p>";
        }
        StringBuilder builder = new StringBuilder();
        builder.append("<svg width=\"").append(CHART_WIDTH)
                .append("\" height=\"").append(CHART_HEIGHT + 40)
                .append("\" viewBox=\"0 0 ").append(CHART_WIDTH).append(" ")
                .append(CHART_HEIGHT + 40)
                .append("\" xmlns=\"http://www.w3.org/2000/svg\">");
        builder.append("<rect x=\"0\" y=\"0\" width=\"").append(CHART_WIDTH)
                .append("\" height=\"").append(CHART_HEIGHT + 40)
                .append("\" fill=\"#ffffff\"/>");
        builder.append("<rect x=\"").append(CHART_PADDING)
                .append("\" y=\"").append(CHART_PADDING)
                .append("\" width=\"").append(CHART_WIDTH - CHART_PADDING * 2)
                .append("\" height=\"").append(CHART_HEIGHT - CHART_PADDING)
                .append("\" fill=\"#fafafa\" stroke=\"#e0e0e0\"/>");
        appendChartAxisLabels(builder);
        appendChartPriceRange(builder, chartData, zone);
        appendChartSegments(builder, chartData);
        builder.append("</svg>");
        return builder.toString();
    }

    private void appendChartAxisLabels(StringBuilder builder) {
        int baseY = CHART_HEIGHT + 20;
        int[] minutes = new int[]{-20, -15, -10, -5, 0};
        for (int minute : minutes) {
            double ratio = (minute + 20) / 20.0;
            int x = (int) Math.round(CHART_PADDING + ratio * (CHART_WIDTH - CHART_PADDING * 2));
            builder.append("<text x=\"").append(x).append("\" y=\"").append(baseY)
                    .append("\" fill=\"#616161\" font-size=\"12\" text-anchor=\"middle\">")
                    .append(minute).append("m</text>");
        }
    }

    private void appendChartPriceRange(StringBuilder builder, ChartData chartData, ZoneId zone) {
        String minValue = escapeHtml(formatPrice(chartData.minPrice()));
        String maxValue = escapeHtml(formatPrice(chartData.maxPrice()));
        builder.append("<text x=\"").append(CHART_PADDING)
                .append("\" y=\"").append(CHART_PADDING - 6)
                .append("\" fill=\"#616161\" font-size=\"12\">")
                .append("高 ").append(maxValue)
                .append("</text>");
        builder.append("<text x=\"").append(CHART_PADDING)
                .append("\" y=\"").append(CHART_HEIGHT - 6)
                .append("\" fill=\"#616161\" font-size=\"12\">")
                .append("低 ").append(minValue)
                .append("</text>");
        if (chartData.anchorTime() != null) {
            builder.append("<text x=\"").append(CHART_WIDTH - CHART_PADDING)
                    .append("\" y=\"").append(CHART_PADDING - 6)
                    .append("\" fill=\"#616161\" font-size=\"12\" text-anchor=\"end\">")
                    .append("截至 ")
                    .append(escapeHtml(formatInstant(chartData.anchorTime(), zone)))
                    .append("</text>");
        }
    }

    private void appendChartSegments(StringBuilder builder, ChartData chartData) {
        for (List<ChartPoint> segment : chartData.points()) {
            if (segment.size() < 2) {
                if (!segment.isEmpty()) {
                    ChartPoint point = segment.getFirst();
                    builder.append("<circle cx=\"").append(point.x())
                            .append("\" cy=\"").append(point.y())
                            .append("\" r=\"2\" fill=\"#1976d2\"/>");
                }
                continue;
            }
            StringBuilder polyline = new StringBuilder();
            for (ChartPoint point : segment) {
                polyline.append(point.x()).append(',').append(point.y()).append(' ');
            }
            builder.append("<polyline fill=\"none\" stroke=\"#1976d2\" stroke-width=\"2\" points=\"")
                    .append(polyline)
                    .append("\"/>");
        }
    }

    private String buildPlainTextChart(List<GoldPriceSnapshot> recentSnapshots, Instant anchorTime) {
        ChartData chartData = buildChartData(recentSnapshots, anchorTime);
        if (chartData.points().isEmpty()) {
            return "chart=无可用数据\n";
        }
        char[] sparkline = new char[CHART_MAX_POINTS];
        for (int i = 0; i < sparkline.length; i++) {
            sparkline[i] = ' ';
        }
        for (List<ChartPoint> segment : chartData.points()) {
            for (ChartPoint point : segment) {
                if (point.index() >= 0 && point.index() < sparkline.length) {
                    sparkline[point.index()] = '*';
                }
            }
        }
        StringBuilder builder = new StringBuilder();
        builder.append('[').append(new String(sparkline)).append(']').append('\n');
        builder.append("轴: -20m -15m -10m -5m 0").append('\n');
        builder.append("价格: ").append(formatPrice(chartData.minPrice()))
                .append(" ~ ").append(formatPrice(chartData.maxPrice()))
                .append('\n');
        return builder.toString();
    }

    private ChartData buildChartData(List<GoldPriceSnapshot> recentSnapshots, Instant anchorTime) {
        if (recentSnapshots == null || recentSnapshots.isEmpty()) {
            return new ChartData(List.of(), BigDecimal.ZERO, BigDecimal.ZERO, anchorTime);
        }
        Instant effectiveAnchor = anchorTime == null ? recentSnapshots.getFirst().fetchedAt() : anchorTime;
        Instant windowStart = effectiveAnchor.minus(CHART_WINDOW);
        List<GoldPriceSnapshot> ordered = recentSnapshots.stream()
                .filter(Objects::nonNull)
                .filter(snapshot -> snapshot.fetchedAt() != null)
                .filter(snapshot -> !snapshot.fetchedAt().isAfter(effectiveAnchor))
                .filter(snapshot -> !snapshot.fetchedAt().isBefore(windowStart))
                .sorted((a, b) -> a.fetchedAt().compareTo(b.fetchedAt()))
                .toList();
        if (ordered.isEmpty()) {
            return new ChartData(List.of(), BigDecimal.ZERO, BigDecimal.ZERO, effectiveAnchor);
        }
        BigDecimal minPrice = null;
        BigDecimal maxPrice = null;
        for (GoldPriceSnapshot snapshot : ordered) {
            BigDecimal price = snapshot.getPrice();
            if (price == null) {
                continue;
            }
            if (minPrice == null || price.compareTo(minPrice) < 0) {
                minPrice = price;
            }
            if (maxPrice == null || price.compareTo(maxPrice) > 0) {
                maxPrice = price;
            }
        }
        if (minPrice == null || maxPrice == null) {
            return new ChartData(List.of(), BigDecimal.ZERO, BigDecimal.ZERO, effectiveAnchor);
        }
        if (minPrice.compareTo(maxPrice) == 0) {
            minPrice = minPrice.subtract(BigDecimal.ONE);
            maxPrice = maxPrice.add(BigDecimal.ONE);
        }
        List<List<ChartPoint>> segments = new java.util.ArrayList<>();
        List<ChartPoint> current = new java.util.ArrayList<>();
        Instant previousTime = null;
        for (GoldPriceSnapshot snapshot : ordered) {
            BigDecimal price = snapshot.getPrice();
            if (price == null) {
                continue;
            }
            Instant fetchedAt = snapshot.fetchedAt();
            if (previousTime != null) {
                Duration gap = Duration.between(previousTime, fetchedAt);
                if (gap.compareTo(CHART_GAP_THRESHOLD) > 0) {
                    if (!current.isEmpty()) {
                        segments.add(List.copyOf(current));
                        current = new java.util.ArrayList<>();
                    }
                }
            }
            double ratio = (double) Duration.between(windowStart, fetchedAt).toMillis()
                    / (double) CHART_WINDOW.toMillis();
            int x = (int) Math.round(CHART_PADDING + ratio * (CHART_WIDTH - CHART_PADDING * 2));
            double priceRatio = price.subtract(minPrice).doubleValue()
                    / maxPrice.subtract(minPrice).doubleValue();
            int y = (int) Math.round(CHART_HEIGHT - CHART_PADDING - priceRatio * (CHART_HEIGHT - CHART_PADDING * 2));
            int index = (int) Math.round(ratio * (CHART_MAX_POINTS - 1));
            current.add(new ChartPoint(x, y, index));
            previousTime = fetchedAt;
        }
        if (!current.isEmpty()) {
            segments.add(List.copyOf(current));
        }
        return new ChartData(segments, minPrice, maxPrice, effectiveAnchor);
    }

    private record ChartPoint(int x, int y, int index) {
    }

    private record ChartData(List<List<ChartPoint>> points, BigDecimal minPrice, BigDecimal maxPrice, Instant anchorTime) {
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
