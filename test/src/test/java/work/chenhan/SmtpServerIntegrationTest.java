package work.chenhan;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@SpringBootTest(classes = Main.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class SmtpServerIntegrationTest {


    private static final int SMTP_PORT = 2525;

    @Test
    void testSendEmail() {
        assertDoesNotThrow(() -> {
            try (java.net.Socket socket = new java.net.Socket("localhost", SMTP_PORT);
                    java.io.BufferedReader reader = new java.io.BufferedReader(
                            new java.io.InputStreamReader(socket.getInputStream()));
                    java.io.PrintWriter writer = new java.io.PrintWriter(socket.getOutputStream(), true)) {

                // Read server banner
                String line = reader.readLine();
                if (line == null || !line.startsWith("220")) {
                    throw new RuntimeException("Invalid server banner: " + line);
                }

                // Send HELO
                writer.println("HELO localhost");
                line = reader.readLine();
                if (!line.startsWith("250"))
                    throw new RuntimeException("HELO failed: " + line);

                // Send MAIL FROM
                writer.println("MAIL FROM:<test-sender@example.com>");
                line = reader.readLine();
                if (!line.startsWith("250"))
                    throw new RuntimeException("MAIL FROM failed: " + line);

                // Send RCPT TO
                writer.println("RCPT TO:<test-recipient@example.com>");
                line = reader.readLine();
                if (!line.startsWith("250"))
                    throw new RuntimeException("RCPT TO failed: " + line);

                // Send DATA
                writer.println("DATA");
                line = reader.readLine();
                if (!line.startsWith("354"))
                    throw new RuntimeException("DATA failed: " + line);

                // Send Content
                writer.println("Subject: Integration Test Email");
                writer.println("");
                writer.println("This is a test email sent from the integration test.");
                writer.println(".");

                // Read response
                line = reader.readLine();
                if (!line.startsWith("250"))
                    throw new RuntimeException("Data transmission failed: " + line);

                // QUIT
                writer.println("QUIT");
            }
        }, "Sending email via raw SMTP should succeed");
    }
}
