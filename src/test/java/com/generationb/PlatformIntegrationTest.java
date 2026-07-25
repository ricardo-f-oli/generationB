package com.generationb;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.generationb.briefs.CreateBriefCommand;
import com.generationb.briefs.ToneOfVoice;
import com.generationb.campaigns.CampaignType;
import com.generationb.campaigns.CreateCampaignCommand;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PlatformIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void testFullWorkflow() throws Exception {
        // 1. Login to get token
        String loginBody = objectMapper.writeValueAsString(Map.of(
                "email", "director@communications.com",
                "role", "DIRECTOR",
                "brandId", "11111111-1111-1111-1111-111111111111",
                "userId", "22222222-2222-2222-2222-222222222222"
        ));

        String loginResponse = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token", notNullValue()))
                .andReturn().getResponse().getContentAsString();

        String token = objectMapper.readTree(loginResponse).path("data").path("token").asText();
        String authHeader = "Bearer " + token;

        // 2. Create a brief
        CreateBriefCommand createBrief = new CreateBriefCommand(
                "Summer Campaign 2026",
                "Promote hydration products",
                "Drink water regularly",
                List.of("1x Instagram Post", "1x TikTok Video"),
                new BigDecimal("1000.00"),
                new BigDecimal("5000.00"),
                Instant.now(),
                Instant.now().plusSeconds(864000),
                ToneOfVoice.INSPIRATIONAL,
                "Focus on outdoor locations"
        );

        String briefResponse = mockMvc.perform(post("/api/briefs")
                        .header("Authorization", authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createBrief)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.campaignName", is("Summer Campaign 2026")))
                .andExpect(jsonPath("$.data.status", is("DRAFT")))
                .andExpect(jsonPath("$.data.brandId", is("11111111-1111-1111-1111-111111111111")))
                .andReturn().getResponse().getContentAsString();

        String briefId = objectMapper.readTree(briefResponse).path("data").path("id").asText();

        // 3. Generate AI brief
        mockMvc.perform(post("/api/briefs/" + briefId + "/generate")
                        .header("Authorization", authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("GENERATED")))
                .andExpect(jsonPath("$.data.aiGeneratedContent", containsString("Summer Campaign 2026")));

        // 4. Export PDF
        mockMvc.perform(get("/api/briefs/" + briefId + "/export/pdf")
                        .header("Authorization", authHeader))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));

        // 5. Create a campaign
        CreateCampaignCommand createCampaign = new CreateCampaignCommand(
                "Influencer Launch Campaign",
                CampaignType.PAID,
                Instant.now(),
                Instant.now().plusSeconds(864000)
        );

        String campaignResponse = mockMvc.perform(post("/api/campaigns")
                        .header("Authorization", authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createCampaign)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name", is("Influencer Launch Campaign")))
                .andReturn().getResponse().getContentAsString();

        String campaignId = objectMapper.readTree(campaignResponse).path("data").path("id").asText();

        // 6. Create board and verify default columns are initialized
        String boardBody = objectMapper.writeValueAsString(Map.of("name", "Launch Board"));
        String boardResponse = mockMvc.perform(post("/api/campaigns/" + campaignId + "/boards")
                        .header("Authorization", authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(boardBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name", is("Launch Board")))
                .andReturn().getResponse().getContentAsString();

        String boardId = objectMapper.readTree(boardResponse).path("data").path("id").asText();

        // Check columns
        mockMvc.perform(get("/api/boards/" + boardId)
                        .header("Authorization", authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.columns", hasSize(4)))
                .andExpect(jsonPath("$.data.columns[0].name", is("Briefing")))
                .andExpect(jsonPath("$.data.columns[2].name", is("Awaiting Approval")));
    }
}
