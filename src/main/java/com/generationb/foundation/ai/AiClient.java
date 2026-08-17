package com.generationb.foundation.ai;

import java.util.Optional;

/**
 * One place that knows which LLM we call and what happens when it is unavailable.
 *
 * <p>Q-B18/Q-B19: the two services that used the LLM each embedded their own HTTP call, their own
 * API key property, and a model id (<code>claude-sonnet-4-6</code>) that does not exist — so
 * every "AI" feature failed at runtime and was caught and swallowed. Both now go through here.
 *
 * <p>Generation never throws. A caller gets {@link Optional#empty()} and falls back to its own
 * deterministic draft, because a missing API key must not break a screen.
 */
public interface AiClient {

    /**
     * @param systemPrompt who the model is and what it must not do
     * @param userPrompt   the actual request, with the brand's context already merged in
     * @return the model's text, or empty if the provider is not configured or did not answer
     */
    Optional<String> generate(String systemPrompt, String userPrompt);

    /** Whether a key is configured. Screens use this to say "AI drafting is off" honestly. */
    boolean isEnabled();
}
