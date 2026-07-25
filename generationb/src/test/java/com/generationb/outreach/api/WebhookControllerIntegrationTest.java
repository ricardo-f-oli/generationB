package com.generationb.outreach.api;

import com.generationb.outreach.RecipientStatus;
import com.generationb.outreach.internal.EmailThreadRepository;
import com.generationb.outreach.internal.OutreachRecipient;
import com.generationb.outreach.internal.OutreachRecipientRepository;
import com.generationb.outreach.internal.SendGridEmailSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Transactional
class WebhookControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OutreachRecipientRepository recipientRepository;

    @Autowired
    private EmailThreadRepository emailThreadRepository;

    @org.springframework.boot.test.mock.mockito.MockBean
    private SendGridEmailSender sendGridEmailSender;

    private OutreachRecipient recipient;

    @BeforeEach
    void setUp() {
        recipient = new OutreachRecipient();
        recipient.setOutreachCampaignId(UUID.randomUUID());
        recipient.setBrandId(UUID.randomUUID());
        recipient.setCreatorId(UUID.randomUUID());
        recipient.setCreatorEmail("creator@example.com");
        recipient.setCreatorFirstName("Test");
        recipient.setCreatorHandle("@test");
        recipient.setStatus(RecipientStatus.SENT);
        recipient = recipientRepository.save(recipient);
    }

    @Test
    void testInvalidSignatureRejection() throws Exception {
        mockMvc.perform(post("/api/webhooks/sendgrid")
                .header("X-Twilio-Email-Event-Webhook-Signature", "INVALID_SIG")
                .contentType("application/json")
                .content("[]"))
                .andExpect(status().isForbidden());
    }

    @Test
    void testOpenEvent() throws Exception {
        String jsonPayload = String.format("""
            [
                {
                    "event": "open",
                    "outreach_recipient_id": "%s"
                }
            ]
            """, recipient.getId());

        mockMvc.perform(post("/api/webhooks/sendgrid")
                .header("X-Twilio-Email-Event-Webhook-Signature", "VALID_SIG")
                .contentType("application/json")
                .content(jsonPayload))
                .andExpect(status().isOk());

        OutreachRecipient updated = recipientRepository.findById(recipient.getId()).orElseThrow();
        assertEquals(RecipientStatus.OPENED, updated.getStatus());
    }
}
