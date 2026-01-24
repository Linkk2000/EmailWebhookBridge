package work.chenhan.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "webhook_push_log")
public class WebhookPushLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 基础信息副本，用于自包含日志
    @Column(name = "sca_project_name")
    private String scaProjectName;

    @Column(name = "sca_application_name")
    private String scaApplicationName;

    @Column(name = "sca_branch")
    private String scaBranch;

    @Column(name = "sca_task_id")
    private String scaTaskId;

    @Column(name = "sca_app_id")
    private String scaAppId;

    @Column(name = "sca_start_time")
    private String scaStartTime;

    @Column(name = "sca_end_time")
    private String scaEndTime;

    // 推送特定字段
    // 状态: SUCCESS / FAILED
    @Column(name = "status")
    private String status;

    @Column(name = "http_status_code")
    private Integer httpStatusCode;

    // 记录响应体或错误信息
    @Column(name = "response_body", length = 2048)
    private String responseBody;

    // 记录目标 Webhook 地址
    @Column(name = "webhook_url")
    private String webhookUrl;

    // 记录 Webhook 名称快照
    @Column(name = "webhook_name")
    private String webhookName;

    @Column(name = "pushed_at")
    private LocalDateTime pushedAt;

    @PrePersist
    public void prePersist() {
        if (pushedAt == null) {
            pushedAt = LocalDateTime.now();
        }
    }

    // Getters Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getScaProjectName() {
        return scaProjectName;
    }

    public void setScaProjectName(String scaProjectName) {
        this.scaProjectName = scaProjectName;
    }

    public String getScaApplicationName() {
        return scaApplicationName;
    }

    public void setScaApplicationName(String scaApplicationName) {
        this.scaApplicationName = scaApplicationName;
    }

    public String getScaBranch() {
        return scaBranch;
    }

    public void setScaBranch(String scaBranch) {
        this.scaBranch = scaBranch;
    }

    public String getScaTaskId() {
        return scaTaskId;
    }

    public void setScaTaskId(String scaTaskId) {
        this.scaTaskId = scaTaskId;
    }

    public String getScaAppId() {
        return scaAppId;
    }

    public void setScaAppId(String scaAppId) {
        this.scaAppId = scaAppId;
    }

    public String getScaStartTime() {
        return scaStartTime;
    }

    public void setScaStartTime(String scaStartTime) {
        this.scaStartTime = scaStartTime;
    }

    public String getScaEndTime() {
        return scaEndTime;
    }

    public void setScaEndTime(String scaEndTime) {
        this.scaEndTime = scaEndTime;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getHttpStatusCode() {
        return httpStatusCode;
    }

    public void setHttpStatusCode(Integer httpStatusCode) {
        this.httpStatusCode = httpStatusCode;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public void setResponseBody(String responseBody) {
        this.responseBody = responseBody;
    }

    public String getWebhookUrl() {
        return webhookUrl;
    }

    public void setWebhookUrl(String webhookUrl) {
        this.webhookUrl = webhookUrl;
    }

    public String getWebhookName() {
        return webhookName;
    }

    public void setWebhookName(String webhookName) {
        this.webhookName = webhookName;
    }

    public LocalDateTime getPushedAt() {
        return pushedAt;
    }

    public void setPushedAt(LocalDateTime pushedAt) {
        this.pushedAt = pushedAt;
    }
}
