#!/usr/bin/env python3
"""Generate the checked-in meeting zone -> country metadata catalog (offline, deterministic).

The runtime never calls this script and never touches the network. It derives, for every
timezone ID published in ``meeting-timezones-zh.properties``, the owning country:

1. an exact zone entry in the existing ``contact-country-timezones.json`` wins;
2. otherwise follow the pinned IANA ``backward`` file: a ``Link`` with a ``#=`` original
   target resolves to that original, every other ``Link`` resolves to its target;
3. alias chains recurse until a country-catalog zone entry is reached; cycles fail.

Country attribution is *never* guessed from labels, offsets or ZoneRules. An unresolved ID
fails the build unless it is one of the explicitly listed ``SystemV/*`` IDs, which have no
country attribution at all.

Usage:

    python3 scripts/generate_meeting_zone_countries.py \\
        --country-catalog src/main/resources/contact-country-timezones.json \\
        --zone-labels src/main/resources/meeting-timezones-zh.properties \\
        --backward <iana-2026c-backward> \\
        --iso3166 <iana-2026c-iso3166.tab> \\
        --output src/main/resources/meeting-zone-countries.properties

Output format (one line per zone ID, sorted by ID, after ``#`` header comments):

    <zoneId>=<countryCode>\\t<countryLabelZh>\\t<countryLabelEn>\\t<canonicalZoneId>

``UTC`` is a fixed special entry (code ``UTC``). The 13 ``SystemV/*`` IDs are omitted.
"""

import argparse
import hashlib
import json
from pathlib import Path

#: IDs that legitimately have no country attribution. Fixed to this round's audit list so
#: an arbitrary unknown ``SystemV/`` prefix can never silently count as success.
SYSTEMV_EXEMPT = (
    "SystemV/AST4",
    "SystemV/AST4ADT",
    "SystemV/CST6",
    "SystemV/CST6CDT",
    "SystemV/EST5",
    "SystemV/EST5EDT",
    "SystemV/HST10",
    "SystemV/MST7",
    "SystemV/MST7MDT",
    "SystemV/PST8",
    "SystemV/PST8PDT",
    "SystemV/YST9",
    "SystemV/YST9YDT",
)

UTC_ENTRY = ("UTC", "协调世界时", "Coordinated Universal Time", "UTC")


class GenerationError(ValueError):
    """Raised when inputs cannot be attributed without guessing."""


def sha256_hex(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def read_zone_ids(zone_labels_path):
    ids = []
    for line in Path(zone_labels_path).read_text(encoding="utf-8").splitlines():
        if not line or line.startswith("#"):
            continue
        key, separator, _ = line.partition("=")
        key = key.strip()
        if not separator or not key:
            raise GenerationError("malformed zone label line: " + line)
        ids.append(key)
    if not ids:
        raise GenerationError("zone label catalog is empty: " + str(zone_labels_path))
    return ids


def read_country_catalog(country_catalog_path):
    payload = json.loads(Path(country_catalog_path).read_text(encoding="utf-8"))
    countries = payload["countries"]
    direct = {}
    for country in countries:
        for zone in country["zones"]:
            direct[zone["id"]] = country
    return payload, direct


def read_links(backward_path):
    links = {}
    for line in Path(backward_path).read_text(encoding="utf-8").splitlines():
        if not line.startswith("Link"):
            continue
        parts = line.split()
        # Link TARGET LINK-NAME [#= ORIGINAL]
        target, name = parts[1], parts[2]
        links[name] = parts[4] if len(parts) > 4 and parts[3] == "#=" else target
    return links


def read_english_labels(iso3166_path):
    english = {}
    for line in Path(iso3166_path).read_text(encoding="utf-8").splitlines():
        if line and not line.startswith("#"):
            code, label = line.split("\t", 1)
            english[code] = label
    return english


def resolve(zone, direct, links, seen=()):
    """Return ``(canonicalZoneId, country)`` or ``None``; never guess from names/offsets."""
    if zone in direct:
        return zone, direct[zone]
    if zone in seen:
        raise GenerationError("alias cycle: " + str(seen + (zone,)))
    if zone in links:
        return resolve(links[zone], direct, links, seen + (zone,))
    return None


def build_rows(country_catalog_path, zone_labels_path, backward_path, iso3166_path):
    zone_ids = read_zone_ids(zone_labels_path)
    _, direct = read_country_catalog(country_catalog_path)
    links = read_links(backward_path)
    english = read_english_labels(iso3166_path)

    rows = {}
    unmapped = []
    for zone_id in zone_ids:
        if zone_id == "UTC":
            rows[zone_id] = UTC_ENTRY
            continue
        resolved = resolve(zone_id, direct, links)
        if resolved is None:
            if zone_id not in SYSTEMV_EXEMPT:
                unmapped.append(zone_id)
            continue
        canonical, country = resolved
        code = country["code"]
        if code not in english:
            raise GenerationError("missing English country label for " + code + " (" + zone_id + ")")
        label_zh = country["labelZh"]
        label_en = english[code]
        if not label_zh or not label_en or not canonical:
            raise GenerationError("missing country attribution for " + zone_id)
        rows[zone_id] = (code, label_zh, label_en, canonical)

    if unmapped:
        raise GenerationError("unmapped zone IDs without a country: " + ", ".join(sorted(unmapped)))
    # An exempt ID must genuinely be unmapped: if a country entry appears for it, stop.
    mapped_exempt = [zone_id for zone_id in SYSTEMV_EXEMPT if zone_id in rows]
    if mapped_exempt:
        raise GenerationError("SystemV IDs unexpectedly attributed: " + ", ".join(mapped_exempt))
    return rows


def render(rows, country_catalog_path, zone_labels_path, backward_path, iso3166_path, source_version):
    lines = [
        "# Generated by scripts/generate_meeting_zone_countries.py from IANA tzdb "
        + source_version + ". Do not edit by hand.",
        "# sources:"
        + " contact-country-timezones.json=" + sha256_hex(country_catalog_path)
        + " meeting-timezones-zh.properties=" + sha256_hex(zone_labels_path)
        + " backward=" + sha256_hex(backward_path)
        + " iso3166.tab=" + sha256_hex(iso3166_path),
        "# entries: " + str(len(rows)),
    ]
    for zone_id in sorted(rows):
        code, label_zh, label_en, canonical = rows[zone_id]
        lines.append(zone_id + "=" + "\t".join([code, label_zh, label_en, canonical]))
    return "\n".join(lines) + "\n"


def generate(country_catalog_path, zone_labels_path, backward_path, iso3166_path, output_path):
    payload, _ = read_country_catalog(country_catalog_path)
    source_version = str(payload.get("sourceVersion") or "unknown")
    rows = build_rows(country_catalog_path, zone_labels_path, backward_path, iso3166_path)
    text = render(
        rows,
        country_catalog_path,
        zone_labels_path,
        backward_path,
        iso3166_path,
        source_version,
    )
    Path(output_path).write_text(text, encoding="utf-8")
    return text


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--country-catalog", required=True)
    parser.add_argument("--zone-labels", required=True)
    parser.add_argument("--backward", required=True)
    parser.add_argument("--iso3166", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()
    try:
        text = generate(
            args.country_catalog,
            args.zone_labels,
            args.backward,
            args.iso3166,
            args.output,
        )
    except GenerationError as error:
        parser.exit(1, "generation failed: " + str(error) + "\n")
    entries = sum(1 for line in text.splitlines() if line and not line.startswith("#"))
    print(json.dumps({"entries": entries, "output": str(args.output)}, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
