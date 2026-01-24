package work.chenhan.smtp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.subethamail.smtp.MessageContext;
import org.subethamail.smtp.MessageHandler;
import org.subethamail.smtp.MessageHandlerFactory;
import org.subethamail.smtp.RejectException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import work.chenhan.service.EmailProcessor;

@Component
public class SimpleMessageHandlerFactory implements MessageHandlerFactory {

    private static final Logger log = LoggerFactory.getLogger(SimpleMessageHandlerFactory.class);

    private final EmailProcessor emailProcessor;

    public SimpleMessageHandlerFactory(EmailProcessor emailProcessor) {
        this.emailProcessor = emailProcessor;
    }

    @Override
    public MessageHandler create(MessageContext ctx) {
        return new MessageHandler() {
            private String from;
            private final java.util.List<String> recipients = new java.util.ArrayList<>();

            @Override
            public void from(String from) throws RejectException {
                this.from = from;
                this.recipients.clear();
            }

            @Override
            public void recipient(String recipient) throws RejectException {
                this.recipients.add(recipient);
            }

            @Override
            public void data(InputStream data) throws IOException {
                byte[] payload = readAllBytes(data);
                log.info("SMTP message received. from={}, recipients={}, size={} bytes", from, recipients,
                        payload.length);

                work.chenhan.dto.EmailContent content = new work.chenhan.dto.EmailContent(
                        from,
                        new java.util.ArrayList<>(recipients),
                        payload,
                        java.time.LocalDateTime.now());

                // Asynchronous processing according to requirements
                emailProcessor.process(content);
            }

            @Override
            public void done() {
                // no-op
            }
        };
    }

    private static byte[] readAllBytes(InputStream inputStream) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] data = new byte[4096];
        int nRead;
        while ((nRead = inputStream.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }
        return buffer.toByteArray();
    }
}
