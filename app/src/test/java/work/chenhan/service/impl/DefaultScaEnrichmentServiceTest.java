package work.chenhan.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import work.chenhan.dto.ScaWebhookPayload;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * 补全逻辑的定向测试。
 *
 * 重点在失败路径：宁可把 taskId 留空，也不能写入一个属于另一次扫描的 ID。
 */
class DefaultScaEnrichmentServiceTest {

    private static final String PROJECT = "测试用";
    private static final String APP = "管理系统企业前端";
    private static final String VERSION = "master";
    private static final String END_TIME = "2026-01-22 14:35:57";

    private ScaOpenApiClient client;

    @BeforeEach
    void setUp() {
        client = Mockito.mock(ScaOpenApiClient.class);
        when(client.isEnabled()).thenReturn(true);
        when(client.resolveType(anyString())).thenReturn(23);
    }

    private DefaultScaEnrichmentService service(boolean blockOnFailure, long tolerance) {
        return new DefaultScaEnrichmentService(client, blockOnFailure, tolerance, 3);
    }

    private ScaWebhookPayload payload() {
        ScaWebhookPayload p = new ScaWebhookPayload();
        p.setScaProjectName(PROJECT);
        p.setScaApplicationName(APP);
        p.setScaBranch(VERSION);
        p.setScaEndTime(END_TIME);
        p.setScaSource("GitLab_V4");
        return p;
    }

    private ScaOpenApiClient.AppRecord record(String project, String app, String version, Integer appId) {
        return new ScaOpenApiClient.AppRecord(appId, app, version, 66, project,
                "代码仓库管理-GitLab_V4", null);
    }

    @Test
    @DisplayName("命中且检测完成时间一致时补全 taskId / appId")
    void enrichesTaskIdAndAppIdWhenMatched() {
        when(client.listApplications(anyInt(), anyString(), anyString()))
                .thenReturn(List.of(record(PROJECT, APP, VERSION, 6055)));
        when(client.latestScaTask(6055))
                .thenReturn(Optional.of(new ScaOpenApiClient.TaskInfo(97, 5, END_TIME)));

        ScaWebhookPayload p = payload();
        assertTrue(service(false, 0).enrich(p));
        assertEquals("97", p.getScaTaskId());
        assertEquals("6055", p.getScaAppId());
    }

    @Test
    @DisplayName("检测完成时间对不上时不写入 taskId")
    void skipsWhenDetectEndTimeMismatch() {
        when(client.listApplications(anyInt(), anyString(), anyString()))
                .thenReturn(List.of(record(PROJECT, APP, VERSION, 6055)));
        // 邮件到达后又跑了一次扫描，latestScaTask 已经是新的那次
        when(client.latestScaTask(6055))
                .thenReturn(Optional.of(new ScaOpenApiClient.TaskInfo(98, 5, "2026-01-22 16:00:00")));

        ScaWebhookPayload p = payload();
        assertTrue(service(false, 0).enrich(p), "默认放行");
        assertNull(p.getScaTaskId(), "宁可留空也不能写入别次扫描的 taskId");
        assertNull(p.getScaAppId());
    }

    @Test
    @DisplayName("任务未处于检测完成状态时不写入 taskId")
    void skipsWhenTaskNotFinished() {
        when(client.listApplications(anyInt(), anyString(), anyString()))
                .thenReturn(List.of(record(PROJECT, APP, VERSION, 6055)));
        when(client.latestScaTask(6055))
                .thenReturn(Optional.of(new ScaOpenApiClient.TaskInfo(97, 3, END_TIME)));

        ScaWebhookPayload p = payload();
        assertTrue(service(false, 0).enrich(p));
        assertNull(p.getScaTaskId());
    }

    @Test
    @DisplayName("版本不同的同名应用不算命中")
    void skipsWhenVersionDiffers() {
        when(client.listApplications(anyInt(), anyString(), anyString()))
                .thenReturn(List.of(record(PROJECT, APP, "develop", 6055)));

        ScaWebhookPayload p = payload();
        assertTrue(service(false, 0).enrich(p));
        assertNull(p.getScaTaskId());
        verify(client, never()).latestScaTask(anyInt());
    }

    @Test
    @DisplayName("项目名不同的同名应用不算命中")
    void skipsWhenProjectDiffers() {
        when(client.listApplications(anyInt(), anyString(), anyString()))
                .thenReturn(List.of(record("另一个项目", APP, VERSION, 7001)));

        ScaWebhookPayload p = payload();
        assertTrue(service(false, 0).enrich(p));
        assertNull(p.getScaTaskId());
        verify(client, never()).latestScaTask(anyInt());
    }

    @Test
    @DisplayName("容差范围内的时间差视为同一次扫描")
    void acceptsTimeWithinTolerance() {
        when(client.listApplications(anyInt(), anyString(), anyString()))
                .thenReturn(List.of(record(PROJECT, APP, VERSION, 6055)));
        when(client.latestScaTask(6055))
                .thenReturn(Optional.of(new ScaOpenApiClient.TaskInfo(97, 5, "2026-01-22 14:36:00")));

        ScaWebhookPayload p = payload();
        assertTrue(service(false, 5).enrich(p), "容差 5 秒，实际差 3 秒");
        assertEquals("97", p.getScaTaskId());
    }

    @Test
    @DisplayName("配置拦截时补全失败则不放行")
    void blocksOnFailureWhenConfigured() {
        when(client.listApplications(anyInt(), anyString(), anyString())).thenReturn(List.of());

        assertFalse(service(true, 0).enrich(payload()));
    }

    @Test
    @DisplayName("未配置 SCA 地址时直接放行且不发起调用")
    void skipsEntirelyWhenNotConfigured() {
        when(client.isEnabled()).thenReturn(false);

        ScaWebhookPayload p = payload();
        assertTrue(service(false, 0).enrich(p));
        assertNull(p.getScaTaskId());
        verify(client, never()).listApplications(anyInt(), anyString(), anyString());
    }

    @Test
    @DisplayName("缺少项目名或应用名时不发起调用")
    void skipsWhenMailFieldsIncomplete() {
        ScaWebhookPayload p = payload();
        p.setScaApplicationName(null);

        assertTrue(service(false, 0).enrich(p));
        verify(client, never()).listApplications(anyInt(), anyString(), anyString());
    }

    @Test
    @DisplayName("邮件应用版本为空时退化为两字段匹配并回填版本")
    void backfillsVersionWhenMailVersionBlank() {
        when(client.listApplications(anyInt(), anyString(), anyString()))
                .thenReturn(List.of(record(PROJECT, APP, "master", 6055)));
        when(client.latestScaTask(6055))
                .thenReturn(Optional.of(new ScaOpenApiClient.TaskInfo(97, 5, END_TIME)));

        ScaWebhookPayload p = payload();
        p.setScaBranch(null);

        assertTrue(service(false, 0).enrich(p));
        assertEquals("97", p.getScaTaskId());
        assertEquals("master", p.getScaBranch(), "分支/版本这一栏应由 SCA 返回值补上");
    }

    @Test
    @DisplayName("邮件有版本时版本不同的记录仍不算命中")
    void stillRequiresVersionWhenMailHasOne() {
        when(client.listApplications(anyInt(), anyString(), anyString()))
                .thenReturn(List.of(record(PROJECT, APP, "develop", 6055)));

        ScaWebhookPayload p = payload();
        assertTrue(service(false, 0).enrich(p));
        assertNull(p.getScaTaskId());
        verify(client, never()).latestScaTask(anyInt());
    }

    @Test
    @DisplayName("SCA 调用抛异常时按放行处理")
    void allowsWhenClientThrows() {
        when(client.listApplications(anyInt(), anyString(), anyString()))
                .thenThrow(new RuntimeException("connect timed out"));

        ScaWebhookPayload p = payload();
        assertTrue(service(false, 0).enrich(p));
        assertNull(p.getScaTaskId());
    }
}
