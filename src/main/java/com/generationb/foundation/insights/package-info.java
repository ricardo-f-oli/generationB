/**
 * The platform API clients for creator data: Meta's Graph API (Instagram) and the YouTube Data
 * API, plus the hashtag allowance and the cipher for connected accounts' tokens. Exposed as a
 * named interface so the creators module can reach them without importing foundation's
 * internals, and so there is exactly one place that knows each base URL, auth scheme and limit.
 *
 * <p>Deliberately transport-only: the clients return raw {@code JsonNode} and know nothing about
 * creators, coverage or brands. Mapping onto our own shapes lives in {@code creators.internal}.
 */
@org.springframework.modulith.NamedInterface("insights")
package com.generationb.foundation.insights;
