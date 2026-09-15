#!/usr/bin/env python3

import csv
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


SCRIPT = Path(__file__).with_name("build_sbir_research_fields_update.py")


class BuildSbirResearchFieldsUpdateTest(unittest.TestCase):
    def test_builds_candidate_only_partial_updates_and_drops_overflowing_titles(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            source = root / "preview.csv"
            output = root / "updates.ndjson"
            summary = root / "summary.json"
            with source.open("w", encoding="utf-8", newline="") as handle:
                writer = csv.DictWriter(
                    handle,
                    fieldnames=["es_doc_id", "proposedResearchFields"],
                )
                writer.writeheader()
                writer.writerows(
                    [
                        {
                            "es_doc_id": "EMAIL-one",
                            "proposedResearchFields": "Advanced manufacturing",
                        },
                        {
                            "es_doc_id": "EMAIL-two",
                            "proposedResearchFields": f"{'A' * 300}; {'B' * 300}",
                        },
                    ]
                )

            result = subprocess.run(
                [
                    sys.executable,
                    str(SCRIPT),
                    "--input",
                    str(source),
                    "--output",
                    str(output),
                    "--summary",
                    str(summary),
                    "--max-chars",
                    "512",
                ],
                text=True,
                capture_output=True,
            )

            self.assertEqual(0, result.returncode, result.stderr)
            lines = [json.loads(line) for line in output.read_text().splitlines()]
            self.assertEqual(
                {"update": {"_index": "orcid_info_candidate", "_id": "EMAIL-one"}},
                lines[0],
            )
            self.assertEqual(
                {"doc": {"researchFields": "Advanced manufacturing"}},
                lines[1],
            )
            self.assertEqual("A" * 300, lines[3]["doc"]["researchFields"])
            self.assertNotIn("tags", lines[1]["doc"])
            report = json.loads(summary.read_text())
            self.assertEqual(2, report["documents"])
            self.assertEqual(1, report["trimmed_documents"])
            self.assertFalse(report["writes_elasticsearch"])


if __name__ == "__main__":
    unittest.main()
