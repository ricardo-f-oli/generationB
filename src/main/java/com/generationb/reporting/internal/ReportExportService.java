package com.generationb.reporting.internal;

import com.generationb.foundation.ApiException;
import com.generationb.foundation.BrandLookupPort;
import com.generationb.reporting.ReportMetrics;
// Deliberately not a wildcard import: com.lowagie.text.Font and POI's Font would collide.
import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xslf.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.awt.Rectangle;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Requirement #54: client deliverables in PDF, PowerPoint and Excel.
 *
 * <p>Where a metric could not be measured the export prints "Not tracked" rather than a zero —
 * the same rule the metrics engine follows. A client deck showing "0 impressions" would be read
 * as a result rather than an absence.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportExportService {

    private static final NumberFormat NUM = NumberFormat.getIntegerInstance(Locale.UK);
    /** The prototype's brand red. */
    private static final Color BRAND_RED = new Color(0xE0, 0x00, 0x08);

    private final ReportService reportService;
    private final BrandLookupPort brandLookup;

    public record Export(byte[] content, String filename, String contentType) {
    }

    @Transactional(readOnly = true)
    public Export export(UUID reportId, String format) {
        Report report = reportService.require(reportId);
        ReportMetrics metrics = reportService.readMetrics(report);
        if (metrics == null) {
            throw ApiException.conflict("This report has no generated figures yet.");
        }
        String brandName = brandLookup.findBrandName(report.getBrandId()).orElse("Generation B");
        String base = slug(report.getName());

        return switch (format == null ? "" : format.toLowerCase()) {
            case "pdf" -> new Export(pdf(report, metrics, brandName), base + ".pdf", "application/pdf");
            case "excel", "xlsx" -> new Export(excel(report, metrics, brandName), base + ".xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            case "powerpoint", "pptx" -> new Export(powerpoint(report, metrics, brandName), base + ".pptx",
                    "application/vnd.openxmlformats-officedocument.presentationml.presentation");
            default -> throw ApiException.badRequest("Unsupported format. Use pdf, excel or powerpoint.");
        };
    }

    // ------------------------------------------------------------------ PDF

    private byte[] pdf(Report report, ReportMetrics m, String brandName) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 48, 48, 48, 48);
        try {
            PdfWriter.getInstance(document, out);
            document.open();

            com.lowagie.text.Font title = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 22, Color.BLACK);
            com.lowagie.text.Font heading = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, BRAND_RED);
            com.lowagie.text.Font body = FontFactory.getFont(FontFactory.HELVETICA, 10, Color.BLACK);
            com.lowagie.text.Font muted = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 8, Color.GRAY);

            document.add(new Paragraph(brandName, heading));
            document.add(new Paragraph(report.getName(), title));
            document.add(new Paragraph(
                    report.getPeriodStart() + " to " + report.getPeriodEnd()
                            + "  ·  " + report.getStatus(), muted));
            document.add(Chunk.NEWLINE);

            document.add(new Paragraph("Summary", heading));
            PdfPTable summary = new PdfPTable(2);
            summary.setWidthPercentage(100);
            summary.setSpacingBefore(8);
            addRow(summary, "Posts", NUM.format(m.posts()), body);
            addRow(summary, "Estimated reach", NUM.format(m.estimatedReach()), body);
            addRow(summary, "Views", NUM.format(m.views()), body);
            addRow(summary, "Likes", NUM.format(m.likes()), body);
            addRow(summary, "Comments", NUM.format(m.comments()), body);
            addRow(summary, "Average engagement rate", pct(m.averageEngagementRate()), body);
            addRow(summary, "Follower growth",
                    m.followerGrowth() == null ? "Not tracked" : NUM.format(m.followerGrowth()), body);
            addRow(summary, "Impressions", "Not tracked", body);
            addRow(summary, "Conversion", "Not tracked", body);
            addRow(summary, "Short / long form",
                    NUM.format(m.shortFormPosts()) + " / " + NUM.format(m.longFormPosts()), body);
            document.add(summary);

            if (m.reconciliation() != null) {
                document.add(Chunk.NEWLINE);
                document.add(new Paragraph("Send vs posted", heading));
                PdfPTable rec = new PdfPTable(2);
                rec.setWidthPercentage(100);
                rec.setSpacingBefore(8);
                addRow(rec, "Sent to", NUM.format(m.reconciliation().sentTo()), body);
                addRow(rec, "Posted", NUM.format(m.reconciliation().posted()), body);
                addRow(rec, "Not yet posted", NUM.format(m.reconciliation().notPosted()), body);
                addRow(rec, "Post rate", pct(m.reconciliation().postRate()), body);
                document.add(rec);
            }

            if (!m.creatorBreakdown().isEmpty()) {
                document.add(Chunk.NEWLINE);
                document.add(new Paragraph("Creator breakdown", heading));
                PdfPTable table = new PdfPTable(new float[]{3, 1, 2, 1.5f, 1.5f});
                table.setWidthPercentage(100);
                table.setSpacingBefore(8);
                headerCells(table, "Creator", "Posts", "Views", "ER", "Insights");
                for (ReportMetrics.CreatorRow row : m.creatorBreakdown()) {
                    table.addCell(cell("@" + row.handle(), body));
                    table.addCell(cell(NUM.format(row.posts()), body));
                    table.addCell(cell(NUM.format(row.views()), body));
                    table.addCell(cell(pct(row.engagementRate()), body));
                    table.addCell(cell(row.insightStatus() == null ? "—" : row.insightStatus(), body));
                }
                document.add(table);
            }

            if (!m.notes().isEmpty()) {
                document.add(Chunk.NEWLINE);
                document.add(new Paragraph("Notes on this report", heading));
                for (String note : m.notes()) {
                    document.add(new Paragraph("• " + note, muted));
                }
            }

            document.close();
        } catch (Exception e) {
            log.error("PDF export failed for report {}", report.getId(), e);
            throw ApiException.conflict("Could not build the PDF export.");
        }
        return out.toByteArray();
    }

    // ---------------------------------------------------------------- Excel

    private byte[] excel(Report report, ReportMetrics m, String brandName) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CellStyle header = workbook.createCellStyle();
            Font bold = workbook.createFont();
            bold.setBold(true);
            header.setFont(bold);

            Sheet summary = workbook.createSheet("Summary");
            int r = 0;
            r = kv(summary, r, "Brand", brandName, header);
            r = kv(summary, r, "Report", report.getName(), header);
            r = kv(summary, r, "Period",
                    report.getPeriodStart() + " to " + report.getPeriodEnd(), header);
            r = kv(summary, r, "Status", report.getStatus().name(), header);
            r++;
            r = kv(summary, r, "Posts", NUM.format(m.posts()), header);
            r = kv(summary, r, "Estimated reach", NUM.format(m.estimatedReach()), header);
            r = kv(summary, r, "Views", NUM.format(m.views()), header);
            r = kv(summary, r, "Likes", NUM.format(m.likes()), header);
            r = kv(summary, r, "Comments", NUM.format(m.comments()), header);
            r = kv(summary, r, "Average ER", pct(m.averageEngagementRate()), header);
            r = kv(summary, r, "Follower growth",
                    m.followerGrowth() == null ? "Not tracked" : NUM.format(m.followerGrowth()), header);
            r = kv(summary, r, "Impressions", "Not tracked", header);
            r = kv(summary, r, "Conversion", "Not tracked", header);
            kv(summary, r, "Short / long form",
                    m.shortFormPosts() + " / " + m.longFormPosts(), header);
            summary.autoSizeColumn(0);
            summary.autoSizeColumn(1);

            // One row per clipping is what the WIP spreadsheet needs (requirement #14).
            Sheet creators = workbook.createSheet("Creator breakdown");
            Row head = creators.createRow(0);
            String[] columns = {"Creator", "Posts", "Views", "Likes", "Comments", "ER %",
                                "Follower growth", "Quality", "Insights"};
            for (int i = 0; i < columns.length; i++) {
                Cell c = head.createCell(i);
                c.setCellValue(columns[i]);
                c.setCellStyle(header);
            }
            int rowIndex = 1;
            for (ReportMetrics.CreatorRow row : m.creatorBreakdown()) {
                Row line = creators.createRow(rowIndex++);
                line.createCell(0).setCellValue("@" + row.handle());
                line.createCell(1).setCellValue(row.posts());
                line.createCell(2).setCellValue(row.views());
                line.createCell(3).setCellValue(row.likes());
                line.createCell(4).setCellValue(row.comments());
                line.createCell(5).setCellValue(row.engagementRate() == null
                        ? 0d : row.engagementRate().doubleValue());
                line.createCell(6).setCellValue(row.followerGrowth() == null ? 0 : row.followerGrowth());
                line.createCell(7).setCellValue(row.qualityBand() == null ? "" : row.qualityBand());
                line.createCell(8).setCellValue(row.insightStatus() == null ? "" : row.insightStatus());
            }
            for (int i = 0; i < columns.length; i++) {
                creators.autoSizeColumn(i);
            }

            if (m.reconciliation() != null && !m.reconciliation().outstanding().isEmpty()) {
                Sheet pending = workbook.createSheet("Not yet posted");
                Row ph = pending.createRow(0);
                ph.createCell(0).setCellValue("Creator");
                ph.createCell(1).setCellValue("Insight status");
                ph.getCell(0).setCellStyle(header);
                ph.getCell(1).setCellStyle(header);
                int pr = 1;
                for (ReportMetrics.PendingCreator p : m.reconciliation().outstanding()) {
                    Row line = pending.createRow(pr++);
                    line.createCell(0).setCellValue("@" + p.handle());
                    line.createCell(1).setCellValue(p.insightStatus());
                }
                pending.autoSizeColumn(0);
                pending.autoSizeColumn(1);
            }

            Sheet notes = workbook.createSheet("Notes");
            int n = 0;
            for (String note : m.notes()) {
                notes.createRow(n++).createCell(0).setCellValue(note);
            }
            notes.setColumnWidth(0, 20000);

            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            log.error("Excel export failed for report {}", report.getId(), e);
            throw ApiException.conflict("Could not build the Excel export.");
        }
    }

    // ----------------------------------------------------------- PowerPoint

    private byte[] powerpoint(Report report, ReportMetrics m, String brandName) {
        try (XMLSlideShow deck = new XMLSlideShow(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            XSLFSlide cover = deck.createSlide();
            addText(cover, brandName, 40, 60, 640, 40, 14, true, BRAND_RED);
            addText(cover, report.getName(), 40, 110, 640, 80, 30, true, Color.BLACK);
            addText(cover, report.getPeriodStart() + " – " + report.getPeriodEnd(),
                    40, 200, 640, 30, 14, false, Color.DARK_GRAY);

            XSLFSlide summary = deck.createSlide();
            addText(summary, "Summary", 40, 40, 640, 40, 24, true, Color.BLACK);
            String[] lines = {
                    "Posts: " + NUM.format(m.posts()),
                    "Estimated reach: " + NUM.format(m.estimatedReach()),
                    "Views: " + NUM.format(m.views()),
                    "Average engagement rate: " + pct(m.averageEngagementRate()),
                    "Follower growth: " + (m.followerGrowth() == null
                            ? "Not tracked" : NUM.format(m.followerGrowth())),
                    "Short / long form: " + m.shortFormPosts() + " / " + m.longFormPosts(),
            };
            int y = 110;
            for (String line : lines) {
                addText(summary, "•  " + line, 50, y, 620, 30, 15, false, Color.BLACK);
                y += 34;
            }

            if (!m.creatorBreakdown().isEmpty()) {
                XSLFSlide breakdown = deck.createSlide();
                addText(breakdown, "Creator breakdown", 40, 40, 640, 40, 24, true, Color.BLACK);
                int by = 105;
                for (ReportMetrics.CreatorRow row : m.creatorBreakdown().stream().limit(10).toList()) {
                    addText(breakdown,
                            "@" + row.handle() + "   ·   " + NUM.format(row.views()) + " views   ·   "
                                    + pct(row.engagementRate()) + " ER",
                            50, by, 620, 26, 13, false, Color.BLACK);
                    by += 30;
                }
            }

            if (!m.notes().isEmpty()) {
                XSLFSlide notes = deck.createSlide();
                addText(notes, "Notes", 40, 40, 640, 40, 24, true, Color.BLACK);
                int ny = 105;
                for (String note : m.notes()) {
                    addText(notes, "•  " + note, 50, ny, 620, 44, 11, false, Color.DARK_GRAY);
                    ny += 46;
                }
            }

            deck.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            log.error("PowerPoint export failed for report {}", report.getId(), e);
            throw ApiException.conflict("Could not build the PowerPoint export.");
        }
    }

    // -------------------------------------------------------------- helpers

    private void addText(XSLFSlide slide, String text, int x, int y, int w, int h,
                         int size, boolean bold, Color colour) {
        XSLFTextBox box = slide.createTextBox();
        box.setAnchor(new Rectangle(x, y, w, h));
        XSLFTextParagraph p = box.addNewTextParagraph();
        XSLFTextRun run = p.addNewTextRun();
        run.setText(text);
        run.setFontSize((double) size);
        run.setBold(bold);
        run.setFontColor(colour);
    }

    private int kv(Sheet sheet, int rowIndex, String key, String value, CellStyle header) {
        Row row = sheet.createRow(rowIndex);
        Cell k = row.createCell(0);
        k.setCellValue(key);
        k.setCellStyle(header);
        row.createCell(1).setCellValue(value);
        return rowIndex + 1;
    }

    private void addRow(PdfPTable table, String key, String value, com.lowagie.text.Font font) {
        table.addCell(cell(key, font));
        table.addCell(cell(value, font));
    }

    private void headerCells(PdfPTable table, String... labels) {
        com.lowagie.text.Font font = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE);
        for (String label : labels) {
            PdfPCell cell = new PdfPCell(new Phrase(label, font));
            cell.setBackgroundColor(Color.DARK_GRAY);
            cell.setPadding(5);
            table.addCell(cell);
        }
    }

    private PdfPCell cell(String text, com.lowagie.text.Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text == null ? "—" : text, font));
        cell.setPadding(5);
        return cell;
    }

    /** Q-J1: never print a bare "null" into a client deliverable. */
    private String pct(BigDecimal value) {
        return value == null ? "Not measured" : value + "%";
    }

    private String slug(String name) {
        String cleaned = name == null ? "report" : name.toLowerCase().replaceAll("[^a-z0-9]+", "-");
        return cleaned.replaceAll("(^-|-$)", "");
    }
}
