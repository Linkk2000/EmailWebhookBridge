package work.chenhan.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 源鉴 SCA OpenAPI 客户端，用于补全邮件中缺失的 taskId / applicationId。
 *
 * 只需要 OpenApiUserToken（SCA 个人中心生成），不需要登录换 webToken。
 *
 * 对外路径与字段一律以官方 OpenAPI 文档为准：网关持有独立的请求/响应 DTO，
 * 与 SCA 内部 Controller 不是同一套。已知的差异：
 * 内部叫 git/list，对外叫 vcs/list；对外 type 是必填字段而内部没有这个参数；
 * 对外响应不含 taskId，所以必须走 vcs/list -> application/{id} 两跳。
 */
@Component
public class ScaOpenApiClient {

    private static final Logger log = LoggerFactory.getLogger(ScaOpenApiClient.class);

    /**
     * 邮件正文【】中的来源名 -> vcs/list 的 type 参数。
     *
     * 取值是 RepositoryTypeEnum（对应库表 asset_repository_manage.repository_type），
     * 不是 ApplicationAndTaskParamSourceEnum——两套枚举同名不同值，用错了查不到任何数据。
     * 已实测：type=2 能查到 applicationSource 为 GitLab_V4 的应用，type=8 覆盖通用 Git。
     *
     * 键取自 application 详情与 vcs/list 返回的 applicationSource，实测无“代码仓库管理-”前缀。
     * 不在此表中的来源（应用包审查分析、SBOM清单扫描、二进制成分分析、容器镜像安全扫描、
     * CI/CD 流水线等）不属于代码仓库，vcs/list 查不到，需要各自的列表接口。
     */
    private static final Map<String, Integer> SOURCE_TYPE;

    static {
        Map<String, Integer> m = new LinkedHashMap<>();
        m.put("GitLab_V3", 1);
        m.put("GitLab_V4", 2);
        m.put("SVN", 3);
        m.put("通用Git", 8);
        m.put("Gitee开源版", 9);
        m.put("Gitee定制版", 22);
        m.put("Gitee企业版", 23);
        SOURCE_TYPE = Map.copyOf(m);
    }

    private final String baseUrl;
    private final String token;
    private final int pageSize;
    private final int defaultType;
    private final RestClient restClient;

    public ScaOpenApiClient(@Value("${sca.openapi.base-url:}") String baseUrl,
            @Value("${sca.openapi.token:}") String token,
            @Value("${sca.openapi.connect-timeout-seconds:5}") int connectTimeoutSeconds,
            @Value("${sca.openapi.read-timeout-seconds:5}") int readTimeoutSeconds,
            @Value("${sca.openapi.page-size:50}") int pageSize,
            @Value("${sca.openapi.default-type:8}") int defaultType,
            @Value("${sca.openapi.skip-ssl-verify:false}") boolean skipSslVerify,
            RestClient.Builder restClientBuilder) {
        this.baseUrl = trimTrailingSlash(baseUrl);
        this.token = token == null ? "" : token.trim();
        this.pageSize = Math.max(1, Math.min(pageSize, 200));
        this.defaultType = defaultType;

        SimpleClientHttpRequestFactory factory = skipSslVerify
                ? new SkipSslVerifyRequestFactory()
                : new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Math.max(1, connectTimeoutSeconds) * 1000);
        factory.setReadTimeout(Math.max(1, readTimeoutSeconds) * 1000);
        if (skipSslVerify) {
            log.warn("已关闭 SCA OpenAPI 的 TLS 证书与主机名校验（sca.openapi.skip-ssl-verify=true）。"
                    + "仅限内网自签证书场景，公网环境请改用导入证书的方式。");
        }
        // clone 一份，避免把超时设置写回容器里共享的 Builder
        this.restClient = restClientBuilder.clone().requestFactory(factory).build();
    }

    /** 未配置地址或 token 时整个补全环节跳过，行为与改造前一致。 */
    public boolean isEnabled() {
        return !this.baseUrl.isEmpty() && !this.token.isEmpty();
    }

    /**
     * 邮件来源名映射为 type 参数。识别不了时回落到配置的默认值，
     * 因为 type 是 vcs/list 的必填字段，缺了整个查询都发不出去。
     */
    public int resolveType(String sourceName) {
        if (sourceName == null || sourceName.isBlank()) {
            return this.defaultType;
        }
        Integer type = SOURCE_TYPE.get(sourceName.trim());
        if (type == null) {
            log.warn("未知的 SCA 来源，回落到默认 type. source={} defaultType={}", sourceName, this.defaultType);
            return this.defaultType;
        }
        return type;
    }

    /**
     * 代码仓库分页查询。只取第一页——name 过滤后候选本就极少，
     * 翻页会让单封邮件的请求量随全库应用数增长。
     */
    public List<AppRecord> listApplications(int type, String projectName, String applicationName) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("pageNum", 1);
        body.put("pageSize", this.pageSize);
        body.put("type", type);
        if (projectName != null && !projectName.isBlank()) {
            body.put("projectName", projectName);
        }
        if (applicationName != null && !applicationName.isBlank()) {
            body.put("name", applicationName);
        }

        JsonNode data = post("/openapi/v1/vcs/list", body);
        if (data == null) {
            return List.of();
        }
        JsonNode records = data.path("records");
        if (!records.isArray()) {
            log.warn("vcs/list 响应缺少 records 数组. type={} project={}", type, projectName);
            return List.of();
        }
        List<AppRecord> result = new ArrayList<>(records.size());
        for (JsonNode r : records) {
            result.add(new AppRecord(
                    intOrNull(r, "applicationId"),
                    textOrNull(r, "applicationName"),
                    textOrNull(r, "applicationVersion"),
                    intOrNull(r, "projectId"),
                    textOrNull(r, "projectName"),
                    textOrNull(r, "applicationSource"),
                    // 官方文档的响应字段表里没有 taskId，这里仍然读一次：
                    // 网关若哪天透传了内部 GitListVO 的字段，就能自动省掉第二跳。
                    firstNonNullInt(r, "taskId", "scaTaskId")));
        }
        return result;
    }

    /** 应用详情，取 latestScaTask。vcs/list 不返回 taskId 时的第二跳。 */
    public Optional<TaskInfo> latestScaTask(int applicationId) {
        JsonNode data = post("/openapi/v1/application/" + applicationId, Map.of());
        if (data == null) {
            return Optional.empty();
        }
        JsonNode latest = data.path("latestScaTask");
        if (latest.isMissingNode() || latest.isNull()) {
            log.warn("应用详情缺少 latestScaTask. applicationId={}", applicationId);
            return Optional.empty();
        }
        return Optional.of(new TaskInfo(
                intOrNull(latest, "id"),
                intOrNull(latest, "status"),
                textOrNull(latest, "detectEndTime")));
    }

    /**
     * 统一的 POST + 业务码校验，返回 data 节点。
     * 外部响应属于系统边界，逐层校验后再交给上层。
     */
    private JsonNode post(String path, Object body) {
        String url = this.baseUrl + path;
        try {
            JsonNode root = this.restClient.post()
                    .uri(url)
                    .header("OpenApiUserToken", this.token)
                    .header("Accept", "application/json")
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);

            if (root == null) {
                log.warn("SCA OpenAPI 返回空响应体. path={}", path);
                return null;
            }
            int code = root.path("code").asInt(-1);
            if (code != 0) {
                log.warn("SCA OpenAPI 业务失败. path={} code={} message={}",
                        path, code, root.path("message").asText(""));
                return null;
            }
            JsonNode data = root.path("data");
            return data.isMissingNode() || data.isNull() ? null : data;
        } catch (Exception e) {
            log.error("调用 SCA OpenAPI 失败. path={}", path, e);
            return null;
        }
    }

    private static String trimTrailingSlash(String s) {
        if (s == null) {
            return "";
        }
        String t = s.trim();
        while (t.endsWith("/")) {
            t = t.substring(0, t.length() - 1);
        }
        return t;
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return v.isMissingNode() || v.isNull() ? null : v.asText();
    }

    private static Integer intOrNull(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return v.isMissingNode() || v.isNull() || !v.canConvertToInt() ? null : v.asInt();
    }

    private static Integer firstNonNullInt(JsonNode node, String... fields) {
        for (String f : fields) {
            Integer v = intOrNull(node, f);
            if (v != null) {
                return v;
            }
        }
        return null;
    }

    /**
     * 跳过 TLS 校验的请求工厂，供内网自签证书环境使用。
     *
     * 只作用于本客户端自己的连接，不去动 HttpsURLConnection 的全局默认值，
     * 避免影响进程内其它 HTTPS 调用。
     */
    private static final class SkipSslVerifyRequestFactory extends SimpleClientHttpRequestFactory {

        private final SSLSocketFactory socketFactory;

        private SkipSslVerifyRequestFactory() {
            try {
                TrustManager[] trustAll = new TrustManager[] { new X509TrustManager() {
                    @Override
                    public void checkClientTrusted(X509Certificate[] chain, String authType) {
                    }

                    @Override
                    public void checkServerTrusted(X509Certificate[] chain, String authType) {
                    }

                    @Override
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }
                } };
                SSLContext context = SSLContext.getInstance("TLS");
                context.init(null, trustAll, new SecureRandom());
                this.socketFactory = context.getSocketFactory();
            } catch (Exception e) {
                throw new IllegalStateException("初始化跳过校验的 SSLContext 失败", e);
            }
        }

        @Override
        protected void prepareConnection(HttpURLConnection connection, String httpMethod) throws IOException {
            if (connection instanceof HttpsURLConnection httpsConnection) {
                httpsConnection.setSSLSocketFactory(this.socketFactory);
                httpsConnection.setHostnameVerifier((hostname, session) -> true);
            }
            super.prepareConnection(connection, httpMethod);
        }
    }

    /** vcs/list 的一条记录。taskId 通常为 null，见字段注释。 */
    public record AppRecord(Integer applicationId, String applicationName, String applicationVersion,
            Integer projectId, String projectName, String applicationSource, Integer taskId) {
    }

    /** application/{id} 里的 latestScaTask。 */
    public record TaskInfo(Integer id, Integer status, String detectEndTime) {
    }
}
