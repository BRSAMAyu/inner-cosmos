package com.innercosmos.service;

import com.innercosmos.entity.User;

/**
 * Creates an isolated, non-discoverable copy of a curated classroom story.
 *
 * <p>The returned user owns every generated memory and capsule. Implementations must never return
 * one of the shared template identities.</p>
 */
public interface DemoSandboxService {
    User createPersonalSandbox(String templateKey);
}
