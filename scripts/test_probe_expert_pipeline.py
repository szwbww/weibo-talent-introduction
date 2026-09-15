#!/usr/bin/env python3

import csv
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


SCRIPT = Path(__file__).with_name("probe_expert_pipeline.py")


class ExpertPipelineCliTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp_dir = tempfile.TemporaryDirectory()
        self.root = Path(self.temp_dir.name)
        self.fixtures = self.root / "fixtures"
        self.output = self.root / "output"
        self.fixtures.mkdir()
        self._write_fixtures()

    def tearDown(self) -> None:
        self.temp_dir.cleanup()

    def _write_json(self, name: str, payload: object) -> None:
        (self.fixtures / f"{name}.json").write_text(
            json.dumps(payload), encoding="utf-8"
        )

    def _write_fixtures(self) -> None:
        self._write_json(
            "openalex",
            {
                "meta": {"count": 2},
                "results": [
                    {
                        "id": "https://openalex.org/W1",
                        "doi": "https://doi.org/10.1000/example",
                        "display_name": "Photonics for industrial sensors",
                        "publication_year": 2026,
                        "primary_topic": {"display_name": "Photonics"},
                        "primary_location": {
                            "landing_page_url": "https://doi.org/10.1000/example"
                        },
                        "authorships": [
                            {
                                "author_position": "last",
                                "is_corresponding": True,
                                "author": {
                                    "id": "https://openalex.org/A1",
                                    "display_name": "Alice Professor",
                                    "orcid": "https://orcid.org/0000-0001-0000-0001",
                                },
                                "institutions": [
                                    {
                                        "id": "https://openalex.org/I1",
                                        "display_name": "Example University",
                                        "country_code": "US",
                                        "type": "education",
                                    }
                                ],
                            },
                            {
                                "author_position": "middle",
                                "is_corresponding": False,
                                "author": {
                                    "id": "https://openalex.org/A2",
                                    "display_name": "Bob Engineer",
                                    "orcid": None,
                                },
                                "institutions": [
                                    {
                                        "id": "https://openalex.org/I2",
                                        "display_name": "Example Devices Inc.",
                                        "country_code": "US",
                                        "type": "company",
                                    }
                                ],
                            },
                        ],
                    },
                    {
                        "id": "https://openalex.org/W2",
                        "doi": "https://doi.org/10.1000/example-2",
                        "display_name": "A second photonics paper",
                        "publication_year": 2025,
                        "primary_topic": {"display_name": "Optical Engineering"},
                        "primary_location": {
                            "landing_page_url": "https://doi.org/10.1000/example-2"
                        },
                        "authorships": [
                            {
                                "author_position": "last",
                                "is_corresponding": False,
                                "author": {
                                    "id": "https://openalex.org/A1",
                                    "display_name": "Alice Professor",
                                    "orcid": "https://orcid.org/0000-0001-0000-0001",
                                },
                                "institutions": [
                                    {
                                        "id": "https://openalex.org/I1",
                                        "display_name": "Example University",
                                        "country_code": "US",
                                        "type": "education",
                                    }
                                ],
                            }
                        ],
                    },
                ],
            },
        )
        self._write_json(
            "nsf",
            {
                "response": {
                    "award": [
                        {
                            "id": "2600001",
                            "pdPIName": "Ada Lovelace",
                            "piEmail": "ada@example-tech.com",
                            "awardeeName": "Example Tech LLC",
                            "fundProgramName": "SBIR Phase II",
                            "title": "Industrial optical inspection system",
                            "abstractText": "Develop a production prototype.",
                        }
                    ]
                }
            },
        )
        self._write_json(
            "contactout",
            {
                "status_code": 200,
                "metadata": {"page": 1, "page_size": 25, "total_results": 2},
                "profiles": {
                    "https://linkedin.com/in/grace-scientist": {
                        "full_name": "Grace Scientist",
                        "title": "Principal Scientist",
                        "company": {"name": "Industrial Labs", "domain": "industrial.example"},
                        "location": "Boston, US",
                        "skills": ["Photonics", "Sensors"],
                        "contact_info": {
                            "personal_emails": ["grace@gmail.com"],
                            "work_emails": ["Grace@Industrial.example"],
                            "work_email_status": {"Grace@Industrial.example": "Verified"},
                            "phones": ["+15555550100"],
                        },
                    },
                    "https://linkedin.com/in/junior-researcher": {
                        "full_name": "Junior Researcher",
                        "title": "Research Assistant",
                        "company": {"name": "Industrial Labs", "domain": "industrial.example"},
                        "contact_info": {
                            "work_emails": ["junior@industrial.example"],
                            "work_email_status": {"junior@industrial.example": "Verified"},
                        },
                    },
                },
            },
        )

    def _run(self, *extra: str) -> subprocess.CompletedProcess[str]:
        return subprocess.run(
            [
                sys.executable,
                str(SCRIPT),
                "--academic-keywords",
                "photonics sensors",
                "--country",
                "US",
                "--enterprise-sources",
                "nsf",
                "--contactout-title",
                "Principal Scientist",
                "--fixture-dir",
                str(self.fixtures),
                "--output-dir",
                str(self.output),
                *extra,
            ],
            text=True,
            capture_output=True,
            check=False,
        )

    def test_combines_academic_public_rnd_and_contactout_into_one_csv(self) -> None:
        result = self._run()

        self.assertEqual(0, result.returncode, result.stderr)
        with (self.output / "experts_combined.csv").open(
            encoding="utf-8", newline=""
        ) as handle:
            rows = list(csv.DictReader(handle))

        self.assertEqual(3, len(rows))
        academic = next(row for row in rows if row["expert_name"] == "Alice Professor")
        self.assertEqual("ACADEMIC", academic["expert_type"])
        self.assertEqual("https://openalex.org/A1", academic["openalex_id"])
        self.assertEqual("Example University", academic["organization"])
        self.assertEqual("", academic["email"])
        self.assertEqual("NEEDS_TITLE_AND_EMAIL", academic["qualification"])
        self.assertIn("corresponding author", academic["evidence"])

        enterprise = next(row for row in rows if row["expert_name"] == "Ada Lovelace")
        self.assertEqual("ENTERPRISE", enterprise["expert_type"])
        self.assertEqual("PUBLIC_SOURCE", enterprise["email_acquisition_type"])

        enriched = next(row for row in rows if row["expert_name"] == "Grace Scientist")
        self.assertEqual("grace@industrial.example", enriched["email"])
        self.assertEqual("CONTACTOUT", enriched["email_source"])
        self.assertEqual("VERIFIED", enriched["email_status"])
        self.assertNotIn("grace@gmail.com", {row["email"] for row in rows})
        self.assertNotIn("Junior Researcher", {row["expert_name"] for row in rows})

    def test_console_masks_contact_addresses(self) -> None:
        result = self._run()

        self.assertEqual(0, result.returncode, result.stderr)
        self.assertNotIn("ada@example-tech.com", result.stdout)
        self.assertNotIn("grace@industrial.example", result.stdout)
        self.assertIn("a***@example-tech.com", result.stdout)
        self.assertIn("g***@industrial.example", result.stdout)


if __name__ == "__main__":
    unittest.main()
