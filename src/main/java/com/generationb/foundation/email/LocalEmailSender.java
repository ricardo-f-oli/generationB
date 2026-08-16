package com.generationb.foundation.email;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@Profile("!prod")
public class LocalEmailSender implements EmailSender {

    @Override
    public void sendPasswordResetEmail(String recipientEmail, String resetToken) {
        String resetUrl = "http://localhost:5173/reset-password?token=" + resetToken;
        log.info("==========================================================================");
        log.info("[LOCAL EMAIL SENDER] Password Reset Request for: {}", recipientEmail);
        log.info("[LOCAL EMAIL SENDER] Reset URL: {}", resetUrl);
        log.info("==========================================================================");
    }
}
