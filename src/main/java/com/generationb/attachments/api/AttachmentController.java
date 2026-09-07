package com.generationb.attachments.api;

import com.generationb.attachments.AttachmentResponse;
import com.generationb.attachments.internal.AttachmentService;
import com.generationb.foundation.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * Requirement #5: attachments on cards, briefs, reports and creators.
 */
@RestController
@RequestMapping("/api/attachments")
@RequiredArgsConstructor
public class AttachmentController {

    private final AttachmentService attachmentService;

    @GetMapping
    public ApiResponse<List<AttachmentResponse>> list(@RequestParam String ownerType,
                                                      @RequestParam UUID ownerId) {
        return ApiResponse.of(attachmentService.list(ownerType, ownerId));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<AttachmentResponse> upload(@RequestParam String ownerType,
                                                  @RequestParam UUID ownerId,
                                                  @RequestPart("file") MultipartFile file) {
        return ApiResponse.of(attachmentService.upload(ownerType, ownerId, file));
    }

    /**
     * Redirects to a short-lived signed URL where the provider can sign one, so a large file
     * never occupies an application thread. Falls back to streaming otherwise.
     */
    @GetMapping("/{id}/download")
    public ResponseEntity<?> download(@PathVariable UUID id) {
        AttachmentService.Download download = attachmentService.download(id);

        if (download.isRedirect()) {
            return ResponseEntity.status(302)
                    .location(URI.create(download.signedUrl()))
                    .build();
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition(download.filename()))
                .contentType(MediaType.parseMediaType(download.contentType()))
                .contentLength(download.sizeBytes())
                // Attachments are per-tenant and reachable only with a token; never let a shared
                // cache hold one.
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(new InputStreamResource(download.content().stream()));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        attachmentService.delete(id);
        return ApiResponse.success();
    }

    /**
     * RFC 6266 encoding. The filename is user-supplied, so a naive
     * {@code filename="..."} would let a crafted name break out of the header — the storage
     * layer already strips CR/LF, and this quotes and percent-encodes what remains.
     */
    private String contentDisposition(String filename) {
        String ascii = filename.replaceAll("[^A-Za-z0-9._ -]", "_");
        String encoded = java.net.URLEncoder.encode(filename, StandardCharsets.UTF_8)
                .replace("+", "%20");
        return "attachment; filename=\"" + ascii + "\"; filename*=UTF-8''" + encoded;
    }
}
