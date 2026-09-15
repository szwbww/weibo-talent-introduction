import unittest
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from enrich_engineer_emails import (
    build_bulk_request,
    may_continue,
    remaining_capacity,
    select_candidates,
)


class EmailEnrichmentSafetyTest(unittest.TestCase):
    def test_selection_uses_only_a_b_email_available_and_excludes_public_or_stale(self):
        source = {
            "apollo_email_available": [
                {"engineer_id": "e-b", "apollo_person_id": "p-b", "name": "B", "title": "Senior Engineer", "high_value_tier": "B", "domestic_enterprise_match_count": 9, "apollo_has_email": True},
                {"engineer_id": "e-a", "apollo_person_id": "p-a", "name": "A", "title": "Principal Engineer", "high_value_tier": "A", "domestic_enterprise_match_count": 3, "apollo_has_email": True},
                {"engineer_id": "e-c", "apollo_person_id": "p-c", "name": "C", "title": "Principal Engineer", "high_value_tier": "C", "domestic_enterprise_match_count": 99, "apollo_has_email": True},
                {"engineer_id": "e-no", "apollo_person_id": "p-no", "name": "No", "title": "Engineer", "high_value_tier": "A", "domestic_enterprise_match_count": 99, "apollo_has_email": False},
            ],
            "email_discovery_required": [],
        }
        public = {"results": [
            {"apollo_person_id": "p-a", "status": "PUBLIC_EMAIL_CONFIRMED"},
            {"apollo_person_id": "p-b", "status": "NO_CONFIRMED_PUBLIC_EMAIL", "employment_review_required": True},
        ]}
        self.assertEqual([], select_candidates(source, public, 10))

    def test_selection_ranks_a_before_b_and_deduplicates_person_id(self):
        source = {
            "apollo_email_available": [
                {"engineer_id": "e-b", "apollo_person_id": "p-b", "name": "B", "title": "Principal Engineer", "high_value_tier": "B", "domestic_enterprise_match_count": 20, "apollo_has_email": True},
                {"engineer_id": "e-a", "apollo_person_id": "p-a", "name": "A", "title": "Senior Engineer", "high_value_tier": "A", "domestic_enterprise_match_count": 2, "apollo_has_email": True},
                {"engineer_id": "e-a2", "apollo_person_id": "p-a", "name": "A duplicate", "title": "Senior Engineer", "high_value_tier": "A", "domestic_enterprise_match_count": 1, "apollo_has_email": True},
            ],
            "email_discovery_required": [],
        }
        got = select_candidates(source, {"results": []}, 10)
        self.assertEqual(["p-a", "p-b"], [row["apollo_person_id"] for row in got])

    def test_bulk_request_can_only_request_native_work_email(self):
        url, body = build_bulk_request(["p-1", "p-2"])
        self.assertEqual("https://api.apollo.io/api/v1/people/bulk_match?reveal_personal_emails=false&reveal_phone_number=false&run_waterfall_email=false&run_waterfall_phone=false", url)
        self.assertEqual({"details": [{"id": "p-1"}, {"id": "p-2"}]}, body)

    def test_remaining_capacity_preserves_reserve(self):
        self.assertEqual(700, remaining_capacity(left=782, reserve=82, requested=700))
        self.assertEqual(8, remaining_capacity(left=90, reserve=82, requested=100))
        self.assertEqual(0, remaining_capacity(left=82, reserve=82, requested=100))

    def test_shared_balance_jump_does_not_stop_above_reserve(self):
        self.assertTrue(may_continue(left_after=662, reserve=82))
        self.assertFalse(may_continue(left_after=82, reserve=82))


if __name__ == "__main__":
    unittest.main()
