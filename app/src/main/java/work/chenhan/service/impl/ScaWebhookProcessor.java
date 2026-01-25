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

                // 1. 信息补全与决策
                boolean allowed = enrichmentService.enrich(payload);

                // 2. 记录决策结果
                saveProcessRecord(payload, allowed, bodyText);

                if (allowed) {
                    // 3. 发送 Webhook 并记录日志
                    broadcast(payload);
                } else {
                    log.info("Payload 被增强服务拦截: {}", payload.getScaProjectName());
                }
            } else {
                log.warn("未能从邮件正文解析出标准 SCA 报告，转为非标准处理模式。");
                ScaWebhookPayload fallbackPayload = new ScaWebhookPayload();
                fallbackPayload
                        .setScaProjectName("未知格式: " + (content.getSubject() != null ? content.getSubject() : "无标题"));

                // 记录为“不通过”或根据策略放行，此处建议记录并标记为 allowed=false (或 true，取决于用户希望如何看到原文)
                // 既然用户希望在记录中看到，我们记录下来
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

        // 这些字段目前无法从提供的样本中提取
        // 如果用户提供提取逻辑，可以设置默认值或保留为 null
        payload.setScaRepoAddress(null);
        payload.setScaTaskId(null);
        payload.setScaAppId(null);

        return payload;
    }

    private void broadcast(ScaWebhookPayload payload) {
        java.util.List<work.chenhan.entity.WebhookConfig> configs = webhookConfigService.getEnabledConfigs();
        if (configs.isEmpty()) {
            log.info("未配置任何启用的 Webhook，跳过推送。");
        } else {
            for (work.chenhan.entity.WebhookConfig config : configs) {
                if ("DOWN".equals(config.getLastStatus())) {
                    log.warn("Webhook {} 状态为 DOWN，跳过本次推送。", config.getUrl());
                    continue;
                }
                sendWebhook(payload, config.getUrl(), config.getName(), config.getId());
            }
        }
    }

    private void sendWebhook(ScaWebhookPayload payload, String targetUrl, String webhookName, Long webhookId) {
        String status = "FAILED";
        Integer statusCode = null;
        String responseBody = null;
        String errorMsg = null;

        try {
            org.springframework.http.ResponseEntity<String> response = restClient.post()
                    .uri(targetUrl)
                    .body(payload)
                    .retrieve()
                    .toEntity(String.class);

            statusCode = response.getStatusCode().value();
            status = response.getStatusCode().is2xxSuccessful() ? "SUCCESS" : "FAILED";
            responseBody = response.getBody();
            log.info("成功发送 Webhook 至 {}。状态码: {}", targetUrl, statusCode);

            if (webhookId != null) {
                webhookConfigService.updateStatus(webhookId, "UP", null);
            }
        } catch (org.springframework.web.client.RestClientResponseException e) {
            statusCode = e.getStatusCode().value();
            responseBody = e.getResponseBodyAsString();
            errorMsg = e.getStatusText();
            status = "FAILED";
            log.error("Webhook 发送失败，HTTP 错误: {} {}", statusCode, errorMsg);
            if (webhookId != null) {
                webhookConfigService.updateStatus(webhookId, "DOWN", "HTTP " + statusCode + ": " + errorMsg);
            }
        } catch (Exception e) {
            responseBody = e.getMessage();
            errorMsg = e.getMessage();
            status = "FAILED";
            log.error("发送 Webhook 至 {} 失败", targetUrl, e);
            if (webhookId != null) {
                webhookConfigService.updateStatus(webhookId, "DOWN", errorMsg);
            }
        } finally {
            // 如果响应体过长（>2048字符），截断以避免数据库错误
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

            // 新增字段
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
                // 重点：避免直接调用 part.getContent()，因为在有类加载冲突时它会触发 ClassCastException
                // 我们通过 MimeMultipart 构造函数直接解析内容对象
                MimeMultipart mimeMultipart;
                if (part instanceof MimeMessage message) {
                    mimeMultipart = (MimeMultipart) message.getContent();
                } else if (part instanceof BodyPart bodyPart) {
                    // 如果已经是 BodyPart 且是多部分，通常 getContent() 返回 MimeMultipart
                    // 如果 getContent() 仍然失败，可以通过输入流手动构造
                    Object content;
                    try {
                        content = part.getContent();
                    } catch (Exception e) {
                        log.warn("通过 getContent() 获取 MimeMultipart 失败，尝试通过输入流解析: {}", e.getMessage());
                        mimeMultipart = new MimeMultipart(part.getDataHandler().getDataSource());
                        content = mimeMultipart;
                    }

                    if (content instanceof MimeMultipart) {
                        mimeMultipart = (MimeMultipart) content;
                    } else {
                        log.warn("内容不是 MimeMultipart 类型: {}", content.getClass());
                        return content.toString();
                    }
                } else {
                    // 通用降级方案
                    mimeMultipart = new MimeMultipart(part.getDataHandler().getDataSource());
                }

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
