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

@Component
public class SimpleMessageHandlerFactory implements MessageHandlerFactory {

    private static final Logger log = LoggerFactory.getLogger(SimpleMessageHandlerFactory.class);

    @Override
    public MessageHandler create(MessageContext ctx) {
        return new MessageHandler() {
            private String from;
            private String recipient;

            @Override
            public void from(String from) throws RejectException {
                this.from = from;
            }

            @Override
            public void recipient(String recipient) throws RejectException {
                this.recipient = recipient;
            }

            @Override
            public void data(InputStream data) throws IOException {
                byte[] payload = readAllBytes(data);
                log.info("SMTP message received. from={}, to={}, size={} bytes", from, recipient, payload.length);
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
