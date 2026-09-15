#!/usr/bin/env python3

import csv
import hashlib
import json
import ssl
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch


sys.path.insert(0, str(Path(__file__).resolve().parent))
import build_sbir_expert_import as target  # noqa: E402


SCRIPT = Path(__file__).with_name("build_sbir_expert_import.py")


class BuildSbirExpertImportCliTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp_dir = tempfile.TemporaryDirectory()
        self.root = Path(self.temp_dir.name)
        self.current = self.root / "award_data_no_abstract.csv"
        self.archive = self.root / "award_data.csv"
        self.output = self.root / "output"
        self.existing = self.root / "existing.txt"
        self._write_current()
        self._write_archive()
        self.existing.write_text("existing@existing-labs.com\n", encoding="utf-8")

    def tearDown(self) -> None:
        self.temp_dir.cleanup()

    @staticmethod
    def _fields(include_abstract: bool) -> list[str]:
        fields = [
            "Company",
            "Award Title",
            "Agency",
            "Phase",
            "Program",
            "Agency Tracking Number",
            "Contract",
            "Proposal Award Date",
            "Award Year",
            "Company Website",
        ]
        if include_abstract:
            fields.append("Abstract")
        fields.extend(["PI Name", "PI Title", "PI Email"])
        return fields

    def _write_rows(self, path: Path, rows: list[dict[str, str]], *, include_abstract: bool) -> None:
        with path.open("w", encoding="utf-8", newline="") as handle:
            writer = csv.DictWriter(handle, fieldnames=self._fields(include_abstract))
            writer.writeheader()
            writer.writerows(rows)

    def _write_current(self) -> None:
        common = {
            "Agency": "Department of Energy",
            "Program": "SBIR",
            "Proposal Award Date": "05/01/2025",
            "Award Year": "2025",
            "PI Title": "Principal Engineer",
        }
        rows = [
            {
                **common,
                "Company": "Acme Devices Inc.",
                "Award Title": "Production sensor prototype",
                "Phase": "Phase II",
                "Agency Tracking Number": "DOE-001",
                "Contract": "C-001",
                "Company Website": "https://www.acme-devices.com",
                "PI Name": "Ada Lovelace",
                "PI Email": "ADA@acme-devices.com",
            },
            {
                **common,
                "Company": "Acme Devices Inc.",
                "Award Title": "Industrial sensor qualification",
                "Phase": "Phase II",
                "Agency Tracking Number": "DOE-002",
                "Contract": "C-002",
                "Award Year": "2024",
                "Company Website": "https://acme-devices.com/about",
                "PI Name": "Ada Lovelace",
                "PI Email": "ada@acme-devices.com",
            },
            {
                **common,
                "Company": "Phase One Corp.",
                "Award Title": "Early feasibility",
                "Phase": "Phase I",
                "Agency Tracking Number": "DOE-003",
                "Contract": "C-003",
                "Company Website": "https://phase-one.com",
                "PI Name": "Phil One",
                "PI Email": "phil@phase-one.com",
            },
            {
                **common,
                "Company": "Personal Mail LLC",
                "Award Title": "Advanced manufacturing",
                "Phase": "Phase II",
                "Agency Tracking Number": "DOE-004",
                "Contract": "C-004",
                "Company Website": "https://personal-mail.com",
                "PI Name": "Gina Mail",
                "PI Email": "gina@gmail.com",
            },
            {
                **common,
                "Company": "Mismatch Systems Inc.",
                "Award Title": "Autonomous controls",
                "Phase": "Phase II",
                "Agency Tracking Number": "DOE-005",
                "Contract": "C-005",
                "Company Website": "https://mismatch-systems.com",
                "PI Name": "Mia Match",
                "PI Email": "mia@old-employer.com",
            },
            {
                **common,
                "Company": "Unknown Domain LLC",
                "Award Title": "Composite process",
                "Phase": "Phase II",
                "Agency Tracking Number": "DOE-006",
                "Contract": "C-006",
                "Company Website": "",
                "PI Name": "Uma Unknown",
                "PI Email": "uma@unknown-domain.com",
            },
            {
                **common,
                "Company": "Broken Email Inc.",
                "Award Title": "Robotic assembly",
                "Phase": "Phase II",
                "Agency Tracking Number": "DOE-007",
                "Contract": "C-007",
                "Company Website": "https://broken-email.com",
                "PI Name": "Invalid Address",
                "PI Email": "not-an-email",
            },
            {
                **common,
                "Company": "Existing Labs Inc.",
                "Award Title": "Production materials",
                "Phase": "Phase II",
                "Agency Tracking Number": "DOE-008",
                "Contract": "C-008",
                "Company Website": "https://existing-labs.com",
                "PI Name": "Existing Person",
                "PI Email": "existing@existing-labs.com",
            },
        ]
        self._write_rows(self.current, rows, include_abstract=False)

    def _write_archive(self) -> None:
        rows = [
            {
                "Company": "Acme Devices Inc.",
                "Award Title": "Production sensor prototype",
                "Agency": "Department of Energy",
                "Phase": "Phase II",
                "Program": "SBIR",
                "Agency Tracking Number": "DOE-001",
                "Contract": "C-001",
                "Proposal Award Date": "05/01/2025",
                "Award Year": "2025",
                "Company Website": "https://www.acme-devices.com",
                "Abstract": "Build and validate a production-grade optical sensor.",
                "PI Name": "Ada Lovelace",
                "PI Title": "Principal Engineer",
                "PI Email": "ada@acme-devices.com",
            },
            {
                "Company": "Archive Only Inc.",
                "Award Title": "Historical project",
                "Agency": "Department of Defense",
                "Phase": "Phase II",
                "Program": "SBIR",
                "Agency Tracking Number": "OLD-001",
                "Contract": "OLD-C-001",
                "Proposal Award Date": "01/01/2022",
                "Award Year": "2022",
                "Company Website": "https://archive-only.com",
                "Abstract": "Historical abstract.",
                "PI Name": "Old Expert",
                "PI Title": "Engineer",
                "PI Email": "old@archive-only.com",
            },
        ]
        self._write_rows(self.archive, rows, include_abstract=True)

    def _run(self) -> subprocess.CompletedProcess[str]:
        return subprocess.run(
            [
                sys.executable,
                str(SCRIPT),
                "--current",
                str(self.current),
                "--archive",
                str(self.archive),
                "--since-year",
                "2023",
                "--existing-emails",
                str(self.existing),
                "--output-dir",
                str(self.output),
            ],
            text=True,
            capture_output=True,
            check=False,
        )

    def test_builds_only_phase_two_company_domain_matches_and_enriches_abstract(self) -> None:
        result = self._run()

        self.assertEqual(0, result.returncode, result.stderr)
        with (self.output / "experts_import.csv").open(encoding="utf-8", newline="") as handle:
            rows = list(csv.DictReader(handle))

        self.assertEqual(1, len(rows))
        expert = rows[0]
        self.assertEqual("ada@acme-devices.com", expert["email"])
        self.assertEqual("2", expert["award_count"])
        self.assertEqual("2024|2025", expert["award_years"])
        self.assertIn("Production sensor prototype", expert["project_titles"])
        self.assertIn("Industrial sensor qualification", expert["project_titles"])
        self.assertEqual(
            "Build and validate a production-grade optical sensor.",
            expert["project_abstracts"],
        )
        self.assertEqual("SBIR_CURRENT|SBIR_ARCHIVE_ABSTRACT", expert["source_files"])

    def test_rejected_output_explains_each_exclusion_and_never_imports_archive_only(self) -> None:
        result = self._run()

        self.assertEqual(0, result.returncode, result.stderr)
        with (self.output / "experts_rejected.csv").open(encoding="utf-8", newline="") as handle:
            rejected = list(csv.DictReader(handle))

        self.assertEqual(
            {
                "PHASE_NOT_II",
                "PERSONAL_EMAIL",
                "DOMAIN_MISMATCH",
                "DOMAIN_UNKNOWN",
                "INVALID_EMAIL",
                "EXISTS_IN_ES",
            },
            {row["reject_reason"] for row in rejected},
        )
        self.assertNotIn("old@archive-only.com", {row["email"] for row in rejected})

    def test_bulk_file_create_writes_raw_and_candidate_with_same_tagged_document(self) -> None:
        result = self._run()

        self.assertEqual(0, result.returncode, result.stderr)
        lines = (self.output / "experts_bulk.ndjson").read_text(encoding="utf-8").splitlines()
        self.assertEqual(4, len(lines))
        raw_action = json.loads(lines[0])
        raw_document = json.loads(lines[1])
        candidate_action = json.loads(lines[2])
        candidate_document = json.loads(lines[3])
        expected_id = "EMAIL-" + hashlib.sha256(b"ada@acme-devices.com").hexdigest()[:19]
        self.assertEqual(
            {"create": {"_index": "orcid_info", "_id": expected_id}},
            raw_action,
        )
        self.assertEqual(
            {"create": {"_index": "orcid_info_candidate", "_id": expected_id}},
            candidate_action,
        )
        self.assertEqual(expected_id, raw_document["orcidId"])
        self.assertEqual("ada@acme-devices.com", raw_document["email"])
        self.assertEqual("SBIR", raw_document["dataSource"])
        self.assertEqual("SBIR_PUBLIC_AWARD", raw_document["emailSource"])
        self.assertEqual("PASSED", raw_document["filterResult"])
        self.assertIn("SBIR Phase II research and development", raw_document["employment"])
        self.assertEqual(2, len(raw_document["recentWorkTitles"]))
        self.assertIn("SBIR导入", raw_document["tags"])
        self.assertIn("candidateValidatedAt", candidate_document)
        self.assertEqual(raw_document["tags"], candidate_document["tags"])

    def test_es_read_only_lookup_returns_unique_email_buckets(self) -> None:
        self.assertTrue(
            hasattr(target, "fetch_existing_emails_from_es"),
            "ES read-only email lookup must be implemented",
        )

        class FakeResponse:
            def __enter__(self) -> "FakeResponse":
                return self

            def __exit__(self, *args: object) -> None:
                pass

            def read(self) -> bytes:
                return json.dumps(
                    {
                        "aggregations": {
                            "existing_emails": {
                                "buckets": [
                                    {"key": "ada@acme-devices.com", "doc_count": 2},
                                    {"key": "alias@unrequested.example", "doc_count": 1},
                                ]
                            }
                        }
                    }
                ).encode("utf-8")

        with patch.object(target.urllib.request, "urlopen", return_value=FakeResponse()) as mocked:
            existing = target.fetch_existing_emails_from_es(
                es_url="https://es.example.com:9200",
                indexes="raw,candidate,application",
                emails=["ada@acme-devices.com", "new@new-labs.com"],
                username="elastic",
                password="secret",
                timeout=10,
                batch_size=500,
                insecure=True,
                host_header="es.example.com:19200",
            )

        self.assertEqual({"ada@acme-devices.com"}, existing)
        request = mocked.call_args.args[0]
        self.assertEqual(
            "https://es.example.com:9200/raw,candidate,application/_search",
            request.full_url,
        )
        self.assertEqual("es.example.com:19200", request.get_header("Host"))
        payload = json.loads(request.data)
        self.assertEqual(0, payload["size"])
        self.assertEqual(
            ["ada@acme-devices.com", "new@new-labs.com"],
            payload["query"]["terms"]["email"],
        )
        context = mocked.call_args.kwargs["context"]
        self.assertEqual(ssl.CERT_NONE, context.verify_mode)
        self.assertFalse(context.check_hostname)

    def test_summary_reconciles_current_rows_and_outputs(self) -> None:
        result = self._run()

        self.assertEqual(0, result.returncode, result.stderr)
        summary = json.loads((self.output / "summary.json").read_text(encoding="utf-8"))
        self.assertEqual(8, summary["current_rows"])
        self.assertEqual(1, summary["import_experts"])
        self.assertEqual(6, summary["rejected_rows"])
        self.assertEqual(1, summary["existing_email_count"])
        self.assertEqual(1, summary["archive_abstract_matches"])
        with (self.output / "es_existing_emails.csv").open(
            encoding="utf-8", newline=""
        ) as handle:
            self.assertEqual([], list(csv.DictReader(handle)))

    def test_es_status_message_distinguishes_query_from_write(self) -> None:
        self.assertEqual(
            "ES queried read-only; matches=14; no ES data modified",
            target.es_status_message("https://127.0.0.1:19200", 14),
        )
        self.assertEqual(
            "ES was not contacted or modified",
            target.es_status_message("", 0),
        )


if __name__ == "__main__":
    unittest.main()
