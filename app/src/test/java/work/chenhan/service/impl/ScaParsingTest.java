package work.chenhan.service.impl;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import work.chenhan.dto.ScaReport;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;

class ScaParsingTest {

    @Test
    void testParseReport() {
        // Sample text provided by user
        String emailBody = """
                Link Check
                Tangerine，您好：
                【GitLab_V4】风险检测已完成，风险结果如下：
                项目名称：测试用，应用名称：管理系统企业前端，应用版本：master
                共检测出564个组件，其中严重10个，高危19个，中危13个，低危2个，无漏洞520个。
                共检测出48个漏洞，其中严重7个，高危20个，中危18个，低危3个。
                共检测出9个许可证，其中高风险0个，中风险0个，低风险9个。
                检测开始时间：2026-01-22 14:35:45
                检测完成时间：2026-01-22 14:35:57
                检测时长：12秒
                检测详情请见附件，为了您的数字供应链安全，请及时处理相应风险!
                                """;

        // Mock dependencies to create the processor (we only test the pure method)
        ScaWebhookProcessor processor = new ScaWebhookProcessor(Mockito.mock(RestClient.Builder.class),
                null, null, null, null);

        ScaReport report = processor.parseReport(emailBody);

        assertNotNull(report, "Report should not be null");
        assertEquals("测试用", report.getProjectName());
        assertEquals("管理系统企业前端", report.getApplicationName());
        assertEquals("master", report.getApplicationVersion());
        assertEquals(564, report.getComponentCount());
        assertEquals(48, report.getVulnerabilityCount());
        assertEquals(9, report.getLicenseCount());
        assertEquals("2026-01-22 14:35:45", report.getStartTime());
        assertEquals("2026-01-22 14:35:57", report.getEndTime());
    }
}
