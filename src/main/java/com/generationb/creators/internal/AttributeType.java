package com.generationb.creators.internal;

import com.generationb.foundation.ApiException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Requirement #16: what a custom attribute is allowed to hold.
 *
 * <p>The definitions have carried a {@code type} since they were introduced, but nothing read it:
 * {@code setAttributeValue} stored whatever string arrived, so a "Birthday" attribute could hold
 * "next Tuesday" and a "Dress size" could hold "ask Amber". The values are typed the moment
 * anyone tries to filter, sort or merge on them, and by then the bad rows are already in.
 *
 * <p>Values are stored as text — one sparse table across every brand's schema, which is the right
 * trade for something a brand admin redefines at will. So the type lives here rather than in the
 * column, and this enum is the only thing that enforces it.
 *
 * <p>Validation also <em>normalises</em>. A date typed as {@code 03/04/2026} and one pasted as
 * {@code 2026-04-03} must land in the column identically, or the two creators sort apart and no
 * filter finds both.
 */
public enum AttributeType {

    /** Free text. The default, and the only type that accepts anything. */
    STRING {
        @Override
        String check(String value, List<String> options) {
            return value;
        }
    },

    NUMBER {
        @Override
        String check(String value, List<String> options) {
            try {
                // Returned via BigDecimal so "007" and "7.0" both normalise to "7".
                return new BigDecimal(value.replace(",", "")).stripTrailingZeros()
                        .toPlainString();
            } catch (NumberFormatException e) {
                throw ApiException.badRequest("\"" + value + "\" is not a number.");
            }
        }
    },

    /** ISO on the way in and on the way out, whatever the person typed. */
    DATE {
        @Override
        String check(String value, List<String> options) {
            for (var formatter : DATE_FORMATS) {
                try {
                    return LocalDate.parse(value, formatter).toString();
                } catch (DateTimeParseException ignored) {
                    // Try the next shape.
                }
            }
            throw ApiException.badRequest(
                    "\"" + value + "\" is not a date. Use 2026-04-03 or 03/04/2026.");
        }
    },

    BOOLEAN {
        @Override
        String check(String value, List<String> options) {
            String lower = value.toLowerCase(Locale.UK);
            if (TRUTHY.contains(lower)) {
                return "true";
            }
            if (FALSY.contains(lower)) {
                return "false";
            }
            throw ApiException.badRequest("\"" + value + "\" is not a yes or no.");
        }
    },

    /** One of the defined options. */
    SELECT {
        @Override
        String check(String value, List<String> options) {
            return matchOption(value, options);
        }

        @Override
        public boolean needsOptions() {
            return true;
        }
    },

    /** Several of the defined options, stored comma-separated. */
    MULTI_SELECT {
        @Override
        String check(String value, List<String> options) {
            List<String> chosen = Arrays.stream(value.split(","))
                    .map(String::trim)
                    .filter(part -> !part.isEmpty())
                    .map(part -> matchOption(part, options))
                    .distinct()
                    .toList();
            if (chosen.isEmpty()) {
                throw ApiException.badRequest("Choose at least one option.");
            }
            return String.join(",", chosen);
        }

        @Override
        public boolean needsOptions() {
            return true;
        }
    },

    URL {
        @Override
        String check(String value, List<String> options) {
            // A creator pastes "instagram.com/x" far more often than a full URL, so the scheme
            // is added rather than the value rejected.
            String candidate = value.matches("(?i)^https?://.*") ? value : "https://" + value;
            try {
                java.net.URI uri = java.net.URI.create(candidate);
                if (uri.getHost() == null || !uri.getHost().contains(".")) {
                    throw new IllegalArgumentException();
                }
                return candidate;
            } catch (IllegalArgumentException e) {
                throw ApiException.badRequest("\"" + value + "\" is not a web address.");
            }
        }
    },

    EMAIL {
        @Override
        String check(String value, List<String> options) {
            String trimmed = value.toLowerCase(Locale.UK);
            if (!EMAIL_PATTERN.matcher(trimmed).matches()) {
                throw ApiException.badRequest("\"" + value + "\" is not an email address.");
            }
            return trimmed;
        }
    };

    /**
     * STRICT, and {@code uuuu} rather than {@code yyyy}.
     *
     * <p>Java's default SMART resolver clamps rather than rejects: {@code 31/02/2026} parses
     * happily and comes back as 28 February. A typo silently becoming a different date is worse
     * than a rejection, because nobody ever finds out — the gift goes out on the wrong day.
     *
     * <p>STRICT requires {@code uuuu} (proleptic year) because {@code yyyy} is year-of-era, which
     * has no meaning without an era field and throws instead.
     */
    private static final List<java.time.format.DateTimeFormatter> DATE_FORMATS = List.of(
            java.time.format.DateTimeFormatter.ISO_LOCAL_DATE
                    .withResolverStyle(java.time.format.ResolverStyle.STRICT),
            strict("dd/MM/uuuu"),
            strict("d/M/uuuu"),
            strict("dd-MM-uuuu"));

    private static java.time.format.DateTimeFormatter strict(String pattern) {
        return java.time.format.DateTimeFormatter.ofPattern(pattern)
                .withResolverStyle(java.time.format.ResolverStyle.STRICT);
    }

    private static final List<String> TRUTHY = List.of("true", "yes", "y", "1");
    private static final List<String> FALSY = List.of("false", "no", "n", "0");

    /** Deliberately permissive: the authority on whether an address works is a delivered email. */
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[^@\\s]+@[^@\\s.]+(\\.[^@\\s.]+)+$");

    /** Validates and normalises. Subclasses assume a non-blank, trimmed value. */
    abstract String check(String value, List<String> options);

    /** True for the types that are meaningless without a list to choose from. */
    public boolean needsOptions() {
        return false;
    }

    /**
     * The entry point.
     *
     * @param raw      what the user typed; null or blank clears the value
     * @param required when true, blank is rejected instead of clearing
     * @return the normalised value to store, or null to clear it
     */
    public String normalise(String raw, List<String> options, boolean required, String label) {
        String trimmed = raw == null ? "" : raw.trim();
        if (trimmed.isEmpty()) {
            if (required) {
                throw ApiException.badRequest(label + " is required.");
            }
            // Clearing an optional attribute is a legitimate edit, not a validation failure.
            return null;
        }
        if (needsOptions() && (options == null || options.isEmpty())) {
            throw ApiException.unprocessable(
                    label + " is a list attribute but has no options defined.");
        }
        return check(trimmed, options);
    }

    private static String matchOption(String value, List<String> options) {
        // Case-insensitive match, but the stored value is the option's own casing — otherwise
        // "Vegan" and "vegan" become two different filter buckets.
        return options.stream()
                .filter(option -> option.equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> ApiException.badRequest(
                        "\"" + value + "\" is not one of: " + String.join(", ", options)));
    }

    /**
     * Parses a stored type name.
     *
     * <p>Unknown names fall back to {@link #STRING} rather than throwing: definitions created
     * before this enum existed carry whatever string was passed, and a brand's creator screen
     * should not 500 because of a row somebody typed in months ago. Creating a <em>new</em>
     * definition goes through {@link #require} instead, which does reject.
     */
    public static AttributeType of(String name) {
        if (name == null) {
            return STRING;
        }
        for (AttributeType type : values()) {
            if (type.name().equalsIgnoreCase(name.trim())) {
                return type;
            }
        }
        return STRING;
    }

    /** Strict parse, for the admin defining a new attribute. */
    public static AttributeType require(String name) {
        if (name == null || name.isBlank()) {
            return STRING;
        }
        for (AttributeType type : values()) {
            if (type.name().equalsIgnoreCase(name.trim())) {
                return type;
            }
        }
        throw ApiException.badRequest("\"" + name + "\" is not a supported attribute type. "
                + "Use one of: " + Arrays.stream(values()).map(Enum::name).toList());
    }
}
