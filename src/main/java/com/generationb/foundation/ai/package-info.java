/**
 * The LLM client. Exposed as a named interface so briefs and outreach can generate copy without
 * reaching into foundation's internals, and so there is exactly one place that knows the
 * provider, the model and the failure behaviour.
 */
@org.springframework.modulith.NamedInterface("ai")
package com.generationb.foundation.ai;
