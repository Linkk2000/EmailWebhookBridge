package work.chenhan.service;

import work.chenhan.dto.ScaWebhookPayload;

public interface ScaEnrichmentService {

    /**
     * 补全并校验负载数据。
     * 
     * @param payload 待补全的负载数据。
     * @return true 如果允许继续处理，false 如果应被忽略。
     */
    boolean enrich(ScaWebhookPayload payload);
}
