#!/usr/bin/env python3
"""Generate the checked-in Chinese meeting-timezone catalog from Unicode CLDR XML.

The application never reads CLDR at runtime.  Pass the Java runtime's selectable
ZoneId list (one ID per line) so a JDK tzdata upgrade makes catalog coverage
explicit and reviewable.
"""

import argparse
import json
import xml.etree.ElementTree as ET
from pathlib import Path


COMPATIBILITY_LABELS = {
    "America/Ciudad_Juarez": "墨西哥 · 华雷斯城",
    "Europe/Istanbul": "土耳其 · 伊斯坦布尔",
    "Europe/Kyiv": "乌克兰 · 基辅",
    "Asia/Shanghai": "中国 · 北京 / 上海",
    "Europe/London": "英国 · 伦敦",
    "Europe/Berlin": "德国 · 柏林",
    "America/New_York": "美国东部 · 纽约",
    "America/Los_Angeles": "美国西部 · 洛杉矶",
    "Asia/Tokyo": "日本 · 东京",
    "Asia/Kolkata": "印度 · 加尔各答",
    "Asia/Calcutta": "印度 · 加尔各答",
    "Australia/Sydney": "澳大利亚 · 悉尼",
    "Asia/Hong_Kong": "中国 · 香港",
    "Asia/Singapore": "新加坡",
    "Europe/Paris": "法国 · 巴黎",
    "Europe/Moscow": "俄罗斯 · 莫斯科",
    "America/Toronto": "加拿大 · 多伦多",
    "Pacific/Auckland": "新西兰 · 奥克兰",
    "Asia/Dubai": "阿联酋 · 迪拜",
}
COMPATIBILITY_ALIASES = {
    "America/Ciudad_Juarez": ["墨西哥", "华雷斯城", "Mexico", "Ciudad Juárez", "Ciudad Juarez"],
    "Europe/Istanbul": ["土耳其", "伊斯坦布尔", "Turkey", "Türkiye", "Istanbul"],
    "Europe/Kyiv": ["乌克兰", "基辅", "Ukraine", "Kyiv", "Kiev"],
    "Asia/Shanghai": ["中国", "北京", "上海", "China", "Beijing", "Shanghai"],
    "Europe/London": ["英国", "伦敦", "UK", "London"],
    "Europe/Berlin": ["德国", "柏林", "Germany", "Berlin"],
    "America/New_York": ["美国东部", "纽约", "US", "Eastern", "New York"],
    "America/Los_Angeles": ["美国西部", "洛杉矶", "US", "Pacific", "Los Angeles"],
    "Asia/Tokyo": ["日本", "东京", "Japan", "Tokyo"],
    "Asia/Kolkata": ["印度", "加尔各答", "India", "Kolkata"],
    "Asia/Calcutta": ["印度", "加尔各答", "India", "Kolkata", "Calcutta"],
    "Australia/Sydney": ["澳大利亚", "悉尼", "Australia", "Sydney"],
    "Asia/Hong_Kong": ["中国", "香港", "Hong Kong"],
    "Asia/Singapore": ["新加坡", "Singapore"],
    "Europe/Paris": ["法国", "巴黎", "France", "Paris"],
    "Europe/Moscow": ["俄罗斯", "莫斯科", "Russia", "Moscow"],
    "America/Toronto": ["加拿大", "多伦多", "Canada", "Toronto"],
    "Pacific/Auckland": ["新西兰", "奥克兰", "New Zealand", "Auckland"],
    "Asia/Dubai": ["阿联酋", "迪拜", "UAE", "Dubai"],
}
UTC_ENTRY = ("协调世界时", ["协调世界时", "世界协调时间", "UTC", "Coordinated Universal Time"])

# Production uses JDK 8 tzdata, which still exposes these IDs.  Keep the
# checked-in catalog compatible with the build JDK and the production JDK.
RUNTIME_COMPATIBILITY_ZONE_IDS = ["America/Ciudad_Juarez", "Europe/Kyiv"]


def texts_by_type(root, tag):
    return {
        node.attrib["type"]: "".join(node.itertext()).strip()
        for node in root.iter(tag)
        if node.attrib.get("type") and "alt" not in node.attrib and "".join(node.itertext()).strip()
    }


def zone_cities(root):
    result = {}
    for node in root.iter("zone"):
        zone_id = node.attrib.get("type")
        city = node.findtext("exemplarCity")
        if zone_id and city and city.strip():
            result[zone_id] = city.strip()
    return result


def timezone_records(root):
    records = {}
    aliases_by_zone = {}
    for node in root.iter("type"):
        aliases = node.attrib.get("alias", "").split()
        description = node.attrib.get("description", "").strip()
        if not aliases or not description:
            continue
        for zone_id in aliases:
            records[zone_id] = description
            aliases_by_zone[zone_id] = aliases
    return records, aliases_by_zone


def split_description(description, english_territories):
    city, separator, country = description.rpartition(", ")
    if separator and country in english_territories:
        return city, country
    return description, ""


def unique(values):
    return list(dict.fromkeys(value for value in values if value))


def first_city(cities, zone_id, aliases):
    return next((cities[alias] for alias in [zone_id, *aliases] if alias in cities), "")


def entry_for(zone_id, zh_cities, en_cities, records, aliases_by_zone, zh_countries, en_countries):
    if zone_id == "UTC":
        return UTC_ENTRY
    description = records.get(zone_id, "")
    record_aliases = aliases_by_zone.get(zone_id, [])
    fallback_city = zone_id.rsplit("/", 1)[-1].replace("_", " ")
    english_city, english_country = split_description(description, set(en_countries.values()))
    english_city = english_city or first_city(en_cities, zone_id, record_aliases) or fallback_city
    chinese_city = first_city(zh_cities, zone_id, record_aliases)
    chinese_country = next(
        (zh_countries[code] for code, name in en_countries.items() if name == english_country and code in zh_countries),
        "",
    )
    if chinese_country and chinese_city:
        label = f"{chinese_country} · {chinese_city}"
    elif chinese_city:
        label = chinese_city
    elif chinese_country:
        label = chinese_country
    else:
        label = "系统时区 · " + fallback_city
    label = COMPATIBILITY_LABELS.get(zone_id, label)
    aliases = unique([
        chinese_country,
        chinese_city,
        english_country,
        english_city,
        description,
        zone_id,
        *COMPATIBILITY_ALIASES.get(zone_id, []),
    ])
    return label, aliases


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--zone-ids", required=True, type=Path)
    parser.add_argument("--zh", required=True, type=Path)
    parser.add_argument("--en", required=True, type=Path)
    parser.add_argument("--timezone", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()

    zone_ids = [line.strip() for line in args.zone_ids.read_text(encoding="utf-8").splitlines() if line.strip()]
    zone_ids = unique([*zone_ids, *RUNTIME_COMPATIBILITY_ZONE_IDS, "UTC"])
    zh_root = ET.parse(args.zh).getroot()
    en_root = ET.parse(args.en).getroot()
    records, aliases_by_zone = timezone_records(ET.parse(args.timezone).getroot())
    zh_countries = texts_by_type(zh_root, "territory")
    en_countries = texts_by_type(en_root, "territory")
    zh_cities = zone_cities(zh_root)
    en_cities = zone_cities(en_root)

    catalog = {
        zone_id: entry_for(zone_id, zh_cities, en_cities, records, aliases_by_zone, zh_countries, en_countries)
        for zone_id in sorted(zone_ids)
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    lines = [
        "# Generated from Unicode CLDR common/main/{zh,en}.xml and common/bcp47/timezone.xml.",
        "# Regenerate with scripts/generate_meeting_timezone_catalog.py; do not edit by hand.",
    ]
    for zone_id, (label, aliases) in catalog.items():
        lines.append(f"{zone_id}={label}\t" + "\u001f".join(aliases))
    args.output.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(json.dumps({"entries": len(catalog), "output": str(args.output)}, ensure_ascii=False))


if __name__ == "__main__":
    main()
