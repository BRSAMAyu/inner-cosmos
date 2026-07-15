# Known limitations

- Only the Aurora journey has migrated to React; the complete five-space shell has not.
- Durable replay is covered by backend integration and TypeScript protocol tests, but
  a browser network fault/reconnect has not yet been injected end to end.
- Browser evaluation used the project Mock LLM. Real Provider A/B output generation
  is gated on four ephemeral approved configuration values in `INNO-EVAL-002`.
- No human pairwise preference result exists, so no model-quality superiority is claimed.
