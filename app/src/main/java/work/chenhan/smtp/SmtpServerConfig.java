package work.chenhan.smtp;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.subethamail.smtp.server.SMTPServer;

@Configuration
public class SmtpServerConfig {

    @Bean(initMethod = "start", destroyMethod = "stop")
    public SMTPServer smtpServer(
            @Value("${smtp.port:2525}") int port,
            SimpleMessageHandlerFactory messageHandlerFactory) {
        SMTPServer server = new SMTPServer(messageHandlerFactory);
        server.setPort(port);
        return server;
    }
}
