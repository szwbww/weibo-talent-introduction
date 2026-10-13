#!/usr/bin/env python3
"""02 维护 CLI 的 unittest：默认预览、日志恢复、备份失败保护与留痕边界。"""
from __future__ import annotations

import json
import os
import stat
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import replay  # noqa: E402


class FakeClient:
    def __init__(self, preview_status="RECOVERABLE"):
        self.preview_status = preview_status
        self.preview_calls = []
        self.apply_calls = []

    def preview(self, doc_id):
        self.preview_calls.append(doc_id)
        return 200, {
            "docId": doc_id,
            "status": self.preview_status,
            "reason": None,
            "snapshotHash": "snap-" + doc_id,
            "proposalHash": "prop-" + doc_id,
            "identityRuleVersion": 1,
            "sourceLocator": "pmcId:PMC1",
            "sourceUrl": "https://www.ebi.ac.uk/europepmc/webservices/rest/PMC1/fullTextXML",
            "evidenceSource": "JATS",
            "evidenceHash": "a" * 64,
            "proposedGivenNames": "Jane",
            "proposedFamilyNames": "Doe",
            "layers": [{"level": "RAW", "present": True}],
            "blockedReasons": [],
            "observedAt": "2026-10-13T00:00:00Z",
        }

    def apply(self, doc_id, snapshot_hash, proposal_hash):
        self.apply_calls.append((doc_id, snapshot_hash, proposal_hash))
        return 200, {"docId": doc_id, "status": "APPLIED", "admissionDecision": "AUTO_PASSED"}


class ReplayCliTest(unittest.TestCase):
    def setUp(self):
        self._tmp = tempfile.TemporaryDirectory()
        self.out_dir = self._tmp.name

    def tearDown(self):
        self._tmp.cleanup()

    # ── 默认预览 ────────────────────────────────────────────────────────────

    def test_default_mode_is_preview(self):
        args = replay.build_parser().parse_args(["--manifest", "m", "--out-dir", "o"])
        self.assertFalse(args.apply)

    def test_preview_writes_one_jsonl_line_per_id_and_is_private(self):
        client = FakeClient()
        summary = replay.run_preview(client, ["a", "b"], self.out_dir)

        self.assertEqual(2, summary["previewed"])
        self.assertEqual(["a", "b"], client.preview_calls)
        self.assertEqual(0o700, stat.S_IMODE(os.stat(self.out_dir).st_mode))
        preview_path = os.path.join(self.out_dir, replay.PREVIEW_FILE)
        self.assertEqual(0o600, stat.S_IMODE(os.stat(preview_path).st_mode))
        records = replay.read_jsonl(preview_path)
        self.assertEqual(2, len(records))
        self.assertEqual("RECOVERABLE", records[0]["status"])
        # 留痕既不写认证头，也不写姓名/邮箱。
        blob = json.dumps(records)
        self.assertNotIn("cookie", blob.lower())
        self.assertNotIn("authorization", blob.lower())

    def test_manifest_is_frozen_with_digest(self):
        manifest = replay.write_manifest(self.out_dir, ["a", "b"])
        self.assertEqual(2, manifest["count"])
        with open(os.path.join(self.out_dir, replay.MANIFEST_FILE), encoding="utf-8") as fh:
            reread = json.load(fh)
        self.assertEqual(manifest["digest"], reread["digest"])
        # 同一名单 digest 稳定；顺序变化即变化。
        self.assertEqual(manifest["digest"], replay.manifest_digest(["a", "b"]))
        self.assertNotEqual(manifest["digest"], replay.manifest_digest(["b", "a"]))

    def test_resume_skips_already_previewed_ids(self):
        client = FakeClient()
        replay.run_preview(client, ["a"], self.out_dir)
        summary = replay.run_preview(client, ["a", "b"], self.out_dir)

        self.assertEqual(1, summary["skipped"])
        self.assertEqual(1, summary["previewed"])
        self.assertEqual(["a", "b"], client.preview_calls)
        self.assertEqual(2, len(replay.read_jsonl(os.path.join(self.out_dir, replay.PREVIEW_FILE))))

    def test_read_manifest_dedupes_and_skips_comments(self):
        path = os.path.join(self.out_dir, "ids.txt")
        with open(path, "w", encoding="utf-8") as fh:
            fh.write("# frozen list\na\n\n a \nb\na\n")
        self.assertEqual(["a", "b"], replay.load_manifest(path))

    # ── 应用调度 ────────────────────────────────────────────────────────────

    def test_apply_requires_a_preview_and_only_touches_recoverable_rows(self):
        client = FakeClient()
        replay.run_preview(client, ["a"], self.out_dir)
        # 追加一条不可恢复的预览记录。
        replay.append_jsonl(os.path.join(self.out_dir, replay.PREVIEW_FILE), {
            "docId": "b", "status": "NEEDS_MANUAL", "snapshotHash": "s", "proposalHash": "p",
        })

        summary = replay.run_apply(client, ["a", "b", "c"], self.out_dir)

        self.assertEqual(1, summary["applied"])
        self.assertEqual([("a", "snap-a", "prop-a")], client.apply_calls)
        # 备份先于 apply 写入。
        backups = replay.read_jsonl(os.path.join(self.out_dir, replay.BACKUP_FILE))
        self.assertEqual(["a"], [record["docId"] for record in backups])

    def test_backup_write_failure_stops_that_row_from_applying(self):
        client = FakeClient()
        replay.run_preview(client, ["a"], self.out_dir)

        original = replay.append_jsonl

        def boom(path, record):
            if path.endswith(replay.BACKUP_FILE):
                raise OSError("disk full")
            return original(path, record)

        replay.append_jsonl = boom
        try:
            summary = replay.run_apply(client, ["a"], self.out_dir)
        finally:
            replay.append_jsonl = original

        self.assertEqual(1, summary["backupFailures"])
        self.assertEqual([], client.apply_calls)
        self.assertFalse(os.path.exists(os.path.join(self.out_dir, replay.APPLY_FILE)))

    def test_rerun_after_apply_does_not_reapply(self):
        client = FakeClient()
        replay.run_preview(client, ["a"], self.out_dir)
        replay.run_apply(client, ["a"], self.out_dir)
        summary = replay.run_apply(client, ["a"], self.out_dir)

        self.assertEqual(1, summary["skipped"])
        self.assertEqual(1, len(client.apply_calls))

    # ── 回滚提案 ────────────────────────────────────────────────────────────

    def test_rollback_proposal_is_advisory_only(self):
        client = FakeClient()
        replay.run_preview(client, ["a"], self.out_dir)
        replay.run_apply(client, ["a"], self.out_dir)

        proposals = replay.run_rollback(self.out_dir)

        self.assertEqual(1, proposals)
        record = replay.read_jsonl(os.path.join(self.out_dir, replay.ROLLBACK_FILE))[0]
        self.assertTrue(record["proposalOnly"])
        self.assertEqual("Jane", record["targetGivenNames"])
        # 回滚提案不执行任何写：apply 调用仍是 1 次。
        self.assertEqual(1, len(client.apply_calls))


if __name__ == "__main__":
    unittest.main()
