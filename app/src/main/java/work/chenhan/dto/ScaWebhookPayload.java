package work.chenhan.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

public class ScaWebhookPayload {

    @JsonProperty("sca_project_name")
    private String scaProjectName;

    @JsonProperty("sca_application_name")
    private String scaApplicationName;

    @JsonProperty("sca_branch")
    private String scaBranch; // 映射自应用版本

    @JsonProperty("sca_component_count")
    private Integer scaComponentCount;

    @JsonProperty("sca_vulnerability_count")
    private Integer scaVulnerabilityCount;

    @JsonProperty("sca_license_count")
    private Integer scaLicenseCount;

    @JsonProperty("sca_start_time")
    private String scaStartTime;

    @JsonProperty("sca_end_time")
    private String scaEndTime;

    @JsonProperty("sca_repo_address")
    private String scaRepoAddress;

    @JsonProperty("sca_task_id")
    private String scaTaskId;

    @JsonProperty("sca_app_id")
    private String scaAppId;

    /**
     * 邮件正文【】里的来源名，如 GitLab_V4。
     * 仅用于反查时确定 vcs/list 的 type 参数，不推送给下游。
     */
    @JsonIgnore
    private String scaSource;

    // Getters and Setters

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

    public Integer getScaComponentCount() {
        return scaComponentCount;
    }

    public void setScaComponentCount(Integer scaComponentCount) {
        this.scaComponentCount = scaComponentCount;
    }

    public Integer getScaVulnerabilityCount() {
        return scaVulnerabilityCount;
    }

    public void setScaVulnerabilityCount(Integer scaVulnerabilityCount) {
        this.scaVulnerabilityCount = scaVulnerabilityCount;
    }

    public Integer getScaLicenseCount() {
        return scaLicenseCount;
    }

    public void setScaLicenseCount(Integer scaLicenseCount) {
        this.scaLicenseCount = scaLicenseCount;
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

    public String getScaRepoAddress() {
        return scaRepoAddress;
    }

    public void setScaRepoAddress(String scaRepoAddress) {
        this.scaRepoAddress = scaRepoAddress;
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

    public String getScaSource() {
        return scaSource;
    }

    public void setScaSource(String scaSource) {
        this.scaSource = scaSource;
    }

    @Override
    public String toString() {
        return "ScaWebhookPayload{" +
                "scaProjectName='" + scaProjectName + '\'' +
                ", scaApplicationName='" + scaApplicationName + '\'' +
                ", scaBranch='" + scaBranch + '\'' +
                ", scaComponentCount=" + scaComponentCount +
                ", scaVulnerabilityCount=" + scaVulnerabilityCount +
                ", scaLicenseCount=" + scaLicenseCount +
                ", scaStartTime='" + scaStartTime + '\'' +
                ", scaEndTime='" + scaEndTime + '\'' +
                ", scaRepoAddress='" + scaRepoAddress + '\'' +
                ", scaTaskId='" + scaTaskId + '\'' +
                ", scaAppId='" + scaAppId + '\'' +
                ", scaSource='" + scaSource + '\'' +
                '}';
    }
}
