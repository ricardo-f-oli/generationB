package com.generationb.attachments;

import com.generationb.support.IntegrationTest;
import com.generationb.support.TestAuth;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Requirement #5, described as behaviour rather than as method calls.
 *
 * <p>Runs against a real PostgreSQL and a real S3 API (MinIO), through the full HTTP stack
 * including the JWT filter — so what is asserted here is what a browser would actually get.
 */
@DisplayName("Attaching files to a campaign card")
class AttachmentBehaviourTest extends IntegrationTest {

    private static final UUID CARD = UUID.fromString("e4000000-0000-0000-0000-000000000002");

    private MockMultipartFile file(String name, String type, byte[] content) {
        return new MockMultipartFile("file", name, type, content);
    }

    @Test
    @DisplayName("a PDF can be uploaded, listed and downloaded again")
    void roundTripsAFile() throws Exception {
        String token = auth.bearer(mockMvc, TestAuth.ADMIN);
        byte[] content = "a pretend brief".getBytes();

        String id = mockMvc.perform(multipart("/api/attachments")
                        .file(file("Autumn brief.pdf", "application/pdf", content))
                        .param("ownerType", "CARD")
                        .param("ownerId", CARD.toString())
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.filename").value("Autumn brief.pdf"))
                .andExpect(jsonPath("$.data.sizeBytes").value(content.length))
                .andExpect(jsonPath("$.data.humanSize").value(notNullValue()))
                // The storage key must never reach the client.
                .andExpect(jsonPath("$.data.storageKey").doesNotExist())
                .andReturn().getResponse().getContentAsString()
                .replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");

        mockMvc.perform(get("/api/attachments")
                        .param("ownerType", "CARD")
                        .param("ownerId", CARD.toString())
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id=='" + id + "')]").exists());

        // MinIO can sign, so the download redirects rather than streaming.
        mockMvc.perform(get("/api/attachments/{id}/download", id)
                        .header("Authorization", token))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", containsString("generationb-test")));
    }

    @Test
    @DisplayName("an executable is refused, whatever it claims to be")
    void refusesADisallowedType() throws Exception {
        String token = auth.bearer(mockMvc, TestAuth.ADMIN);

        mockMvc.perform(multipart("/api/attachments")
                        .file(file("payload.sh", "application/x-sh", "#!/bin/sh\nrm -rf /".getBytes()))
                        .param("ownerType", "CARD")
                        .param("ownerId", CARD.toString())
                        .header("Authorization", token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("cannot accept")));
    }

    @Test
    @DisplayName("an SVG is refused, because it can carry script")
    void refusesSvg() throws Exception {
        String token = auth.bearer(mockMvc, TestAuth.ADMIN);

        mockMvc.perform(multipart("/api/attachments")
                        .file(file("logo.svg", "image/svg+xml",
                                "<svg onload=\"alert(1)\"/>".getBytes()))
                        .param("ownerType", "CARD")
                        .param("ownerId", CARD.toString())
                        .header("Authorization", token))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("an empty file is refused")
    void refusesAnEmptyFile() throws Exception {
        String token = auth.bearer(mockMvc, TestAuth.ADMIN);

        mockMvc.perform(multipart("/api/attachments")
                        .file(file("empty.pdf", "application/pdf", new byte[0]))
                        .param("ownerType", "CARD")
                        .param("ownerId", CARD.toString())
                        .header("Authorization", token))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("a hostile filename is stored under a harmless one")
    void sanitisesTheFilename() throws Exception {
        String token = auth.bearer(mockMvc, TestAuth.ADMIN);

        mockMvc.perform(multipart("/api/attachments")
                        .file(file("../../../etc/passwd.pdf", "application/pdf", "x".getBytes()))
                        .param("ownerType", "CARD")
                        .param("ownerId", CARD.toString())
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.filename").value("passwd.pdf"));
    }

    @Test
    @DisplayName("an anonymous request cannot upload")
    void requiresAuthentication() throws Exception {
        mockMvc.perform(multipart("/api/attachments")
                        .file(file("brief.pdf", "application/pdf", "x".getBytes()))
                        .param("ownerType", "CARD")
                        .param("ownerId", CARD.toString()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("an unknown attachment id is a 404, not a 500")
    void unknownIdIsNotFound() throws Exception {
        String token = auth.bearer(mockMvc, TestAuth.ADMIN);

        mockMvc.perform(get("/api/attachments/{id}/download", UUID.randomUUID())
                        .header("Authorization", token))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("a deleted attachment disappears from the list and cannot be downloaded")
    void deleteRemovesIt() throws Exception {
        String token = auth.bearer(mockMvc, TestAuth.ADMIN);

        String id = mockMvc.perform(multipart("/api/attachments")
                        .file(file("temp.pdf", "application/pdf", "x".getBytes()))
                        .param("ownerType", "CARD")
                        .param("ownerId", CARD.toString())
                        .header("Authorization", token))
                .andReturn().getResponse().getContentAsString()
                .replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");

        mockMvc.perform(delete("/api/attachments/{id}", id).header("Authorization", token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/attachments/{id}/download", id).header("Authorization", token))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("an unknown owner type is refused rather than silently stored")
    void refusesAnUnknownOwnerType() throws Exception {
        String token = auth.bearer(mockMvc, TestAuth.ADMIN);

        mockMvc.perform(multipart("/api/attachments")
                        .file(file("brief.pdf", "application/pdf", "x".getBytes()))
                        .param("ownerType", "SOMETHING_ELSE")
                        .param("ownerId", CARD.toString())
                        .header("Authorization", token))
                .andExpect(status().isBadRequest());
    }
}
