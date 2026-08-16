package com.generationb.foundation.email;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@Slf4j
@Service
@Profile("prod")
public class ResendEmailSender implements EmailSender {

    @Value("${resend.api-key:}")
    private String apiKey;

    @Value("${app.frontend-url:https://generation-bfe.vercel.app}")
    private String frontendUrl;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Override
    public void sendPasswordResetEmail(String recipientEmail, String resetToken) {
        String resetUrl = frontendUrl + "/reset-password?token=" + resetToken;
        String jsonPayload = String.format(
                "{\"from\":\"Generation B <onboarding@resend.dev>\",\"to\":[\"%s\"],\"subject\":\"Reset your Generation B password\",\"html\":\"<p>Click <a href=\\\"%s\\\">here</a> to reset your password. The link expires in 30 minutes.</p>\"}",
                recipientEmail, resetUrl
        );

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.resend.com/emails"))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("Resend email response status: {}, body: {}", response.statusCode(), response.body());
        } catch (Exception e) {
            log.error("Failed to send reset email via Resend to {}", recipientEmail, e);
        }
    }
}
