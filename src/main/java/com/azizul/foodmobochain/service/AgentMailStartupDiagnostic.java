package com.azizul.foodmobochain.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Disabled by default. Temporary, controlled verification of the Render
 * application key against the project's own inbox. Sends no personal data.
 * Disable APP_MAIL_SELF_TEST_ON_START immediately after staging diagnosis.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.mail.self-test-on-start", havingValue = "true")
public class AgentMailStartupDiagnostic {
    private static final Logger log = LoggerFactory.getLogger(AgentMailStartupDiagnostic.class);
    private final AgentMailService mail;

    @EventListener(ApplicationReadyEvent.class)
    public void runOnceAtStartup() {
        mail.diagnosticInboxRead();
        try {
            mail.sendSelfDiagnostic();
            log.info("AgentMail Render-key self-send was accepted");
        } catch (AgentMailService.MailUnavailableException error) {
            // The AgentMailService logs only safe HTTP status and known error code.
            log.warn("AgentMail Render-key self-send failed");
        }
    }
}
