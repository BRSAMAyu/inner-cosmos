# Singapore safety-resource verification — 2026-07-31

Status: **machine-verified implementation; human language review remains open**
Scope: `locale=en-SG`, `region=SG` only. This is a reachability and source-governance record, not medical advice.

## Current product contract

| Need | Product entry | Channel | Primary authority checked |
| --- | --- | --- | --- |
| Immediate police danger | Singapore Police `999` | telephone | [gov.sg emergency contacts](https://www.gov.sg/contact-us/) |
| Emergency ambulance or fire | SCDF `995` | telephone | [SCDF Emergency Medical Services](https://www.scdf.gov.sg/home/about-scdf/emergency-medical-services) |
| General mental-health support | national mindline `1771` | telephone, 24/7 | [Singapore MOH mental-health services](https://www.moh.gov.sg/seeking-healthcare/find-a-facility-or-service/mental-health-services/) |
| General mental-health support | national mindline `6669 1771` | WhatsApp, 24/7 | [Singapore MOH mental-health services](https://www.moh.gov.sg/seeking-healthcare/find-a-facility-or-service/mental-health-services/) |
| Suicide crisis support | Samaritans of Singapore `1767` | telephone, 24/7 | [SOS support for people in distress](https://www.sos.org.sg/support-those-in-distress/) |
| Suicide crisis support | SOS CareText `9151 1767` | WhatsApp, 24/7 | [SOS support for people in distress](https://www.sos.org.sg/support-those-in-distress/) |

The MOH launch record independently describes national mindline's telephone, WhatsApp and web-chat channels: [national mindline 1771 announcement](https://www.moh.gov.sg/newsroom/national-mindline-1771-to-provide--round-the-clock-support-for-mental-health/).

## Safety boundaries encoded in the product

- The catalog is selected only when locale and region agree on Singapore; missing or conflicting context falls back to number-free global guidance.
- Telephone resources use `tel:` links. WhatsApp resources use international-format `https://wa.me/65...` links, so a text channel is never mislabeled or launched as a voice call.
- The catalog deliberately excludes the legacy IMH helpline `6389 2222` and the unrelated ambulance number `1777`; regression tests prevent either from entering the Singapore safety copy.
- Inner Cosmos does not diagnose, provide emergency care, or replace emergency services, clinicians, counsellors or crisis lines.

## Verification evidence

- Backend contract tests assert exact numbers, channels, region, source date and product boundary.
- Frontend tests assert `tel:` for telephone resources and `wa.me` with Singapore country code for WhatsApp resources.
- Focused verification on 2026-07-31: Java `45/45` and web `13/13` tests passed.
- Full local verification: Java `1503` tests, `0` failures, `0` errors and `31` Docker-dependent skips; SpotBugs reported `0` findings. Web `696/696` tests, production build and OpenAPI generated-contract check passed.
- Fresh isolated-demo browser acceptance: signed in, switched through the visible language control to English, opened `/app/aurora/safety-harbor`, observed `lang=en-SG`, all six exact channel-correct links, the product boundary, and `0` application-console errors or warnings.

Remaining acceptance boundary: a non-author must complete the full English five-space browser journey and review wording in context before `SG-PRODUCT` can become `PASS`.
