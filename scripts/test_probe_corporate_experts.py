"""Black-box regression tests; all people/addresses below are synthetic."""
import copy
import csv
import json
import importlib.util
import io
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
import urllib.parse
from unittest.mock import Mock

SCRIPT = Path(__file__).with_name("probe_corporate_experts.py")


def person(pid, first="John", last="Smith", email=None, title="Director of R&D"):
    return {"id": pid, "first_name": first, "last_name": last,
            "name": first + " " + last, "title": title, "country": "Italy",
            "email": email, "organization_id": "org-1",
            "organization": {"id": "org-1", "name": "Test Cable Group",
                             "primary_domain": "testcable.example", "industry": "manufacturing"},
            "employment_history": [{"organization_id": "org-1", "current": True,
                                    "title": title}], "linkedin_url": ""}


class CorporateProbeTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.fixture = self.root / "fixture.json"
        self.output = self.root / "out"
        self.data = {
            "organizations": [{"id": "org-1", "name": "Test Cable Group",
                               "primary_domain": "testcable.example", "industry": "manufacturing"}],
            "search_pages": [[person("p1", email="john.smith@testcable.example")]],
            "people": {"p1": person("p1", email="john.smith@testcable.example")},
            "verification": {"john.smith@testcable.example": {"status": "success", "result": "valid", "flags": []}},
        }

    def run_probe(self, *args):
        self.fixture.write_text(json.dumps(self.data), encoding="utf-8")
        env = {k: v for k, v in os.environ.items() if k not in {"APOLLO_API_KEY", "NEVERBOUNCE_API_KEY"}}
        return subprocess.run([sys.executable, str(SCRIPT), "--company", "Test Cable Group",
                               "--fixture-file", str(self.fixture), "--output-dir", str(self.output),
                               *args], capture_output=True, text=True, env=env, timeout=15)

    def rows(self, name):
        with (self.output / name).open(encoding="utf-8-sig", newline="") as stream:
            return list(csv.DictReader(stream))

    def summary(self):
        return json.loads((self.output / "summary.json").read_text())

    def test_dry_run_needs_no_key_and_does_not_create_checkpoint(self):
        result = self.run_probe("--dry-run")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertFalse((self.output / "checkpoint.json").exists())
        self.assertIn("mixed_companies/search", result.stdout)

    def test_current_rnd_person_keeps_evidence_and_does_not_claim_qualification(self):
        result = self.run_probe()
        self.assertEqual(0, result.returncode, result.stderr)
        row = self.rows("review.csv")[0]
        self.assertEqual("john.smith@testcable.example", row["email"])
        self.assertEqual("APOLLO", row["email_source"])
        self.assertEqual("NEEDS_RND_EVIDENCE", row["qualification_status"])
        self.assertEqual("valid", row["verification_result"])
        self.assertEqual("OFFLINE_FIXTURE", row["run_mode"])
        self.assertIn("Director of R&D", row["evidence"])
        journal = json.loads((self.output / "checkpoint.json").read_text())
        requests = [x for x in journal["requests"].values() if x["kind"] == "enrich"]
        self.assertEqual(False, requests[0]["params"]["reveal_phone_number"])
        self.assertEqual(False, requests[0]["params"]["run_waterfall_email"])

    def test_guess_after_missing_email_and_stop_after_first_valid(self):
        self.data["people"]["p1"]["email"] = None
        self.data["verification"]["john.smith@testcable.example"]["result"] = "invalid"
        self.data["verification"]["jsmith@testcable.example"] = {"status": "success", "result": "valid"}
        result = self.run_probe("--max-verifications", "2")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(["john.smith@testcable.example", "jsmith@testcable.example"],
                         [x["email"] for x in self.rows("email_checks.csv")])
        self.assertEqual("GUESSED", self.rows("review.csv")[0]["email_source"])
        self.assertEqual("UNCONFIRMED", self.rows("review.csv")[0]["email_ownership"])

    def test_catchall_does_not_try_other_patterns_or_appear_as_valid(self):
        self.data["people"]["p1"]["email"] = None
        self.data["verification"]["john.smith@testcable.example"]["result"] = "catchall"
        result = self.run_probe()
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(1, len(self.rows("email_checks.csv")))
        self.assertEqual([], self.rows("review.csv"))
        self.assertEqual("EMAIL_UNCERTAIN", self.rows("people.csv")[0]["status"])

    def test_timeout_is_reserved_and_not_retried_on_resume(self):
        self.data["verification"]["john.smith@testcable.example"] = {"_error": "timeout"}
        result = self.run_probe()
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual("REQUEST_UNCERTAIN", self.rows("email_checks.csv")[0]["verification_result"])
        result = self.run_probe()
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(1, self.summary()["attempts"]["verify"])
        self.assertEqual(0, self.summary()["new_attempts"]["verify"])

    def test_validation_cap_persists_and_can_be_raised_on_resume(self):
        self.data["people"]["p1"]["email"] = None
        self.data["verification"]["john.smith@testcable.example"]["result"] = "invalid"
        self.data["verification"]["jsmith@testcable.example"] = {"status": "success", "result": "valid"}
        result = self.run_probe("--max-verifications", "1")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual("VERIFICATION_LIMIT", self.rows("people.csv")[0]["status"])
        result = self.run_probe("--max-verifications", "2")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(2, self.summary()["attempts"]["verify"])
        self.assertEqual(1, self.summary()["new_attempts"]["verify"])
        self.assertEqual("jsmith@testcable.example", self.rows("review.csv")[0]["email"])

    def test_foreign_company_sales_and_china_are_not_verified(self):
        other = person("p2", "Mary", "Jones", title="Principal Scientist")
        other["organization_id"] = "other"
        other["organization"]["id"] = "other"
        other["employment_history"][0]["current"] = False
        sales = person("p3", title="Director of Sales")
        china = person("p4")
        china["country"] = "China"
        self.data["search_pages"] = [[other, sales, china]]
        self.data["people"] = {p["id"]: p for p in [other, sales, china]}
        result = self.run_probe()
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual([], self.rows("email_checks.csv"))
        self.assertEqual(0, self.summary()["attempts"]["verify"])

    def test_ambiguous_companies_stop_before_person_enrichment(self):
        self.data["organizations"].append({"id": "org-2", "name": "Test Cable",
                                            "primary_domain": "another.example"})
        result = self.run_probe()
        self.assertEqual(2, result.returncode, result.stderr)
        self.assertEqual(2, len(self.rows("organizations.csv")))
        self.assertEqual(0, self.summary()["attempts"]["enrich"])

    def test_obfuscated_name_never_generates_addresses(self):
        self.data["people"]["p1"].update(email=None, last_name=None, last_name_obfuscated="Sm***")
        result = self.run_probe()
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual([], self.rows("email_checks.csv"))
        self.assertEqual("NAME_INCOMPLETE", self.rows("people.csv")[0]["status"])

    def test_duplicate_person_and_shared_mailbox_do_not_spend_twice(self):
        duplicate = copy.deepcopy(self.data["search_pages"][0][0])
        p2 = person("p2", "Jane", "Doe", "john.smith@testcable.example")
        self.data["search_pages"] = [[duplicate, duplicate, p2]]
        self.data["people"]["p2"] = p2
        result = self.run_probe("--limit", "2")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(2, len(self.rows("people.csv")))
        self.assertEqual(1, self.summary()["attempts"]["verify"])
        self.assertTrue(all(p["email_ownership"] == "CONFLICT" for p in self.rows("review.csv")))

    def test_unknown_country_does_not_count_as_overseas(self):
        self.data["people"]["p1"]["country"] = None
        self.data["search_pages"][0][0]["country"] = None
        result = self.run_probe()
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual("COUNTRY_UNKNOWN", self.rows("people.csv")[0]["status"])
        self.assertEqual(0, self.summary()["attempts"]["verify"])

    def test_changed_company_search_config_cannot_reuse_checkpoint(self):
        self.assertEqual(0, self.run_probe().returncode)
        result = self.run_probe("--keywords", "optical fiber")
        self.assertEqual(2, result.returncode)
        self.assertIn("output", result.stderr.lower())

    def test_defaults_spend_on_at_most_one_person_and_one_email(self):
        self.data["people"]["p1"]["email"] = None
        self.data["verification"]["john.smith@testcable.example"]["result"] = "invalid"
        p2 = person("p2", "Jane", "Doe", "jane.doe@testcable.example")
        self.data["search_pages"][0].append(p2)
        self.data["people"]["p2"] = p2
        result = self.run_probe()
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(1, self.summary()["attempts"]["enrich"])
        self.assertEqual(1, self.summary()["attempts"]["verify"])

    def test_company_website_change_does_not_discard_provider_work_email(self):
        # Website and corporate email domains can differ after a rebrand.
        self.data["people"]["p1"]["email"] = "john.smith@testcablegroup.example"
        self.data["verification"]["john.smith@testcablegroup.example"] = {"status": "success", "result": "valid"}
        result = self.run_probe()
        self.assertEqual(0, result.returncode, result.stderr)
        row = self.rows("review.csv")[0]
        self.assertEqual("john.smith@testcablegroup.example", row["email"])
        self.assertEqual("DIFFERENT_DOMAIN_REVIEW", row["email_domain_match"])

    def test_free_mail_is_not_used_as_work_email(self):
        self.data["people"]["p1"]["email"] = "john@gmail.com"
        result = self.run_probe()
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual("john.smith@testcable.example", self.rows("email_checks.csv")[0]["email"])

    def test_pending_request_after_crash_is_not_submitted_again(self):
        self.assertEqual(0, self.run_probe().returncode)
        checkpoint = self.output / "checkpoint.json"
        state = json.loads(checkpoint.read_text())
        item = next(v for v in state["requests"].values() if v["kind"] == "verify")
        item["state"] = "PENDING"
        item.pop("response")
        checkpoint.write_text(json.dumps(state))
        result = self.run_probe()
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(0, self.summary()["new_attempts"]["verify"])
        self.assertEqual("REQUEST_UNCERTAIN", self.rows("email_checks.csv")[0]["verification_result"])

    def test_wire_parameters_use_scoped_auth_and_preserve_plus_in_email(self):
        spec = importlib.util.spec_from_file_location("probe_test_module", SCRIPT)
        probe = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(probe)
        keys = {"APOLLO_API_KEY": "dummy-apollo-key", "NEVERBOUNCE_API_KEY": "dummy-nb-key"}
        request = probe.request_spec("search", {"organization_ids": ["a", "b"], "person_titles": ["R&D"]}, keys)
        query = urllib.parse.parse_qs(urllib.parse.urlsplit(request.full_url).query)
        self.assertEqual(["a", "b"], query["organization_ids[]"])
        self.assertEqual(["R&D"], query["person_titles[]"])
        self.assertEqual("POST", request.method)
        self.assertEqual("dummy-apollo-key", request.get_header("X-api-key"))
        self.assertNotIn("dummy-nb-key", request.full_url)
        request = probe.request_spec("verify", {"email": "john+test@testcable.example"}, keys)
        query = urllib.parse.parse_qs(urllib.parse.urlsplit(request.full_url).query)
        self.assertEqual(["john+test@testcable.example"], query["email"])
        self.assertNotIn("dummy-apollo-key", request.full_url)

    def test_secret_redaction_covers_response_fields_and_error_messages(self):
        spec = importlib.util.spec_from_file_location("probe_redaction_test", SCRIPT)
        probe = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(probe)
        secret = "dummy-secret-value"
        value = {"error": "bad key=" + secret, "api_key": "unexpected-token", "nested": [{"key": secret}]}
        scrubbed = probe.redact(value, [secret])
        self.assertNotIn(secret, json.dumps(scrubbed))
        self.assertNotIn("unexpected-token", json.dumps(scrubbed))

    def test_neverbounce_failure_preserves_redacted_diagnostic_across_resume(self):
        spec = importlib.util.spec_from_file_location("probe_diagnostic_test", SCRIPT)
        probe = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(probe)
        keys = {"APOLLO_API_KEY": "dummy-apollo-key", "NEVERBOUNCE_API_KEY": "dummy-nb-key"}
        transport = probe.LiveTransport(keys, delay=0)
        transport.opener = Mock()
        body = {"status": "general_failure", "message": "Insufficient credits; supplied key=dummy-nb-key", "execution_time": 5}
        transport.opener.open.return_value = io.BytesIO(json.dumps(body).encode())
        self.output.mkdir()
        journal = probe.Journal(self.output, {"test": True}, {"verify": 1}, transport)
        with self.assertRaises(probe.ApiFailure) as failure:
            journal.call("verify", {"email": "john.smith@testcable.example"})
        self.assertIn("Insufficient credits", str(failure.exception))
        raw = (self.output / "checkpoint.json").read_text()
        self.assertNotIn("dummy-nb-key", raw)
        request = next(iter(json.loads(raw)["requests"].values()))
        self.assertEqual("general_failure", request["response"]["status"])
        self.assertIn("Insufficient credits", request["response"]["message"])
        second = probe.Journal(self.output, {"test": True}, {"verify": 1}, transport)
        with self.assertRaises(probe.ApiFailure) as resumed:
            second.call("verify", {"email": "john.smith@testcable.example"})
        self.assertIn("Insufficient credits", str(resumed.exception))
        self.assertEqual(1, transport.opener.open.call_count)


if __name__ == "__main__":
    unittest.main()
