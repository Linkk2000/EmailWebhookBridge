package work.chenhan.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Service;
import work.chenhan.dto.ScaWebhookPayload;
import work.chenhan.service.ScaEnrichmentService;

@Service
public class DefaultScaEnrichmentService implements ScaEnrichmentService {

    private static final Logger log = LoggerFactory.getLogger(DefaultScaEnrichmentService.class);

    @Override
    public boolean enrich(ScaWebhookPayload payload) {
        log.info("Enriching payload (Default: No-op): {}", payload);
        // 默认实现：不做任何操作，允许所有。
        return true;
    }
}
