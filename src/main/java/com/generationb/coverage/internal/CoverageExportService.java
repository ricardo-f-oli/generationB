package com.generationb.coverage.internal;

import com.generationb.foundation.ApiException;
import com.generationb.foundation.BrandContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * Requirement #14: the coverage log out to the WIP spreadsheet.
 *
 * <p>The previous version returned a {@code /downloads/...} URL for a file that was never
 * written. This streams a real workbook (or CSV) back.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CoverageExportService {

    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(ZoneId.of("Europe/London"));

    private static final String[] COLUMNS = {
            "Clipping name", "Creator", "Platform", "Post type", "Form", "Posted",
            "Views", "Likes", "Comments", "Shares", "Saves", "ER %", "Unsolicited", "Source", "URL"
    };

    private final CoverageItemRepository coverageRepository;

    public record Export(byte[] content, String filename, String contentType) {
    }

    @Transactional(readOnly = true)
    public Export export(String format, UUID campaignId) {
        BrandContext.requireBrandId();
        List<CoverageItem> items = coverageRepository.findAllScopedForExport(campaignId);
        if (items.isEmpty()) {
            throw ApiException.unprocessable("There is no coverage to export yet.");
        }

        return switch (format == null ? "excel" : format.toLowerCase()) {
            case "csv" -> new Export(csv(items).getBytes(StandardCharsets.UTF_8),
                    "coverage-log.csv", "text/csv");
            case "excel", "xlsx" -> new Export(excel(items), "coverage-log.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            default -> throw ApiException.badRequest("Unsupported format. Use excel or csv.");
        };
    }

    private byte[] excel(List<CoverageItem> items) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Coverage");

            CellStyle headerStyle = workbook.createCellStyle();
            Font bold = workbook.createFont();
            bold.setBold(true);
            headerStyle.setFont(bold);

            Row header = sheet.createRow(0);
            for (int i = 0; i < COLUMNS.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(COLUMNS[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowIndex = 1;
            for (CoverageItem item : items) {
                Row row = sheet.createRow(rowIndex++);
                row.createCell(0).setCellValue(nz(item.getStandardizedName()));
                row.createCell(1).setCellValue("@" + nz(item.getCreatorHandle()));
                row.createCell(2).setCellValue(nz(item.getPlatform()));
                row.createCell(3).setCellValue(nz(item.getPostType()));
                row.createCell(4).setCellValue(nz(item.getContentForm()));
                row.createCell(5).setCellValue(item.getPostedAt() == null
                        ? "" : DATE.format(item.getPostedAt()));
                row.createCell(6).setCellValue(zero(item.getViews()));
                row.createCell(7).setCellValue(zero(item.getLikes()));
                row.createCell(8).setCellValue(zero(item.getComments()));
                row.createCell(9).setCellValue(zero(item.getShares()));
                row.createCell(10).setCellValue(zero(item.getSaves()));
                row.createCell(11).setCellValue(item.getEr() == null ? 0d : item.getEr().doubleValue());
                row.createCell(12).setCellValue(item.isUnsolicited() ? "Yes" : "No");
                row.createCell(13).setCellValue(nz(item.getSource()));
                row.createCell(14).setCellValue(nz(item.getUrl()));
            }

            // A totals row is what the WIP sheet is actually read for.
            Row totals = sheet.createRow(rowIndex + 1);
            Cell label = totals.createCell(5);
            label.setCellValue("Total");
            label.setCellStyle(headerStyle);
            totals.createCell(6).setCellValue(items.stream().mapToLong(i -> zeroL(i.getViews())).sum());
            totals.createCell(7).setCellValue(items.stream().mapToLong(i -> zeroL(i.getLikes())).sum());
            totals.createCell(8).setCellValue(items.stream().mapToLong(i -> zeroL(i.getComments())).sum());

            for (int i = 0; i < COLUMNS.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            log.error("Coverage export failed", e);
            throw ApiException.conflict("Could not build the coverage export.");
        }
    }

    private String csv(List<CoverageItem> items) {
        StringBuilder csv = new StringBuilder();
        csv.append(String.join(",", COLUMNS)).append('\n');
        for (CoverageItem item : items) {
            csv.append(quote(item.getStandardizedName())).append(',')
                    .append(quote("@" + nz(item.getCreatorHandle()))).append(',')
                    .append(quote(item.getPlatform())).append(',')
                    .append(quote(item.getPostType())).append(',')
                    .append(quote(item.getContentForm())).append(',')
                    .append(item.getPostedAt() == null ? "" : DATE.format(item.getPostedAt())).append(',')
                    .append(zeroL(item.getViews())).append(',')
                    .append(zeroL(item.getLikes())).append(',')
                    .append(zeroL(item.getComments())).append(',')
                    .append(zeroL(item.getShares())).append(',')
                    .append(zeroL(item.getSaves())).append(',')
                    .append(item.getEr() == null ? "0" : item.getEr().toPlainString()).append(',')
                    .append(item.isUnsolicited() ? "Yes" : "No").append(',')
                    .append(quote(item.getSource())).append(',')
                    .append(quote(item.getUrl())).append('\n');
        }
        return csv.toString();
    }

    /**
     * Q-B22: a field starting with =, +, - or @ is escaped so Excel treats it as text. A creator
     * handle is user-supplied, and "@SUM(...)" in a cell is a formula-injection route.
     */
    private static String quote(String value) {
        String safe = value == null ? "" : value;
        if (!safe.isEmpty() && "=+-@".indexOf(safe.charAt(0)) >= 0) {
            safe = "'" + safe;
        }
        return '"' + safe.replace("\"", "\"\"") + '"';
    }

    private static String nz(String value) {
        return value == null ? "" : value;
    }

    private static double zero(Long value) {
        return value == null ? 0d : value;
    }

    private static long zeroL(Long value) {
        return value == null ? 0L : value;
    }
}
