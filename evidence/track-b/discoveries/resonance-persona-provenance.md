# Resolved: the 11 Resonance sample personas are intentional, permanent product content

B0 (`evidence/track-b/golden-journeys.md`, J3 step 3) flagged that every fresh account sees 11
fully-written capsule personas (洛哥, 苏格拉底, 庄周, ...) in Plaza/Resonance discovery, and left
their provenance — intentional showcase content vs. leaked seed/dev data — as an open question
for B1/B2 to resolve before designing the Resonance first-run state.

## Answer (source-confirmed, read-only, no Java edited)

They are intentional. `src/main/java/com/innercosmos/config/SeedCapsuleContent.java` is a
dedicated config class whose class-level Javadoc states outright:

> "Official seed EchoCapsules. These are product-designed agents, not user clones."

This is corroborated by `SeedCapsule`'s definition and companions in
`AuroraContentLibrary.java`/`AuroraChatController.java`/`AuroraAgentServiceImpl.java` — the same
philosopher/persona names appear throughout the AI prompt/content layer, not in any user-data
table or seed-account fixture. These are permanent, curated "practice capsule" content the product
ships with, analogous to a showcase gallery, not test/QA noise (which is the separate, real problem
already filed as `TB-REQ-001`).

## Product decision this unblocks for B2

Design the Resonance first-run/empty state to **showcase these proudly** as a safe way to
experience Capsule conversation before a user has authored their own — not hide or downplay them.
The remaining design work is giving them clear, honest provenance framing in the UI (e.g. an
"official sample capsule" badge/label distinct from real-user capsules) so a new user never
mistakes "洛哥" or "苏格拉底" for an actual person on the platform. That labeling gap — not the
personas' existence — is the real remaining B2 item.
