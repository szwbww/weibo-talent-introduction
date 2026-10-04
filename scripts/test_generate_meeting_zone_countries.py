#!/usr/bin/env python3
"""Offline unit tests for scripts/generate_meeting_zone_countries.py (no network, no DB)."""

import importlib.util
import json
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).with_name("generate_meeting_zone_countries.py")
spec = importlib.util.spec_from_file_location("generate_meeting_zone_countries", SCRIPT)
generator = importlib.util.module_from_spec(spec)
spec.loader.exec_module(generator)

TAB = "\t"


class GeneratorTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)

    def write(self, name, text):
        path = self.root / name
        path.write_text(text, encoding="utf-8")
        return path

    def fixtures(self, zone_lines, backward_lines, iso_lines, country_entries):
        country = {
            "sourceVersion": "2026c",
            "countries": country_entries,
        }
        return {
            "country_catalog": self.write("countries.json", json.dumps(country, ensure_ascii=False)),
            "zone_labels": self.write("zones.properties", "\n".join(zone_lines) + "\n"),
            "backward": self.write("backward", "\n".join(backward_lines) + "\n"),
            "iso3166": self.write("iso3166.tab", "\n".join(iso_lines) + "\n"),
        }

    def generate(self, fixtures, output_name="out.properties"):
        output = self.root / output_name
        text = generator.generate(
            fixtures["country_catalog"],
            fixtures["zone_labels"],
            fixtures["backward"],
            fixtures["iso3166"],
            output,
        )
        return text, output

    def parse_rows(self, text):
        rows = {}
        for line in text.splitlines():
            if not line or line.startswith("#"):
                continue
            zone, value = line.split("=", 1)
            rows[zone] = value.split(TAB)
        return rows

    def base_fixtures(self):
        return self.fixtures(
            zone_lines=[
                "Brazil/East=巴西 · 圣保罗" + TAB + "Brazil",
                "Asia/Calcutta=印度 · 加尔各答" + TAB + "India",
                "Pacific/Ponape=密克罗尼西亚 · 波纳佩" + TAB + "Micronesia",
                "Europe/Brussels=比利时 · 布鲁塞尔" + TAB + "Belgium",
                "America/Sao_Paulo=巴西 · 圣保罗" + TAB + "Sao Paulo",
                "Pacific/Pohnpei=密克罗尼西亚 · 波纳佩" + TAB + "Pohnpei",
                "Asia/Kolkata=印度 · 加尔各答" + TAB + "Kolkata",
                "UTC=协调世界时" + TAB + "Coordinated Universal Time",
                "SystemV/AST4=大西洋标准时间" + TAB + "AST4",
            ],
            backward_lines=[
                "# fixture backward file",
                "Link America/Sao_Paulo Brazil/East",
                "Link Asia/Kolkata Asia/Calcutta",
                "Link Pacific/Guadalcanal Pacific/Ponape #= Pacific/Pohnpei",
                "Link America/Sao_Paulo Europe/Brussels",
                "Link Brazil/East America/Sao_Paulo",
                "Link Pacific/Pohnpei Pacific/Guadalcanal",
            ],
            iso_lines=[
                "BR" + TAB + "Brazil",
                "IN" + TAB + "India",
                "FM" + TAB + "Micronesia",
                "BE" + TAB + "Belgium",
                "SB" + TAB + "Solomon Islands",
            ],
            country_entries=[
                {"code": "BR", "labelZh": "巴西", "defaultZoneId": "America/Sao_Paulo",
                 "zones": [{"id": "America/Sao_Paulo", "labelZh": "巴西 · 圣保罗"}]},
                {"code": "IN", "labelZh": "印度", "defaultZoneId": "Asia/Kolkata",
                 "zones": [{"id": "Asia/Kolkata", "labelZh": "印度 · 加尔各答"}]},
                {"code": "FM", "labelZh": "密克罗尼西亚", "defaultZoneId": "Pacific/Pohnpei",
                 "zones": [{"id": "Pacific/Pohnpei", "labelZh": "密克罗尼西亚 · 波纳佩"}]},
                {"code": "BE", "labelZh": "比利时", "defaultZoneId": "Europe/Brussels",
                 "zones": [{"id": "Europe/Brussels", "labelZh": "比利时 · 布鲁塞尔"}]},
            ],
        )

    # ── 别名链与精确匹配（I-1） ──

    def test_alias_chain_resolves_to_country_and_canonical_zone(self):
        fixtures = self.base_fixtures()
        text, _ = self.generate(fixtures)
        rows = self.parse_rows(text)

        self.assertEqual(["BR", "巴西", "Brazil", "America/Sao_Paulo"], rows["Brazil/East"])
        self.assertEqual(["IN", "印度", "India", "Asia/Kolkata"], rows["Asia/Calcutta"])
        # #= 原目标优先于跨大洲合并目标（FM 而非 SB）。
        self.assertEqual(["FM", "密克罗尼西亚", "Micronesia", "Pacific/Pohnpei"], rows["Pacific/Ponape"])
        # 精确国家项不被跨国 link 覆盖。
        self.assertEqual(["BE", "比利时", "Belgium", "Europe/Brussels"], rows["Europe/Brussels"])

    def test_plain_links_and_hash_equals_original_target_differ(self):
        fixtures = self.base_fixtures()
        text, _ = self.generate(fixtures)
        rows = self.parse_rows(text)
        # Pacific/Ponape 的普通目标是 Guadalcanal（SB），但 #= 原目标是 Pohnpei（FM）。
        self.assertEqual("FM", rows["Pacific/Ponape"][0])
        self.assertNotEqual("SB", rows["Pacific/Ponape"][0])

    def test_utc_is_a_fixed_special_entry(self):
        fixtures = self.base_fixtures()
        text, _ = self.generate(fixtures)
        rows = self.parse_rows(text)
        self.assertEqual(generator.UTC_ENTRY, tuple(rows["UTC"]))

    def test_systemv_exempt_ids_are_not_emitted(self):
        fixtures = self.base_fixtures()
        text, _ = self.generate(fixtures)
        rows = self.parse_rows(text)
        self.assertNotIn("SystemV/AST4", rows)
        self.assertEqual(8, len(rows))

    def test_output_is_sorted_and_four_tab_fields(self):
        fixtures = self.base_fixtures()
        text, _ = self.generate(fixtures)
        entries = [line for line in text.splitlines() if line and not line.startswith("#")]
        zones = [line.split("=", 1)[0] for line in entries]
        self.assertEqual(sorted(zones), zones)
        for line in entries:
            self.assertEqual(4, len(line.split("=", 1)[1].split(TAB)))

    def test_header_records_source_version_and_hashes(self):
        fixtures = self.base_fixtures()
        text, _ = self.generate(fixtures)
        self.assertIn("IANA tzdb 2026c", text)
        self.assertIn("backward=", text)
        self.assertIn("iso3166.tab=", text)

    def test_regeneration_is_byte_identical(self):
        fixtures = self.base_fixtures()
        first, _ = self.generate(fixtures, "first.properties")
        second, _ = self.generate(fixtures, "second.properties")
        self.assertEqual(first, second)

    # ── 失败路径（I-1） ──

    def test_alias_cycle_fails(self):
        fixtures = self.fixtures(
            zone_lines=["Cycle/One=循环" + TAB + "Cycle"],
            backward_lines=[
                "Link Cycle/Two Cycle/One",
                "Link Cycle/One Cycle/Two",
            ],
            iso_lines=["BR" + TAB + "Brazil"],
            country_entries=[
                {"code": "BR", "labelZh": "巴西", "zones": [{"id": "America/Sao_Paulo", "labelZh": "巴西"}]}
            ],
        )
        with self.assertRaises(generator.GenerationError) as ctx:
            self.generate(fixtures)
        self.assertIn("cycle", str(ctx.exception))

    def test_unknown_zone_fails_and_lists_id(self):
        fixtures = self.fixtures(
            zone_lines=["Mars/Olympus=火星" + TAB + "Mars"],
            backward_lines=["# none"],
            iso_lines=["BR" + TAB + "Brazil"],
            country_entries=[
                {"code": "BR", "labelZh": "巴西", "zones": [{"id": "America/Sao_Paulo", "labelZh": "巴西"}]}
            ],
        )
        with self.assertRaises(generator.GenerationError) as ctx:
            self.generate(fixtures)
        self.assertIn("Mars/Olympus", str(ctx.exception))

    def test_unknown_systemv_prefix_is_not_exempt(self):
        fixtures = self.fixtures(
            zone_lines=["SystemV/NOT_IN_AUDIT=未知" + TAB + "NOPE"],
            backward_lines=["# none"],
            iso_lines=["BR" + TAB + "Brazil"],
            country_entries=[
                {"code": "BR", "labelZh": "巴西", "zones": [{"id": "America/Sao_Paulo", "labelZh": "巴西"}]}
            ],
        )
        with self.assertRaises(generator.GenerationError) as ctx:
            self.generate(fixtures)
        self.assertIn("SystemV/NOT_IN_AUDIT", str(ctx.exception))

    def test_missing_english_label_fails(self):
        fixtures = self.fixtures(
            zone_lines=["Test/Zone=测试" + TAB + "Test"],
            backward_lines=["# none"],
            iso_lines=["BR" + TAB + "Brazil"],
            country_entries=[
                {"code": "ZZ", "labelZh": "无英文", "zones": [{"id": "Test/Zone", "labelZh": "测试"}]}
            ],
        )
        with self.assertRaises(generator.GenerationError) as ctx:
            self.generate(fixtures)
        self.assertIn("English country label", str(ctx.exception))

    def test_exempt_id_that_gains_attribution_fails(self):
        fixtures = self.fixtures(
            zone_lines=["SystemV/AST4=大西洋" + TAB + "AST4"],
            backward_lines=["Link America/Sao_Paulo SystemV/AST4"],
            iso_lines=["BR" + TAB + "Brazil"],
            country_entries=[
                {"code": "BR", "labelZh": "巴西", "zones": [{"id": "America/Sao_Paulo", "labelZh": "巴西"}]}
            ],
        )
        with self.assertRaises(generator.GenerationError) as ctx:
            self.generate(fixtures)
        self.assertIn("SystemV", str(ctx.exception))


if __name__ == "__main__":
    unittest.main()
