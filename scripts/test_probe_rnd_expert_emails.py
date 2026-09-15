#!/usr/bin/env python3

import csv
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch
from urllib.parse import parse_qs, urlparse


sys.path.insert(0, str(Path(__file__).resolve().parent))
import probe_rnd_expert_emails as target  # noqa: E402


SCRIPT = Path(__file__).with_name("probe_rnd_expert_emails.py")


class ExpertEmailProbeCliTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp_dir = tempfile.TemporaryDirectory()
        self.root = Path(self.temp_dir.name)
        self.fixtures = self.root / "fixtures"
        self.output = self.root / "output"
        self.fixtures.mkdir()
        self._write_fixtures()

    def tearDown(self) -> None:
        self.temp_dir.cleanup()

    def _write_json(self, name: str, value: object) -> None:
        (self.fixtures / f"{name}.json").write_text(
            json.dumps(value), encoding="utf-8"
        )

    def _write_fixtures(self) -> None:
        self._write_json(
            "nsf",
            {
                "response": {
                    "metadata": {"totalCount": 1},
                    "award": [
                        {
                            "id": "2600001",
                            "pdPIName": "Ada Lovelace",
                            "piEmail": "ada@example-tech.com",
                            "awardeeName": "Example Tech LLC",
                            "fundProgramName": "SBIR Phase II",
                            "title": "Industrial optical inspection system",
                            "abstractText": "Develop and manufacture an optical inspection prototype.",
                        }
                    ],
                }
            },
        )
        self._write_json(
            "sbir",
            [
                {
                    "pi_name": "Ada Lovelace",
                    "pi_email": "ADA@example-tech.com",
                    "pi_title": "Principal Scientist",
                    "firm": "Example Tech LLC",
                    "phase": "Phase II",
                    "award_title": "Industrial optical inspection system",
                    "abstract": "Build and test a production prototype.",
                    "award_link": "https://www.sbir.gov/awards/1",
                },
                {
                    "pi_name": "No Email",
                    "pi_email": "",
                    "firm": "Silent Systems Inc.",
                    "phase": "Phase II",
                    "award_title": "Should not be emitted",
                },
            ],
        )
        self._write_json(
            "osti",
            [
                {
                    "osti_id": "42",
                    "title": "DOE SBIR final technical report",
                    "authors": ["Grace Hopper <grace@industrial-labs.com>"],
                    "research_orgs": ["Industrial Labs, Inc."],
                    "sponsor_orgs": ["USDOE Office of SBIR/STTR Programs"],
                    "links": [
                        {"rel": "citation", "href": "https://www.osti.gov/biblio/42"}
                    ],
                }
            ],
        )
        self._write_json(
            "ietf",
            {
                "objects": [
                    {
                        "affiliation": "Network Devices GmbH",
                        "document": "/api/v1/doc/document/draft-example-routing/",
                        "email": "/api/v1/person/email/linus%40network-devices.example/",
                        "person_name": "Linus Example",
                    },
                    {
                        "affiliation": "Example University",
                        "document": "/api/v1/doc/document/draft-example-academic/",
                        "email": "/api/v1/person/email/prof%40university.example/",
                        "person_name": "Academic Example",
                    },
                ]
            },
        )
        self._write_json(
            "orcid",
            {
                "expanded-result": [
                    {
                        "orcid-id": "0000-0001-2345-6789",
                        "given-names": "Katherine",
                        "family-names": "Johnson",
                        "email": ["katherine@aerospace.example"],
                        "institution-name": ["Aerospace Products Corporation"],
                    }
                ]
            },
        )

    def _run(self) -> subprocess.CompletedProcess[str]:
        return subprocess.run(
            [
                sys.executable,
                str(SCRIPT),
                "--fixture-dir",
                str(self.fixtures),
                "--output-dir",
                str(self.output),
                "--sources",
                "nsf,sbir,osti,ietf,orcid",
            ],
            text=True,
            capture_output=True,
            check=False,
        )

    def test_fixture_probe_outputs_email_people_and_deduplicates_by_email(self) -> None:
        result = self._run()

        self.assertEqual(0, result.returncode, result.stderr)
        with (self.output / "experts_all.csv").open(encoding="utf-8", newline="") as f:
            rows = list(csv.DictReader(f))

        self.assertEqual(4, len(rows))
        ada = next(row for row in rows if row["email"] == "ada@example-tech.com")
        self.assertEqual("Ada Lovelace", ada["expert_name"])
        self.assertEqual("NSF|SBIR", ada["sources"])
        self.assertEqual("Example Tech LLC", ada["company"])
        self.assertNotIn("No Email", {row["expert_name"] for row in rows})
        self.assertNotIn("Academic Example", {row["expert_name"] for row in rows})

    def test_strict_output_excludes_orcid_affiliation_only_candidate(self) -> None:
        result = self._run()

        self.assertEqual(0, result.returncode, result.stderr)
        with (self.output / "experts_strict.csv").open(encoding="utf-8", newline="") as f:
            rows = list(csv.DictReader(f))

        self.assertEqual(3, len(rows))
        self.assertEqual(
            {"ada@example-tech.com", "grace@industrial-labs.com", "linus@network-devices.example"},
            {row["email"] for row in rows},
        )
        self.assertTrue(all(row["qualification"] == "PRODUCTION_RND" for row in rows))

    def test_terminal_masks_email_but_local_csv_keeps_it(self) -> None:
        result = self._run()

        self.assertEqual(0, result.returncode, result.stderr)
        self.assertNotIn("ada@example-tech.com", result.stdout)
        self.assertIn("a***@example-tech.com", result.stdout)
        self.assertIn("ada@example-tech.com", (self.output / "experts_all.csv").read_text())

    def test_official_sbir_csv_maps_company_and_checks_email_domain(self) -> None:
        sbir_csv = self.root / "award_data_no_abstract.csv"
        with sbir_csv.open("w", encoding="utf-8", newline="") as handle:
            writer = csv.DictWriter(
                handle,
                fieldnames=[
                    "Company",
                    "Award Title",
                    "Phase",
                    "Award Year",
                    "Company Website",
                    "PI Name",
                    "PI Title",
                    "PI Email",
                ],
            )
            writer.writeheader()
            writer.writerows(
                [
                    {
                        "Company": "Acme Devices Inc.",
                        "Award Title": "Production sensor prototype",
                        "Phase": "Phase II",
                        "Award Year": "2025",
                        "Company Website": "https://www.acme-devices.com/about",
                        "PI Name": "Ava Engineer",
                        "PI Title": "Principal Scientist",
                        "PI Email": "ava@acme-devices.com",
                    },
                    {
                        "Company": "Beta Materials LLC",
                        "Award Title": "Advanced coating process",
                        "Phase": "Phase I",
                        "Award Year": "2025",
                        "Company Website": "https://betamaterials.example",
                        "PI Name": "Ben Scientist",
                        "PI Title": "PI",
                        "PI Email": "ben@gmail.com",
                    },
                    {
                        "Company": "Gamma Robotics Corp.",
                        "Award Title": "Industrial robot actuator",
                        "Phase": "Phase II",
                        "Award Year": "2025",
                        "Company Website": "https://gamma-robotics.example",
                        "PI Name": "Gia Researcher",
                        "PI Title": "PI",
                        "PI Email": "gia@old-employer.example",
                    },
                ]
            )

        output = self.root / "sbir-output"
        result = subprocess.run(
            [
                sys.executable,
                str(SCRIPT),
                "--sources",
                "sbir",
                "--sbir-csv",
                str(sbir_csv),
                "--limit",
                "3",
                "--since-year",
                "2025",
                "--output-dir",
                str(output),
            ],
            text=True,
            capture_output=True,
            check=False,
        )

        self.assertEqual(0, result.returncode, result.stderr)
        with (output / "experts_strict.csv").open(encoding="utf-8", newline="") as f:
            rows = {row["email"]: row for row in csv.DictReader(f)}

        self.assertEqual("Acme Devices Inc.", rows["ava@acme-devices.com"]["company"])
        self.assertEqual("acme-devices.com", rows["ava@acme-devices.com"]["company_domain"])
        self.assertEqual("MATCH", rows["ava@acme-devices.com"]["domain_match"])
        self.assertEqual("PERSONAL_EMAIL", rows["ben@gmail.com"]["domain_match"])
        self.assertEqual("MISMATCH", rows["gia@old-employer.example"]["domain_match"])


class SourceQueryTest(unittest.TestCase):
    def test_ietf_query_limits_documents_to_requested_recency(self) -> None:
        args = SimpleNamespace(limit=10, since_year=2025, timeout=1.0, delay=0.0)

        with patch.object(target, "request_json", return_value={"objects": [], "meta": {}}):
            result = target.fetch_ietf(args)

        query = parse_qs(urlparse(result.endpoint).query)
        self.assertEqual(["2025-01-01T00:00:00Z"], query["document__time__gte"])


if __name__ == "__main__":
    unittest.main()
