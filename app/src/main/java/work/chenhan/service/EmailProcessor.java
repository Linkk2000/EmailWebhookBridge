package work.chenhan.service;

import work.chenhan.dto.EmailContent;

/**
 * Interface for processing received emails.
 * Implementations should handle the email content asynchronously if needed.
 */
public interface EmailProcessor {
    /**
     * Process the received email.
     * 
     * @param content The email content and metadata
     */
    void process(EmailContent content);
}
