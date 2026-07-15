# Mobile domain ownership human gate

Owner roles: DNS/domain owner, Android release-signing owner, Apple Developer Team owner.

Required non-secret inputs:

1. The SHA-256 fingerprint of the actual Android release/app-signing certificate (not the debug certificate).
2. The 10-character Apple Developer Team ID used to sign bundle `sg.innercosmos.app`.
3. DNS and HTTPS publishing authority for `app.innercosmos.sg`.

Render into a review directory:

```powershell
python scripts/mobile_domain_association.py render `
  --fingerprint "AA:...:FF" `
  --team-id "A1B2C3D4E5" `
  --output build/mobile-domain-association
```

Publish the two generated files without redirects or authentication:

- `https://app.innercosmos.sg/.well-known/assetlinks.json`
- `https://app.innercosmos.sg/.well-known/apple-app-site-association`

Both responses must be HTTPS 200 and `application/json` (Apple may also use `application/pkcs7-mime`). Then run:

```powershell
python scripts/mobile_domain_association.py online `
  --fingerprint "AA:...:FF" `
  --team-id "A1B2C3D4E5"
```

Device acceptance:

- Install the release-signed Android build, reset link verification, run Android's domain-verification command, and open an Aurora wake link without an app chooser.
- Install the signed iOS build, open the same HTTPS link from Notes/Mail, and verify Universal Link routing plus browser fallback when the app is absent.
- Capture OS version, app version/commit, signing identity hash (never private keys), command output and screen recording.

Failure rollback:

- Remove the association files or remove the affected app entry, purge CDN caches, and keep ordinary HTTPS browser routing operational.
- Do not widen paths, publish debug fingerprints, substitute a different Team ID, or fall back to the custom scheme as proof of domain ownership.
