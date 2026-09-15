"""Local task store, exact-snapshot approvals and crash-safe request reservations."""
from __future__ import annotations

import copy
import csv
import fcntl
import hashlib
import json
import os
import re
import tempfile
from contextlib import contextmanager
from datetime import datetime, timezone
from pathlib import Path


class Stop(Exception):
    """An actionable stop; no implicit retries or fallbacks."""


def clean(value):
    return " ".join(str(value or "").split())


def stamp():
    return datetime.now(timezone.utc).isoformat()


def digest(value):
    return hashlib.sha256(json.dumps(value, ensure_ascii=False, sort_keys=True).encode()).hexdigest()


def read_json(path):
    return json.loads(Path(path).read_text(encoding="utf-8-sig"))


def atomic_json(path, value):
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    fd, temporary = tempfile.mkstemp(dir=path.parent, prefix=".writing-")
    try:
        with os.fdopen(fd, "w", encoding="utf-8") as handle:
            json.dump(value, handle, ensure_ascii=False, indent=2)
            handle.write("\n")
            handle.flush()
            os.fsync(handle.fileno())
        os.replace(temporary, path)
    finally:
        if os.path.exists(temporary):
            os.unlink(temporary)


def redact(value, secrets):
    if isinstance(value, dict):
        return {k: "[REDACTED]" if re.sub("[^a-z]", "", k.lower()) in
                {"key", "apikey", "authorization", "token", "accesstoken"}
                else redact(v, secrets) for k, v in value.items()}
    if isinstance(value, list):
        return [redact(v, secrets) for v in value]
    if isinstance(value, str):
        for secret in secrets:
            if secret:
                value = value.replace(secret, "[REDACTED]")
    return value


def keys_from(path=None):
    names = ("DEEPSEEK_API_KEY", "APOLLO_API_KEY", "EMAILABLE_API_KEY")
    result = {}
    if path:
        for line in Path(path).read_text(encoding="utf-8-sig").splitlines():
            line = line.strip()
            if not line or line.startswith(("#", "!")):
                continue
            name, sep, value = line.removeprefix("export ").partition("=")
            if sep and name.strip() in names:
                result[name.strip()] = value.strip().strip("\"'")
    for name in names:
        if os.environ.get(name):
            result[name] = os.environ[name]
    return result


@contextmanager
def job_lock(directory):
    root = Path(directory)
    root.mkdir(parents=True, exist_ok=True)
    with (root / ".lock").open("a") as handle:
        try:
            fcntl.flock(handle, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            raise Stop("该任务正在执行，请稍后再试。") from None
        try:
            yield
        finally:
            fcntl.flock(handle, fcntl.LOCK_UN)


class Job:
    def __init__(self, root):
        self.root = Path(root)
        self.path = self.root / "job.json"
        self.data = read_json(self.path) if self.path.exists() else None
        if self.data and self.data.get("version") != 1:
            raise Stop("任务版本不兼容。")

    def save(self):
        atomic_json(self.path, self.data)

    def create(self, config, fixtures=None):
        if self.data is not None:
            raise Stop("任务目录已存在；请 review 或使用新的目录，禁止覆盖。")
        self.data = {"version": 1, "created_at": stamp(), "config": config,
                     "mode": "DEMO" if fixtures is not None else "LIVE",
                     "fixtures": fixtures, "candidates": [], "documents": [],
                     "issues": [], "proposals": {}, "requests": {}, "imports": []}
        self.save()

    def require(self):
        if self.data is None:
            raise Stop("任务不存在，请先执行 plan。")

    def snapshot(self):
        return digest({k: self.data[k] for k in
                       ("config", "mode", "fixtures", "candidates", "documents")})

    def propose(self, action, targets, limits, reason):
        self.require()
        proposal = {"action": action, "targets": copy.deepcopy(targets),
                    "limits": limits, "reason": reason, "snapshot": self.snapshot()}
        identity = digest(proposal)[:16]
        attempt = 0
        while self.data["proposals"].get(identity, {}).get("state") == "CANCELLED":
            attempt += 1
            proposal["attempt"] = attempt
            identity = digest(proposal)[:16]
        if identity not in self.data["proposals"]:
            self.data["proposals"][identity] = dict(proposal, id=identity,
                state="PROPOSED", created_at=stamp())
            self.save()
        return self.data["proposals"][identity]

    def proposal(self, identity):
        self.require()
        proposal = self.data["proposals"].get(identity)
        if not proposal:
            raise Stop("找不到 proposal ID；请 review。")
        immutable = {k: proposal[k] for k in ("action", "targets", "limits", "reason", "snapshot")}
        if "attempt" in proposal:
            immutable["attempt"] = proposal["attempt"]
        if digest(immutable)[:16] != identity:
            raise Stop("提案内容已改变；请重新生成提案。")
        return proposal

    def approve(self, identity):
        p = self.proposal(identity)
        if p["state"] in {"DONE", "RUNNING", "STOPPED", "CANCELLED"}:
            raise Stop("此提案已执行或已取消，不能再次批准。")
        if p["snapshot"] != self.snapshot():
            raise Stop("名单、配置或资料已变化，原提案失效；请重新 review/propose。")
        p.update(state="APPROVED", approved_at=stamp())
        self.save()

    def cancel(self, identity):
        p = self.proposal(identity)
        if p["state"] not in {"PROPOSED", "APPROVED"}:
            raise Stop("只能取消尚未执行的提案。")
        p.update(state="CANCELLED", cancelled_at=stamp())
        self.save()

    def begin(self, identity, action):
        p = self.proposal(identity)
        if p["action"] != action:
            raise Stop("命令与提案动作不符。")
        if p["state"] == "DONE":
            return None
        if p["state"] != "APPROVED":
            raise Stop("尚未批准，或上次执行未完整结束；先 review，禁止自动重跑。")
        if p["snapshot"] != self.snapshot():
            raise Stop("批准对应的名单或配置已改变；需重新生成并确认提案。")
        p.update(state="RUNNING", started_at=stamp())
        self.save()
        return p

    def finish(self, p, error=None):
        p.update(state="STOPPED" if error else "DONE", finished_at=stamp())
        if error:
            p["error"] = str(error)
        self.save()

    def request(self, proposal, kind, params, execute):
        active = self.proposal(proposal["id"])
        if active is not proposal or active["state"] != "RUNNING":
            raise Stop("请求只能由已批准且正在执行的提案发起。")
        # Provider/kind/mode are part of cache identity. Test results can never be live.
        key = digest([self.data["mode"], kind, params])
        previous = self.data["requests"].get(key)
        if previous:
            if previous["state"] == "DONE":
                return copy.deepcopy(previous["response"])
            raise Stop(f"{kind} 上次请求 {previous['state']}，结果待确认，不自动重试。")
        used = sum(r["kind"] == kind and r["proposal_id"] == proposal["id"]
                   for r in self.data["requests"].values())
        if used >= proposal["limits"].get(kind, 0):
            raise Stop(f"达到 {kind} 已批准请求上限。")
        item = {"kind": kind, "params": params, "proposal_id": proposal["id"],
                "state": "PENDING", "started_at": stamp()}
        self.data["requests"][key] = item
        self.save()  # Crash after this point must never silently spend again.
        try:
            result = execute()
        except Exception as exc:
            item.update(state="UNCERTAIN", error=type(exc).__name__)
            self.save()
            raise
        if kind == "emailable.verify" and result.get("http_status") == 249:
            item.update(state="PENDING_PROVIDER", response=result)
            self.save()
            raise Stop("Emailable 尚未完成（249）；已停在待确认，不自动重试或验证其他邮箱。")
        item.update(state="DONE", response=result, completed_at=stamp())
        self.save()
        return copy.deepcopy(result)


def csv_write(path, rows, fields):
    with Path(path).open("w", encoding="utf-8-sig", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=fields, extrasaction="ignore")
        writer.writeheader()
        for row in rows:
            values = {}
            for field in fields:
                value = row.get(field, "")
                if isinstance(value, (list, dict)):
                    value = json.dumps(value, ensure_ascii=False)
                value = str(value if value is not None else "")
                if value.lstrip().startswith(("=", "+", "-", "@")):
                    value = "'" + value
                values[field] = value
            writer.writerow(values)
