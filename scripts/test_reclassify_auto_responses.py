"""Focused regression tests for the FIRST_REPLY_V3 reclassification script.

Standard library only: the script is loaded by path and every database call is
served by an in-memory fake cursor, so no MySQL server or PyMySQL install is
required.

Run: python3 -m unittest scripts/test_reclassify_auto_responses.py
"""

from __future__ import annotations

import datetime as dt
import importlib.util
import json
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock

SCRIPT_PATH = Path(__file__).resolve().with_name("reclassify-auto-responses.py")

# Contract under test: the five protected fallback reasons from the master
# plan must never be selected by scan or updated by apply.
PROTECTED_REASONS = (
    "BODY_TRUNCATED",
    "LEGACY_UID_UNVERIFIABLE",
    "RECIPIENT_UNRESOLVED",
    "CONTACT_NOT_FOUND",
    "INTRODUCTION_NOT_SENT",
)

RECEIVED_AT = dt.datetime(2026, 10, 20, 12, 0, 0)
UPDATED_AT = dt.datetime(2026, 10, 20, 12, 0, 5)
SENT_AT = RECEIVED_AT - dt.timedelta(seconds=30)
ORDINARY_REASON = "UNCLEAR_INTENT"


def _load_script():
    spec = importlib.util.spec_from_file_location("reclassify_auto_responses_under_test", SCRIPT_PATH)
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


reclassify = _load_script()


def _candidate(processing_id: int, reason: str) -> dict:
    return {
        "id": processing_id,
        "expert_contact_id": processing_id,
        "sender_account_code": "sender-a",
        "mailbox_owner_code": "owner-a",
        "uid_validity": "123",
        "imap_uid": str(processing_id),
        "message_id": f"<message-{processing_id}@example.test>",
        "received_at": RECEIVED_AT,
        "process_status": "MANUAL_REVIEW",
        "process_reason": reason,
        "reason_type": "UNCLEAR_INTENT",
        "updated_at": UPDATED_AT,
        "body": f"body-{processing_id}",
    }


def _manifest_row(candidate: dict, outbound_id: int) -> dict:
    return {
        "processingId": candidate["id"],
        "expertContactId": candidate["expert_contact_id"],
        "outboundMailRecordId": outbound_id,
        "senderAccountCode": candidate["sender_account_code"],
        "mailboxOwnerCode": candidate["mailbox_owner_code"],
        "uidValidity": candidate["uid_validity"],
        "imapUid": candidate["imap_uid"],
        "messageId": candidate["message_id"],
        "receivedAt": candidate["received_at"].isoformat(),
        "sentAt": SENT_AT.isoformat(),
        "deltaSeconds": 30.0,
        "firstReplyProof": {
            "eligible": True,
            "diagnosis": "first-reply-within-window",
        },
        "bodySha256": reclassify.body_hash(candidate["body"]),
        "bodyIsNull": False,
        "before": {
            "processStatus": candidate["process_status"],
            "processReason": candidate["process_reason"],
            "reasonType": candidate["reason_type"],
            "updatedAt": candidate["updated_at"].isoformat(),
        },
        "approvedReason": reclassify.APPROVED_REASON,
    }


def _norm(sql: str) -> str:
    return " ".join(sql.split())


class FakeCursor:
    def __init__(self, candidates: list, candidate_by_id: dict | None, outbound_id: int):
        self._candidates = candidates
        self._candidate_by_id = candidate_by_id
        self._outbound_id = outbound_id
        self._by_contact = {row["expert_contact_id"]: row for row in candidates}
        if candidate_by_id is not None:
            self._by_contact[candidate_by_id["expert_contact_id"]] = candidate_by_id
        self._result: list = []
        self.rowcount = 0
        self.proof_contacts: list = []
        self.updates: list = []

    def __enter__(self):
        return self

    def __exit__(self, *exc_info):
        return False

    def execute(self, sql: str, params=None):
        normalized = _norm(sql)
        self.rowcount = 0
        if normalized == "START TRANSACTION WITH CONSISTENT SNAPSHOT":
            self._result = []
        elif normalized == _norm(reclassify.CANDIDATE_SQL):
            self._result = list(self._candidates)
        elif normalized == _norm(reclassify.LOCK_CONTACT_SQL):
            self._result = [{"id": params[0]}]
        elif normalized == _norm(reclassify.CANDIDATE_BY_ID_SQL):
            self._result = [self._candidate_by_id] if self._candidate_by_id else []
        elif normalized == _norm(reclassify.INTRODUCTION_SQL):
            self._result = [{"id": 900}]
        elif normalized == _norm(reclassify.EARLIEST_PROCESSING_SQL):
            self.proof_contacts.append(params[0])
            row = self._by_contact[params[0]]
            self._result = [{"id": row["id"]}]
        elif normalized == _norm(reclassify.MATCHING_INBOUND_RECORD_SQL):
            row = self._by_contact[params[0]]
            self._result = [{"id": 7000 + row["id"], "received_at": row["received_at"]}]
        elif normalized == _norm(reclassify.INDEPENDENT_INBOUND_HISTORY_SQL):
            self._result = []
        elif normalized == _norm(reclassify.NEAREST_SENT_OUTBOUND_SQL):
            self._result = [{"id": self._outbound_id, "sent_at": SENT_AT}]
        elif normalized.startswith("UPDATE inbound_mail_processing"):
            self.updates.append(params)
            self.rowcount = 1
            self._result = []
        elif normalized.startswith("SELECT updated_at FROM inbound_mail_processing"):
            self._result = [{"updated_at": dt.datetime.now()}]
        else:  # pragma: no cover - makes accidental SQL drift obvious
            raise AssertionError(f"unexpected SQL: {normalized}")

    def fetchone(self):
        return self._result[0] if self._result else None

    def fetchall(self):
        return list(self._result)


class FakeConnection:
    def __init__(self, cursor: FakeCursor):
        self.cursor_obj = cursor
        self.commits = 0
        self.rollbacks = 0
        self.closed = False

    def cursor(self):
        return self.cursor_obj

    def commit(self):
        self.commits += 1

    def rollback(self):
        self.rollbacks += 1

    def close(self):
        self.closed = True


def _manifest(rows: list) -> dict:
    return {
        "schemaVersion": reclassify.SCHEMA_VERSION,
        "ruleVersion": reclassify.RULE_VERSION,
        "generatedAt": "2026-10-20T12:00:00",
        "rows": rows,
        "skipped": [],
    }


class ScanProtectedReasonTest(unittest.TestCase):
    def test_scan_excludes_all_protected_reasons_before_proof(self):
        self.assertEqual(set(PROTECTED_REASONS), set(reclassify.PROTECTED_REASONS))

        protected = [_candidate(index + 1, reason) for index, reason in enumerate(PROTECTED_REASONS)]
        ordinary = _candidate(99, ORDINARY_REASON)
        cursor = FakeCursor(candidates=protected + [ordinary], candidate_by_id=None, outbound_id=555)
        connection = FakeConnection(cursor)

        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "manifest.json"
            with mock.patch.object(reclassify, "connect", lambda: connection):
                reclassify.scan(str(output))
            manifest = json.loads(output.read_text(encoding="utf-8"))

        self.assertEqual([ordinary["id"]], [row["processingId"] for row in manifest["rows"]])

        protected_skips = [entry for entry in manifest["skipped"] if entry["diagnosis"] == "protected-reason"]
        self.assertEqual(
            {row["id"] for row in protected},
            {entry["processingId"] for entry in protected_skips},
        )
        self.assertEqual({ordinary["expert_contact_id"]}, set(cursor.proof_contacts))


class ApplyProtectedReasonTest(unittest.TestCase):
    def _apply(self, candidate: dict):
        cursor = FakeCursor(candidates=[], candidate_by_id=candidate, outbound_id=555)
        connection = FakeConnection(cursor)
        manifest = _manifest([_manifest_row(candidate, outbound_id=555)])

        with tempfile.TemporaryDirectory() as directory:
            manifest_path = Path(directory) / "manifest.json"
            audit_path = Path(directory) / "audit.json"
            manifest_path.write_text(json.dumps(manifest), encoding="utf-8")
            with mock.patch.object(reclassify, "connect", lambda: connection):
                reclassify.apply(str(manifest_path), str(audit_path))
            audit = json.loads(audit_path.read_text(encoding="utf-8"))

        return cursor, audit

    def test_apply_skips_each_protected_reason_without_update(self):
        for reason in PROTECTED_REASONS:
            with self.subTest(reason=reason):
                cursor, audit = self._apply(_candidate(10, reason))

                self.assertEqual([], cursor.updates)
                self.assertEqual(0, audit["counts"]["affected"])
                self.assertEqual(1, audit["counts"]["skipped"])
                self.assertEqual("protected-reason", audit["skipped"][0]["diagnosis"])

    def test_apply_updates_matching_ordinary_row(self):
        cursor, audit = self._apply(_candidate(11, ORDINARY_REASON))

        self.assertEqual(1, len(cursor.updates))
        self.assertEqual(reclassify.APPROVED_REASON, cursor.updates[0][0])
        self.assertEqual(1, audit["counts"]["affected"])
        self.assertEqual(0, audit["counts"]["skipped"])
        self.assertEqual("PROCESSED", audit["affected"][0]["after"]["processStatus"])


if __name__ == "__main__":
    unittest.main()
