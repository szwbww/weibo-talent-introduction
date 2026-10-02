#!/usr/bin/env python3
"""G-0 探针：校验某个 JVM 的 tzdb 是否覆盖随包的两份时区目录。

用法:
    python3 scripts/tzdb_catalog_probe.py [--java /path/to/bin/java]

判据（任一不满足即 exit 1，并逐条打印缺失项）:
    1. `contact-country-timezones.json` 的全部目录 id 在该 JVM 上可解析；
    2. `meeting-timezones-zh.properties` 覆盖该 JVM 的全部可选取 id
       （口径与 `MeetingConfirmationService.catalogZoneIds()` 一致：
       含 `/` 且不以 `Etc/` 开头，附 `UTC`）；
    3. 固定时刻 `2026-07-01T12:00Z` 的偏移断言与 `EXPECTED_OFFSETS` 一致。

构建 JDK 与生产 JVM 必须使用同一 tzdb 族（见 contact-timing 总计划 G-0 / A4）：
发布前对两边各跑一次本脚本，输出即 G-0 证据。
"""

from __future__ import annotations

import argparse
import json
import subprocess
import sys
import tempfile
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[1]
LOCATION_CATALOG = REPO_ROOT / "src/main/resources/contact-country-timezones.json"
MEETING_CATALOG = REPO_ROOT / "src/main/resources/meeting-timezones-zh.properties"
PROBE_DATE_UTC = "2026-07-01T12:00Z"

# 绑定 JDK（Zulu 11.0.32.1 / tzdb 2026b）实测值；换 JDK 时必须重新记录。
# 注意 America/Mexico_City 与 America/Coyhaique 是典型的「旧 tzdb 会算错/解析不了」样本。
EXPECTED_OFFSETS = {
    "Asia/Shanghai": "+08:00",
    "America/Santiago": "-04:00",
    "America/Coyhaique": "-03:00",
    "America/Punta_Arenas": "-03:00",
    "Pacific/Easter": "-06:00",
    "America/Sao_Paulo": "-03:00",
    "Europe/London": "+01:00",
    "America/New_York": "-04:00",
    "America/Mexico_City": "-06:00",
    "Europe/Kyiv": "+03:00",
}

PROBE_SOURCE = """
import java.time.*;
public class TzdbCatalogProbe {
  public static void main(String[] args) {
    System.out.println("#java.version=" + System.getProperty("java.version"));
    System.out.println("#java.home=" + System.getProperty("java.home"));
    ZoneId.getAvailableZoneIds().stream().sorted().forEach(System.out::println);
    Instant at = ZonedDateTime.of(2026, 7, 1, 12, 0, 0, 0, ZoneOffset.UTC).toInstant();
    for (String id : args) {
      try {
        System.out.println("#offset " + id + " " + ZoneId.of(id).getRules().getOffset(at).getId());
      } catch (Exception ex) {
        System.out.println("#offset " + id + " ERROR " + ex.getMessage());
      }
    }
  }
}
"""


def load_location_catalog_zones() -> list[str]:
    catalog = json.loads(LOCATION_CATALOG.read_text(encoding="utf-8"))
    return sorted({zone["id"] for country in catalog["countries"] for zone in country.get("zones", [])})


def load_meeting_catalog_keys() -> set[str]:
    keys = set()
    for line in MEETING_CATALOG.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        keys.add(line.split("=", 1)[0])
    return keys


def meeting_selectable_ids(jvm_zone_ids: set[str]) -> set[str]:
    """与 MeetingConfirmationService.catalogZoneIds() 同口径。"""
    selectable = {zone for zone in jvm_zone_ids if "/" in zone and not zone.startswith("Etc/")}
    selectable.add("UTC")
    return selectable


def run_probe(java: str) -> tuple[dict[str, str], set[str], dict[str, str]]:
    with tempfile.TemporaryDirectory(prefix="tzdb-catalog-probe-") as work:
        source = Path(work) / "TzdbCatalogProbe.java"
        source.write_text(PROBE_SOURCE, encoding="utf-8")
        proc = subprocess.run(
            [java, str(source), *EXPECTED_OFFSETS.keys()],
            capture_output=True, text=True, timeout=180,
        )
    if proc.returncode != 0:
        raise SystemExit(f"探针 JVM 调用失败（退出码 {proc.returncode}）：\n{proc.stderr.strip()}")

    meta: dict[str, str] = {}
    zone_ids: set[str] = set()
    offsets: dict[str, str] = {}
    for line in proc.stdout.splitlines():
        if line.startswith("#java."):
            key, _, value = line[1:].partition("=")
            meta[key] = value
        elif line.startswith("#offset "):
            _, zone, value = line.split(" ", 2)
            offsets[zone] = value.strip()
        elif line.strip():
            zone_ids.add(line.strip())
    return meta, zone_ids, offsets


def main() -> int:
    parser = argparse.ArgumentParser(description="tzdb 目录覆盖探针（G-0）")
    parser.add_argument("--java", default="java", help="要探测的 java 可执行文件；默认取 PATH 上的 java")
    args = parser.parse_args()

    meta, jvm_zone_ids, offsets = run_probe(args.java)
    location_zones = load_location_catalog_zones()
    meeting_keys = load_meeting_catalog_keys()
    selectable = meeting_selectable_ids(jvm_zone_ids)

    missing_location = [zone for zone in location_zones if zone not in jvm_zone_ids]
    missing_meeting = sorted(selectable - meeting_keys)
    offset_mismatch = [
        (zone, EXPECTED_OFFSETS[zone], offsets.get(zone, "<missing>"))
        for zone in EXPECTED_OFFSETS
        if offsets.get(zone) != EXPECTED_OFFSETS[zone]
    ]

    print(f"java: {meta.get('java.version', '?')}  ({meta.get('java.home', '?')})")
    print(f"jvm zone ids: {len(jvm_zone_ids)}")
    print(f"location catalog ids: {len(location_zones)}  缺失: {len(missing_location)} {missing_location}")
    print(f"meeting selectable ids: {len(selectable)}  目录未覆盖: {len(missing_meeting)} {missing_meeting}")
    print(f"offset assertions @ {PROBE_DATE_UTC}: {len(EXPECTED_OFFSETS) - len(offset_mismatch)}/{len(EXPECTED_OFFSETS)} 通过")
    for zone, expected, actual in offset_mismatch:
        print(f"  offset mismatch: {zone} 期望 {expected} 实际 {actual}")

    failures = bool(missing_location or missing_meeting or offset_mismatch)
    print("RESULT: " + ("FAIL" if failures else "PASS"))
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
