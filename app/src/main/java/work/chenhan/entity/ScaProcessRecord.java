package work.chenhan.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "sca_process_record")
public class ScaProcessRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 对应字段：scaProjectName, scaApplicationName, scaBranch
    @Column(name = "sca_project_name")
    private String scaProjectName;

    @Column(name = "sca_application_name")
    private String scaApplicationName;

    @Column(name = "sca_branch")
    private String scaBranch;

    // 对应字段：scaTaskId, scaAppId
    @Column(name = "sca_task_id")
    private String scaTaskId;

    @Column(name = "sca_app_id")
    private String scaAppId;

    // 对应字段：scaStartTime, scaEndTime
    @Column(name = "sca_start_time")
    private String scaStartTime;

    @Column(name = "sca_end_time")
    private String scaEndTime;

    // 应用特定字段
    @Column(name = "received_at")
    private LocalDateTime receivedAt;

    @Column(name = "is_allowed")
    private Boolean isAllowed;

    @PrePersist
    public void prePersist() {
        if (receivedAt == null) {
            receivedAt = LocalDateTime.now();
        }
    }

    // Getters and Setters
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

    public LocalDateTime getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(LocalDateTime receivedAt) {
        this.receivedAt = receivedAt;
    }

    public Boolean getIsAllowed() {
        return isAllowed;
    }

    public void setIsAllowed(Boolean allowed) {
        isAllowed = allowed;
    }
}
