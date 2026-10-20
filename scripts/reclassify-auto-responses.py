#!/usr/bin/env python3
"""Reclassify the first reply of an expert as a system-handled auto response.

Rule version: FIRST_REPLY_V3 (see docs/plans/2026-10-20/auto-response-exclusion.md).

Only rows that are still proven to be the expert's *first* reply, were originally
left in MANUAL_REVIEW, and arrived 0..300 seconds (inclusive) after the nearest
successful OUTBOUND for the same expert and logical sender account are changed.
Only four fields are written: process_status, process_reason, reason_type,
updated_at.  resolved_at/resolved_by stay untouched (NULL).

Modes (default is dry-run, identical to `scan`):

    python3 scripts/reclassify-auto-responses.py scan --output <manifest.json>
    python3 scripts/reclassify-auto-responses.py apply --manifest <manifest.json> --audit-output <audit.json>
    python3 scripts/reclassify-auto-responses.py rollback --audit <audit.json> --output <rollback.json>

`apply` re-proves first-reply eligibility under the expert_contact row lock and
only performs a conditional UPDATE; a changed row is reported as a conflict and
skipped.  Repeating `apply` on the same manifest performs zero updates.

Connection settings come from the environment (never printed):
    TI_DB_HOST (default 127.0.0.1)
    TI_DB_PORT (default 3306)
    TI_DB_USER (default root)
    TI_DB_PASSWORD (default empty)
    TI_DB_NAME (default talent_introduction)

Requires PyMySQL (``python3 -m pip install PyMySQL``).
"""

from __future__ import annotations

import argparse
import datetime as dt
import hashlib
import json
import os
import sys
from dataclasses import dataclass
from typing import Any, Dict, Iterable, List, Optional, Sequence, Tuple

try:  # PyMySQL is only required for real DB access, so --help stays available.
    import pymysql
    from pymysql.cursors import DictCursor
except ImportError:  # pragma: no cover - environment guard
    pymysql = None  # type: ignore[assignment]
    DictCursor = None  # type: ignore[assignment]

SCHEMA_VERSION = 1
RULE_VERSION = "FIRST_REPLY_V3"
REASON_TYPE = "AUTO_RESPONSE"
APPROVED_REASON = "AUTO_RESPONSE_FAST_5M"
WINDOW_SECONDS = 300
MODES = ("scan", "apply", "rollback")

LOCK_CONTACT_SQL = "SELECT id FROM expert_contact WHERE id = %s FOR UPDATE"

EARLIEST_PROCESSING_SQL = """
SELECT id
  FROM inbound_mail_processing
 WHERE expert_contact_id = %s
 ORDER BY created_at IS NULL, created_at, id
 LIMIT 1
"""

MATCHING_INBOUND_RECORD_SQL = """
SELECT id, received_at
  FROM mail_record
 WHERE expert_contact_id = %s
   AND direction = 'INBOUND'
   AND sender_account_code = %s
   AND message_id = %s
 ORDER BY id
"""

INDEPENDENT_INBOUND_HISTORY_SQL = """
SELECT id, message_id, received_at
  FROM mail_record
 WHERE expert_contact_id = %s
   AND direction = 'INBOUND'
   AND (message_id IS NULL OR message_id <> %s)
   AND received_at <= %s
 ORDER BY received_at, id
"""

INTRODUCTION_SQL = """
SELECT id
  FROM mail_record
 WHERE expert_contact_id = %s
   AND direction = 'OUTBOUND'
   AND mail_type = 'INTRODUCTION'
 ORDER BY id
 LIMIT 1
"""

NEAREST_SENT_OUTBOUND_SQL = """
SELECT id, sent_at
  FROM mail_record
 WHERE expert_contact_id = %s
   AND sender_account_code = %s
   AND direction = 'OUTBOUND'
   AND send_status = 'SENT'
   AND sent_at IS NOT NULL
   AND sent_at <= %s
 ORDER BY sent_at DESC, id DESC
 LIMIT 1
"""

CANDIDATE_SQL = """
SELECT id, expert_contact_id, sender_account_code, mailbox_owner_code,
       uid_validity, imap_uid, message_id, received_at,
       process_status, process_reason, reason_type, updated_at, body
  FROM inbound_mail_processing
 WHERE expert_contact_id IS NOT NULL
   AND process_status = 'MANUAL_REVIEW'
 ORDER BY id
"""

CANDIDATE_BY_ID_SQL = """
SELECT id, expert_contact_id, sender_account_code, mailbox_owner_code,
       uid_validity, imap_uid, message_id, received_at,
       process_status, process_reason, reason_type, updated_at, body
  FROM inbound_mail_processing
 WHERE id = %s
 FOR UPDATE
"""


def _dsn() -> Dict[str, Any]:
    return {
        "host": os.environ.get("TI_DB_HOST", "127.0.0.1"),
        "port": int(os.environ.get("TI_DB_PORT", "3306")),
        "user": os.environ.get("TI_DB_USER", "root"),
        "password": os.environ.get("TI_DB_PASSWORD", ""),
        "database": os.environ.get("TI_DB_NAME", "talent_introduction"),
        "charset": "utf8mb4",
        "cursorclass": DictCursor,
        "autocommit": False,
    }


def connect() -> "pymysql.connections.Connection":
    if pymysql is None:  # pragma: no cover - environment guard
        sys.stderr.write("PyMySQL is required: python3 -m pip install PyMySQL\n")
        raise SystemExit(2)
    return pymysql.connect(**_dsn())


def _iso(value: Optional[dt.datetime]) -> Optional[str]:
    return value.isoformat() if isinstance(value, dt.datetime) else None


def _parse_iso(value: Optional[str]) -> Optional[dt.datetime]:
    if value is None:
        return None
    return dt.datetime.fromisoformat(value)


def body_hash(body: Optional[str]) -> str:
    payload = (body or "").encode("utf-8")
    return hashlib.sha256(payload).hexdigest()


def _delta_seconds(received_at: dt.datetime, sent_at: dt.datetime) -> float:
    return (received_at - sent_at).total_seconds()


@dataclass
class Proof:
    eligible: bool
    reason: str
    earliest_processing_id: Optional[int] = None
    same_inbound_record_id: Optional[int] = None
    outbound_id: Optional[int] = None
    sent_at: Optional[dt.datetime] = None

    def as_json(self) -> Dict[str, Any]:
        return {
            "eligible": self.eligible,
            "diagnosis": self.reason,
            "earliestProcessingId": self.earliest_processing_id,
            "sameInboundMailRecordId": self.same_inbound_record_id,
            "outboundMailRecordId": self.outbound_id,
            "sentAt": _iso(self.sent_at),
        }


def prove_first_reply(
    cursor: Any,
    processing_id: int,
    contact_id: int,
    account_code: str,
    message_id: Optional[str],
    received_at: Optional[dt.datetime],
) -> Proof:
    """Re-prove first-reply eligibility with current reads (caller holds the contact lock)."""
    if received_at is None:
        return Proof(False, "missing-received-at")

    cursor.execute(INTRODUCTION_SQL, (contact_id,))
    if cursor.fetchone() is None:
        return Proof(False, "introduction-not-sent")

    cursor.execute(EARLIEST_PROCESSING_SQL, (contact_id,))
    earliest = cursor.fetchone()
    earliest_id = earliest["id"] if earliest else None
    if earliest_id != processing_id:
        return Proof(False, "not-first-processing", earliest_processing_id=earliest_id)

    if not message_id:
        return Proof(False, "missing-message-id", earliest_processing_id=earliest_id)

    cursor.execute(MATCHING_INBOUND_RECORD_SQL, (contact_id, account_code, message_id))
    matching = cursor.fetchall()
    if len(matching) != 1:
        return Proof(
            False,
            "same-mail-record-unproven" if not matching else "ambiguous-same-mail-records",
            earliest_processing_id=earliest_id,
        )
    same_record = matching[0]
    same_received_at = same_record["received_at"]
    if same_received_at is None or abs((same_received_at - received_at).total_seconds()) > 1:
        return Proof(False, "same-mail-time-mismatch", earliest_processing_id=earliest_id)

    cursor.execute(INDEPENDENT_INBOUND_HISTORY_SQL, (contact_id, message_id, received_at))
    for row in cursor.fetchall():
        other_received_at = row["received_at"]
        if other_received_at is not None and other_received_at < received_at:
            return Proof(
                False,
                "independent-earlier-inbound",
                earliest_processing_id=earliest_id,
                same_inbound_record_id=same_record["id"],
            )
        return Proof(
            False,
            "ambiguous-same-time-inbound",
            earliest_processing_id=earliest_id,
            same_inbound_record_id=same_record["id"],
        )

    cursor.execute(NEAREST_SENT_OUTBOUND_SQL, (contact_id, account_code, received_at))
    outbound = cursor.fetchone()
    if outbound is None or outbound["sent_at"] is None:
        return Proof(
            False,
            "no-successful-outbound",
            earliest_processing_id=earliest_id,
            same_inbound_record_id=same_record["id"],
        )

    delta = _delta_seconds(received_at, outbound["sent_at"])
    if delta < 0 or delta > WINDOW_SECONDS:
        return Proof(
            False,
            "outside-five-minute-window",
            earliest_processing_id=earliest_id,
            same_inbound_record_id=same_record["id"],
            outbound_id=outbound["id"],
            sent_at=outbound["sent_at"],
        )

    return Proof(
        True,
        "first-reply-within-window",
        earliest_processing_id=earliest_id,
        same_inbound_record_id=same_record["id"],
        outbound_id=outbound["id"],
        sent_at=outbound["sent_at"],
    )


def scan(output_path: str) -> int:
    connection = connect()
    rows: List[Dict[str, Any]] = []
    skipped: List[Dict[str, Any]] = []
    try:
        with connection.cursor() as cursor:
            cursor.execute("START TRANSACTION WITH CONSISTENT SNAPSHOT")
            cursor.execute(CANDIDATE_SQL)
            candidates = cursor.fetchall()
            for candidate in candidates:
                proof = prove_first_reply(
                    cursor,
                    candidate["id"],
                    candidate["expert_contact_id"],
                    candidate["sender_account_code"],
                    candidate["message_id"],
                    candidate["received_at"],
                )
                if not proof.eligible or candidate["received_at"] is None or proof.sent_at is None:
                    skipped.append({
                        "processingId": candidate["id"],
                        "expertContactId": candidate["expert_contact_id"],
                        "diagnosis": proof.reason,
                    })
                    continue
                rows.append({
                    "processingId": candidate["id"],
                    "expertContactId": candidate["expert_contact_id"],
                    "outboundMailRecordId": proof.outbound_id,
                    "senderAccountCode": candidate["sender_account_code"],
                    "mailboxOwnerCode": candidate["mailbox_owner_code"],
                    "uidValidity": candidate["uid_validity"],
                    "imapUid": candidate["imap_uid"],
                    "messageId": candidate["message_id"],
                    "receivedAt": _iso(candidate["received_at"]),
                    "sentAt": _iso(proof.sent_at),
                    "deltaSeconds": _delta_seconds(candidate["received_at"], proof.sent_at),
                    "firstReplyProof": proof.as_json(),
                    "bodySha256": body_hash(candidate["body"]),
                    "bodyIsNull": candidate["body"] is None,
                    "before": {
                        "processStatus": candidate["process_status"],
                        "processReason": candidate["process_reason"],
                        "reasonType": candidate["reason_type"],
                        "updatedAt": _iso(candidate["updated_at"]),
                    },
                    "approvedReason": APPROVED_REASON,
                })
            connection.rollback()
    finally:
        connection.close()

    manifest = {
        "schemaVersion": SCHEMA_VERSION,
        "ruleVersion": RULE_VERSION,
        "generatedAt": dt.datetime.now().isoformat(timespec="seconds"),
        "rows": rows,
        "skipped": skipped,
    }
    _write_json(output_path, manifest)
    print(f"scan: candidates={len(rows)} skipped={len(skipped)} output={output_path}")
    return 0


def _load_json(path: str) -> Dict[str, Any]:
    with open(path, "r", encoding="utf-8") as handle:
        return json.load(handle)


def _write_json(path: str, payload: Dict[str, Any]) -> None:
    temporary = f"{path}.tmp"
    with open(temporary, "w", encoding="utf-8") as handle:
        json.dump(payload, handle, ensure_ascii=False, indent=2)
        handle.write("\n")
    os.replace(temporary, path)


def _validate_manifest(manifest: Dict[str, Any]) -> List[Dict[str, Any]]:
    if manifest.get("schemaVersion") != SCHEMA_VERSION:
        raise SystemExit("manifest schemaVersion mismatch")
    if manifest.get("ruleVersion") != RULE_VERSION:
        raise SystemExit("manifest ruleVersion mismatch")
    rows = manifest.get("rows")
    if not isinstance(rows, list):
        raise SystemExit("manifest rows must be a list")
    return rows


def _reclassify_row(cursor: Any, row: Dict[str, Any]) -> Tuple[str, Dict[str, Any]]:
    """Return (status, detail) where status is affected|skipped|conflict."""
    processing_id = int(row["processingId"])
    contact_id = int(row["expertContactId"])
    account_code = row["senderAccountCode"]
    before = row.get("before") or {}

    cursor.execute(LOCK_CONTACT_SQL, (contact_id,))
    if cursor.fetchone() is None:
        return "conflict", {"processingId": processing_id, "diagnosis": "contact-not-found"}

    cursor.execute(CANDIDATE_BY_ID_SQL, (processing_id,))
    candidate = cursor.fetchone()
    if candidate is None:
        return "conflict", {"processingId": processing_id, "diagnosis": "processing-not-found"}
    if candidate["expert_contact_id"] != contact_id:
        return "conflict", {"processingId": processing_id, "diagnosis": "contact-changed"}
    if (
        candidate["process_status"] != "MANUAL_REVIEW"
        or candidate["process_reason"] != before.get("processReason")
        or candidate["reason_type"] != before.get("reasonType")
        or _iso(candidate["updated_at"]) != before.get("updatedAt")
    ):
        return "skipped", {"processingId": processing_id, "diagnosis": "row-changed-since-scan"}
    if (
        body_hash(candidate["body"]) != row.get("bodySha256")
        or (candidate["body"] is None) != bool(row.get("bodyIsNull"))
    ):
        return "conflict", {"processingId": processing_id, "diagnosis": "body-changed"}

    proof = prove_first_reply(
        cursor,
        processing_id,
        contact_id,
        account_code,
        candidate["message_id"],
        candidate["received_at"],
    )
    if not proof.eligible:
        return "skipped", {"processingId": processing_id, "diagnosis": proof.reason}
    if proof.outbound_id != row.get("outboundMailRecordId"):
        return "conflict", {"processingId": processing_id, "diagnosis": "outbound-anchor-changed"}

    now = dt.datetime.now()
    cursor.execute(
        """
        UPDATE inbound_mail_processing
           SET process_status = 'PROCESSED',
               process_reason = %s,
               reason_type = %s,
               updated_at = %s
         WHERE id = %s
           AND process_status = 'MANUAL_REVIEW'
           AND process_reason = %s
           AND (reason_type <=> %s)
           AND (updated_at <=> %s)
        """,
        (
            APPROVED_REASON,
            REASON_TYPE,
            now,
            processing_id,
            before.get("processReason"),
            before.get("reasonType"),
            _parse_iso(before.get("updatedAt")),
        ),
    )
    if cursor.rowcount != 1:
        return "conflict", {"processingId": processing_id, "diagnosis": "conditional-update-lost"}

    # DATETIME 只有秒精度：以实际持久化值为准，rollback 才能精确比对写后快照。
    cursor.execute("SELECT updated_at FROM inbound_mail_processing WHERE id = %s", (processing_id,))
    persisted = cursor.fetchone()

    return "affected", {
        "processingId": processing_id,
        "expertContactId": contact_id,
        "before": {
            "processStatus": "MANUAL_REVIEW",
            "processReason": before.get("processReason"),
            "reasonType": before.get("reasonType"),
            "updatedAt": before.get("updatedAt"),
        },
        "after": {
            "processStatus": "PROCESSED",
            "processReason": APPROVED_REASON,
            "reasonType": REASON_TYPE,
            "updatedAt": _iso(persisted["updated_at"] if persisted else None),
        },
        "bodySha256": row.get("bodySha256"),
        "outboundMailRecordId": proof.outbound_id,
        "receivedAt": row.get("receivedAt"),
        "deltaSeconds": row.get("deltaSeconds"),
    }


def apply(manifest_path: str, audit_output: str) -> int:
    manifest = _load_json(manifest_path)
    rows = _validate_manifest(manifest)
    connection = connect()
    affected: List[Dict[str, Any]] = []
    skipped: List[Dict[str, Any]] = []
    conflicts: List[Dict[str, Any]] = []
    try:
        with connection.cursor() as cursor:
            for row in rows:
                try:
                    status, detail = _reclassify_row(cursor, row)
                except Exception as error:  # noqa: BLE001 - report and continue
                    connection.rollback()
                    conflicts.append({
                        "processingId": row.get("processingId"),
                        "diagnosis": f"{type(error).__name__}",
                    })
                    continue
                if status == "affected":
                    connection.commit()
                    affected.append(detail)
                else:
                    connection.rollback()
                    (skipped if status == "skipped" else conflicts).append(detail)
    finally:
        connection.close()

    audit = {
        "schemaVersion": SCHEMA_VERSION,
        "ruleVersion": RULE_VERSION,
        "appliedAt": dt.datetime.now().isoformat(timespec="seconds"),
        "sourceManifest": os.path.basename(manifest_path),
        "counts": {
            "requested": len(rows),
            "affected": len(affected),
            "skipped": len(skipped),
            "conflict": len(conflicts),
        },
        "affected": affected,
        "skipped": skipped,
        "conflicts": conflicts,
    }
    _write_json(audit_output, audit)
    print(
        "apply: requested={requested} affected={affected} skipped={skipped} conflict={conflict}".format(
            **audit["counts"]
        )
    )
    return 0


def rollback(audit_path: str, output_path: str) -> int:
    audit = _load_json(audit_path)
    if audit.get("schemaVersion") != SCHEMA_VERSION or audit.get("ruleVersion") != RULE_VERSION:
        raise SystemExit("audit schemaVersion/ruleVersion mismatch")
    rows = audit.get("affected") or []
    connection = connect()
    restored: List[Dict[str, Any]] = []
    skipped: List[Dict[str, Any]] = []
    conflicts: List[Dict[str, Any]] = []
    try:
        with connection.cursor() as cursor:
            for row in rows:
                processing_id = int(row["processingId"])
                contact_id = int(row["expertContactId"])
                before = row.get("before") or {}
                after = row.get("after") or {}
                cursor.execute(LOCK_CONTACT_SQL, (contact_id,))
                if cursor.fetchone() is None:
                    conflicts.append({"processingId": processing_id, "diagnosis": "contact-not-found"})
                    connection.rollback()
                    continue
                cursor.execute(CANDIDATE_BY_ID_SQL, (processing_id,))
                current = cursor.fetchone()
                if current is None:
                    conflicts.append({"processingId": processing_id, "diagnosis": "processing-not-found"})
                    connection.rollback()
                    continue
                if (
                    current["process_status"] != after.get("processStatus")
                    or current["process_reason"] != after.get("processReason")
                    or current["reason_type"] != after.get("reasonType")
                    or _iso(current["updated_at"]) != after.get("updatedAt")
                ):
                    skipped.append({"processingId": processing_id, "diagnosis": "changed-after-apply"})
                    connection.rollback()
                    continue
                if body_hash(current["body"]) != row.get("bodySha256"):
                    conflicts.append({"processingId": processing_id, "diagnosis": "body-changed"})
                    connection.rollback()
                    continue

                cursor.execute(
                    """
                    UPDATE inbound_mail_processing
                       SET process_status = %s,
                           process_reason = %s,
                           reason_type = %s,
                           updated_at = %s
                     WHERE id = %s
                       AND process_status = %s
                       AND process_reason = %s
                       AND (reason_type <=> %s)
                       AND (updated_at <=> %s)
                    """,
                    (
                        before.get("processStatus"),
                        before.get("processReason"),
                        before.get("reasonType"),
                        _parse_iso(before.get("updatedAt")),
                        processing_id,
                        after.get("processStatus"),
                        after.get("processReason"),
                        after.get("reasonType"),
                        _parse_iso(after.get("updatedAt")),
                    ),
                )
                if cursor.rowcount != 1:
                    conflicts.append({"processingId": processing_id, "diagnosis": "conditional-update-lost"})
                    connection.rollback()
                    continue
                connection.commit()
                restored.append({
                    "processingId": processing_id,
                    "expertContactId": contact_id,
                    "restored": before,
                })
    finally:
        connection.close()

    result = {
        "schemaVersion": SCHEMA_VERSION,
        "ruleVersion": RULE_VERSION,
        "rolledBackAt": dt.datetime.now().isoformat(timespec="seconds"),
        "counts": {
            "requested": len(rows),
            "restored": len(restored),
            "skipped": len(skipped),
            "conflict": len(conflicts),
        },
        "restored": restored,
        "skipped": skipped,
        "conflicts": conflicts,
    }
    _write_json(output_path, result)
    print(
        "rollback: requested={requested} restored={restored} skipped={skipped} conflict={conflict}".format(
            **result["counts"]
        )
    )
    return 0


def main(argv: Sequence[str]) -> int:
    mode = "scan"
    if argv and argv[0] in MODES:
        mode = argv[0]
        argv = argv[1:]

    parser = argparse.ArgumentParser(description="FIRST_REPLY_V3 auto-response reclassification")
    parser.add_argument("--output")
    parser.add_argument("--manifest")
    parser.add_argument("--audit-output")
    parser.add_argument("--audit")
    arguments = parser.parse_args(argv)

    if mode == "scan":
        if not arguments.output:
            parser.error("scan requires --output")
        return scan(arguments.output)
    if mode == "apply":
        if not arguments.manifest or not arguments.audit_output:
            parser.error("apply requires --manifest and --audit-output")
        return apply(arguments.manifest, arguments.audit_output)
    if not arguments.audit or not arguments.output:
        parser.error("rollback requires --audit and --output")
    return rollback(arguments.audit, arguments.output)


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
