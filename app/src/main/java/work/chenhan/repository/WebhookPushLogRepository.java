package work.chenhan.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import work.chenhan.entity.WebhookPushLog;

public interface WebhookPushLogRepository
        extends JpaRepository<WebhookPushLog, Long>, JpaSpecificationExecutor<WebhookPushLog> {

    java.util.List<work.chenhan.entity.WebhookPushLog> findByWebhookUrlOrderByPushedAtDesc(String webhookUrl);
}
