import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


SCRIPT = Path(__file__).with_name("pull_engineer_directory.py")


class PullEngineerDirectoryTest(unittest.TestCase):
    def test_builds_relational_directory_without_email_data(self):
        source = {
            "domestic_enterprises": [
                {"enterprise_id": "CN-0001", "enterprise_name": "甲公司", "core_need": "海底电缆绝缘材料"}
            ],
            "apollo_search_profiles": {
                "CN-0001": {"keywords": ["cable"], "titles": ["Cable Engineer", "R&D Engineer"]}
            },
            "merged_benchmark_enterprises": [
                {"benchmark_id": "BM-1", "benchmark_name": "Alpha Cable", "official_domain": "alpha.example"}
            ],
            "merged_relations": [
                {"enterprise_id": "CN-0001", "benchmark_id": "BM-1", "match_reason": "同类电缆技术"}
            ],
        }
        fixture = {
            "alpha.example": {
                "pagination": {"total_entries": 3},
                "people": [
                    {"id": "p1", "name": "Ada Smith", "title": "Senior Cable Engineer", "has_email": True,
                     "email": "must-not-be-exported@example.com", "country": "United Kingdom",
                     "linkedin_url": "https://linkedin.example/p1",
                     "organization": {"name": "Alpha Cable", "primary_domain": "alpha.example"}},
                    {"id": "p2", "name": "Wrong Employer", "title": "R&D Engineer",
                     "organization": {"name": "Other", "primary_domain": "other.example"}},
                    {"id": "p1", "name": "Ada Smith", "title": "Senior Cable Engineer",
                     "organization": {"name": "Alpha Cable", "primary_domain": "alpha.example"}},
                ],
            }
        }
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            source_path = root / "source.json"
            fixture_path = root / "fixture.json"
            output_dir = root / "out"
            source_path.write_text(json.dumps(source, ensure_ascii=False), encoding="utf-8")
            fixture_path.write_text(json.dumps(fixture, ensure_ascii=False), encoding="utf-8")
            result = subprocess.run(
                [sys.executable, str(SCRIPT), "--input", str(source_path),
                 "--output-dir", str(output_dir), "--fixture", str(fixture_path)],
                text=True, capture_output=True, check=False,
            )
            self.assertEqual(0, result.returncode, result.stderr)
            data = json.loads((output_dir / "engineer_directory.json").read_text(encoding="utf-8"))
            self.assertEqual(1, data["summary"]["unique_engineers"])
            self.assertEqual(1, data["summary"]["benchmark_engineer_relations"])
            self.assertEqual(1, data["summary"]["enterprise_engineer_relations"])
            self.assertEqual(0, data["summary"]["apollo_enrichment_calls"])
            self.assertEqual(0, data["summary"]["email_verification_calls"])
            serialized = json.dumps(data, ensure_ascii=False)
            self.assertNotIn("must-not-be-exported@example.com", serialized)
            self.assertEqual("未补全", data["engineers"][0]["email_enrichment_status"])
            self.assertIs(True, data["engineers"][0]["apollo_has_email"])


if __name__ == "__main__":
    unittest.main()
