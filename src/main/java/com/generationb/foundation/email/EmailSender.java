package com.generationb.foundation.email;

public interface EmailSender {
    void sendPasswordResetEmail(String recipientEmail, String resetToken);
}
