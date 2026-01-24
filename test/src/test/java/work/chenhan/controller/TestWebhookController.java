package work.chenhan.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import work.chenhan.dto.ScaWebhookPayload;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

@RestController
public class TestWebhookController {

    public static final BlockingQueue<ScaWebhookPayload> receivedPayloads = new LinkedBlockingQueue<>();

    @PostMapping("/callback")
    public void receiveCallback(@RequestBody ScaWebhookPayload payload) {
        System.out.println("Test Webhook Received: " + payload);
        receivedPayloads.offer(payload);
    }
}
