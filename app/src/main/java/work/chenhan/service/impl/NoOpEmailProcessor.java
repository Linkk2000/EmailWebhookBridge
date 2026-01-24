package work.chenhan.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import work.chenhan.dto.EmailContent;
import work.chenhan.service.EmailProcessor;

@Service
public class NoOpEmailProcessor implements EmailProcessor {

    private static final Logger log = LoggerFactory.getLogger(NoOpEmailProcessor.class);

    @Override
    @Async
    public void process(EmailContent content) {
        log.info("NoOpEmailProcessor received email from: {}, recipients: {}. Raw size: {} bytes",
                content.getFrom(), content.getRecipients(),
                content.getRawData() != null ? content.getRawData().length : 0);
        // Do nothing else
    }
}
