package com.generationb.briefs.internal;

import com.generationb.briefs.ContractClauseResponse;
import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Requirement #2: the brief a creator actually receives.
 *
 * <p>Previously a stack of unstyled {@code Paragraph}s — no hierarchy, no spacing, half the
 * fields missing, and the campaign name in the same 12pt roman as the budget. It was a text file
 * with a .pdf extension, and it went out under the agency's name to people deciding whether to
 * work with them.
 *
 * <p>Extracted from {@code BriefService} because layout is its own concern and because the
 * service had no business knowing about fonts. The design follows the platform's own identity:
 * one red accent used sparingly, generous rules, a clear type scale.
 *
 * <p>Helvetica throughout rather than the prototype's Instrument Serif: embedding a font would
 * need the file shipped in the jar, and a PDF that silently falls back on the reader's machine
 * is worse than one that deliberately uses a face every reader has.
 */
@Component
public class BriefPdfRenderer {

    /** The one accent, from the prototype palette. Used for rules and labels, never body text. */
    private static final Color RED = new Color(0xE0, 0x00, 0x08);
    private static final Color INK = new Color(0x00, 0x00, 0x00);
    private static final Color MUTED = new Color(0x6B, 0x6B, 0x6B);
    private static final Color RULE = new Color(0xD8, 0xD8, 0xD8);

    private static final Font TITLE =
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 24, INK);
    private static final Font SECTION =
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, RED);
    private static final Font LABEL =
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, MUTED);
    private static final Font BODY =
            FontFactory.getFont(FontFactory.HELVETICA, 10.5f, INK);
    private static final Font BODY_MUTED =
            FontFactory.getFont(FontFactory.HELVETICA, 10.5f, MUTED);
    private static final Font EYEBROW =
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, MUTED);
    private static final Font CLAUSE_TITLE =
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, INK);
    private static final Font FOOTNOTE =
            FontFactory.getFont(FontFactory.HELVETICA, 8, MUTED);

    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("d MMMM yyyy").withZone(ZoneId.of("Europe/London"));

    /**
     * @param brand   the brand's display name for the masthead
     * @param clauses the contract clauses attached to this brief (#3), in their chosen order
     */
    public byte[] render(Brief brief, String brand, List<ContractClauseResponse> clauses) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        // Generous margins: a brief is read, and a full-bleed wall of text is not.
        Document document = new Document(PageSize.A4, 56, 56, 54, 54);

        try {
            PdfWriter.getInstance(document, out);
            document.addTitle(brief.getCampaignName() + " — campaign brief");
            document.addCreator("Generation B");
            document.open();

            masthead(document, brief, brand);
            overview(document, brief);
            deliverables(document, brief);
            commercials(document, brief);
            aiDetail(document, brief);
            clauses(document, clauses);
            footer(document, brief);

            document.close();
        } catch (DocumentException e) {
            throw new IllegalStateException("Could not build the brief PDF", e);
        }
        return out.toByteArray();
    }

    // =====================================================================

    private void masthead(Document doc, Brief brief, String brand) throws DocumentException {
        Paragraph eyebrow = new Paragraph(
                upper(brand) + "  ·  CAMPAIGN BRIEF", EYEBROW);
        eyebrow.setSpacingAfter(6);
        doc.add(eyebrow);

        Paragraph title = new Paragraph(nz(brief.getCampaignName(), "Untitled campaign"), TITLE);
        title.setSpacingAfter(10);
        doc.add(title);

        doc.add(thickRule());
    }

    private void overview(Document doc, Brief brief) throws DocumentException {
        section(doc, "The campaign");
        field(doc, "Goal", brief.getCampaignGoal());
        field(doc, "Key messages", brief.getKeyMessages());
        field(doc, "Tone of voice",
                brief.getToneOfVoice() == null ? null : humanise(brief.getToneOfVoice().name()));
    }

    private void deliverables(Document doc, Brief brief) throws DocumentException {
        List<String> items = brief.getDeliverables();
        if (items == null || items.isEmpty()) {
            return;
        }
        section(doc, "Deliverables");

        // A real list. These were absent from the old export entirely, which is the one section
        // a creator reads twice.
        com.lowagie.text.List list = new com.lowagie.text.List(false, 12);
        list.setListSymbol(new Chunk("—  ", BODY));
        for (String item : items) {
            ListItem listItem = new ListItem(item, BODY);
            listItem.setSpacingAfter(3);
            list.add(listItem);
        }
        list.setIndentationLeft(2);
        doc.add(list);
        doc.add(spacer(10));
    }

    private void commercials(Document doc, Brief brief) throws DocumentException {
        section(doc, "Commercials");

        // Two columns: these are the facts people scan for rather than read.
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setWidths(new float[] {1f, 1f});
        table.getDefaultCell().setBorder(Rectangle.NO_BORDER);
        table.getDefaultCell().setPaddingBottom(10);

        table.addCell(cell("Budget", formatBudget(brief.getBudgetMin(), brief.getBudgetMax())));
        table.addCell(cell("Timeline", formatTimeline(brief.getTimelineStart(), brief.getTimelineEnd())));
        doc.add(table);
        doc.add(spacer(4));

        field(doc, "Additional notes", brief.getAdditionalNotes());
    }

    private void aiDetail(Document doc, Brief brief) throws DocumentException {
        String content = brief.getAiGeneratedContent();
        if (content == null || content.isBlank()) {
            return;
        }
        section(doc, "Detail");
        // Paragraph breaks are preserved: the generated copy is written in paragraphs and
        // collapsing them produced one unreadable block.
        for (String block : content.split("\\n\\s*\\n")) {
            Paragraph paragraph = new Paragraph(block.trim().replace("\n", " "), BODY);
            paragraph.setLeading(15);
            paragraph.setSpacingAfter(8);
            paragraph.setAlignment(Element.ALIGN_LEFT);
            doc.add(paragraph);
        }
    }

    /**
     * Requirement #3. The clauses attached to this brief, printed in the order chosen for it —
     * which is the point of attaching them rather than keeping a library nobody can use.
     */
    private void clauses(Document doc, List<ContractClauseResponse> clauses) throws DocumentException {
        if (clauses == null || clauses.isEmpty()) {
            return;
        }
        // Terms start on their own page. Nobody wants the non-compete wrapping around the
        // deliverables.
        doc.newPage();
        section(doc, "Terms");

        int index = 1;
        for (ContractClauseResponse clause : clauses) {
            Paragraph heading = new Paragraph(
                    index++ + ".  " + humanise(clause.clauseType().name()), CLAUSE_TITLE);
            heading.setSpacingBefore(10);
            heading.setSpacingAfter(4);
            doc.add(heading);

            Paragraph body = new Paragraph(clause.content(), BODY);
            body.setLeading(14);
            body.setIndentationLeft(14);
            body.setSpacingAfter(6);
            doc.add(body);
        }
    }

    private void footer(Document doc, Brief brief) throws DocumentException {
        doc.add(spacer(16));
        doc.add(thinRule());
        Paragraph footer = new Paragraph(
                "Prepared by Generation B  ·  "
                        + DATE.format(brief.getCreatedAt() == null
                                ? Instant.now() : brief.getCreatedAt())
                        + "  ·  This brief is confidential.", FOOTNOTE);
        footer.setSpacingBefore(6);
        doc.add(footer);
    }

    // =====================================================================
    // Building blocks
    // =====================================================================

    private void section(Document doc, String title) throws DocumentException {
        Paragraph heading = new Paragraph(upper(title), SECTION);
        heading.setSpacingBefore(18);
        heading.setSpacingAfter(8);
        doc.add(heading);
    }

    /** A labelled field. Skipped entirely when empty rather than printed as "null". */
    private void field(Document doc, String label, String value) throws DocumentException {
        if (value == null || value.isBlank()) {
            return;
        }
        Paragraph labelLine = new Paragraph(upper(label), LABEL);
        labelLine.setSpacingAfter(2);
        doc.add(labelLine);

        Paragraph body = new Paragraph(value, BODY);
        body.setLeading(15);
        body.setSpacingAfter(12);
        doc.add(body);
    }

    private PdfPCell cell(String label, String value) {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPaddingBottom(8);
        Paragraph labelLine = new Paragraph(upper(label), LABEL);
        labelLine.setSpacingAfter(2);
        cell.addElement(labelLine);
        cell.addElement(new Paragraph(value, value.startsWith("To be") ? BODY_MUTED : BODY));
        return cell;
    }

    private Paragraph thickRule() {
        Paragraph rule = new Paragraph(new Chunk(new com.lowagie.text.pdf.draw.LineSeparator(
                1.4f, 100, INK, Element.ALIGN_CENTER, -2)));
        rule.setSpacingAfter(4);
        return rule;
    }

    private Paragraph thinRule() {
        return new Paragraph(new Chunk(new com.lowagie.text.pdf.draw.LineSeparator(
                0.6f, 100, RULE, Element.ALIGN_CENTER, -2)));
    }

    private Paragraph spacer(float height) {
        Paragraph spacer = new Paragraph(" ");
        spacer.setSpacingAfter(height);
        return spacer;
    }

    // =====================================================================
    // Formatting
    // =====================================================================

    /**
     * Q-J1: this printed "£null - £null" when no budget was set. An unset budget is a real state
     * — the fee is often agreed after the creator says yes — so it says so.
     */
    static String formatBudget(BigDecimal min, BigDecimal max) {
        NumberFormat money = NumberFormat.getCurrencyInstance(Locale.UK);
        money.setMaximumFractionDigits(0);

        if (min == null && max == null) {
            return "To be discussed";
        }
        if (min != null && max != null) {
            return min.compareTo(max) == 0
                    ? money.format(min)
                    : money.format(min) + " – " + money.format(max);
        }
        return min != null ? "From " + money.format(min) : "Up to " + money.format(max);
    }

    static String formatTimeline(Instant start, Instant end) {
        if (start == null && end == null) {
            return "To be confirmed";
        }
        if (start != null && end != null) {
            return DATE.format(start) + " – " + DATE.format(end);
        }
        return start != null ? "From " + DATE.format(start) : "By " + DATE.format(end);
    }

    private static String humanise(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String spaced = value.replace('_', ' ').toLowerCase(Locale.UK);
        return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }

    /** Letter-spaced small caps are not available, so uppercase carries the label style. */
    private static String upper(String value) {
        return value == null ? "" : value.toUpperCase(Locale.UK);
    }

    private static String nz(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
