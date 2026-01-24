package work.chenhan.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import work.chenhan.entity.WebhookConfig;
import java.util.List;

public interface WebhookConfigRepository extends JpaRepository<WebhookConfig, Long> {

    // 查找所有已启用的 Webhook
    List<WebhookConfig> findByEnabledTrue();
}
