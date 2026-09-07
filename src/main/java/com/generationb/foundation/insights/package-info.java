/**
 * The creator-data vendor client (Modash). Exposed as a named interface so the creators module
 * can reach the vendor without importing foundation's internals, and so there is exactly one
 * place that knows the base URL, the auth scheme, the rate limit and the credit budget.
 *
 * <p>Deliberately transport-only: it returns raw {@code JsonNode} and knows nothing about
 * creators, coverage or brands. The mapping from Modash's shapes onto ours lives in
 * {@code creators.internal}, next to the mock it replaces.
 */
@org.springframework.modulith.NamedInterface("insights")
package com.generationb.foundation.insights;
