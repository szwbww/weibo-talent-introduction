#!/usr/bin/env python3
"""02 维护 CLI：旧名单回源**预览**与**受控应用**调度（本地留痕，不发邮件）。

- 默认只 preview；apply 必须显式 `--apply` 且引用同一输出目录里已生成的 preview 记录。
- 每人一行 JSONL；输出目录 0700、文件 0600；文件名不含姓名/邮箱；**从不记录认证头**。
- apply 前先原子写备份；备份写失败则**该条停止进入 apply**；恢复时按 docId 去重。
- PARTIAL（部分层已落地）/STALE（旧 hash 过期）不是终态：重新预览取得服务端新检查点后按同一原申请续跑。
- 只在服务器已判 `RECOVERABLE` 的条目上调用 apply；服务器在 apply 时重新取证重算 hash。
- 仅生成逐条事实回滚**提案**，不提供接受任意旧 proof/姓名的写接口。

用法::

    python3 replay.py --manifest ids.txt --out-dir out            # 预览
    python3 replay.py --manifest ids.txt --out-dir out --apply    # 应用（需已预览）
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

PREVIEW_ENDPOINT = "/api/expert-discovery/identity-recovery/preview"
APPLY_ENDPOINT = "/api/expert-discovery/identity-recovery/apply"

PREVIEW_FILE = "preview.jsonl"
APPLY_FILE = "apply.jsonl"
BACKUP_FILE = "backup.jsonl"
ROLLBACK_FILE = "rollback.jsonl"
MANIFEST_FILE = "manifest.json"

APPLY_TERMINAL = {"APPLIED", "NOT_RECOVERABLE", "FAILED"}

# R-17：沿用现有管理入口访问控制 —— 只把**已有登录会话**作为 Cookie 请求头发送；
# 凭证绝不写入审计/日志/文件名，缺失或过期时给出可操作错误。
UNAUTHORIZED_HINT = (
    "UNAUTHORIZED：需要现有登录会话（--cookie 或 TALENT_SESSION_COOKIE）；"
    "访问控制未放宽，也不会把凭证写入审计/日志"
)
SESSION_ENV = "TALENT_SESSION_COOKIE"

# I-3/V-4：PARTIAL（部分层已落地）、STALE（旧 hash 已过期）以及服务端新增的**非完整恢复** token
# （准入未验证/可重试等）都不是终态 —— 同一 ID 可先重新预览取得服务端新检查点，再按同一原申请
# 续跑剩余层；只有终态才永久跳过。run_apply 的实际判据是「非终态」，本集合列已知 token 供阅读与兼容。
APPLY_RESUMABLE = {"PARTIAL", "STALE", "ADMISSION_UNVERIFIED", "ADMISSION_REVERIFY_FAILED", "NEEDS_RETRY", "RETRYABLE"}


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


def manifest_digest(ids: list) -> str:
    canonical = "\n".join(ids)
    return hashlib.sha256(canonical.encode("utf-8")).hexdigest()


def ensure_private_dir(path: str) -> None:
    os.makedirs(path, exist_ok=True)
    os.chmod(path, stat.S_IRWXU)  # 0700


def _restrict_file(path: str) -> None:
    try:
        os.chmod(path, stat.S_IRUSR | stat.S_IWUSR)  # 0600
    except OSError:
        pass


def atomic_write_json(path: str, payload) -> None:
    directory = os.path.dirname(os.path.abspath(path))
    fd, tmp = tempfile.mkstemp(dir=directory, prefix=".tmp-")
    try:
        with os.fdopen(fd, "w", encoding="utf-8") as fh:
            fh.write(json.dumps(payload, ensure_ascii=False, sort_keys=True))
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


def append_jsonl(path: str, record) -> None:
    with open(path, "a", encoding="utf-8") as fh:
        fh.write(json.dumps(record, ensure_ascii=False, sort_keys=True) + "\n")
        fh.flush()
        os.fsync(fh.fileno())
    _restrict_file(path)


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


def completed_ids(path: str, terminal=APPLY_TERMINAL) -> set:
    done = set()
    for record in read_jsonl(path):
        if record.get("docId") and record.get("status") in terminal:
            done.add(record["docId"])
    return done


def resumable_ids(path: str, terminal=APPLY_TERMINAL) -> set:
    """V-4：apply 日志里**非完整恢复**的条目 —— 判据取「非空且非终态」，
    故服务端新增的非完整 token（见 [APPLY_RESUMABLE]）不会被 CLI 静默永久跳过；
    完整 APPLIED 仍为终态、永久跳过。"""
    resumable = set()
    for record in read_jsonl(path):
        status = record.get("status")
        if record.get("docId") and status and status not in terminal:
            resumable.add(record["docId"])
    return resumable


def _now() -> str:
    return _dt.datetime.now(_dt.timezone.utc).isoformat()


class ApiClient:
    """最小 HTTP 客户端；注入 transport 便于测试。"""

    def __init__(self, base_url: str, transport=None, timeout: float = 30.0, session_cookie: str = None):
        self.base_url = base_url.rstrip("/")
        self._transport = transport
        self._timeout = timeout
        self._session_cookie = (session_cookie or "").strip() or None

    @property
    def authenticated(self) -> bool:
        return bool(self._session_cookie)

    def preview(self, doc_id: str):
        return self._post(PREVIEW_ENDPOINT, {"docId": doc_id})

    def apply(self, doc_id: str, snapshot_hash: str, proposal_hash: str):
        return self._post(APPLY_ENDPOINT, {
            "docId": doc_id,
            "snapshotHash": snapshot_hash,
            "proposalHash": proposal_hash,
        })

    def _post(self, endpoint: str, payload):
        if self._transport is not None:
            return self._transport(self.base_url + endpoint, payload)
        return self._http_post(self.base_url + endpoint, payload)

    def _http_post(self, url: str, payload):
        data = json.dumps(payload).encode("utf-8")
        headers = {"Content-Type": "application/json"}
        if self._session_cookie:
            headers["Cookie"] = self._session_cookie
        request = urllib.request.Request(url, data=data, method="POST", headers=headers)
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


# ── 记录构造（绝不写入认证头/姓名/邮箱） ────────────────────────────────────


def preview_record(doc_id: str, status: int, body: dict, observed_at: str = None) -> dict:
    return {
        "docId": doc_id,
        "httpStatus": status,
        "status": body.get("status"),
        "reason": body.get("reason"),
        "snapshotHash": body.get("snapshotHash"),
        "proposalHash": body.get("proposalHash"),
        "identityRuleVersion": body.get("identityRuleVersion"),
        "sourceLocator": body.get("sourceLocator"),
        "sourceUrl": body.get("sourceUrl"),
        "evidenceSource": body.get("evidenceSource"),
        "evidenceHash": body.get("evidenceHash"),
        "proposedGivenNames": body.get("proposedGivenNames"),
        "proposedFamilyNames": body.get("proposedFamilyNames"),
        "layers": body.get("layers", []),
        "blockedReasons": body.get("blockedReasons", []),
        "observedAt": observed_at or body.get("observedAt") or _now(),
    }


def backup_record(preview: dict, observed_at: str = None) -> dict:
    """apply 前的原子备份：只固定本次预览的身份事实与两项 hash，供回滚核对。"""
    return {
        "docId": preview.get("docId"),
        "snapshotHash": preview.get("snapshotHash"),
        "proposalHash": preview.get("proposalHash"),
        "layers": preview.get("layers", []),
        "sourceLocator": preview.get("sourceLocator"),
        "evidenceSource": preview.get("evidenceSource"),
        "evidenceHash": preview.get("evidenceHash"),
        "observedAt": observed_at or _now(),
    }


def apply_record(doc_id: str, status: int, body: dict, observed_at: str = None) -> dict:
    return {
        "docId": doc_id,
        "httpStatus": status,
        "status": body.get("status"),
        "reason": body.get("reason"),
        "admissionDecision": body.get("admissionDecision"),
        "admissionIdentityChanged": body.get("admissionIdentityChanged"),
        "layers": body.get("layers", []),
        "observedAt": observed_at or body.get("observedAt") or _now(),
    }


def rollback_proposal(preview: dict, applied: dict, observed_at: str = None) -> dict:
    """逐条事实回滚**提案**（不执行写）：列明本次目标值，交由人工以受控补丁处理。"""
    return {
        "docId": preview.get("docId"),
        "proposalOnly": True,
        "proposalHash": preview.get("proposalHash"),
        "appliedStatus": applied.get("status"),
        "targetGivenNames": preview.get("proposedGivenNames"),
        "targetFamilyNames": preview.get("proposedFamilyNames"),
        "evidenceSource": preview.get("evidenceSource"),
        "evidenceHash": preview.get("evidenceHash"),
        "observedAt": observed_at or _now(),
    }


# ── 调度 ────────────────────────────────────────────────────────────────────


def write_manifest(out_dir: str, ids: list, observed_at: str = None) -> dict:
    manifest = {
        "ids": ids,
        "count": len(ids),
        "digest": manifest_digest(ids),
        "observedAt": observed_at or _now(),
    }
    atomic_write_json(os.path.join(out_dir, MANIFEST_FILE), manifest)
    return manifest


def run_preview(client, ids: list, out_dir: str) -> dict:
    ensure_private_dir(out_dir)
    write_manifest(out_dir, ids)
    preview_path = os.path.join(out_dir, PREVIEW_FILE)
    done = completed_ids(preview_path, terminal={"RECOVERABLE", "ROUTE_TO_REPAIR", "NEEDS_MANUAL",
                                                 "SOURCE_LOCATOR_MISSING", "SOURCE_IDENTITY_CONFLICT",
                                                 "SOURCE_UNAVAILABLE", "DEFERRED", "NOT_FOUND"})
    summary = {"total": len(ids), "previewed": 0, "skipped": 0, "writeFailures": 0}
    for doc_id in ids:
        if doc_id in done:
            summary["skipped"] += 1
            continue
        status, body = client.preview(doc_id)
        if status == 401:
            summary["unauthorized"] = True
            return summary
        try:
            append_jsonl(preview_path, preview_record(doc_id, status, body))
            summary["previewed"] += 1
        except OSError:
            # 报告写失败：该条不得进入 apply（watchdog 靠日志完整性）。
            summary["writeFailures"] += 1
    return summary


def run_apply(client, ids: list, out_dir: str) -> dict:
    ensure_private_dir(out_dir)
    preview_path = os.path.join(out_dir, PREVIEW_FILE)
    apply_path = os.path.join(out_dir, APPLY_FILE)
    backups = {record.get("docId") for record in read_jsonl(os.path.join(out_dir, BACKUP_FILE))}
    applied = completed_ids(apply_path, terminal=APPLY_TERMINAL)
    resumable = resumable_ids(apply_path)
    previews = {record.get("docId"): record for record in read_jsonl(preview_path)}
    summary = {"total": len(ids), "applied": 0, "skipped": 0, "backupFailures": 0, "repreviewed": 0}
    for doc_id in ids:
        if doc_id in applied:
            summary["skipped"] += 1
            continue
        preview = previews.get(doc_id)
        if preview is None:
            summary["skipped"] += 1
            continue
        # I-3 断点续跑：上次 PARTIAL/STALE 后旧 hash 已与 ES 当前事实不符，先重新预览取服务端新检查点，
        # 再按同一原申请续跑；服务器只接受「前值或本次目标值」，遇第三种值仍停止。
        if doc_id in resumable:
            status, body = client.preview(doc_id)
            if status == 401:
                summary["unauthorized"] = True
                return summary
            preview = preview_record(doc_id, status, body)
            append_jsonl(preview_path, preview)
            previews[doc_id] = preview
            summary["repreviewed"] += 1
        if preview.get("status") != "RECOVERABLE":
            summary["skipped"] += 1
            continue
        # 1) 先原子写备份；备份失败则该条停止进入 apply。
        if doc_id not in backups:
            try:
                append_jsonl(os.path.join(out_dir, BACKUP_FILE), backup_record(preview))
                backups.add(doc_id)
            except OSError:
                summary["backupFailures"] += 1
                continue
        # 2) 服务器重新取证重算；客户端只带 docId + 两项 hash。
        status, body = client.apply(doc_id, preview.get("snapshotHash"), preview.get("proposalHash"))
        if status == 401:
            summary["unauthorized"] = True
            return summary
        append_jsonl(os.path.join(out_dir, APPLY_FILE), apply_record(doc_id, status, body))
        if body.get("status") == "APPLIED":
            summary["applied"] += 1
        elif body.get("status") and body.get("status") not in APPLY_TERMINAL:
            resumable.add(doc_id)
    return summary


def run_rollback(out_dir: str) -> int:
    previews = {record.get("docId"): record for record in read_jsonl(os.path.join(out_dir, PREVIEW_FILE))}
    proposals = 0
    for applied in read_jsonl(os.path.join(out_dir, APPLY_FILE)):
        if applied.get("status") != "APPLIED":
            continue
        preview = previews.get(applied.get("docId"))
        if preview is None:
            continue
        append_jsonl(os.path.join(out_dir, ROLLBACK_FILE), rollback_proposal(preview, applied))
        proposals += 1
    return proposals


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="旧名单回源预览/应用（默认 preview）")
    parser.add_argument("--manifest", required=True, help="冻结 docId 名单（每行一个）")
    parser.add_argument("--out-dir", required=True, help="本地留痕输出目录")
    parser.add_argument("--base-url", default="http://127.0.0.1:8080")
    parser.add_argument("--apply", action="store_true", help="显式应用（必须先 preview）")
    parser.add_argument(
        "--cookie",
        default=None,
        help=f"现有登录会话 Cookie（默认取环境变量 {SESSION_ENV}）；只随请求头发送，不落盘",
    )
    parser.add_argument("--rollback-proposal", action="store_true", help="仅生成回滚提案")
    return parser


def main(argv=None) -> int:
    args = build_parser().parse_args(argv)
    ensure_private_dir(args.out_dir)
    if args.rollback_proposal:
        return 0 if run_rollback(args.out_dir) >= 0 else 1
    ids = load_manifest(args.manifest)
    cookie = args.cookie or os.environ.get(SESSION_ENV)
    if not cookie:
        print(json.dumps({"status": 0, "error": f"REFUSED：{UNAUTHORIZED_HINT}"}, ensure_ascii=False))
        return 2
    client = ApiClient(args.base_url, session_cookie=cookie)
    summary = run_apply(client, ids, args.out_dir) if args.apply else run_preview(client, ids, args.out_dir)
    if summary.get("unauthorized"):
        print(json.dumps({"status": 401, "error": UNAUTHORIZED_HINT}, ensure_ascii=False))
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
