package work.chenhan.service.impl;

import jakarta.mail.BodyPart;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import work.chenhan.dto.EmailContent;
import work.chenhan.dto.ScaReport;
import work.chenhan.dto.ScaWebhookPayload;
import work.chenhan.service.EmailProcessor;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Primary
@ConditionalOnProperty(name = "sca.webhook.url")
public class ScaWebhookProcessor implements EmailProcessor {

    private static final Logger log = LoggerFactory.getLogger(ScaWebhookProcessor.class);

    private final String webhookUrl;
    private final RestClient restClient;

    // Regex Patterns
    // 项目名称：测试用，应用名称：管理系统企业前端，应用版本：master
    private static final Pattern INFO_PATTERN = Pattern.compile("项目名称：(.*?)，应用名称：(.*?)，应用版本：(.*)");
    // 共检测出564个组件
    private static final Pattern COMPONENT_PATTERN = Pattern.compile("共检测出(\\d+)个组件");
    // 共检测出48个漏洞
    private static final Pattern VULN_PATTERN = Pattern.compile("共检测出(\\d+)个漏洞");
    // 共检测出9个许可证
    private static final Pattern LICENSE_PATTERN = Pattern.compile("共检测出(\\d+)个许可证");
    // 检测开始时间：2026-01-22 14:35:45
    private static final Pattern START_TIME_PATTERN = Pattern.compile("检测开始时间：(.*)");
    // 检测完成时间：2026-01-22 14:35:57
    private static final Pattern END_TIME_PATTERN = Pattern.compile("检测完成时间：(.*)");

    public ScaWebhookProcessor(@Value("${sca.webhook.url}") String webhookUrl, RestClient.Builder restClientBuilder) {
        this.webhookUrl = webhookUrl;
        this.restClient = restClientBuilder.build();
    }

    @Override
    public void process(EmailContent content) {
        log.info("Processing email from {}", content.getFrom());
        try {
            String bodyText = extractBody(content.getRawData());
            ScaReport report = parseReport(bodyText);

            if (report != null) {
                log.info("Parsed SCA Report: {}", report);
                ScaWebhookPayload payload = mapToPayload(report);
                sendWebhook(payload);
            } else {
                log.warn("Failed to parse SCA report from email body.");
            }
        } catch (Exception e) {
            log.error("Error processing email for webhook", e);
        }
    }

    private ScaWebhookPayload mapToPayload(ScaReport report) {
        ScaWebhookPayload payload = new ScaWebhookPayload();
        payload.setScaProjectName(report.getProjectName());
        payload.setScaApplicationName(report.getApplicationName());
        payload.setScaBranch(report.getApplicationVersion());
        payload.setScaComponentCount(report.getComponentCount());
        payload.setScaVulnerabilityCount(report.getVulnerabilityCount());
        payload.setScaLicenseCount(report.getLicenseCount());
        payload.setScaStartTime(report.getStartTime());
        payload.setScaEndTime(report.getEndTime());

        // These fields are currently not extractable from the provided sample
        // Leaving them null or setting defaults if user provides extraction logic
        payload.setScaRepoAddress(null);
        payload.setScaTaskId(null);
        payload.setScaAppId(null);

        return payload;
    }

    private void sendWebhook(ScaWebhookPayload payload) {
        try {
            restClient.post()
                    .uri(webhookUrl)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Successfully sent webhook to {}", webhookUrl);
        } catch (Exception e) {
            log.error("Failed to send webhook to {}", webhookUrl, e);
        }
    }

    ScaReport parseReport(String text) {
        ScaReport report = new ScaReport();
        boolean found = false;

        Matcher infoMatcher = INFO_PATTERN.matcher(text);
        if (infoMatcher.find()) {
            report.setProjectName(infoMatcher.group(1).trim());
            report.setApplicationName(infoMatcher.group(2).trim());
            report.setApplicationVersion(infoMatcher.group(3).trim());
            found = true;
        }

        Matcher compMatcher = COMPONENT_PATTERN.matcher(text);
        if (compMatcher.find()) {
            report.setComponentCount(Integer.parseInt(compMatcher.group(1)));
        }

        Matcher vulnMatcher = VULN_PATTERN.matcher(text);
        if (vulnMatcher.find()) {
            report.setVulnerabilityCount(Integer.parseInt(vulnMatcher.group(1)));
        }

        Matcher licenseMatcher = LICENSE_PATTERN.matcher(text);
        if (licenseMatcher.find()) {
            report.setLicenseCount(Integer.parseInt(licenseMatcher.group(1)));
        }

        Matcher startMatcher = START_TIME_PATTERN.matcher(text);
        if (startMatcher.find()) {
            report.setStartTime(startMatcher.group(1).trim());
        }

        Matcher endMatcher = END_TIME_PATTERN.matcher(text);
        if (endMatcher.find()) {
            report.setEndTime(endMatcher.group(1).trim());
        }

        return found ? report : null;
    }

    private String extractBody(byte[] rawData) throws MessagingException, IOException {
        Session session = Session.getDefaultInstance(new Properties());
        MimeMessage message = new MimeMessage(session, new ByteArrayInputStream(rawData));
        return getTextFromMessage(message);
    }

    private String getTextFromMessage(jakarta.mail.Part part) throws MessagingException, IOException {
        if (part.isMimeType("text/plain")) {
            // Manually read input stream to avoid DataHandler conflicts
            try (java.io.InputStream is = part.getInputStream()) {
                return new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                // Note: StandardCharsets.UTF_8 is safe guess if charset param missing,
                // essentially we should parse Content-Type header for charset but for this
                // specific task KISS.
            }
        } else if (part.isMimeType("multipart/*")) {
            try {
                Object content = part.getContent();
                if (content instanceof MimeMultipart) {
                    MimeMultipart mimeMultipart = (MimeMultipart) content;
                    StringBuilder result = new StringBuilder();
                    for (int i = 0; i < mimeMultipart.getCount(); i++) {
                        BodyPart bodyPart = mimeMultipart.getBodyPart(i);
                        result.append(getTextFromMessage(bodyPart));
                    }
                    return result.toString();
                } else {
                    log.warn("Multipart content is not instance of MimeMultipart: {}", content.getClass());
                    return content.toString();
                }
            } catch (Exception e) {
                log.error("Failed to parse multipart content", e);
                return "";
            }
        } else if (part.isMimeType("text/html")) {
            try (java.io.InputStream is = part.getInputStream()) {
                return new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            }
        }
        return "";
    }
}
