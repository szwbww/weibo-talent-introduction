"""Verify country attribution from checked-in source plus pinned official links."""
import hashlib
import json
from pathlib import Path

OUT = Path(__file__).resolve().parent
SOURCE = Path("src/main/resources/contact-country-timezones.json")
countries = json.loads(SOURCE.read_text())["countries"]
direct = {z["id"]: c for c in countries for z in c["zones"]}
links = {}
for line in (OUT / "iana-2026c-backward").read_text().splitlines():
    if line.startswith("Link"):
        p = line.split()
        links[p[2]] = p[4] if len(p) > 4 and p[3] == "#=" else p[1]
english = {}
for line in (OUT / "iana-2026c-iso3166.tab").read_text().splitlines():
    if line and not line.startswith("#"):
        code, label = line.split("\t", 1)
        english[code] = label

def resolve(zone, seen=()):
    if zone in direct:
        return zone, direct[zone]
    if zone in seen:
        raise ValueError("alias cycle: " + str(seen + (zone,)))
    if zone in links:
        return resolve(links[zone], seen + (zone,))
    return None

ids = [line.split("=", 1)[0] for line in Path("src/main/resources/meeting-timezones-zh.properties").read_text().splitlines() if line and not line.startswith("#")]
rows, unmapped = {}, []
for zone in ids:
    r = resolve(zone)
    if zone == "UTC":
        rows[zone] = {"countryCode": "UTC", "countryLabelZh": "协调世界时", "countryLabelEn": "Coordinated Universal Time", "canonicalZoneId": "UTC"}
    elif r:
        canonical, country = r
        rows[zone] = {"countryCode": country["code"], "countryLabelZh": country["labelZh"], "countryLabelEn": english[country["code"]], "canonicalZoneId": canonical}
    else:
        unmapped.append(zone)
assert all(z.startswith("SystemV/") for z in unmapped)
assert rows["Brazil/East"]["countryCode"] == "BR"
assert rows["Pacific/Ponape"]["countryCode"] == "FM"
report = {
    "country_count": len(countries), "country_zone_count": len(direct),
    "catalog_count": len(ids), "mapped_including_UTC": len(rows),
    "unmapped": unmapped,
    "mapped_country_count_excluding_UTC": len({r["countryCode"] for r in rows.values()} - {"UTC"}),
    "mapping": rows,
    "sha256": {str(p): hashlib.sha256(p.read_bytes()).hexdigest() for p in [SOURCE, OUT / "iana-2026c-backward", OUT / "iana-2026c-iso3166.tab", OUT / "iana-2026c-zone.tab"]},
    "source": "https://data.iana.org/time-zones/releases/tzdata2026c.tar.gz",
    "notes": ["Exact country zone entry wins over merged tzdb links.", "#= original target wins over cross-country merged target.", "SystemV is not a country; do not guess.", "This is audit evidence, not a runtime resource."]
}
(OUT / "country-mapping-audit.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n")
print(json.dumps({k:v for k,v in report.items() if k not in ["mapping", "sha256"]}, ensure_ascii=False))
