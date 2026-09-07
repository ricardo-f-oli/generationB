package com.generationb.foundation.storage;

import com.generationb.foundation.ApiException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Unit tests for the rules that decide what may be stored and under what key.
 *
 * <p>Pure logic, no Spring — these are the checks standing between a user-supplied filename and
 * the filesystem, so they are worth testing exhaustively and cheaply.
 */
class StorageKeysTest {

    private static final UUID BRAND = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER_BRAND = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Nested
    @DisplayName("filename sanitising")
    class Filenames {

        @ParameterizedTest(name = "{0} is stripped to its basename")
        @ValueSource(strings = {
                "../../../etc/passwd",
                "..\\..\\windows\\system32\\config",
                "/absolute/path/brief.pdf",
                "C:\\Users\\someone\\brief.pdf"
        })
        void stripsAnyDirectoryComponent(String hostile) {
            String safe = StorageKeys.safeFilename(hostile);
            assertThat(safe).doesNotContain("/").doesNotContain("\\").doesNotContain("..");
        }

        @Test
        @DisplayName("a filename cannot inject a second HTTP header")
        void removesCarriageReturnsAndNewlines() {
            // Content-Disposition echoes this back; a raw CRLF would end the header and let the
            // rest be interpreted as a new one.
            String safe = StorageKeys.safeFilename("invoice.pdf\r\nSet-Cookie: admin=true");
            assertThat(safe).doesNotContain("\r").doesNotContain("\n");
        }

        @Test
        void stripsLeadingDotsSoNothingBecomesAHiddenFile() {
            assertThat(StorageKeys.safeFilename("...hidden.txt")).isEqualTo("hidden.txt");
        }

        @Test
        void fallsBackWhenNothingUsableRemains() {
            assertThat(StorageKeys.safeFilename("///")).isEqualTo("file");
            assertThat(StorageKeys.safeFilename("")).isEqualTo("file");
            assertThat(StorageKeys.safeFilename(null)).isEqualTo("file");
        }

        @Test
        void truncatesAbsurdlyLongNames() {
            String safe = StorageKeys.safeFilename("a".repeat(500) + ".pdf");
            assertThat(safe.length()).isLessThanOrEqualTo(120);
        }

        @Test
        void keepsOrdinaryNamesReadable() {
            assertThat(StorageKeys.safeFilename("Autumn brief v2.pdf"))
                    .isEqualTo("Autumn brief v2.pdf");
        }
    }

    @Nested
    @DisplayName("content type and size")
    class Validation {

        @Test
        void acceptsTheTypesACampaignActuallyUses() {
            assertDoesNotThrow(() -> StorageKeys.validate("image/jpeg", 1024));
            assertDoesNotThrow(() -> StorageKeys.validate("application/pdf", 1024));
            assertDoesNotThrow(() -> StorageKeys.validate("video/mp4", 1024));
        }

        @Test
        void ignoresACharsetParameter() {
            assertDoesNotThrow(() -> StorageKeys.validate("text/csv; charset=UTF-8", 512));
        }

        @ParameterizedTest(name = "rejects {0}")
        @ValueSource(strings = {
                "image/svg+xml",        // carries script
                "text/html",            // stored XSS if ever served from our origin
                "application/x-sh",
                "application/x-msdownload",
                "application/octet-stream"
        })
        void rejectsAnythingNotOnTheAllowlist(String contentType) {
            assertThatThrownBy(() -> StorageKeys.validate(contentType, 1024))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("cannot accept");
        }

        @Test
        void rejectsAMissingContentType() {
            assertThatThrownBy(() -> StorageKeys.validate(null, 1024))
                    .isInstanceOf(ApiException.class);
        }

        @Test
        void rejectsEmptyAndOversizedFiles() {
            assertThatThrownBy(() -> StorageKeys.validate("image/png", 0))
                    .isInstanceOf(ApiException.class).hasMessageContaining("empty");

            assertThatThrownBy(() -> StorageKeys.validate("image/png", StorageKeys.MAX_BYTES + 1))
                    .isInstanceOf(ApiException.class).hasMessageContaining("larger than");
        }

        @Test
        void acceptsExactlyTheLimit() {
            assertDoesNotThrow(() -> StorageKeys.validate("image/png", StorageKeys.MAX_BYTES));
        }
    }

    @Nested
    @DisplayName("tenant isolation")
    class Isolation {

        @Test
        void everyKeyStartsWithItsBrand() {
            String key = StorageKeys.build(BRAND, "attachments", "brief.pdf");
            assertThat(key).startsWith(BRAND + "/attachments/");
        }

        @Test
        void twoUploadsOfTheSameNameDoNotCollide() {
            String first = StorageKeys.build(BRAND, "attachments", "brief.pdf");
            String second = StorageKeys.build(BRAND, "attachments", "brief.pdf");
            assertThat(first).isNotEqualTo(second);
        }

        @Test
        @DisplayName("a key from another tenant is refused")
        void refusesAKeyBelongingToAnotherBrand() {
            String theirs = StorageKeys.build(OTHER_BRAND, "attachments", "confidential.pdf");

            assertThatThrownBy(() -> StorageKeys.requireOwnedBy(BRAND, theirs))
                    .isInstanceOf(ApiException.class);
        }

        @Test
        @DisplayName("a prefix that merely looks like the brand is refused")
        void refusesAKeyThatOnlyResemblesTheBrandPrefix() {
            // Without the trailing slash check this would pass for brand 1111...
            assertThatThrownBy(() -> StorageKeys.requireOwnedBy(BRAND, BRAND + "-evil/x/file.pdf"))
                    .isInstanceOf(ApiException.class);
        }

        @Test
        void refusesTraversalInsideAnOtherwiseValidKey() {
            assertThatThrownBy(() ->
                    StorageKeys.requireOwnedBy(BRAND, BRAND + "/attachments/../../secrets"))
                    .isInstanceOf(ApiException.class);
        }

        @Test
        void refusesNulls() {
            assertThatThrownBy(() -> StorageKeys.requireOwnedBy(BRAND, null))
                    .isInstanceOf(ApiException.class);
        }

        @Test
        void acceptsAKeyItBuiltItself() {
            String key = StorageKeys.build(BRAND, "attachments", "brief.pdf");
            assertDoesNotThrow(() -> StorageKeys.requireOwnedBy(BRAND, key));
        }

        @Test
        void normalisesAnOddCategoryRatherThanTrustingIt() {
            String key = StorageKeys.build(BRAND, "../../etc", "brief.pdf");
            assertThat(key).doesNotContain("..");
        }
    }
}
