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
    @Column(name = "push_status")
    private String pushStatus; // 例如 "SUCCESS", "FAILED"

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

    public String getPushStatus() {
        return pushStatus;
    }

    public void setPushStatus(String pushStatus) {
        this.pushStatus = pushStatus;
    }

    public LocalDateTime getPushedAt() {
        return pushedAt;
    }

    public void setPushedAt(LocalDateTime pushedAt) {
        this.pushedAt = pushedAt;
    }
}
