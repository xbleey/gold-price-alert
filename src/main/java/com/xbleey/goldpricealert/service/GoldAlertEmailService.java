package com.xbleey.goldpricealert.service;

import com.xbleey.goldpricealert.config.GoldAlertMailProperties;
import com.xbleey.goldpricealert.enums.GoldAlertLevel;
import com.xbleey.goldpricealert.model.GoldApiResponse;
import com.xbleey.goldpricealert.model.GoldPriceSnapshot;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class GoldAlertEmailService implements GoldAlertNotifier {

    private static final Logger log = LoggerFactory.getLogger(GoldAlertEmailService.class);
    private static final DateTimeFormatter REPORT_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Duration CHART_WINDOW = Duration.ofMinutes(20);
    private static final Duration CHART_EXPECTED_INTERVAL = Duration.ofSeconds(20);
    private static final Duration CHART_GAP_THRESHOLD = CHART_EXPECTED_INTERVAL.multipliedBy(2);
    private static final int CHART_MAX_POINTS = 60;
    private static final int CHART_WIDTH = 900;
    private static final int CHART_HEIGHT = 240;
    private static final int CHART_PADDING = 36;
    private static final int CHART_AXIS_LABEL_HEIGHT = 60;
    private static final int CHART_Y_TICK_COUNT = 4;

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
        EmailContent content = buildHtmlBodyContent(message);
        sendEmail(targets, buildSubject(message), buildPlainText(message), content);
    }

    public void notifyThresholdAlert(GoldThresholdAlertMessage message) {
        if (message == null) {
            return;
        }
        EmailTargets targets = resolveEmailTargets();
        if (targets == null) {
            return;
        }
        EmailContent content = buildThresholdHtmlBodyContent(message);
        sendEmail(targets, buildThresholdSubject(message), buildThresholdPlainText(message), content);
    }

    public void notifyApiError(GoldApiErrorMessage message) {
        if (message == null) {
            return;
        }
        EmailTargets targets = resolveEmailTargets();
        if (targets == null) {
            return;
        }
        EmailContent content = buildApiErrorHtmlBodyContent(message);
        sendEmail(targets, buildApiErrorSubject(), buildApiErrorPlainText(message), content);
    }

    public void notifyApiResume(GoldApiResumeMessage message) {
        if (message == null) {
            return;
        }
        EmailTargets targets = resolveEmailTargets();
        if (targets == null) {
            return;
        }
        EmailContent content = buildApiResumeHtmlBodyContent(message);
        sendEmail(targets, buildApiResumeSubject(), buildApiResumePlainText(message), content);
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
        return buildHtmlBodyContent(message).html();
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

    private void sendEmail(EmailTargets targets, String subject, String plainText, EmailContent content) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            boolean hasInline = content != null && !content.inlineImages().isEmpty();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, hasInline, StandardCharsets.UTF_8.name());
            helper.setFrom(targets.sender());
            helper.setTo(targets.recipients().toArray(new String[0]));
            helper.setSubject(subject);
            String htmlBody = content == null ? "" : content.html();
            helper.setText(plainText, htmlBody);
            if (hasInline) {
                for (InlineImage inlineImage : content.inlineImages()) {
                    helper.addInline(inlineImage.contentId(),
                            new ByteArrayResource(inlineImage.data()),
                            inlineImage.contentType());
                }
            }
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

    private EmailContent buildHtmlBodyContent(GoldAlertMessage message) {
        StringBuilder builder = new StringBuilder();
        List<InlineImage> inlineImages = new ArrayList<>();
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
        HtmlSection chartSection = buildHtmlChartSection(message.recentSnapshots(), message.alertTime(), zone);
        builder.append(chartSection.html());
        inlineImages.addAll(chartSection.inlineImages());
        builder.append("<h3>Recent GoldPriceSnapshot (last ")
                .append(message.recentSnapshots() == null ? 0 : message.recentSnapshots().size())
                .append(")</h3>");
        appendHtmlTable(builder, message.recentSnapshots(), zone, updatedAtZone);
        builder.append("</body></html>");
        return new EmailContent(builder.toString(), inlineImages);
    }

    private EmailContent buildThresholdHtmlBodyContent(GoldThresholdAlertMessage message) {
        StringBuilder builder = new StringBuilder();
        List<InlineImage> inlineImages = new ArrayList<>();
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
        HtmlSection chartSection = buildHtmlChartSection(message == null ? null : message.recentSnapshots(),
                message == null ? null : message.alertTime(), zone);
        builder.append(chartSection.html());
        inlineImages.addAll(chartSection.inlineImages());
        builder.append("<h3>Recent GoldPriceSnapshot (last ")
                .append(message == null || message.recentSnapshots() == null ? 0 : message.recentSnapshots().size())
                .append(")</h3>");
        appendHtmlTable(builder, message == null ? null : message.recentSnapshots(), zone, updatedAtZone);
        builder.append("</body></html>");
        return new EmailContent(builder.toString(), inlineImages);
    }

    private EmailContent buildApiErrorHtmlBodyContent(GoldApiErrorMessage message) {
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
        return new EmailContent(builder.toString(), List.of());
    }

    private EmailContent buildApiResumeHtmlBodyContent(GoldApiResumeMessage message) {
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
        return new EmailContent(builder.toString(), List.of());
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

    private HtmlSection buildHtmlChartSection(List<GoldPriceSnapshot> recentSnapshots, Instant anchorTime, ZoneId zone) {
        StringBuilder builder = new StringBuilder();
        builder.append("<h3>Gold Price Chart (20m)</h3>");
        ChartRenderResult chartResult = buildHtmlPriceChart(recentSnapshots, anchorTime, zone);
        builder.append(chartResult.html());
        return new HtmlSection(builder.toString(), chartResult.inlineImages());
    }

    private ChartRenderResult buildHtmlPriceChart(List<GoldPriceSnapshot> recentSnapshots, Instant anchorTime, ZoneId zone) {
        ChartData chartData = buildChartData(recentSnapshots, anchorTime);
        if (chartData.points().isEmpty()) {
            return new ChartRenderResult("<p>chart=无可用数据</p>", List.of());
        }
        byte[] imageBytes = renderChartImage(chartData, zone);
        if (imageBytes.length == 0) {
            return new ChartRenderResult("<p>chart=无可用数据</p>", List.of());
        }
        String contentId = "chart-" + UUID.randomUUID();
        StringBuilder builder = new StringBuilder();
        builder.append("<img src=\"cid:")
                .append(contentId)
                .append("\" width=\"")
                .append(CHART_WIDTH)
                .append("\" height=\"")
                .append(CHART_HEIGHT + CHART_AXIS_LABEL_HEIGHT)
                .append("\" alt=\"Gold price chart\" style=\"display:block;border:0;\"/>");
        InlineImage inlineImage = new InlineImage(contentId, imageBytes, "image/png");
        return new ChartRenderResult(builder.toString(), List.of(inlineImage));
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
            return new ChartData(List.of(), BigDecimal.ZERO, BigDecimal.ZERO, anchorTime, List.of());
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
            return new ChartData(List.of(), BigDecimal.ZERO, BigDecimal.ZERO, effectiveAnchor, List.of());
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
            return new ChartData(List.of(), BigDecimal.ZERO, BigDecimal.ZERO, effectiveAnchor, List.of());
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
        List<BigDecimal> yTicks = buildChartTicks(minPrice, maxPrice);
        return new ChartData(segments, minPrice, maxPrice, effectiveAnchor, yTicks);
    }

    private List<BigDecimal> buildChartTicks(BigDecimal minPrice, BigDecimal maxPrice) {
        if (minPrice == null || maxPrice == null) {
            return List.of();
        }
        BigDecimal range = maxPrice.subtract(minPrice);
        if (range.compareTo(BigDecimal.ZERO) <= 0) {
            return List.of();
        }
        List<BigDecimal> ticks = new ArrayList<>();
        for (int i = 1; i <= CHART_Y_TICK_COUNT; i++) {
            BigDecimal ratio = BigDecimal.valueOf(i).divide(BigDecimal.valueOf(CHART_Y_TICK_COUNT + 1L), 8, RoundingMode.HALF_UP);
            ticks.add(minPrice.add(range.multiply(ratio)));
        }
        return List.copyOf(ticks);
    }

    private byte[] renderChartImage(ChartData chartData, ZoneId zone) {
        int imageHeight = CHART_HEIGHT + CHART_AXIS_LABEL_HEIGHT;
        BufferedImage image = new BufferedImage(CHART_WIDTH, imageHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, CHART_WIDTH, imageHeight);

            int chartWidth = CHART_WIDTH - CHART_PADDING * 2;
            int chartHeight = CHART_HEIGHT - CHART_PADDING * 2;
            graphics.setColor(new Color(0xFA, 0xFA, 0xFA));
            graphics.fillRect(CHART_PADDING, CHART_PADDING, chartWidth, chartHeight);
            graphics.setColor(new Color(0xE0, 0xE0, 0xE0));
            graphics.drawRect(CHART_PADDING, CHART_PADDING, chartWidth, chartHeight);

            drawChartYAxisTicks(graphics, chartData);
            drawChartXAxisLabels(graphics);
            drawChartHighLowLabels(graphics, chartData, zone);
            drawChartSeries(graphics, chartData);
        } finally {
            graphics.dispose();
        }
        return writePng(image);
    }

    private void drawChartYAxisTicks(Graphics2D graphics, ChartData chartData) {
        if (chartData.yTicks().isEmpty()) {
            return;
        }
        graphics.setFont(new Font("SansSerif", Font.PLAIN, 14));
        FontMetrics metrics = graphics.getFontMetrics();
        int chartHeight = CHART_HEIGHT - CHART_PADDING * 2;
        for (BigDecimal tick : chartData.yTicks()) {
            double ratio = tick.subtract(chartData.minPrice()).doubleValue()
                    / chartData.maxPrice().subtract(chartData.minPrice()).doubleValue();
            int y = (int) Math.round(CHART_HEIGHT - CHART_PADDING - ratio * chartHeight);
            graphics.setColor(new Color(0xEEEEEE));
            graphics.drawLine(CHART_PADDING, y, CHART_WIDTH - CHART_PADDING, y);
            String label = formatPrice(tick);
            int labelWidth = metrics.stringWidth(label);
            graphics.setColor(new Color(0x616161));
            graphics.drawString(label, CHART_PADDING - 8 - labelWidth, y + metrics.getAscent() / 2);
        }
    }

    private void drawChartXAxisLabels(Graphics2D graphics) {
        graphics.setFont(new Font("SansSerif", Font.PLAIN, 14));
        int baseY = CHART_HEIGHT + 30;
        int[] minutes = new int[]{-20, -15, -10, -5, 0};
        for (int minute : minutes) {
            double ratio = (minute + 20) / 20.0;
            int x = (int) Math.round(CHART_PADDING + ratio * (CHART_WIDTH - CHART_PADDING * 2));
            graphics.setColor(new Color(0x616161));
            String label = minute + "m";
            FontMetrics metrics = graphics.getFontMetrics();
            int labelWidth = metrics.stringWidth(label);
            graphics.drawString(label, x - labelWidth / 2, baseY);
        }
    }

    private void drawChartHighLowLabels(Graphics2D graphics, ChartData chartData, ZoneId zone) {
        graphics.setFont(new Font("SansSerif", Font.PLAIN, 14));
        graphics.setColor(new Color(0x616161));
        String highLabel = "High " + formatPrice(chartData.maxPrice());
        String lowLabel = "Low " + formatPrice(chartData.minPrice());
        graphics.drawString(highLabel, CHART_PADDING, CHART_PADDING - 10);
        graphics.drawString(lowLabel, CHART_PADDING, CHART_HEIGHT - CHART_PADDING + 20);
        if (chartData.anchorTime() != null) {
            String anchor = "As of " + formatInstant(chartData.anchorTime(), zone);
            FontMetrics metrics = graphics.getFontMetrics();
            int textWidth = metrics.stringWidth(anchor);
            graphics.drawString(anchor, CHART_WIDTH - CHART_PADDING - textWidth, CHART_PADDING - 10);
        }
    }

    private void drawChartSeries(Graphics2D graphics, ChartData chartData) {
        graphics.setColor(new Color(0x1976D2));
        graphics.setStroke(new BasicStroke(2f));
        for (List<ChartPoint> segment : chartData.points()) {
            if (segment.size() < 2) {
                if (!segment.isEmpty()) {
                    ChartPoint point = segment.getFirst();
                    graphics.fillOval(point.x() - 3, point.y() - 3, 6, 6);
                }
                continue;
            }
            ChartPoint previous = null;
            for (ChartPoint point : segment) {
                if (previous != null) {
                    graphics.drawLine(previous.x(), previous.y(), point.x(), point.y());
                }
                previous = point;
            }
        }
    }

    private byte[] writePng(BufferedImage image) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", outputStream);
            return outputStream.toByteArray();
        } catch (Exception ex) {
            log.warn("Failed to render chart image", ex);
            return new byte[0];
        }
    }

    private record ChartPoint(int x, int y, int index) {
    }

    private record ChartData(
            List<List<ChartPoint>> points,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            Instant anchorTime,
            List<BigDecimal> yTicks
    ) {
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

    private record InlineImage(String contentId, byte[] data, String contentType) {
    }

    private record EmailContent(String html, List<InlineImage> inlineImages) {
    }

    private record ChartRenderResult(String html, List<InlineImage> inlineImages) {
    }

    private record HtmlSection(String html, List<InlineImage> inlineImages) {
    }

}
