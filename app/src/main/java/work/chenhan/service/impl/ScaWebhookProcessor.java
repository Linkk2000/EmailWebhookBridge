package work.chenhan.service.impl;

import jakarta.mail.BodyPart;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
public class ScaWebhookProcessor implements EmailProcessor {

    private static final Logger log = LoggerFactory.getLogger(ScaWebhookProcessor.class);

    private final RestClient restClient;
    private final work.chenhan.service.ScaEnrichmentService enrichmentService;
    private final work.chenhan.repository.ScaProcessRecordRepository processRecordRepository;
    private final work.chenhan.repository.WebhookPushLogRepository pushLogRepository;
    private final work.chenhan.service.WebhookConfigService webhookConfigService;

    // 正则表达式模式
    // 【GitLab_V4】风险检测已完成 —— 方括号内是应用来源，反查时换算成 vcs/list 的 type 参数
    private static final Pattern SOURCE_PATTERN = Pattern.compile("【(.+?)】[^\\n]*?检测已完成");
    private static final Pattern INFO_PATTERN = Pattern.compile("项目名称：(.*?)，应用名称：(.*?)，应用版本：(.*)");
    private static final Pattern COMPONENT_PATTERN = Pattern.compile("共检测出(\\d+)个组件");
    private static final Pattern VULN_PATTERN = Pattern.compile("共检测出(\\d+)个漏洞");
    private static final Pattern LICENSE_PATTERN = Pattern.compile("共检测出(\\d+)个许可证");
    private static final Pattern START_TIME_PATTERN = Pattern.compile("检测开始时间：(.*)");
    private static final Pattern END_TIME_PATTERN = Pattern.compile("检测完成时间：(.*)");

    public ScaWebhookProcessor(RestClient.Builder restClientBuilder,
            work.chenhan.service.ScaEnrichmentService enrichmentService,
            work.chenhan.repository.ScaProcessRecordRepository processRecordRepository,
            work.chenhan.repository.WebhookPushLogRepository pushLogRepository,
            work.chenhan.service.WebhookConfigService webhookConfigService) {
        this.restClient = restClientBuilder.build();
        this.enrichmentService = enrichmentService;
        this.processRecordRepository = processRecordRepository;
        this.pushLogRepository = pushLogRepository;
        this.webhookConfigService = webhookConfigService;
    }

    @Override
    public void process(EmailContent content) {
        log.info("正在处理来自 {} 的邮件", content.getFrom());
        try {
            String bodyText = extractBody(content.getRawData());
            ScaReport report = parseReport(bodyText);

            if (report != null) {
                log.info("已解析 SCA 报告: {}", report);
                ScaWebhookPayload payload = mapToPayload(report);
                boolean allowed = enrichmentService.enrich(payload);
                saveProcessRecord(payload, allowed, bodyText);

                if (allowed) {
                    broadcast(payload);
                } else {
                    log.info("Payload 被增强服务拦截: {}", payload.getScaProjectName());
                }
            } else {
                log.warn("未能从邮件正文解析出标准 SCA 报告，转为非标准处理模式。");
                ScaWebhookPayload fallbackPayload = new ScaWebhookPayload();
                fallbackPayload
                        .setScaProjectName("未知格式: " + (content.getSubject() != null ? content.getSubject() : "无标题"));
                saveProcessRecord(fallbackPayload, false, bodyText);
            }
        } catch (Exception e) {
            log.error("处理 Webhook 邮件时出错", e);
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
        // 来源仅供反查 type 使用，不推送给下游；taskId / appId 由 ScaEnrichmentService 补全
        payload.setScaSource(report.getSource());
        return payload;
    }

    private void broadcast(ScaWebhookPayload payload) {
        java.util.List<work.chenhan.entity.WebhookConfig> configs = webhookConfigService.getEnabledConfigs();
        for (work.chenhan.entity.WebhookConfig config : configs) {
            if ("DOWN".equals(config.getLastStatus()))
                continue;
            sendWebhook(payload, config.getUrl(), config.getName(), config.getId());
        }
    }

    private void sendWebhook(ScaWebhookPayload payload, String targetUrl, String webhookName, Long webhookId) {
        String status = "FAILED";
        Integer statusCode = null;
        String responseBody = null;
        try {
            org.springframework.http.ResponseEntity<String> response = restClient.post()
                    .uri(targetUrl)
                    .body(payload)
                    .retrieve()
                    .toEntity(String.class);
            statusCode = response.getStatusCode().value();
            status = response.getStatusCode().is2xxSuccessful() ? "SUCCESS" : "FAILED";
            responseBody = response.getBody();
            if (webhookId != null)
                webhookConfigService.updateStatus(webhookId, "UP", null);
        } catch (Exception e) {
            responseBody = e.getMessage();
            status = "FAILED";
            log.error("发送 Webhook 至 {} 失败", targetUrl, e);
            if (webhookId != null)
                webhookConfigService.updateStatus(webhookId, "DOWN", e.getMessage());
        } finally {
            if (responseBody != null && responseBody.length() > 2048) {
                responseBody = responseBody.substring(0, 2048);
            }
            savePushLog(payload, status, statusCode, responseBody, targetUrl, webhookName);
        }
    }

    private void saveProcessRecord(ScaWebhookPayload payload, boolean allowed, String rawContent) {
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
            record.setScaRawContent(rawContent);
            processRecordRepository.save(record);
        } catch (Exception e) {
            log.error("保存处理记录失败", e);
        }
    }

    private void savePushLog(ScaWebhookPayload payload, String status, Integer statusCode, String responseBody,
            String url, String webhookName) {
        try {
            work.chenhan.entity.WebhookPushLog log = new work.chenhan.entity.WebhookPushLog();
            log.setScaProjectName(payload.getScaProjectName());
            log.setScaApplicationName(payload.getScaApplicationName());
            log.setScaBranch(payload.getScaBranch());
            log.setScaTaskId(payload.getScaTaskId());
            log.setScaAppId(payload.getScaAppId());
            log.setScaStartTime(payload.getScaStartTime());
            log.setScaEndTime(payload.getScaEndTime());
            log.setStatus(status);
            log.setHttpStatusCode(statusCode);
            log.setResponseBody(responseBody);
            log.setWebhookUrl(url);
            log.setWebhookName(webhookName);
            pushLogRepository.save(log);
        } catch (Exception e) {
            log.error("保存推送日志失败", e);
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
        Matcher sourceMatcher = SOURCE_PATTERN.matcher(text);
        if (sourceMatcher.find())
            report.setSource(sourceMatcher.group(1).trim());
        Matcher compMatcher = COMPONENT_PATTERN.matcher(text);
        if (compMatcher.find())
            report.setComponentCount(Integer.parseInt(compMatcher.group(1)));
        Matcher vulnMatcher = VULN_PATTERN.matcher(text);
        if (vulnMatcher.find())
            report.setVulnerabilityCount(Integer.parseInt(vulnMatcher.group(1)));
        Matcher licenseMatcher = LICENSE_PATTERN.matcher(text);
        if (licenseMatcher.find())
            report.setLicenseCount(Integer.parseInt(licenseMatcher.group(1)));
        Matcher startMatcher = START_TIME_PATTERN.matcher(text);
        if (startMatcher.find())
            report.setStartTime(startMatcher.group(1).trim());
        Matcher endMatcher = END_TIME_PATTERN.matcher(text);
        if (endMatcher.find())
            report.setEndTime(endMatcher.group(1).trim());
        return found ? report : null;
    }

    private String extractBody(byte[] rawData) throws MessagingException, IOException {
        Session session = Session.getDefaultInstance(new Properties());
        MimeMessage message = new MimeMessage(session, new ByteArrayInputStream(rawData));
        return getTextFromMessage(message);
    }

    private String getTextFromMessage(jakarta.mail.Part part) throws MessagingException, IOException {
        if (part.isMimeType("text/plain")) {
            try (java.io.InputStream is = part.getInputStream()) {
                return new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            }
        } else if (part.isMimeType("multipart/*")) {
            try {
                // 彻底防御方案：永远不要调用 part.getContent()，
                // 因为它会触发 DataContentHandler 查找，而在双版本共存时极易抛出 ClassCastException。
                // 我们直接通过数据源构造 MimeMultipart 对象。
                MimeMultipart mimeMultipart = new MimeMultipart(part.getDataHandler().getDataSource());
                StringBuilder result = new StringBuilder();
                for (int i = 0; i < mimeMultipart.getCount(); i++) {
                    BodyPart bodyPart = mimeMultipart.getBodyPart(i);
                    result.append(getTextFromMessage(bodyPart));
                }
                return result.toString();
            } catch (Exception e) {
                log.error("手动解析多部分内容失败", e);
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
