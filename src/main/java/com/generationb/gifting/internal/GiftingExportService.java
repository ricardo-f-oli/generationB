package com.generationb.gifting.internal;

import com.generationb.foundation.ApiException;
import com.generationb.foundation.BrandContext;
import com.generationb.shared.CreatorLookupPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Requirement #42: the fulfilment-house upload.
 *
 * <p>The previous version returned a made-up {@code /downloads/...} URL that pointed at nothing.
 * This builds a real workbook and streams it back.
 *
 * <p>The column set is our best reading of a standard courier manifest. EC's own template has
 * not been shared yet, so the header row is defined in one constant here — swapping it for
 * theirs is a one-line change once we have it.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GiftingExportService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static final String[] COLUMNS = {
            "Recipient name", "Address line 1", "Address line 2", "Town/City", "County",
            "Postcode", "Country", "Phone", "Product", "SKU", "Packaging notes",
            "Courier", "Planned dispatch", "Reference"
    };

    private final DispatchRepository dispatchRepository;
    private final GiftingAddressRepository addressRepository;
    private final CreatorLookupPort creatorLookup;

    public record Export(byte[] content, String filename, String contentType) {
    }

    @Transactional(readOnly = true)
    public Export exportForFulfilment(UUID giftingRunId) {
        UUID brandId = BrandContext.requireBrandId();

        List<Dispatch> dispatches = giftingRunId != null
                ? dispatchRepository.findByGiftingRunId(giftingRunId).stream()
                        .filter(d -> brandId.equals(d.getBrandId())).toList()
                : dispatchRepository.findAllForBrand(brandId).stream()
                        .filter(d -> Dispatch.READY.equals(d.getStatus())).toList();

        if (dispatches.isEmpty()) {
            throw ApiException.unprocessable("There is nothing ready to dispatch to export.");
        }

        List<UUID> creatorIds = dispatches.stream().map(Dispatch::getCreatorId).distinct().toList();
        Map<UUID, GiftingAddress> addresses = addressRepository.findAllByCreatorIdIn(creatorIds)
                .stream().collect(Collectors.toMap(GiftingAddress::getCreatorId, a -> a, (a, b) -> a));
        Map<UUID, CreatorLookupPort.CreatorContact> contacts = creatorLookup.findContacts(creatorIds)
                .stream().collect(Collectors.toMap(
                        CreatorLookupPort.CreatorContact::creatorId, c -> c, (a, b) -> a));

        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Dispatch list");

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
            int skipped = 0;
            for (Dispatch dispatch : dispatches) {
                GiftingAddress address = addresses.get(dispatch.getCreatorId());
                if (address == null || !address.isCaptured()) {
                    // A row with no address is useless to the courier; leave it out and say so.
                    skipped++;
                    continue;
                }
                CreatorLookupPort.CreatorContact contact = contacts.get(dispatch.getCreatorId());

                Row row = sheet.createRow(rowIndex++);
                row.createCell(0).setCellValue(address.getRecipientName() != null
                        ? address.getRecipientName()
                        : (contact == null ? "" : contact.fullName()));
                row.createCell(1).setCellValue(nullToEmpty(address.getStreet()));
                row.createCell(2).setCellValue(nullToEmpty(address.getStreet2()));
                row.createCell(3).setCellValue(nullToEmpty(address.getCity()));
                row.createCell(4).setCellValue(nullToEmpty(address.getCounty()));
                row.createCell(5).setCellValue(nullToEmpty(address.getPostalCode()));
                row.createCell(6).setCellValue(nullToEmpty(address.getCountry()));
                row.createCell(7).setCellValue(nullToEmpty(address.getPhone()));
                row.createCell(8).setCellValue(nullToEmpty(dispatch.getProductName()));
                row.createCell(9).setCellValue(nullToEmpty(dispatch.getSku()));
                row.createCell(10).setCellValue(nullToEmpty(dispatch.getPackagingNotes()));
                row.createCell(11).setCellValue(nullToEmpty(dispatch.getCourier()));
                row.createCell(12).setCellValue(dispatch.getPlannedDispatchDate() == null
                        ? "" : DATE.format(dispatch.getPlannedDispatchDate()));
                row.createCell(13).setCellValue(dispatch.getId().toString().substring(0, 8));
            }

            if (rowIndex == 1) {
                throw ApiException.unprocessable(
                        "None of these dispatches has a confirmed address yet.");
            }
            for (int i = 0; i < COLUMNS.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(out);
            log.info("Fulfilment export: {} row(s), {} skipped without an address",
                    rowIndex - 1, skipped);

            return new Export(out.toByteArray(), "gifting-dispatch-list.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Fulfilment export failed", e);
            throw ApiException.conflict("Could not build the dispatch list.");
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
