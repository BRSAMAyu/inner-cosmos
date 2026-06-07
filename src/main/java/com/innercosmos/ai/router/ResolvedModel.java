package com.innercosmos.ai.router;

import com.innercosmos.ai.client.LlmClient;

/**
 * The result of {@link SessionModelRouter#resolve(Long, Long)}: which provider
 * the request will use, the model name (for display / logging), and the actual
 * {@link LlmClient} to dispatch to.
 */
public record ResolvedModel(String provider, String model, LlmClient client) {
    public boolean isResolved() {
        return client != null;
    }
}
