package work.chenhan.dto;

import java.time.LocalDateTime;
import java.util.List;

public class EmailContent {
    private String from;
    private List<String> recipients;
    private byte[] rawData;
    private LocalDateTime receivedAt;

    public EmailContent() {
    }

    public EmailContent(String from, List<String> recipients, byte[] rawData, LocalDateTime receivedAt) {
        this.from = from;
        this.recipients = recipients;
        this.rawData = rawData;
        this.receivedAt = receivedAt;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public List<String> getRecipients() {
        return recipients;
    }

    public void setRecipients(List<String> recipients) {
        this.recipients = recipients;
    }

    public byte[] getRawData() {
        return rawData;
    }

    public void setRawData(byte[] rawData) {
        this.rawData = rawData;
    }

    public LocalDateTime getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(LocalDateTime receivedAt) {
        this.receivedAt = receivedAt;
    }
}
