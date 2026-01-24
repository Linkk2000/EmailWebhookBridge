package work.chenhan.service;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import work.chenhan.entity.WebhookConfig;
import work.chenhan.repository.WebhookConfigRepository;

import java.util.List;
import java.util.Optional;

@Service
public class WebhookConfigService {

    private final WebhookConfigRepository repository;

    public WebhookConfigService(WebhookConfigRepository repository) {
        this.repository = repository;
    }

    /**
     * 获取所有已启用的 Webhook 配置，结果会被缓存。
     * 当配置发生变化时，缓存会被自动失效。
     */
    @Cacheable(value = "enabled_webhooks")
    public List<WebhookConfig> getEnabledConfigs() {
        return repository.findByEnabledTrue();
    }

    /**
     * 获取所有 Webhook 配置（用于管理界面展示）。
     */
    public List<WebhookConfig> getAllConfigs() {
        return repository.findAll(Sort.by(Sort.Direction.DESC, "id"));
    }

    public Optional<WebhookConfig> findById(Long id) {
        return repository.findById(id);
    }

    /**
     * 保存或更新配置，并失效缓存。
     * 增加重复性检查逻辑。
     */
    @CacheEvict(value = "enabled_webhooks", allEntries = true)
    public WebhookConfig save(WebhookConfig config) {
        // 如果是新增（ID为空），进行重复性检查
        if (config.getId() == null) {
            Optional<WebhookConfig> existing = repository.findByUrl(config.getUrl());
            if (existing.isPresent()) {
                if (!existing.get().getEnabled()) {
                    throw new RuntimeException("该 Webhook 已存在且处于禁用状态，请在下方列表中启用它。");
                } else {
                    throw new RuntimeException("该 Webhook 已存在且已启用。");
                }
            }
        }
        return repository.save(config);
    }

    /**
     * 删除配置，并失效缓存。
     */
    @CacheEvict(value = "enabled_webhooks", allEntries = true)
    public void deleteById(Long id) {
        repository.deleteById(id);
    }

    /**
     * 切换启用状态，并失效缓存。
     */
    @CacheEvict(value = "enabled_webhooks", allEntries = true)
    public void toggleEnabled(Long id) {
        repository.findById(id).ifPresent(config -> {
            config.setEnabled(!config.getEnabled());
            // 切换状态时重置健康状态
            config.setLastStatus("UNKNOWN");
            config.setErrorMessage(null);
            repository.save(config);
        });
    }

    /**
     * 手动测试连通性。
     */
    @CacheEvict(value = "enabled_webhooks", allEntries = true)
    public void testConnection(Long id, org.springframework.web.client.RestClient restClient) {
        repository.findById(id).ifPresent(config -> {
            work.chenhan.dto.ScaWebhookPayload ping = new work.chenhan.dto.ScaWebhookPayload();
            ping.setScaProjectName("CONNECTION_TEST");
            ping.setScaApplicationName("PING");
            ping.setScaTaskId("TEST-" + System.currentTimeMillis());

            try {
                restClient.post()
                        .uri(config.getUrl())
                        .body(ping)
                        .retrieve()
                        .toBodilessEntity();

                config.setLastStatus("UP");
                config.setErrorMessage(null);
            } catch (Exception e) {
                config.setLastStatus("DOWN");
                config.setErrorMessage(e.getMessage());
            } finally {
                config.setLastTestedAt(java.time.LocalDateTime.now());
                repository.save(config);
            }
        });
    }

    /**
     * 自动更新状态（供处理器调用）。
     */
    @CacheEvict(value = "enabled_webhooks", allEntries = true)
    public void updateStatus(Long id, String status, String error) {
        repository.findById(id).ifPresent(config -> {
            config.setLastStatus(status);
            config.setErrorMessage(error);
            config.setLastTestedAt(java.time.LocalDateTime.now());
            repository.save(config);
        });
    }
}
