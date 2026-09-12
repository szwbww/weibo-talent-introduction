#!/usr/bin/env python3

import sys
import unittest
from pathlib import Path


sys.path.insert(0, str(Path(__file__).resolve().parent))

import spike_deepseek_reply as target  # noqa: E402


SPECIFIC_EMAIL = """Dear LiLei,

Please clarify the remote advisory work, including the expected workload,
frequency of consultations, reporting format and technical milestones.
Please also explain the financial terms, payment schedule, currency and
remittance method, and the expected duration of each visit to China.
I have provided my current research focus so that you can identify relevant
industry matches.
"""


class SpecificQuestionRoutingTest(unittest.TestCase):
    def test_programme_name_precedes_proof_and_overview(self) -> None:
        inbound = "What is the official programme name? Please also provide a programme overview."

        self.assertEqual(
            ("KB-PROG-003", "KB-COMP-007", "KB-PROG-002"),
            target.mandatory_fact_ids(inbound)[:3],
        )

        by_id = {fact.fact_id: fact for fact in target.V2_KNOWLEDGE_BASE}
        self.assertEqual(
            "The official name of the initiative is the Qiming Program (启明计划). "
            "It is a national-level talent and innovation program led by China’s Ministry "
            "of Science and Technology and implemented locally through government talent offices.",
            by_id["KB-PROG-003"].answer,
        )
        self.assertNotIn("KB-GOV-004", target.apply_fact_compatibility(
            ["KB-GOV-004", "KB-PROG-002", "KB-COMP-007", "KB-PROG-003"]
        ))

    def test_default_know_more_email_routes_to_general_overview(self) -> None:
        inbound = "I would like to know more about this program."
        self.assertEqual("GENERAL_OVERVIEW", target.classify_request(inbound))
        self.assertEqual(
            ("KB-PROG-002", "KB-FUND-033"),
            target.mandatory_fact_ids(inbound),
        )

    def test_current_email_is_specific_and_does_not_force_general_overview(self) -> None:
        self.assertEqual("SPECIFIC_QUESTIONS", target.classify_request(SPECIFIC_EMAIL))

        mandatory = target.mandatory_fact_ids(SPECIFIC_EMAIL)

        self.assertEqual(
            (
                "KB-WORK-052",
                "KB-WORK-055",
                "KB-PAY-049",
                "KB-FUND-053",
                "KB-FEE-054",
                "KB-TRAVEL-050",
                "KB-MATCH-051",
            ),
            mandatory,
        )
        self.assertNotIn("KB-PROG-002", mandatory)
        self.assertNotIn("KB-FUND-033", mandatory)

    def test_general_overview_still_uses_original_overview_and_salary_facts(self) -> None:
        inbound = "Could you explain the overall nature of the programme?"

        self.assertEqual("GENERAL_OVERVIEW", target.classify_request(inbound))
        self.assertEqual(
            ("KB-PROG-002", "KB-FUND-033"),
            target.mandatory_fact_ids(inbound),
        )
        self.assertEqual(
            ["KB-PROG-002", "KB-FUND-033"],
            [fact.fact_id for fact in target.prefilter_facts(inbound)],
        )

    def test_overview_suppresses_redundant_remote_summary_but_keeps_workload_detail(self) -> None:
        selected = target.apply_fact_compatibility(
            ["KB-PROG-002", "KB-WORK-052", "KB-WORK-055"]
        )

        self.assertEqual(["KB-PROG-002", "KB-WORK-055"], selected)


class ApprovedReplyFactTest(unittest.TestCase):
    def test_specific_email_retrieves_only_approved_section_facts(self) -> None:
        facts = target.prefilter_facts(SPECIFIC_EMAIL)
        fact_ids = {fact.fact_id for fact in facts}

        self.assertTrue(set(target.mandatory_fact_ids(SPECIFIC_EMAIL)).issubset(fact_ids))
        self.assertTrue(
            {
                "KB-PROG-002",
                "KB-FUND-033",
                "KB-FUND-034",
                "KB-FUND-035",
                "KB-FUND-036",
                "KB-WORK-030",
                "KB-WORK-031",
                "KB-WORK-032",
            }.isdisjoint(fact_ids)
        )

    def test_specific_finance_and_travel_facts_match_approved_reply(self) -> None:
        by_id = {fact.fact_id: fact for fact in target.V2_KNOWLEDGE_BASE}

        self.assertIn("currency, payment schedule and remittance method", by_id["KB-PAY-049"].answer)
        self.assertIn("approximately RMB 3–12 million", by_id["KB-FUND-053"].answer)
        self.assertNotIn("housing allowance", by_id["KB-FUND-053"].answer)
        self.assertIn("approximately one week", by_id["KB-TRAVEL-050"].answer)
        self.assertIn("company’s profile, website, location", by_id["KB-MATCH-051"].answer)

    def test_remote_summary_and_workload_detail_are_independent_facts(self) -> None:
        by_id = {fact.fact_id: fact for fact in target.V2_KNOWLEDGE_BASE}

        self.assertIn("part-time, remote technical advisory", by_id["KB-WORK-052"].answer)
        self.assertNotIn("universal workload", by_id["KB-WORK-052"].answer)
        self.assertIn("universal workload", by_id["KB-WORK-055"].answer)
        self.assertNotIn("part-time, remote technical advisory", by_id["KB-WORK-055"].answer)


class RenderingTest(unittest.TestCase):
    def test_programme_name_and_proof_are_placed_before_overview(self) -> None:
        by_id = {f.fact_id: f for f in target.V2_KNOWLEDGE_BASE}
        facts = [by_id["KB-PROG-002"], by_id["KB-COMP-007"], by_id["KB-PROG-003"]]
        draft = (
            "Dear Professor,\n\n1. Programme overview\n\n{{FACT:KB-PROG-002}}\n\n"
            "2. Programme name\n\n{{FACT:KB-PROG-003}}\n\n"
            "3. Verification\n\n{{FACT:KB-COMP-007}}\n\nBest regards,\nLiLei"
        )

        rendered = target.finalize_draft(draft, facts, target.ReplyContext())

        name_at = rendered.index("The official name of the initiative")
        proof_at = rendered.index("Qingfei cooperates with local government talent offices")
        overview_at = rendered.index("Two tracks:")
        self.assertLess(name_at, proof_at)
        self.assertLess(proof_at, overview_at)
        self.assertIn("1. Programme name", rendered)
        self.assertIn("2. Qingfei and government talent offices", rendered)
        self.assertIn("3. Programme overview", rendered)

    def test_overview_moves_before_matching_and_removes_generic_opening(self) -> None:
        by_id = {f.fact_id: f for f in target.V2_KNOWLEDGE_BASE}
        facts = [by_id["KB-MATCH-051"], by_id["KB-PROG-002"]]
        draft = ("Dear Professor,\n\nThank you for sharing your research. We are glad to hear from you.\n\n"
                 "1. Industry partners\n\n{{FACT:KB-MATCH-051}}\n\n"
                 "2. National talent programme\n\n{{FACT:KB-PROG-002}}\n\n"
                 "Best regards,\nLiLei")
        rendered = target.finalize_draft(draft, facts, target.ReplyContext(cv_status="RECEIVED"))
        self.assertTrue(rendered.startswith("Dear Professor,\n\n1. National talent programme\n\nTwo tracks:"))
        self.assertNotIn("Thank you", rendered)
        self.assertIn("2. Industry partners", rendered)
        self.assertEqual(1, rendered.count("Two tracks:"))
        self.assertIn("identify and match you with suitable Chinese companies", rendered)

    def test_confirmed_current_materials_allow_short_acknowledgement(self) -> None:
        rendered = target.finalize_draft(
            "Dear Professor,\n\nThank you for sending your CV.\n\nThe next step follows.", [],
            target.ReplyContext(materials_received_this_reply=True),
        )
        self.assertIn("Thank you for sending your CV.", rendered)

    def test_missing_model_tokens_are_inserted_and_rendered(self) -> None:
        facts = target.prefilter_facts(SPECIFIC_EMAIL)
        draft = (
            "Dear Professor,\n\n"
            "1. Remote Advisory Work\n\n{{FACT:KB-WORK-052}}\n\n"
            "2. Financial Terms and Payment\n\n{{FACT:KB-PAY-049}}\n\n"
            "3. Visits to China\n\n{{FACT:KB-TRAVEL-050}}\n\n"
            "Please let us know if you have any further questions.\n\n"
            "Best regards,\nSomeone"
        )

        rendered = target.finalize_draft(
            draft,
            facts,
            target.ReplyContext(expert_name="Aboulila", sender_name="LiLei"),
        )

        self.assertIn("Dear Professor Aboulila,", rendered)
        self.assertIn("approximately RMB 3–12 million", rendered)
        self.assertIn("Qingfei does not charge experts", rendered)
        self.assertIn("company’s profile, website, location", rendered)
        self.assertIn("LiLei, Customer Care Officer Qingfei Tech Talent Team China", rendered)
        self.assertNotIn("{{FACT:", rendered)
        self.assertNotIn("\n\n\n", rendered)


class FirstReplyTest(unittest.TestCase):
    INBOUND = """My current research interests include gas-sensing nanomaterials.
I would be pleased to learn more about the potential industry partners, the
national talent programme, expected collaboration scope, funding mechanism,
advisory responsibilities, and possible remuneration structure. I would
also appreciate information regarding the application process and
eligibility requirements. Please feel free to share further details or
arrange an initial discussion by email or online meeting."""

    def test_first_reply_covers_questions_without_redundancy_or_materials_and_meeting(self) -> None:
        facts = target.prefilter_facts(self.INBOUND, process_context=target.ReplyContext(cv_status="MISSING"))
        ids = [f.fact_id for f in facts]
        self.assertEqual("KB-PROG-002", ids[0])
        self.assertEqual({"KB-PROG-002", "KB-MATCH-051", "KB-PAY-049", "KB-FUND-053",
                          "KB-FEE-054", "KB-ROLE-056", "KB-APP-058", "KB-APP-057"}, set(ids))
        required = set(target.requested_coverage_keys(self.INBOUND))
        covered = {key for fact in facts for key in fact.coverage_keys}
        self.assertEqual({"application.eligibility_details"}, required - covered)

    def test_materials_and_meeting_excluded_even_when_lexical_fallback_retrieves(self) -> None:
        facts = target.prefilter_facts("CV initial materials Zoom meeting passport", process_context=target.ReplyContext())
        self.assertTrue({"KB-APP-018", "KB-COMM-044", "KB-APP-019", "KB-APP-043"}.isdisjoint(f.fact_id for f in facts))

    def test_later_reply_retains_light_cv_request_gate(self) -> None:
        inbound = "I am interested. What are the next steps?"
        for count, cv_status, expected in ((1, "MISSING", False), (2, "MISSING", True), (2, "RECEIVED", False), (2, "UNKNOWN", False)):
            context = target.ReplyContext(expert_reply_count=count, cv_status=cv_status, expert_tags=("WILLING_TO_CONTINUE",))
            ids = {f.fact_id for f in target.prefilter_facts(inbound, process_context=context)}
            self.assertEqual(expected, "KB-APP-018" in ids)


if __name__ == "__main__":
    unittest.main()
