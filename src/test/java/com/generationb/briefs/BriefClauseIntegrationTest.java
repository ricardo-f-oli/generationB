package com.generationb.briefs;

import com.generationb.support.IntegrationTest;
import com.generationb.support.TestAuth;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Requirements #2 and #3.
 *
 * <p>The clause library and the brief were separate: you could curate clauses and had no way to
 * put any of them into the document a creator signs. The join table existed in the schema from
 * V5 and nothing in Java ever touched it.
 *
 * <p>The PDF assertions read the raw bytes. That is crude, but it is the only way to prove the
 * clause text reached the artefact a creator actually receives — which is the whole point of the
 * feature, and the part a service-level test would miss entirely.
 */
class BriefClauseIntegrationTest extends IntegrationTest {

    private String token;
    private String briefId;

    @BeforeEach
    void setUp() throws Exception {
        token = auth.tokenFor(mockMvc, TestAuth.ADMIN);
        briefId = createBrief();
    }

    // =====================================================================

    @Test
    @DisplayName("a new brief starts with no terms")
    void aNewBriefHasNoClauses() throws Exception {
        mockMvc.perform(get("/api/briefs/" + briefId + "/clauses")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    @DisplayName("attaching a clause puts it on the brief")
    void attachingAClause() throws Exception {
        String clauseId = anyClauseId();

        mockMvc.perform(post("/api/briefs/" + briefId + "/clauses/" + clauseId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id").value(clauseId));
    }

    @Test
    @DisplayName("attaching the same clause twice does not duplicate it")
    void attachingIsIdempotent() throws Exception {
        String clauseId = anyClauseId();

        mockMvc.perform(post("/api/briefs/" + briefId + "/clauses/" + clauseId)
                .header("Authorization", "Bearer " + token));
        mockMvc.perform(post("/api/briefs/" + briefId + "/clauses/" + clauseId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                // A double-click must not print the non-compete twice.
                .andExpect(jsonPath("$.data", hasSize(1)));
    }

    @Test
    @DisplayName("detaching removes it again")
    void detaching() throws Exception {
        String clauseId = anyClauseId();

        mockMvc.perform(post("/api/briefs/" + briefId + "/clauses/" + clauseId)
                .header("Authorization", "Bearer " + token));
        mockMvc.perform(delete("/api/briefs/" + briefId + "/clauses/" + clauseId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    @DisplayName("the order sent is the order stored")
    void orderIsPreserved() throws Exception {
        List<String> ids = allClauseIds();
        assumeAtLeastTwo(ids);

        List<String> reversed = List.of(ids.get(1), ids.get(0));
        mockMvc.perform(put("/api/briefs/" + briefId + "/clauses")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("[\"" + reversed.get(0) + "\",\"" + reversed.get(1) + "\"]"))
                .andExpect(status().isOk())
                // Contractual order is meaningful — a liability clause after the signature block
                // reads differently from one before it.
                .andExpect(jsonPath("$.data[0].id").value(reversed.get(0)))
                .andExpect(jsonPath("$.data[1].id").value(reversed.get(1)));
    }

    @Test
    @DisplayName("replacing the set drops what is no longer chosen")
    void replacingTheSet() throws Exception {
        List<String> ids = allClauseIds();
        assumeAtLeastTwo(ids);

        mockMvc.perform(put("/api/briefs/" + briefId + "/clauses")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content("[\"" + ids.get(0) + "\",\"" + ids.get(1) + "\"]"));

        mockMvc.perform(put("/api/briefs/" + briefId + "/clauses")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("[\"" + ids.get(1) + "\"]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id").value(ids.get(1)));
    }

    @Test
    @DisplayName("a clause id from another brand is not accepted")
    void unknownClauseIsRejected() throws Exception {
        mockMvc.perform(post("/api/briefs/" + briefId + "/clauses/"
                        + "11111111-2222-3333-4444-555555555555")
                        .header("Authorization", "Bearer " + token))
                // Without this check a guessed id would pull another brand's contract terms into
                // this brief.
                .andExpect(status().isNotFound());
    }

    // =====================================================================
    // The artefact
    // =====================================================================

    @Test
    @DisplayName("the PDF is a real PDF and carries the brief's own content")
    void thePdfIsWellFormedAndPopulated() throws Exception {
        byte[] pdf = exportPdf();

        assertTrue(pdf.length > 800, "a styled brief should not be a few hundred bytes");
        assertEquals("%PDF", new String(pdf, 0, 4, StandardCharsets.ISO_8859_1));

        String text = textOf(pdf);
        assertTrue(text.contains("Clause Test Campaign"), text);
        // Sections the old unstyled export omitted entirely.
        assertTrue(text.toUpperCase().contains("DELIVERABLES"),
                "deliverables were missing from the export before: " + text);
        assertTrue(text.contains("1 Reel"), "the deliverable items themselves are missing");
        assertTrue(text.toUpperCase().contains("COMMERCIALS"), text);
    }

    @Test
    @DisplayName("attached clauses reach the PDF")
    void clausesAppearInTheExport() throws Exception {
        String clauseId = anyClauseId();
        String content = clauseContent(clauseId);

        assertFalse(textOf(exportPdf()).toUpperCase().contains("TERMS"),
                "a brief with no clauses should have no terms section");

        mockMvc.perform(post("/api/briefs/" + briefId + "/clauses/" + clauseId)
                .header("Authorization", "Bearer " + token));

        // The document a creator signs has to actually contain the terms. Everything else in
        // this class is bookkeeping; this is the requirement.
        String text = textOf(exportPdf());
        assertTrue(text.toUpperCase().contains("TERMS"), "no terms section: " + text);

        String opening = content.length() > 30 ? content.substring(0, 30) : content;
        assertTrue(text.contains(opening),
                "the clause text itself is missing from the brief:\n" + text);
    }

    @Test
    @DisplayName("an empty budget prints a sentence, not currency symbols around null")
    void anUnsetBudgetReadsProperly() throws Exception {
        // Q-J1 regression: this printed "£null - £null".
        String text = textOf(exportPdf());
        assertFalse(text.toLowerCase().contains("null"),
                "the export must never print the word null: " + text);
        assertTrue(text.contains("To be discussed") || text.contains("To be confirmed"),
                "an unset budget should say so: " + text);
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    private String createBrief() throws Exception {
        String body = """
            {"campaignName":"Clause Test Campaign",
             "campaignGoal":"Grow awareness with a gifted seeding push",
             "keyMessages":"Gentle, effective, everyday",
             "deliverables":["1 Reel","3 Stories"],
             "toneOfVoice":"WITTY"}
            """;
        String response = mockMvc.perform(post("/api/briefs")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().is2xxSuccessful())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.data.id");
    }

    /**
     * The PDF's actual text.
     *
     * <p>Scanning the raw bytes does not work: openpdf compresses content streams, so the words
     * are not there to find. Extracting properly is also the only way to assert what a creator
     * will genuinely read.
     */
    private static String textOf(byte[] pdf) throws Exception {
        com.lowagie.text.pdf.PdfReader reader = new com.lowagie.text.pdf.PdfReader(pdf);
        try {
            com.lowagie.text.pdf.parser.PdfTextExtractor extractor =
                    new com.lowagie.text.pdf.parser.PdfTextExtractor(reader);
            StringBuilder text = new StringBuilder();
            for (int page = 1; page <= reader.getNumberOfPages(); page++) {
                text.append(extractor.getTextFromPage(page)).append('\n');
            }
            return text.toString();
        } finally {
            reader.close();
        }
    }

    private byte[] exportPdf() throws Exception {
        return mockMvc.perform(get("/api/briefs/" + briefId + "/export/pdf")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
    }

    private List<String> allClauseIds() throws Exception {
        String response = mockMvc.perform(get("/api/clauses")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.data[*].id");
    }

    private String anyClauseId() throws Exception {
        List<String> ids = allClauseIds();
        assertFalse(ids.isEmpty(), "the seed data should include contract clauses");
        return ids.get(0);
    }

    private String clauseContent(String clauseId) throws Exception {
        String response = mockMvc.perform(get("/api/clauses")
                        .header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString();
        List<String> contents = JsonPath.read(
                response, "$.data[?(@.id == '" + clauseId + "')].content");
        return contents.isEmpty() ? "" : contents.get(0);
    }

    private static void assumeAtLeastTwo(List<String> ids) {
        org.junit.jupiter.api.Assumptions.assumeTrue(ids.size() >= 2,
                "needs at least two seeded clauses to test ordering");
    }
}
