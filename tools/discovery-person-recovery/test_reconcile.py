#!/usr/bin/env python3
"""10 维护 CLI 的 unittest：只读预览、受限应用门禁、断点/原子落盘与留痕边界。"""
from __future__ import annotations

import json
import os
import stat
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import reconcile  # noqa: E402


def _group(doc_ids, disposition="MERGE_READY", digest="d1"):
    return {
        "groupKey": reconcile.group_key(doc_ids),
        "memberDocIds": list(doc_ids),
        "disposition": disposition,
        "reason": None,
        "evidenceDigest": digest,
        "relationDigest": "r-digest",
        "canMerge": disposition == "MERGE_READY",
        "canSend": True,
        "members": [
            {
                "docId": doc_id,
                "email": f"{doc_id}@uni.edu",
                "admissionReason": None,
                "sendBlockReason": None,
                "historyReference": False,
                "liveDiscovered": True,
                "relationPersonId": None,
                "relationRevision": None,
            }
            for doc_id in doc_ids
        ],
    }


class FakeClient:
    def __init__(self, status_override=None):
        self.preview_calls = []
        self.apply_calls = []
        self.rollback_calls = []
        self.status_override = status_override

    def preview(self, frozen_ids, groups):
        self.preview_calls.append((frozen_ids, groups))
        if self.status_override is not None:
            return self.status_override, {"error": "UNAUTHORIZED"}
        body = {
            "ruleVersion": reconcile.RULE_VERSION,
            "generatedAt": "2026-10-13T00:00:00Z",
            "groups": [_group(["doc-a", "doc-b"], "MERGE_READY", "g-digest"), _group(["doc-c"], "KEEP_SINGLE", "s-digest")],
            "classCounts": {"MERGE_READY": 2, "REVIEW_REQUIRED": 0, "KEEP_SINGLE": 1, "CHANGED_OR_FAILED": 0},
            "totalDocuments": 3,
            "previewDigest": "PREVIEW-DIGEST",
        }
        return 200, body

    def apply(self, preview_digest, frozen_ids, groups):
        self.apply_calls.append((preview_digest, frozen_ids, groups))
        if self.status_override is not None:
            return self.status_override, {"error": "UNAUTHORIZED"}
        return 200, [
            {
                "groupKey": group_key_of(group),
                "status": "APPLIED",
                "personId": "p-" + group_key_of(group)[:6],
                "memberCount": len(group["memberDocIds"]),
                "protectionImported": False,
                "reason": None,
            }
            for group in groups
        ]

    def rollback(self, groups):
        self.rollback_calls.append(groups)
        return 200, [
            {
                "groupKey": reconcile.group_key(members),
                "memberDocIds": members,
                "personId": "p",
                "safeToRollback": True,
                "affectedCount": len(members),
                "reason": None,
            }
            for members in groups
        ]


def group_key_of(group):
    return reconcile.group_key(group["memberDocIds"])


def _read(path):
    with open(path, encoding="utf-8") as fh:
        return fh.read()


class ReconcileCliTest(unittest.TestCase):
    def setUp(self):
        self._tmp = tempfile.TemporaryDirectory()
        self.out_dir = self._tmp.name
        self.frozen = ["doc-a", "doc-b", "doc-c"]
        self.groups = [["doc-a", "doc-b"], ["doc-c"]]

    def tearDown(self):
        self._tmp.cleanup()

    # ── 解析与摘要 ──────────────────────────────────────────────────────────

    def test_manifest_parsing_and_digest_is_order_sensitive(self):
        path = os.path.join(self.out_dir, "ids.txt")
        with open(path, "w", encoding="utf-8") as fh:
            fh.write("# comment\n doc-a \ndoc-b\ndoc-a\n")
        ids = reconcile.load_manifest(path)
        self.assertEqual(["doc-a", "doc-b"], ids)
        self.assertNotEqual(reconcile.manifest_digest(["a", "b"]), reconcile.manifest_digest(["b", "a"]))

    def test_groups_parsing_accepts_json_and_csv(self):
        path = os.path.join(self.out_dir, "groups.jsonl")
        with open(path, "w", encoding="utf-8") as fh:
            fh.write('["doc-a", "doc-b"]\ndoc-c\n')
        self.assertEqual([["doc-a", "doc-b"], ["doc-c"]], reconcile.load_groups(path))

    def test_group_key_is_order_insensitive(self):
        self.assertEqual(reconcile.group_key(["b", "a"]), reconcile.group_key(["a", "b"]))

    # ── 私有输出与原子落盘 ──────────────────────────────────────────────────

    def test_private_dir_and_file_permissions(self):
        reconcile.ensure_private_dir(self.out_dir)
        self.assertEqual(stat.S_IRWXU, stat.S_IMODE(os.stat(self.out_dir).st_mode))
        path = os.path.join(self.out_dir, "x.json")
        reconcile.atomic_write_json(path, {"a": 1})
        self.assertEqual(stat.S_IRUSR | stat.S_IWUSR, stat.S_IMODE(os.stat(path).st_mode))
        leftovers = [name for name in os.listdir(self.out_dir) if name.startswith(".tmp-")]
        self.assertEqual([], leftovers)

    def test_append_jsonl_is_atomic_and_readable(self):
        path = os.path.join(self.out_dir, "records.jsonl")
        reconcile.append_jsonl_atomic(path, {"groupKey": "a"})
        reconcile.append_jsonl_atomic(path, {"groupKey": "b"})
        self.assertEqual(2, len(reconcile.read_jsonl(path)))

    # ── 预览 ────────────────────────────────────────────────────────────────

    def test_preview_writes_records_and_keeps_emails_out_of_main_report(self):
        client = FakeClient()
        result = reconcile.run_preview(client, self.frozen, self.groups, self.out_dir)
        self.assertEqual(200, result["status"])
        self.assertEqual("PREVIEW-DIGEST", result["previewDigest"])
        self.assertEqual(3, result["totalDocuments"])

        preview_text = _read(os.path.join(self.out_dir, reconcile.PREVIEW_FILE))
        summary_text = _read(os.path.join(self.out_dir, reconcile.SUMMARY_FILE))
        self.assertNotIn("@uni.edu", preview_text, "主报告不得含完整邮箱")
        self.assertNotIn("@uni.edu", summary_text)
        members_text = _read(os.path.join(self.out_dir, reconcile.MEMBERS_FILE))
        self.assertIn("doc-a@uni.edu", members_text, "逐成员邮箱只在 0600 明细文件")
        self.assertEqual(stat.S_IRUSR | stat.S_IWUSR, stat.S_IMODE(os.stat(os.path.join(self.out_dir, reconcile.MEMBERS_FILE)).st_mode))

        state = reconcile.load_state(self.out_dir)
        self.assertEqual("PREVIEW-DIGEST", state["previewDigest"])

    def test_preview_resume_does_not_duplicate_records(self):
        client = FakeClient()
        reconcile.run_preview(client, self.frozen, self.groups, self.out_dir)
        reconcile.run_preview(client, self.frozen, self.groups, self.out_dir, resume=True)
        self.assertEqual(2, len(reconcile.read_jsonl(os.path.join(self.out_dir, reconcile.PREVIEW_FILE))))

    # ── 受限应用 ────────────────────────────────────────────────────────────

    def test_apply_is_refused_without_confirm(self):
        client = FakeClient()
        reconcile.run_preview(client, self.frozen, self.groups, self.out_dir)
        result = reconcile.run_apply(client, self.out_dir, confirm=False)
        self.assertNotEqual(200, result["status"])
        self.assertEqual([], client.apply_calls)

    def test_apply_is_refused_without_preview_digest(self):
        client = FakeClient()
        # 无状态文件 → 无预览摘要
        result = reconcile.run_apply(client, self.out_dir, confirm=True)
        self.assertNotEqual(200, result["status"])
        self.assertEqual([], client.apply_calls)

    def test_apply_sends_digest_and_per_group_fingerprints(self):
        client = FakeClient()
        reconcile.run_preview(client, self.frozen, self.groups, self.out_dir)
        result = reconcile.run_apply(client, self.out_dir, confirm=True)
        self.assertEqual(200, result["status"])
        self.assertEqual(2, result["applied"])
        digest, frozen_ids, groups = client.apply_calls[0]
        self.assertEqual("PREVIEW-DIGEST", digest)
        self.assertEqual(self.frozen, frozen_ids, "apply 必须回传完整冻结清单身份")
        self.assertEqual({"g-digest", "s-digest"}, {group["expectedFingerprint"] for group in groups})
        self.assertEqual({"r-digest"}, {group["expectedRelationFingerprint"] for group in groups})
        self.assertEqual(
            {"MERGE_READY", "KEEP_SINGLE"},
            {record["disposition"] for record in reconcile.read_jsonl(os.path.join(self.out_dir, reconcile.PREVIEW_FILE))},
        )
        self.assertEqual(2, len(reconcile.read_jsonl(os.path.join(self.out_dir, reconcile.APPLY_FILE))))

    def test_apply_resume_skips_completed_groups(self):
        client = FakeClient()
        reconcile.run_preview(client, self.frozen, self.groups, self.out_dir)
        reconcile.run_apply(client, self.out_dir, confirm=True)
        second = reconcile.run_apply(client, self.out_dir, confirm=True)
        self.assertEqual(0, second["applied"])
        self.assertEqual(1, len(client.apply_calls), "断点：已完成组不重复提交")

    def test_apply_limit_selects_pilot_subset(self):
        client = FakeClient()
        reconcile.run_preview(client, self.frozen, self.groups, self.out_dir)
        reconcile.run_apply(client, self.out_dir, confirm=True, limit=1)
        _, frozen_ids, groups = client.apply_calls[0]
        self.assertEqual(1, len(groups))
        self.assertEqual(self.frozen, frozen_ids, "试点子集仍使用全量冻结清单身份")

    def test_apply_pilot_then_resume_uses_same_full_frozen_list(self):
        client = FakeClient()
        reconcile.run_preview(client, self.frozen, self.groups, self.out_dir)
        reconcile.run_apply(client, self.out_dir, confirm=True, limit=1)
        reconcile.run_apply(client, self.out_dir, confirm=True)
        self.assertEqual(2, len(client.apply_calls))
        self.assertEqual([self.frozen, self.frozen], [call[1] for call in client.apply_calls])
        self.assertEqual(1, len(client.apply_calls[1][2]), "续跑只提交剩余组")
        self.assertEqual(
            {("doc-a", "doc-b"), ("doc-c",)},
            {tuple(group["memberDocIds"]) for group in [*client.apply_calls[0][2], *client.apply_calls[1][2]]},
        )

    # ── 会话认证 ────────────────────────────────────────────────────────────

    def test_session_cookie_is_sent_as_header_only(self):
        captured = {}

        class FakeResponse:
            status = 200

            def read(self):
                return b'{"ok": true}'

            def __enter__(self):
                return self

            def __exit__(self, *exc):
                return False

        def fake_urlopen(request, timeout=None):
            captured["headers"] = {key.lower(): value for key, value in request.headers.items()}
            return FakeResponse()

        original = reconcile.urllib.request.urlopen
        reconcile.urllib.request.urlopen = fake_urlopen
        try:
            client = reconcile.ApiClient("http://127.0.0.1:8080", session_cookie="JSESSIONID=s3cret")
            status, _ = client.preview(["doc-a"], [])
        finally:
            reconcile.urllib.request.urlopen = original
        self.assertEqual(200, status)
        self.assertEqual("JSESSIONID=s3cret", captured["headers"].get("cookie"))

    def test_unauthorized_is_actionable_and_writes_no_credentials(self):
        client = FakeClient(status_override=401)
        result = reconcile.run_preview(client, self.frozen, self.groups, self.out_dir)
        self.assertEqual(401, result["status"])
        self.assertIn("UNAUTHORIZED", result["error"])
        for name in (reconcile.PREVIEW_FILE, reconcile.MEMBERS_FILE, reconcile.SUMMARY_FILE, reconcile.STATE_FILE):
            path = os.path.join(self.out_dir, name)
            if os.path.exists(path):
                self.assertNotIn("s3cret", _read(path))

    def test_main_refuses_without_existing_session(self):
        import contextlib
        import io

        manifest = os.path.join(self.out_dir, "ids.txt")
        groups = os.path.join(self.out_dir, "groups.jsonl")
        with open(manifest, "w", encoding="utf-8") as fh:
            fh.write("doc-a\n")
        with open(groups, "w", encoding="utf-8") as fh:
            fh.write("doc-a\n")
        buffer = io.StringIO()
        with contextlib.redirect_stdout(buffer):
            code = reconcile.main([
                "preview", "--manifest", manifest, "--groups", groups, "--out-dir", self.out_dir
            ])
        self.assertEqual(2, code)
        self.assertIn("UNAUTHORIZED", buffer.getvalue())

    # ── 回滚提案 ────────────────────────────────────────────────────────────

    def test_rollback_preview_generates_proposals_only(self):
        client = FakeClient()
        reconcile.run_preview(client, self.frozen, self.groups, self.out_dir)
        reconcile.run_apply(client, self.out_dir, confirm=True)
        result = reconcile.run_rollback_preview(client, self.out_dir)
        self.assertEqual(200, result["status"])
        self.assertEqual(2, len(result["proposals"]))
        self.assertTrue(all(proposal["safeToRollback"] for proposal in result["proposals"]))
        proposals = reconcile.read_jsonl(os.path.join(self.out_dir, reconcile.ROLLBACK_FILE))
        self.assertEqual(2, len(proposals))

    def test_rollback_preview_without_apply_proposes_merge_groups(self):
        client = FakeClient()
        reconcile.run_preview(client, self.frozen, self.groups, self.out_dir)
        result = reconcile.run_rollback_preview(client, self.out_dir)
        self.assertEqual(200, result["status"])
        self.assertEqual(1, len(client.rollback_calls[0]), "仅对 MERGE_READY 出提案")


if __name__ == "__main__":
    unittest.main()
