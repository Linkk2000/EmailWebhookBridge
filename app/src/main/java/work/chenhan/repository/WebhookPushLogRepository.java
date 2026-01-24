package work.chenhan.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import work.chenhan.entity.WebhookPushLog;

public interface WebhookPushLogRepository extends JpaRepository<WebhookPushLog, Long> {
}
