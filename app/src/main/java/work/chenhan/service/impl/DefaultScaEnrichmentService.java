package work.chenhan.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import work.chenhan.dto.ScaWebhookPayload;
import work.chenhan.service.ScaEnrichmentService;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 通过 SCA OpenAPI 补全邮件里没有的 taskId / applicationId。
 *
 * 邮件正文只有「项目名称 / 应用名称 / 应用版本 / 统计数 / 起止时间」，没有 taskId，
 * 需要按「项目名 + 应用名 + 版本」反查应用，再取该应用最近一次 SCA 任务。
 *
 * 链路（固定两跳）：
 * 1. POST /openapi/v1/vcs/list          -> applicationId（响应里没有任何检测时间字段）
 * 2. POST /openapi/v1/application/{id}  -> latestScaTask.id / .status / .detectEndTime
 *
 * 第二跳省不掉：时间校验必须用 detectEndTime，而 vcs/list 不返回它。
 */
@Service
public class DefaultScaEnrichmentService implements ScaEnrichmentService {

    private static final Logger log = LoggerFactory.getLogger(DefaultScaEnrichmentService.class);

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** SCA 任务状态：5 = 检测完成。 */
    private static final int STATUS_FINISHED = 5;

    private final ScaOpenApiClient client;
    private final boolean blockOnFailure;
    private final long timeToleranceSeconds;
    private final int maxCandidates;

    public DefaultScaEnrichmentService(ScaOpenApiClient client,
            @Value("${sca.enrichment.block-on-failure:false}") boolean blockOnFailure,
            @Value("${sca.enrichment.time-tolerance-seconds:0}") long timeToleranceSeconds,
            @Value("${sca.enrichment.max-candidates:3}") int maxCandidates) {
        this.client = client;
        this.blockOnFailure = blockOnFailure;
        this.timeToleranceSeconds = Math.max(0, timeToleranceSeconds);
        this.maxCandidates = Math.max(1, maxCandidates);
    }

    @Override
    public boolean enrich(ScaWebhookPayload payload) {
        if (!client.isEnabled()) {
            log.debug("SCA 补全未启用（sca.openapi.base-url / token 未配置），跳过");
            return true;
        }
        try {
            boolean filled = doEnrich(payload);
            if (!filled) {
                return allowOnFailure(payload, "未能补全");
            }
            return true;
        } catch (Exception e) {
            log.error("SCA 补全异常. project={} app={} version={}",
                    payload.getScaProjectName(), payload.getScaApplicationName(), payload.getScaBranch(), e);
            return allowOnFailure(payload, "调用异常");
        }
    }

    private boolean doEnrich(ScaWebhookPayload payload) {
        String projectName = payload.getScaProjectName();
        String applicationName = payload.getScaApplicationName();
        String version = payload.getScaBranch();

        if (isBlank(projectName) || isBlank(applicationName)) {
            log.warn("邮件缺少项目名或应用名，无法反查. project={} app={}", projectName, applicationName);
            return false;
        }
        // 部分邮件的「应用版本」本身就是空的（前端那一栏空白即源于此）。
        // 这种情况下退化为两字段匹配，命中后用 SCA 返回的 applicationVersion 回填。
        boolean versionKnown = !isBlank(version);

        int type = client.resolveType(payload.getScaSource());
        List<ScaOpenApiClient.AppRecord> candidates =
                client.listApplications(type, projectName, applicationName);

        // name / projectName 是否为模糊匹配尚未实测，返回集里可能混入无关记录，
        // 所以三个字段必须在客户端再全等比对一次。
        List<ScaOpenApiClient.AppRecord> hits = new ArrayList<>();
        for (ScaOpenApiClient.AppRecord r : candidates) {
            if (!equalsTrimmed(r.projectName(), projectName)
                    || !equalsTrimmed(r.applicationName(), applicationName)) {
                continue;
            }
            if (versionKnown && !equalsTrimmed(r.applicationVersion(), version)) {
                continue;
            }
            hits.add(r);
        }

        if (hits.isEmpty()) {
            log.warn("反查未命中. type={} project={} app={} version={} 候选={}",
                    type, projectName, applicationName, version, candidates.size());
            return false;
        }
        if (hits.size() > this.maxCandidates) {
            log.warn("反查命中过多，放弃补全以免写入错误 taskId. 命中={} 上限={} project={} app={} version={}",
                    hits.size(), this.maxCandidates, projectName, applicationName, version);
            return false;
        }

        // 命中多条（同名同版本重复应用）时逐条取任务详情，
        // 由检测完成时间决定哪条才是这封邮件对应的那次扫描。
        for (ScaOpenApiClient.AppRecord hit : hits) {
            if (hit.applicationId() == null) {
                continue;
            }
            Optional<ScaOpenApiClient.TaskInfo> task = client.latestScaTask(hit.applicationId());
            if (task.isEmpty()) {
                continue;
            }
            ScaOpenApiClient.TaskInfo t = task.get();

            if (t.id() == null) {
                log.warn("latestScaTask 无任务ID. applicationId={}", hit.applicationId());
                continue;
            }
            if (t.status() == null || t.status() != STATUS_FINISHED) {
                log.info("最近任务未处于检测完成状态，跳过. applicationId={} taskId={} status={}",
                        hit.applicationId(), t.id(), t.status());
                continue;
            }
            // latestScaTask 是「该应用最近一次」，不保证就是这封邮件对应的那次：
            // 邮件到达与本次查询之间可能又触发了扫描。时间对不上宁可留空。
            if (!timeMatches(payload.getScaEndTime(), t.detectEndTime())) {
                log.warn("检测完成时间不匹配，放弃补全. applicationId={} taskId={} 邮件={} SCA={}",
                        hit.applicationId(), t.id(), payload.getScaEndTime(), t.detectEndTime());
                continue;
            }

            if (hit.taskId() != null && !hit.taskId().equals(t.id())) {
                log.warn("vcs/list 与 application 详情的 taskId 不一致. vcs={} detail={}",
                        hit.taskId(), t.id());
            }

            payload.setScaTaskId(String.valueOf(t.id()));
            payload.setScaAppId(String.valueOf(hit.applicationId()));
            if (!versionKnown && !isBlank(hit.applicationVersion())) {
                payload.setScaBranch(hit.applicationVersion());
                log.info("邮件缺少应用版本，已用 SCA 返回值回填. version={}", hit.applicationVersion());
            }
            log.info("SCA 补全成功. project={} app={} version={} taskId={} appId={} source={}",
                    projectName, applicationName, version, t.id(), hit.applicationId(),
                    hit.applicationSource());
            return true;
        }

        log.warn("命中应用但没有一条任务能通过校验. project={} app={} version={}",
                projectName, applicationName, version);
        return false;
    }

    /**
     * 补全失败时的放行策略。默认放行：拦截会让下游收不到扫描完成通知，
     * 是功能退化；放行只是两栏为空，与改造前一致。
     */
    private boolean allowOnFailure(ScaWebhookPayload payload, String reason) {
        if (this.blockOnFailure) {
            log.warn("补全失败且已配置拦截，本次不推送. reason={} project={} app={}",
                    reason, payload.getScaProjectName(), payload.getScaApplicationName());
            return false;
        }
        log.info("补全失败，按放行处理，taskId/appId 留空. reason={} project={} app={}",
                reason, payload.getScaProjectName(), payload.getScaApplicationName());
        return true;
    }

    private boolean timeMatches(String emailEndTime, String scaEndTime) {
        if (isBlank(emailEndTime) || isBlank(scaEndTime)) {
            return false;
        }
        String a = emailEndTime.trim();
        String b = scaEndTime.trim();
        if (a.equals(b)) {
            return true;
        }
        if (this.timeToleranceSeconds == 0) {
            return false;
        }
        try {
            LocalDateTime ta = LocalDateTime.parse(a, TIME_FORMAT);
            LocalDateTime tb = LocalDateTime.parse(b, TIME_FORMAT);
            return Math.abs(Duration.between(ta, tb).getSeconds()) <= this.timeToleranceSeconds;
        } catch (Exception e) {
            log.debug("检测完成时间无法解析，按不匹配处理. email={} sca={}", a, b);
            return false;
        }
    }

    private static boolean equalsTrimmed(String a, String b) {
        return a != null && b != null && a.trim().equals(b.trim());
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
