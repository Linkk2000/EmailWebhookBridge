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
    private final work.chenhan.service.ScaEnrichmentService enrichmentService;
    private final work.chenhan.repository.ScaProcessRecordRepository processRecordRepository;
    private final work.chenhan.repository.WebhookPushLogRepository pushLogRepository;

    // 正则表达式模式
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

    public ScaWebhookProcessor(@Value("${sca.webhook.url}") String webhookUrl,
            RestClient.Builder restClientBuilder,
            work.chenhan.service.ScaEnrichmentService enrichmentService,
            work.chenhan.repository.ScaProcessRecordRepository processRecordRepository,
            work.chenhan.repository.WebhookPushLogRepository pushLogRepository) {
        this.webhookUrl = webhookUrl;
        this.restClient = restClientBuilder.build();
        this.enrichmentService = enrichmentService;
        this.processRecordRepository = processRecordRepository;
        this.pushLogRepository = pushLogRepository;
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

                // 1. Enrichment and Decision
                boolean allowed = enrichmentService.enrich(payload);

                // 2. Record Decision
                saveProcessRecord(payload, allowed);

                if (allowed) {
                    // 3. Send Webhook and Log result
                    sendWebhook(payload);
                } else {
                    log.info("Payload blocked by enrichment service: {}", payload.getScaProjectName());
                }
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

        // 这些字段目前无法从提供的样本中提取
        // 如果用户提供提取逻辑，可以设置默认值或保留为 null
        payload.setScaRepoAddress(null);
        payload.setScaTaskId(null);
        payload.setScaAppId(null);

        return payload;
    }

    private void sendWebhook(ScaWebhookPayload payload) {
        String status = "FAILED";
        Integer statusCode = null;
        String responseBody = null;

        try {
            org.springframework.http.ResponseEntity<Void> response = restClient.post()
                    .uri(webhookUrl)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();

            statusCode = response.getStatusCode().value();
            status = response.getStatusCode().is2xxSuccessful() ? "SUCCESS" : "FAILED";
            log.info("Successfully sent webhook to {}. Status: {}", webhookUrl, statusCode);
        } catch (org.springframework.web.client.RestClientResponseException e) {
            statusCode = e.getStatusCode().value();
            responseBody = e.getResponseBodyAsString();
            status = "FAILED";
            log.error("Webhook failed with HTTP error: {} {}", statusCode, e.getStatusText());
        } catch (Exception e) {
            responseBody = e.getMessage();
            status = "FAILED";
            log.error("Failed to send webhook to {}", webhookUrl, e);
        } finally {
            // Truncate response body if too long
            if (responseBody != null && responseBody.length() > 2048) {
                responseBody = responseBody.substring(0, 2048);
            }
            savePushLog(payload, status, statusCode, responseBody, webhookUrl);
        }
    }

    private void saveProcessRecord(ScaWebhookPayload payload, boolean allowed) {
        try {
            work.chenhan.entity.ScaProcessRecord record = new work.chenhan.entity.ScaProcessRecord();
            record.setScaProjectName(payload.getScaProjectName());
            record.setScaApplicationName(payload.getScaApplicationName());
            record.setScaBranch(payload.getScaBranch());
            record.setScaTaskId(payload.getScaTaskId());
            record.setScaAppId(payload.getScaAppId());
            record.setScaStartTime(payload.getScaStartTime());
            record.setScaEndTime(payload.getScaEndTime());
            record.setIsAllowed(allowed);
            processRecordRepository.save(record);
        } catch (Exception e) {
            log.error("Failed to save process record", e);
        }
    }

    private void savePushLog(ScaWebhookPayload payload, String status, Integer statusCode, String responseBody,
            String url) {
        try {
            work.chenhan.entity.WebhookPushLog log = new work.chenhan.entity.WebhookPushLog();
            log.setScaProjectName(payload.getScaProjectName());
            log.setScaApplicationName(payload.getScaApplicationName());
            log.setScaBranch(payload.getScaBranch());
            log.setScaTaskId(payload.getScaTaskId());
            log.setScaAppId(payload.getScaAppId());
            log.setScaStartTime(payload.getScaStartTime());
            log.setScaEndTime(payload.getScaEndTime());

            // New Fields
            log.setStatus(status);
            log.setHttpStatusCode(statusCode);
            log.setResponseBody(responseBody);
            log.setWebhookUrl(url);

            pushLogRepository.save(log);
        } catch (Exception e) {
            log.error("Failed to save push log", e);
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
            // 手动读取输入流以避免 DataHandler 冲突
            try (java.io.InputStream is = part.getInputStream()) {
                return new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                // 注意：如果缺少字符集参数，UTF_8 是安全的猜测。
                // 本质上我们应该解析 Content-Type 头来获取字符集，但对于此特定任务遵循 KISS 原则。
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
            // 目前将 HTML 视为字符串，但如果正则失败可能需要清理标签。
            // 鉴于正则是简单的文本匹配，如果格式严格匹配，它们在 HTML 源码上也可能工作。
            // 但如果可用，更简单的方法是优先使用纯文本多分部。
            try (java.io.InputStream is = part.getInputStream()) {
                return new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            }
        }
        return "";
    }
}
