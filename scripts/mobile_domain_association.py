#!/usr/bin/env python3
"""Render and verify Android/iOS domain association without inventing owner identifiers."""

from __future__ import annotations

import argparse
import json
import plistlib
import re
import sys
import urllib.request
from pathlib import Path

PACKAGE = "sg.innercosmos.app"
DOMAIN = "app.innercosmos.sg"
FINGERPRINT = re.compile(r"^(?:[0-9A-Fa-f]{2}:){31}[0-9A-Fa-f]{2}$|^[0-9A-Fa-f]{64}$")
TEAM_ID = re.compile(r"^[A-Z0-9]{10}$")


def root_from_script() -> Path:
    return Path(__file__).resolve().parents[1]


def normalize_fingerprint(value: str) -> str:
    if not FINGERPRINT.fullmatch(value):
        raise ValueError("Android SHA-256 fingerprint must contain exactly 32 hexadecimal bytes")
    compact = value.replace(":", "").upper()
    return ":".join(compact[index:index + 2] for index in range(0, 64, 2))


def validate_team_id(value: str) -> str:
    candidate = value.strip().upper()
    if not TEAM_ID.fullmatch(candidate):
        raise ValueError("Apple Team ID must be exactly 10 uppercase letters or digits")
    return candidate


def validate_native_configuration(root: Path) -> dict[str, object]:
    manifest = (root / "web/android/app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
    https_filter = re.search(r'<intent-filter\s+android:autoVerify="true">(?P<body>.*?)</intent-filter>', manifest, re.S)
    if not https_filter or 'android:scheme="https"' not in https_filter.group("body") \
            or f'android:host="{DOMAIN}"' not in https_filter.group("body") \
            or 'android:pathPrefix="/app/aurora"' not in https_filter.group("body"):
        raise ValueError("Android verified-link filter is missing or not scoped to the owned Aurora route")

    entitlements_path = root / "web/ios/App/App/App.entitlements"
    with entitlements_path.open("rb") as handle:
        entitlements = plistlib.load(handle)
    if entitlements.get("com.apple.developer.associated-domains") != [f"applinks:{DOMAIN}"]:
        raise ValueError("iOS associated-domains entitlement is missing or over-broad")
    project = (root / "web/ios/App/App.xcodeproj/project.pbxproj").read_text(encoding="utf-8")
    if project.count("CODE_SIGN_ENTITLEMENTS = App/App.entitlements;") != 2:
        raise ValueError("iOS Debug and Release must both use App.entitlements")

    templates = root / "deploy/mobile-domain-association"
    android = json.loads((templates / "assetlinks.template.json").read_text(encoding="utf-8"))
    apple = json.loads((templates / "apple-app-site-association.template.json").read_text(encoding="utf-8"))
    if android[0]["target"]["package_name"] != PACKAGE \
            or android[0]["target"]["sha256_cert_fingerprints"] != ["${ANDROID_SHA256_CERT_FINGERPRINT}"]:
        raise ValueError("Android association template is not fail-closed")
    if apple["applinks"]["details"][0]["appID"] != f"${{APPLE_TEAM_ID}}.{PACKAGE}":
        raise ValueError("Apple association template is not fail-closed")
    return {"status": "PASS", "domain": DOMAIN, "package": PACKAGE, "native_configuration": True}


def render(root: Path, output: Path, fingerprint: str, team_id: str) -> dict[str, object]:
    normalized = normalize_fingerprint(fingerprint)
    team = validate_team_id(team_id)
    templates = root / "deploy/mobile-domain-association"
    assetlinks = json.loads((templates / "assetlinks.template.json").read_text(encoding="utf-8")
                            .replace("${ANDROID_SHA256_CERT_FINGERPRINT}", normalized))
    association = json.loads((templates / "apple-app-site-association.template.json").read_text(encoding="utf-8")
                              .replace("${APPLE_TEAM_ID}", team))
    validate_payloads(assetlinks, association, normalized, team)
    well_known = output / ".well-known"
    well_known.mkdir(parents=True, exist_ok=True)
    (well_known / "assetlinks.json").write_text(json.dumps(assetlinks, indent=2) + "\n", encoding="utf-8")
    (well_known / "apple-app-site-association").write_text(json.dumps(association, indent=2) + "\n", encoding="utf-8")
    return {"status": "PASS", "output": str(well_known), "domain": DOMAIN, "package": PACKAGE,
            "app_id": f"{team}.{PACKAGE}"}


def validate_payloads(assetlinks: object, association: object, fingerprint: str, team_id: str) -> None:
    if not isinstance(assetlinks, list) or len(assetlinks) != 1:
        raise ValueError("assetlinks.json must contain exactly one app relationship")
    target = assetlinks[0].get("target", {})
    if target.get("namespace") != "android_app" or target.get("package_name") != PACKAGE \
            or target.get("sha256_cert_fingerprints") != [fingerprint]:
        raise ValueError("assetlinks.json does not match the signed Inner Cosmos app")
    try:
        details = association["applinks"]["details"]
        components = details[0]["components"]
    except (KeyError, IndexError, TypeError) as error:
        raise ValueError("apple-app-site-association has an invalid applinks shape") from error
    paths = {component.get("/") for component in components}
    if len(details) != 1 or details[0].get("appID") != f"{team_id}.{PACKAGE}" \
            or paths != {"/app/aurora*", "/oauth/callback*"}:
        raise ValueError("apple-app-site-association is not scoped to the expected app and routes")


def fetch_json(url: str) -> object:
    request = urllib.request.Request(url, headers={"User-Agent": "inner-cosmos-domain-preflight/1"})
    with urllib.request.urlopen(request, timeout=15) as response:
        if response.geturl() != url:
            raise ValueError(f"Association URL must not redirect: {url}")
        content_type = response.headers.get_content_type()
        if content_type not in {"application/json", "application/pkcs7-mime"}:
            raise ValueError(f"Association URL has unsafe content type {content_type}: {url}")
        return json.loads(response.read().decode("utf-8"))


def online(fingerprint: str, team_id: str, domain: str) -> dict[str, object]:
    if domain != DOMAIN:
        raise ValueError(f"Signed app is pinned to {DOMAIN}; refusing a different domain")
    normalized = normalize_fingerprint(fingerprint)
    team = validate_team_id(team_id)
    base = f"https://{domain}/.well-known"
    assetlinks = fetch_json(f"{base}/assetlinks.json")
    association = fetch_json(f"{base}/apple-app-site-association")
    validate_payloads(assetlinks, association, normalized, team)
    return {"status": "PASS", "domain": domain, "android_verified": True, "ios_verified": True}


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("mode", choices=("configuration", "render", "online"))
    parser.add_argument("--root", type=Path, default=root_from_script())
    parser.add_argument("--output", type=Path, default=Path("build/mobile-domain-association"))
    parser.add_argument("--fingerprint", default="")
    parser.add_argument("--team-id", default="")
    parser.add_argument("--domain", default=DOMAIN)
    args = parser.parse_args(argv)
    try:
        if args.mode == "configuration":
            result = validate_native_configuration(args.root.resolve())
        elif args.mode == "render":
            result = render(args.root.resolve(), args.output.resolve(), args.fingerprint, args.team_id)
        else:
            result = online(args.fingerprint, args.team_id, args.domain)
        print(json.dumps(result, ensure_ascii=False, sort_keys=True))
        return 0
    except (ValueError, OSError, json.JSONDecodeError) as error:
        print(json.dumps({"status": "FAIL", "reason": str(error)}, ensure_ascii=False, sort_keys=True), file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
