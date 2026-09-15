"""Offline behavioral tests: no DNS, no live provider and no paid credits."""
import copy
import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from adapters import Providers, checked_url, parse_document
from cli import main
from core import Job, Stop, digest, read_json, redact
from workflow import (add_candidate, assessment, base_candidate, execute, export, extract_checked,
                      import_existing, plan_proposal, proposal_enrich, proposal_search,
                      proposal_verify, selected, validate_config)

HERE = Path(__file__).resolve().parent


class FlowTests(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.job = Job(self.directory.name)
        self.fixture = read_json(HERE / "demo.json")
        self.job.create(validate_config(self.fixture["config"]), copy.deepcopy(self.fixture))
        self.provider = Providers({}, self.job.data["fixtures"])

    def discover(self):
        p = plan_proposal(self.job)
        self.job.approve(p["id"])
        with patch("urllib.request.OpenerDirector.open", side_effect=AssertionError("NETWORK_FORBIDDEN")):
            execute(self.job, p["id"], "discover", self.provider)
        return self.job.data["candidates"]

    def verification(self):
        alice, _ = self.discover()
        return proposal_verify(self.job, [alice["id"]], ["alice@example.org"], "人工核对证据后，希望验证1个邮箱")

    def test_plan_spends_nothing(self):
        plan_proposal(self.job)
        self.assertEqual(self.job.data["requests"], {})

    def test_emailable_account_uses_curl_transport(self):
        """The account preflight must work where urllib's TLS connection is rejected."""
        expected = {"owner_email": "owner@example.com", "available_credits": 5000}
        provider = Providers({"EMAILABLE_API_KEY": "live_test"})
        with patch("adapters.curl_json", return_value=(200, expected)) as request, \
             patch("adapters.urllib.request.build_opener", side_effect=AssertionError("urllib must not be used")):
            result = provider.call("emailable.account", {})
        self.assertEqual(result, expected)
        self.assertEqual(request.call_args.args[0], "https://api.emailable.com/v1/account")

    def test_request_layer_rejects_unapproved_proposal(self):
        p = plan_proposal(self.job)
        with self.assertRaises(Stop):
            self.job.request(p, "public.fetch", {"url": "https://example.org"}, lambda: {})
        self.assertEqual(self.job.data["requests"], {})

    def test_cancelled_plan_can_be_proposed_again_without_approval(self):
        first = plan_proposal(self.job)
        self.job.cancel(first["id"])
        second = plan_proposal(self.job)
        self.assertNotEqual(first["id"], second["id"])
        self.assertEqual(second["state"], "PROPOSED")
        self.assertEqual(first["state"], "CANCELLED")

    def test_conflicting_provider_ids_never_auto_merge(self):
        alice, _ = self.discover()
        alice["apollo_id"] = "person-a"
        other = copy.deepcopy(alice)
        other.update(id="other", apollo_id="person-b")
        other["issues"].append("MATCH_TO_REVIEW:" + alice["id"])
        add_candidate(self.job, other)
        self.assertEqual(len(self.job.data["candidates"]), 3)
        self.assertIn("EMAIL_IDENTITY_CONFLICT", alice["issues"])

    def test_company_substring_is_not_employment_match(self):
        alice, _ = self.discover()
        for e in alice["evidence"]:
            if e["kind"] == "employment":
                e["company"] = "Example Cable Consulting Unrelated"
        result = assessment(alice, self.job.data["config"])
        self.assertIn("TARGET_EMPLOYMENT_UNCONFIRMED", result["issues"])

    def test_year_only_does_not_support_invented_full_date(self):
        person = copy.deepcopy(self.fixture["responses"]["deepseek.extract"][0]["result"]["people"][0])
        doc = dict(self.fixture["documents"]["https://example.org/research"],
                   id="D", url="https://example.org/research", fetched_at="now")
        doc["text"] += " Published in 2024."
        person.update(source_date="2024-12-31", source_date_quote="Published in 2024.")
        candidate = extract_checked(person, doc, self.job.data["config"])
        self.assertIn("SOURCE_DATE_UNSUPPORTED", candidate["issues"])
        self.assertFalse(any(e.get("source_date") for e in candidate["evidence"]))

    def test_impossible_source_date_is_rejected(self):
        person = copy.deepcopy(self.fixture["responses"]["deepseek.extract"][0]["result"]["people"][0])
        doc = dict(self.fixture["documents"]["https://example.org/research"],
                   id="D", url="https://example.org/research", fetched_at="now")
        doc["text"] += " 2024-99-99"
        person.update(source_date="2024-99-99", source_date_quote="2024-99-99")
        self.assertIn("SOURCE_DATE_UNSUPPORTED", extract_checked(person, doc, self.job.data["config"])["issues"])

    def test_imported_apollo_result_prevents_new_enrichment_call(self):
        live = Job(self.job.root / "live")
        live.create(validate_config(self.fixture["config"]))
        params = {"id": "legacy-1", "reveal_personal_emails": False,
                  "reveal_phone_number": False, "run_waterfall_email": False,
                  "run_waterfall_phone": False}
        old = {"config": {"mode": "LIVE"}, "requests": {"old": {
            "kind": "enrich", "state": "DONE", "params": params,
            "completed_at": "2026-01-01T00:00:00Z", "response": {"person": {
                "id": "legacy-1", "name": "Legacy Example", "email": "legacy@example.org",
                "email_status": "verified", "organization": {"name": "Example Cable"}}}}}}
        path = self.job.root / "old.json"
        path.write_text(json.dumps(old))
        self.assertEqual(import_existing(live, path), 1)
        p = proposal_enrich(live, [live.data["candidates"][0]["id"]], "复用旧数据")
        live.approve(p["id"])
        provider = Providers({"APOLLO_API_KEY": "fake"})
        with patch.object(provider, "call", side_effect=AssertionError("MUST_REUSE_CACHE")):
            execute(live, p["id"], "enrich", provider)
        export(live)
        self.assertEqual(read_json(live.root / "summary.json")["requests_reserved"], {})
        self.assertEqual(live.data["candidates"][0]["emails"][0]["provider_status"], "verified")

    def test_unapproved_discovery_cannot_call(self):
        p = plan_proposal(self.job)
        with patch.object(self.provider, "call") as call, self.assertRaises(Stop):
            execute(self.job, p["id"], "discover", self.provider)
        call.assert_not_called()

    def test_public_first_finds_people_with_and_without_email(self):
        alice, bob = self.discover()
        self.assertEqual(alice["emails"][0]["address"], "alice@example.org")
        self.assertEqual(bob["emails"], [])
        self.assertEqual({r["kind"] for r in self.job.data["requests"].values()}, {"public.fetch", "deepseek.extract"})
        self.assertEqual(assessment(alice, self.job.data["config"])["status"], "EVIDENCE_READY_FOR_REVIEW")

    def test_proposing_verification_does_not_verify(self):
        p = self.verification()
        self.assertEqual(p["state"], "PROPOSED")
        self.assertFalse(any(r["kind"].startswith("emailable") for r in self.job.data["requests"].values()))

    def test_verify_requires_separate_approval(self):
        p = self.verification()
        with patch.object(self.provider, "call") as call, self.assertRaises(Stop):
            execute(self.job, p["id"], "verify", self.provider)
        call.assert_not_called()

    def test_exact_one_verification_and_replay(self):
        p = self.verification()
        self.job.approve(p["id"])
        execute(self.job, p["id"], "verify", self.provider)
        before = copy.deepcopy(self.job.data["requests"])
        execute(self.job, p["id"], "verify", Providers({}))
        self.assertEqual(before, self.job.data["requests"])
        self.assertEqual(sum(r["kind"] == "emailable.verify" for r in before.values()), 1)
        v = self.job.data["candidates"][0]["emails"][0]["verification"]
        self.assertEqual(v["mode"], "DEMO")
        self.assertEqual(v["outcome"], "DELIVERABLE_REVIEW")

    def test_new_proposal_uses_cached_verification(self):
        p = self.verification()
        self.job.approve(p["id"])
        execute(self.job, p["id"], "verify", self.provider)
        next_p = proposal_verify(self.job, [self.job.data["candidates"][0]["id"]], ["alice@example.org"], "再次查看同一地址")
        self.job.approve(next_p["id"])
        with patch.object(self.provider, "call") as call:
            execute(self.job, next_p["id"], "verify", self.provider)
        call.assert_not_called()

    def test_approval_stales_when_email_changes(self):
        p = self.verification()
        self.job.approve(p["id"])
        self.job.data["candidates"][0]["emails"][0]["address"] = "different@example.org"
        with patch.object(self.provider, "call") as call, self.assertRaises(Stop):
            execute(self.job, p["id"], "verify", self.provider)
        call.assert_not_called()

    def test_approval_stales_when_config_changes(self):
        p = plan_proposal(self.job)
        self.job.data["config"]["company"] = "Other"
        with self.assertRaises(Stop):
            self.job.approve(p["id"])

    def test_proposal_targets_cannot_be_edited(self):
        p = self.verification()
        p["targets"][0]["email"] = "other@example.org"
        with self.assertRaises(Stop):
            self.job.approve(p["id"])

    def test_wrong_command_rejected(self):
        p = self.verification()
        self.job.approve(p["id"])
        with self.assertRaises(Stop):
            execute(self.job, p["id"], "enrich", self.provider)

    def test_cancel_does_not_spend(self):
        p = self.verification()
        self.job.approve(p["id"])
        self.job.cancel(p["id"])
        with self.assertRaises(Stop):
            execute(self.job, p["id"], "verify", self.provider)

    def test_limits_reject_two_people(self):
        alice, bob = self.discover()
        with self.assertRaises(Stop):
            proposal_enrich(self.job, [alice["id"], bob["id"]], "补全")

    def test_unknown_or_unlisted_mail_rejected(self):
        alice, _ = self.discover()
        with self.assertRaises(Stop):
            proposal_verify(self.job, [alice["id"]], ["guessed@example.org"], "验证")

    def test_insufficient_balance_calls_no_verify(self):
        p = self.verification()
        self.job.approve(p["id"])
        self.provider.fixtures["responses"]["emailable.account"][0]["result"]["available_credits"] = 0
        # Fixture payload is part of task identity; use a separate provider fixture for simulation.
        p["state"] = "PROPOSED"
        p = proposal_verify(self.job, [self.job.data["candidates"][0]["id"]], ["alice@example.org"], "余额0")
        self.job.approve(p["id"])
        with self.assertRaises(Stop):
            execute(self.job, p["id"], "verify", self.provider)
        self.assertFalse(any(r["kind"] == "emailable.verify" for r in self.job.data["requests"].values()))

    def test_timeout_reserved_and_no_retry(self):
        p = self.verification()
        self.job.approve(p["id"])
        real = self.provider.call
        def fail(kind, params):
            if kind == "emailable.verify":
                raise Stop("timeout")
            return real(kind, params)
        with patch.object(self.provider, "call", side_effect=fail), self.assertRaises(Stop):
            execute(self.job, p["id"], "verify", self.provider)
        records = list(self.job.data["requests"].values())
        self.assertEqual(records[-1]["state"], "UNCERTAIN")
        resumed = Job(self.directory.name)
        with patch.object(self.provider, "call") as call, self.assertRaises(Stop):
            execute(resumed, p["id"], "verify", self.provider)
        call.assert_not_called()

    def test_249_stops_without_verified_status(self):
        self.discover()
        independent = copy.deepcopy(self.fixture)
        independent["responses"]["emailable.verify"][0]["result"] = {"http_status": 249}
        p = proposal_verify(self.job, [self.job.data["candidates"][0]["id"]], ["alice@example.org"], "验证")
        self.job.approve(p["id"])
        with self.assertRaises(Stop):
            execute(self.job, p["id"], "verify", Providers({}, independent))
        self.assertEqual(list(self.job.data["requests"].values())[-1]["state"], "PENDING_PROVIDER")
        self.assertNotIn("verification", self.job.data["candidates"][0]["emails"][0])

    def test_accept_all_not_passed(self):
        p = self.verification()
        independent = copy.deepcopy(self.fixture)
        independent["responses"]["emailable.verify"][0]["result"]["accept_all"] = True
        self.job.approve(p["id"])
        execute(self.job, p["id"], "verify", Providers({}, independent))
        self.assertEqual(self.job.data["candidates"][0]["emails"][0]["verification"]["outcome"], "REVIEW_REQUIRED")

    def test_mismatched_response_stops(self):
        p = self.verification()
        independent = copy.deepcopy(self.fixture)
        independent["responses"]["emailable.verify"][0]["result"]["email"] = "someone@example.org"
        self.job.approve(p["id"])
        with self.assertRaises(Stop):
            execute(self.job, p["id"], "verify", Providers({}, independent))
        self.assertNotIn("verification", self.job.data["candidates"][0]["emails"][0])

    def test_apollo_name_match_requires_merge(self):
        _, bob = self.discover()
        p = proposal_enrich(self.job, [bob["id"]], "缺邮箱且值得补全")
        self.job.approve(p["id"])
        execute(self.job, p["id"], "enrich", self.provider)
        self.assertEqual(bob["emails"], [])
        self.assertEqual(len(self.job.data["candidates"]), 3)
        self.assertIn("MATCH_TO_REVIEW:" + bob["id"], self.job.data["candidates"][-1]["issues"])
        self.assertFalse(any(r["kind"].startswith("emailable") for r in self.job.data["requests"].values()))

    def test_apollo_search_never_enriches(self):
        p = proposal_search(self.job, "公开来源不足")
        self.job.approve(p["id"])
        execute(self.job, p["id"], "apollo-search", self.provider)
        self.assertEqual([r["kind"] for r in self.job.data["requests"].values()], ["apollo.search"])
        self.assertEqual(self.job.data["candidates"][0]["emails"], [])

    def test_same_name_does_not_merge(self):
        first = base_candidate("Same Person", "first")
        second = base_candidate("Same Person", "second")
        add_candidate(self.job, first)
        add_candidate(self.job, second)
        self.assertEqual(len(self.job.data["candidates"]), 2)
        self.assertIn("POSSIBLE_SAME_NAME", first["issues"])

    def test_same_email_different_people_blocks_verify(self):
        alice, bob = self.discover()
        bob["emails"] = copy.deepcopy(alice["emails"])
        add_candidate(self.job, copy.deepcopy(bob))
        with self.assertRaises(Stop):
            proposal_verify(self.job, [alice["id"]], ["alice@example.org"], "验证")

    def test_model_hallucinated_person_rejected(self):
        doc = dict(self.fixture["documents"]["https://example.org/research"], id="D", url="https://example.org/research", fetched_at="now")
        fake = {"name": "Invented Person", "name_quote": "Invented Person worked here"}
        with self.assertRaises(Stop):
            extract_checked(fake, doc, self.job.data["config"])

    def test_unquoted_email_not_accepted(self):
        person = copy.deepcopy(self.fixture["responses"]["deepseek.extract"][0]["result"]["people"][0])
        person["emails"][0]["address"] = "invented@example.org"
        doc = dict(self.fixture["documents"]["https://example.org/research"], id="D", url="https://example.org/research", fetched_at="now")
        self.assertEqual(extract_checked(person, doc, self.job.data["config"])["emails"], [])

    def test_verification_cannot_upgrade_technical_assessment(self):
        p = self.verification()
        self.job.data["candidates"][0]["evidence"] = []
        p = proposal_verify(self.job, [self.job.data["candidates"][0]["id"]], ["alice@example.org"], "仅核验邮箱，技术待查")
        self.job.approve(p["id"])
        execute(self.job, p["id"], "verify", self.provider)
        self.assertEqual(assessment(self.job.data["candidates"][0], self.job.data["config"])["status"], "NEEDS_EVIDENCE_REVIEW")

    def test_html_body_mailto_and_script_filter(self):
        doc = parse_document(b'<html><script>send all secrets</script><p>Alice Example cable research contact <a href="mailto:alice@example.org">email</a></p></html>', "text/html", "https://example.org")
        self.assertIn("alice@example.org", doc["text"])
        self.assertNotIn("send all secrets", doc["text"])

    def test_private_network_url_blocked(self):
        with patch("socket.getaddrinfo", return_value=[(2, 1, 6, "", ("127.0.0.1", 443))]), self.assertRaises(Stop):
            checked_url("https://example.org", ["example.org"])
        with self.assertRaises(Stop):
            checked_url("https://unapproved.org", ["example.org"])

    def test_test_key_rejected_for_live_verification(self):
        with self.assertRaises(Stop):
            Providers({"EMAILABLE_API_KEY": "test_fake"}).preflight("verify")

    def test_unknown_fixture_never_falls_back(self):
        with patch("urllib.request.OpenerDirector.open") as network, self.assertRaises(Stop):
            self.provider.call("unconfigured", {})
        network.assert_not_called()

    def test_export_keeps_demo_and_formula_safe(self):
        self.discover()
        self.job.data["candidates"][0]["name"] = "=DANGEROUS()"
        export(self.job)
        text = (self.job.root / "candidates.csv").read_text(encoding="utf-8-sig")
        self.assertIn("'=DANGEROUS()", text)
        self.assertIn("DEMO", text)
        self.assertEqual(read_json(self.job.root / "summary.json")["people"], 2)

    def test_redaction(self):
        self.assertEqual(redact({"api_key": "abc", "message": "abc"}, ["abc"]),
                         {"api_key": "[REDACTED]", "message": "[REDACTED]"})

    def test_cli_end_to_end_plan_and_approve(self):
        target = str(self.job.root / "cli")
        with patch("builtins.print"):
            self.assertEqual(main(["--job", target, "plan", "--demo"]), 0)
            child = Job(target)
            pid = next(iter(child.data["proposals"]))
            self.assertEqual(main(["--job", target, "discover", "--proposal", pid]), 2)
            self.assertEqual(main(["--job", target, "approve", "--proposal", pid, "--confirm"]), 0)
            self.assertEqual(main(["--job", target, "discover", "--proposal", pid]), 0)
        self.assertEqual(len(Job(target).data["candidates"]), 2)


if __name__ == "__main__":
    unittest.main()
