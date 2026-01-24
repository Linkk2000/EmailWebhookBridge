package work.chenhan;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

import static org.junit.jupiter.api.Assertions.*;

import org.springframework.boot.test.mock.mockito.MockBean;
import work.chenhan.service.EmailProcessor;
import work.chenhan.dto.EmailContent;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

@SpringBootTest(classes = Main.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class SmtpProtocolTest {

    @MockBean
    private EmailProcessor emailProcessor;

    private static final int SMTP_PORT = 2525;

    @Test
    void testStateIsolationAndReuse() throws Exception {
        try (Socket socket = new Socket("localhost", SMTP_PORT);
                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                PrintWriter writer = new PrintWriter(socket.getOutputStream(), true)) {

            readExpect(reader, "220");

            writer.println("HELO localhost");
            readExpect(reader, "250");

            // --- Transaction 1: Standard Mail ---
            writer.println("MAIL FROM:<sender1@test.com>");
            readExpect(reader, "250");

            writer.println("RCPT TO:<rcpt1@test.com>");
            readExpect(reader, "250");

            writer.println("DATA");
            readExpect(reader, "354");

            writer.println("Subject: Mail 1");
            writer.println("");
            writer.println("Body 1");
            writer.println(".");
            readExpect(reader, "250");

            // --- Transaction 2: Reuse connection (State Check) ---
            // If the server leaks state, the recipients list might still contain
            // rcpt1@test.com
            // We can't easily inspect server state from here without a spy,
            // but we can ensure the server at least accepts the sequence correctly.
            // To properly verify the BUG (state leak), we rely on the implementation fix
            // ensuring 'from()' clears previous state.
            // Here we verification correct protocol flow for second mail.

            writer.println("MAIL FROM:<sender2@test.com>");
            readExpect(reader, "250");

            writer.println("RCPT TO:<rcpt2@test.com>");
            readExpect(reader, "250");

            writer.println("DATA");
            readExpect(reader, "354");

            // ... after sending second email ...
            writer.println(".");
            readExpect(reader, "250");

            writer.println("QUIT");
            readExpect(reader, "221");

            // Verify Logic
            ArgumentCaptor<EmailContent> captor = ArgumentCaptor.forClass(EmailContent.class);
            verify(emailProcessor, times(2)).process(captor.capture());

            java.util.List<EmailContent> contents = captor.getAllValues();

            // Mail 1 checks
            assertEquals(1, contents.get(0).getRecipients().size());
            assertEquals("rcpt1@test.com", contents.get(0).getRecipients().get(0));

            // Mail 2 checks
            // IF BUG EXISTS: size will be 2 (rcpt1, rcpt2)
            assertEquals(1, contents.get(1).getRecipients().size(),
                    "Second email should only have 1 recipient, checking for state leak");
            assertEquals("rcpt2@test.com", contents.get(1).getRecipients().get(0));
            // Let's be loose on exact string match (contains) but strict on SIZE.
        }
    }

    @Test
    void testRsetCommand() throws Exception {
        try (Socket socket = new Socket("localhost", SMTP_PORT);
                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                PrintWriter writer = new PrintWriter(socket.getOutputStream(), true)) {

            readExpect(reader, "220");
            writer.println("HELO localhost");
            readExpect(reader, "250");

            // Start a transaction but abort it
            writer.println("MAIL FROM:<abort@test.com>");
            readExpect(reader, "250");
            writer.println("RCPT TO:<abort@test.com>");
            readExpect(reader, "250");

            // Abort
            writer.println("RSET");
            readExpect(reader, "250");

            // Start real transaction
            writer.println("MAIL FROM:<real@test.com>");
            readExpect(reader, "250");
            writer.println("RCPT TO:<real@test.com>");
            readExpect(reader, "250");
            writer.println("DATA");
            readExpect(reader, "354");
            writer.println(".");
            readExpect(reader, "250");

            writer.println("QUIT");
            readExpect(reader, "221");
        }
    }

    @Test
    void testCapabilities() throws Exception {
        try (Socket socket = new Socket("localhost", SMTP_PORT);
                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                PrintWriter writer = new PrintWriter(socket.getOutputStream(), true)) {

            readExpect(reader, "220");

            writer.println("EHLO localhost");
            // Expect multi-line 250 response.
            boolean seenAuth = false;
            boolean seenStartTls = false;

            String line;
            do {
                line = reader.readLine();
                assertNotNull(line);
                String upper = line.toUpperCase();
                if (upper.contains("AUTH"))
                    seenAuth = true;
                if (upper.contains("STARTTLS"))
                    seenStartTls = true;
            } while (line.length() >= 4 && line.charAt(3) == '-');

            assertFalse(seenAuth, "Should not advertise AUTH");
            assertFalse(seenStartTls, "Should not advertise STARTTLS");

            writer.println("QUIT");
            readExpect(reader, "221");
        }
    }

    private void readExpect(BufferedReader reader, String code) throws Exception {
        String line = reader.readLine();
        assertNotNull(line, "Server closed connection unexpectedly");
        assertTrue(line.startsWith(code), "Expected " + code + " but got: " + line);
    }
}
