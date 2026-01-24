package work.chenhan;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import work.chenhan.controller.TestWebhookController;
import work.chenhan.dto.ScaWebhookPayload;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = { Main.class,
        TestWebhookController.class }, webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT, properties = "smtp.port=2526")
class WebhookEndToEndTest {

    private static final int SMTP_PORT = 2526;

    @org.springframework.beans.factory.annotation.Autowired
    private work.chenhan.repository.ScaProcessRecordRepository processRecordRepository;

    @org.springframework.beans.factory.annotation.Autowired
    private work.chenhan.repository.WebhookPushLogRepository pushLogRepository;

    @Test
    void testEmailToWebhookFlow() throws Exception {
        // Clear queue and DB before start
        TestWebhookController.receivedPayloads.clear();
        processRecordRepository.deleteAll();
        pushLogRepository.deleteAll();

        // Send Email
        try (Socket socket = new Socket("localhost", SMTP_PORT);
                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                PrintWriter writer = new PrintWriter(socket.getOutputStream(), true)) {

            readExpect(reader, "220");
            writer.println("HELO localhost");
            readExpect(reader, "250");
            writer.println("MAIL FROM:<test@local.com>");
            readExpect(reader, "250");
            writer.println("RCPT TO:<admin@local.com>");
            readExpect(reader, "250");
            writer.println("DATA");
            readExpect(reader, "354");

            // Email Body (Matches the sample provided by user)
            writer.println("Subject: SCA Report");
            writer.println("");
            writer.println("Link Check");
            writer.println("Tangerine，您好：");
            writer.println("【GitLab_V4】风险检测已完成，风险结果如下：");
            writer.println("项目名称：测试用，应用名称：管理系统企业前端，应用版本：master");
            writer.println("共检测出564个组件，其中严重10个，高危19个，中危13个，低危2个，无漏洞520个。");
            writer.println("共检测出48个漏洞，其中严重7个，高危20个，中危18个，低危3个。");
            writer.println("共检测出9个许可证，其中高风险0个，中风险0个，低风险9个。");
            writer.println("检测开始时间：2026-01-22 14:35:45");
            writer.println("检测完成时间：2026-01-22 14:35:57");
            writer.println(".");

            readExpect(reader, "250");
            writer.println("QUIT");
            readExpect(reader, "221");
        }

        // Wait for Webhook (Async)
        ScaWebhookPayload payload = TestWebhookController.receivedPayloads.poll(5, TimeUnit.SECONDS);
        assertNotNull(payload, "Webhook should have been received within 5 seconds");

        System.out.println("Verified Payload: " + payload);

        // Assertions
        assertEquals("测试用", payload.getScaProjectName());
        assertEquals("管理系统企业前端", payload.getScaApplicationName());
        assertEquals("master", payload.getScaBranch());
        assertEquals(564, payload.getScaComponentCount());
        assertEquals(48, payload.getScaVulnerabilityCount());
        assertEquals(9, payload.getScaLicenseCount());
        assertEquals("2026-01-22 14:35:45", payload.getScaStartTime());
        assertEquals("2026-01-22 14:35:57", payload.getScaEndTime());

        // Assertions - Database
        assertEquals(1, processRecordRepository.count(), "Should have 1 process record");
        assertEquals(1, pushLogRepository.count(), "Should have 1 push log");

        work.chenhan.entity.ScaProcessRecord record = processRecordRepository.findAll().get(0);
        assertTrue(record.getIsAllowed(), "Decision should be allowed by default");
        assertEquals("测试用", record.getScaProjectName());

        work.chenhan.entity.WebhookPushLog log = pushLogRepository.findAll().get(0);
        assertEquals("SUCCESS", log.getStatus(), "Push status should be SUCCESS");
        assertEquals("测试用", log.getScaProjectName());
        assertEquals(200, log.getHttpStatusCode());
        // Verify URL contains expected path (configured in test yaml or overridden?)
        // In this test, TestWebhookController is local.
        // Wait, did we override sca.webhook.url in test?
        // No, TestWebhookController runs on random port?
        // Ah, `WebhookEndToEndTest` has `@SpringBootTest(webEnvironment =
        // ...DEFINED_PORT)`.
        // and TestWebhookController is a Bean.
        // But application.yml has "http://localhost:8080/callback".
        // The test environment usually picks up application-test.yml if exists, or
        // application.yml.
        // If DEFINED_PORT is used, default is 8080 unless configured.
        // Let's assume it hits localhost:8080/callback.
        assertEquals("http://localhost:9090/callback", log.getWebhookUrl());
    }

    private void readExpect(BufferedReader reader, String code) throws Exception {
        String line = reader.readLine();
        assertNotNull(line, "Server closed connection unexpectedly");
        assertTrue(line.startsWith(code), "Expected " + code + " but got: " + line);
    }
}
