#!/usr/bin/env python3
"""10 维护 CLI：新发现历史人物合并的**预览 / 受限应用 / 回滚提案**（本地留痕，不发邮件）。

- 默认只 preview；apply 必须显式 `--confirm` 且引用同一输出目录里已生成的预览摘要与逐组指纹。
- 每组一行 JSONL；输出目录 0700、文件 0600；文件名不含姓名/邮箱；**从不记录认证头**。
- 落盘先原子写临时文件再 `os.replace`；保存各组结果与断点（重复运行按组去重，不重复写）。
- 全量预览不是自动执行授权；本工具**不做生产写**——apply 只调用服务端受限 apply 接口。
- 绝不把完整邮箱名单写入仓库可见报告；逐成员邮箱只写 0600 明细文件。

用法::

    python3 reconcile.py preview --manifest ids.txt --groups groups.jsonl --out-dir out
    python3 reconcile.py apply --out-dir out --confirm [--preview-digest D] [--limit 50]
    python3 reconcile.py rollback-preview --out-dir out
"""
from __future__ import annotations

import argparse
import datetime as _dt
import hashlib
import json
import os
import stat
import sys
import tempfile
import urllib.error
import urllib.request

PREVIEW_ENDPOINT = "/api/expert-discovery/person-maintenance/preview"
APPLY_ENDPOINT = "/api/expert-discovery/person-maintenance/apply"
ROLLBACK_ENDPOINT = "/api/expert-discovery/person-maintenance/rollback-preview"

RULE_VERSION = 1
MERGEABLE = {"MERGE_READY", "KEEP_SINGLE"}
APPLY_TERMINAL = {"APPLIED", "UNCHANGED", "CHANGED_OR_FAILED", "SKIPPED"}

MANIFEST_FILE = "manifest.json"
GROUPS_FILE = "groups.jsonl"
PREVIEW_FILE = "preview.jsonl"
MEMBERS_FILE = "members.jsonl"
SUMMARY_FILE = "summary.json"
STATE_FILE = "state.json"
APPLY_FILE = "apply.jsonl"
ROLLBACK_FILE = "rollback.jsonl"


def normalize_doc_id(raw: str) -> str:
    return raw.strip()


def load_manifest(path: str) -> list:
    """读取冻结 ID 名单：去空白/注释、按出现顺序去重。"""
    ids: list = []
    seen = set()
    with open(path, "r", encoding="utf-8") as fh:
        for line in fh:
            doc_id = normalize_doc_id(line)
            if not doc_id or doc_id.startswith("#"):
                continue
            if doc_id not in seen:
                seen.add(doc_id)
                ids.append(doc_id)
    return ids


def load_groups(path: str) -> list:
    """候选组标识：每行一个 JSON 数组，或逗号分隔的 docId 列表。"""
    groups: list = []
    with open(path, "r", encoding="utf-8") as fh:
        for line in fh:
            line = line.strip()
            if not line or line.startswith("#"):
                continue
            if line.startswith("["):
                members = [normalize_doc_id(str(item)) for item in json.loads(line)]
            else:
                members = [normalize_doc_id(item) for item in line.split(",")]
            members = [m for m in members if m]
            if members:
                groups.append(members)
    return groups


def manifest_digest(ids: list) -> str:
    return hashlib.sha256("\n".join(ids).encode("utf-8")).hexdigest()


def group_key(members: list) -> str:
    canonical = "\n".join(sorted({normalize_doc_id(m) for m in members if normalize_doc_id(m)}))
    return hashlib.sha256(canonical.encode("utf-8")).hexdigest()


# ── 私有输出：目录 0700、文件 0600、原子替换 ───────────────────────────────


def ensure_private_dir(path: str) -> None:
    os.makedirs(path, exist_ok=True)
    os.chmod(path, stat.S_IRWXU)  # 0700


def _restrict_file(path: str) -> None:
    try:
        os.chmod(path, stat.S_IRUSR | stat.S_IWUSR)  # 0600
    except OSError:
        pass


def _atomic_replace(path: str, text: str) -> None:
    directory = os.path.dirname(os.path.abspath(path))
    fd, tmp = tempfile.mkstemp(dir=directory, prefix=".tmp-")
    try:
        with os.fdopen(fd, "w", encoding="utf-8") as fh:
            fh.write(text)
            fh.flush()
            os.fsync(fh.fileno())
        os.replace(tmp, path)
    except BaseException:
        try:
            os.unlink(tmp)
        except OSError:
            pass
        raise
    _restrict_file(path)


def atomic_write_json(path: str, payload) -> None:
    _atomic_replace(path, json.dumps(payload, ensure_ascii=False, sort_keys=True))


def _dump_jsonl(records: list) -> str:
    return "".join(json.dumps(record, ensure_ascii=False, sort_keys=True) + "\n" for record in records)


def atomic_write_jsonl(path: str, records: list) -> None:
    _atomic_replace(path, _dump_jsonl(records))


def append_jsonl_atomic(path: str, record) -> None:
    """读-追加-原子替换：任一步中断都不留下半行。"""
    records = read_jsonl(path)
    records.append(record)
    atomic_write_jsonl(path, records)


def read_jsonl(path: str) -> list:
    if not os.path.exists(path):
        return []
    records = []
    with open(path, "r", encoding="utf-8") as fh:
        for line in fh:
            line = line.strip()
            if not line:
                continue
            try:
                records.append(json.loads(line))
            except json.JSONDecodeError:
                continue
    return records


def load_state(out_dir: str) -> dict:
    path = os.path.join(out_dir, STATE_FILE)
    if not os.path.exists(path):
        return {}
    with open(path, "r", encoding="utf-8") as fh:
        return json.load(fh)


def completed_group_keys(path: str) -> set:
    done = set()
    for record in read_jsonl(path):
        if record.get("groupKey") and record.get("status") in APPLY_TERMINAL:
            done.add(record["groupKey"])
    return done


def _now() -> str:
    return _dt.datetime.now(_dt.timezone.utc).isoformat()


class ApiClient:
    """最小 HTTP 客户端；注入 transport 便于测试。"""

    def __init__(self, base_url: str, transport=None, timeout: float = 60.0):
        self.base_url = base_url.rstrip("/")
        self._transport = transport
        self._timeout = timeout

    def preview(self, frozen_ids: list, groups: list):
        return self._post(PREVIEW_ENDPOINT, {
            "ruleVersion": RULE_VERSION,
            "frozenDocIds": frozen_ids,
            "candidateGroups": groups,
        })

    def apply(self, preview_digest: str, groups: list):
        return self._post(APPLY_ENDPOINT, {
            "ruleVersion": RULE_VERSION,
            "previewDigest": preview_digest,
            "groups": [
                {"memberDocIds": group["memberDocIds"], "expectedFingerprint": group["expectedFingerprint"]}
                for group in groups
            ],
        })

    def rollback(self, groups: list):
        return self._post(ROLLBACK_ENDPOINT, {"ruleVersion": RULE_VERSION, "groups": groups})

    def _post(self, endpoint: str, payload):
        if self._transport is not None:
            return self._transport(self.base_url + endpoint, payload)
        return self._http_post(self.base_url + endpoint, payload)

    def _http_post(self, url: str, payload):
        data = json.dumps(payload).encode("utf-8")
        request = urllib.request.Request(
            url, data=data, method="POST",
            headers={"Content-Type": "application/json"},
        )
        try:
            with urllib.request.urlopen(request, timeout=self._timeout) as response:
                return response.status, json.loads(response.read().decode("utf-8") or "{}")
        except urllib.error.HTTPError as error:
            body = error.read().decode("utf-8")
            try:
                parsed = json.loads(body or "{}")
            except json.JSONDecodeError:
                parsed = {"raw": body[:200]}
            return error.code, parsed


# ── 记录构造（绝不写入认证头；主报告不含完整邮箱） ──────────────────────────


def preview_record(group: dict, observed_at: str = None) -> dict:
    return {
        "groupKey": group.get("groupKey"),
        "memberDocIds": group.get("memberDocIds", []),
        "disposition": group.get("disposition"),
        "reason": group.get("reason"),
        "evidenceDigest": group.get("evidenceDigest"),
        "canMerge": group.get("canMerge"),
        "canSend": group.get("canSend"),
        "memberCount": len(group.get("memberDocIds", [])),
        "observedAt": observed_at or _now(),
    }


def member_records(group: dict) -> list:
    """逐成员明细（含邮箱）：只写 0600 明细文件，绝不进入主报告。"""
    records = []
    for member in group.get("members", []):
        records.append({
            "groupKey": group.get("groupKey"),
            "docId": member.get("docId"),
            "email": member.get("email"),
            "admissionReason": member.get("admissionReason"),
            "sendBlockReason": member.get("sendBlockReason"),
            "historyReference": member.get("historyReference"),
        })
    return records


def apply_record(result: dict, observed_at: str = None) -> dict:
    return {
        "groupKey": result.get("groupKey"),
        "status": result.get("status"),
        "personId": result.get("personId"),
        "memberCount": result.get("memberCount"),
        "protectionImported": result.get("protectionImported"),
        "reason": result.get("reason"),
        "observedAt": observed_at or _now(),
    }


def rollback_proposal_record(proposal: dict, observed_at: str = None) -> dict:
    return {
        "groupKey": proposal.get("groupKey"),
        "memberDocIds": proposal.get("memberDocIds", []),
        "personId": proposal.get("personId"),
        "safeToRollback": proposal.get("safeToRollback"),
        "affectedCount": proposal.get("affectedCount"),
        "reason": proposal.get("reason"),
        "observedAt": observed_at or _now(),
    }


# ── 调度 ────────────────────────────────────────────────────────────────────


def run_preview(client, frozen_ids: list, groups: list, out_dir: str, resume: bool = False) -> dict:
    ensure_private_dir(out_dir)
    if not resume:
        atomic_write_jsonl(os.path.join(out_dir, PREVIEW_FILE), [])
        atomic_write_jsonl(os.path.join(out_dir, MEMBERS_FILE), [])
    atomic_write_json(os.path.join(out_dir, MANIFEST_FILE), {
        "ruleVersion": RULE_VERSION,
        "documentCount": len(frozen_ids),
        "groupCount": len(groups),
        "manifestDigest": manifest_digest(frozen_ids),
        "observedAt": _now(),
    })
    status, body = client.preview(frozen_ids, groups)
    if status != 200:
        return {"status": status, "error": body}

    preview_path = os.path.join(out_dir, PREVIEW_FILE)
    members_path = os.path.join(out_dir, MEMBERS_FILE)
    existing = {record.get("groupKey") for record in read_jsonl(preview_path)}
    summary = body.get("classCounts", {})
    for group in body.get("groups", []):
        key = group.get("groupKey")
        if key in existing:
            continue
        append_jsonl_atomic(preview_path, preview_record(group))
        for member in member_records(group):
            append_jsonl_atomic(members_path, member)
        existing.add(key)
    result = {
        "status": 200,
        "previewDigest": body.get("previewDigest"),
        "totalDocuments": body.get("totalDocuments"),
        "classCounts": summary,
        "groupCount": len(body.get("groups", [])),
        "observedAt": _now(),
    }
    atomic_write_json(os.path.join(out_dir, SUMMARY_FILE), result)
    state = load_state(out_dir)
    state.update({
        "previewDigest": body.get("previewDigest"),
        "groupKeys": [group.get("groupKey") for group in body.get("groups", [])],
        "updatedAt": _now(),
    })
    atomic_write_json(os.path.join(out_dir, STATE_FILE), state)
    return result


def run_apply(client, out_dir: str, confirm: bool, preview_digest: str = None, limit: int = None) -> dict:
    ensure_private_dir(out_dir)
    state = load_state(out_dir)
    digest = preview_digest or state.get("previewDigest")
    if not confirm:
        return {"status": 0, "error": "REFUSED：apply 需要显式 --confirm（全量预览不是执行授权）"}
    if not digest:
        return {"status": 0, "error": "REFUSED：缺少预览摘要（先 preview 或传 --preview-digest）"}

    previews = read_jsonl(os.path.join(out_dir, PREVIEW_FILE))
    mergeable = [
        record for record in previews
        if record.get("disposition") in MERGEABLE and record.get("evidenceDigest")
    ]
    if limit is not None:
        mergeable = mergeable[:limit]
    done = completed_group_keys(os.path.join(out_dir, APPLY_FILE))
    pending = [record for record in mergeable if record.get("groupKey") not in done]
    if not pending:
        return {"status": 200, "applied": 0, "unchanged": 0, "results": []}

    groups = [
        {"memberDocIds": record["memberDocIds"], "expectedFingerprint": record["evidenceDigest"]}
        for record in pending
    ]
    status, body = client.apply(digest, groups)
    if status != 200:
        return {"status": status, "error": body}

    apply_path = os.path.join(out_dir, APPLY_FILE)
    results = body if isinstance(body, list) else []
    for result in results:
        append_jsonl_atomic(apply_path, apply_record(result))
    state = load_state(out_dir)
    state["lastApplyAt"] = _now()
    atomic_write_json(os.path.join(out_dir, STATE_FILE), state)
    applied = sum(1 for result in results if result.get("status") == "APPLIED")
    unchanged = sum(1 for result in results if result.get("status") == "UNCHANGED")
    return {"status": 200, "applied": applied, "unchanged": unchanged, "results": results}


def run_rollback_preview(client, out_dir: str) -> dict:
    ensure_private_dir(out_dir)
    previews = read_jsonl(os.path.join(out_dir, PREVIEW_FILE))
    applied_keys = {
        record.get("groupKey") for record in read_jsonl(os.path.join(out_dir, APPLY_FILE))
        if record.get("status") in {"APPLIED", "UNCHANGED"}
    }
    if applied_keys:
        groups = [record["memberDocIds"] for record in previews if record.get("groupKey") in applied_keys]
    else:
        groups = [record["memberDocIds"] for record in previews if record.get("disposition") == "MERGE_READY"]
    if not groups:
        return {"status": 200, "proposals": []}
    status, body = client.rollback(groups)
    if status != 200:
        return {"status": status, "error": body}
    rollback_path = os.path.join(out_dir, ROLLBACK_FILE)
    atomic_write_jsonl(rollback_path, [])
    proposals = body if isinstance(body, list) else []
    for proposal in proposals:
        append_jsonl_atomic(rollback_path, rollback_proposal_record(proposal))
    return {"status": 200, "proposals": proposals}


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="新发现历史人物合并：预览/受限应用/回滚提案（默认 preview）")
    parser.add_argument("--base-url", default=os.environ.get("TALENT_BASE_URL", "http://127.0.0.1:8080"))
    sub = parser.add_subparsers(dest="command")

    preview = sub.add_parser("preview", help="只读预览（默认命令）")
    preview.add_argument("--manifest", required=True, help="冻结 docId 名单")
    preview.add_argument("--groups", required=True, help="候选组标识文件")
    preview.add_argument("--out-dir", required=True)
    preview.add_argument("--resume", action="store_true", help="断点续跑：跳过已写记录")

    apply = sub.add_parser("apply", help="受限应用（需 --confirm 与预览摘要）")
    apply.add_argument("--out-dir", required=True)
    apply.add_argument("--confirm", action="store_true")
    apply.add_argument("--preview-digest", default=None)
    apply.add_argument("--limit", type=int, default=None, help="试点上限（如 50）")

    rollback = sub.add_parser("rollback-preview", help="回滚提案（只提案，不执行）")
    rollback.add_argument("--out-dir", required=True)
    return parser


def main(argv=None) -> int:
    args = build_parser().parse_args(argv)
    client = ApiClient(args.base_url)
    if args.command in (None, "preview"):
        if args.command is None:
            print("默认 preview 需要 --manifest/--groups/--out-dir", file=sys.stderr)
            return 2
        result = run_preview(
            client, load_manifest(args.manifest), load_groups(args.groups), args.out_dir, resume=args.resume
        )
        print(json.dumps({"status": result.get("status"), "previewDigest": result.get("previewDigest"),
                          "totalDocuments": result.get("totalDocuments"), "classCounts": result.get("classCounts")},
                         ensure_ascii=False))
        return 0 if result.get("status") == 200 else 1
    if args.command == "apply":
        result = run_apply(client, args.out_dir, args.confirm, args.preview_digest, args.limit)
        print(json.dumps(result, ensure_ascii=False))
        return 0 if result.get("status") == 200 else 1
    if args.command == "rollback-preview":
        result = run_rollback_preview(client, args.out_dir)
        print(json.dumps({"status": result.get("status"), "proposals": len(result.get("proposals", []))},
                         ensure_ascii=False))
        return 0 if result.get("status") == 200 else 1
    return 2


if __name__ == "__main__":
    sys.exit(main())
