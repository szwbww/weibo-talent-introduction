#!/usr/bin/env python3
"""Enrich selected high-value engineers with Apollo native work email only."""
from __future__ import annotations

import argparse
import json
import random
import re
import subprocess
import time
import urllib.parse
from datetime import datetime, timezone
from pathlib import Path


APOLLO_BASE = "https://api.apollo.io/api/v1"
FREE_EMAIL_DOMAINS = {"gmail.com", "hotmail.com", "outlook.com", "yahoo.com", "icloud.com", "qq.com", "163.com"}
EMAIL = re.compile(r"^[^@\s]+@[^@\s]+\.[^@\s]+$")


def stamp() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="seconds")


def load_json(path: str | Path) -> dict:
    return json.loads(Path(path).read_text(encoding="utf-8"))


def atomic_json(path: Path, value: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temp = path.with_suffix(path.suffix + ".tmp")
    temp.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    temp.replace(path)


def load_properties(path: str | Path) -> dict[str, str]:
    values = {}
    for raw in Path(path).read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if line and not line.startswith("#") and "=" in line:
            key, value = line.split("=", 1)
            values[key.strip()] = value.strip().strip('"').strip("'")
    return values


def title_score(title: str) -> int:
    value = str(title or "").casefold()
    if any(term in value for term in ("sales", "marketing", "recruit", "human resources", "business development")):
        return -100
    score = 0
    for term, points in (
        ("principal", 50), ("distinguished", 50), ("chief scientist", 50),
        ("staff", 45), ("lead", 40), ("expert", 40), ("director", 38),
        ("senior", 30), ("scientist", 25), ("r&d", 25), ("research", 25),
        ("engineer", 20), ("process", 10), ("manufacturing", 10),
    ):
        if term in value:
            score += points
    return score


def select_candidates(source: dict, public: dict, limit: int) -> list[dict]:
    excluded = set()
    for row in public.get("results") or []:
        if row.get("status") == "PUBLIC_EMAIL_CONFIRMED" or row.get("employment_review_required") is True:
            excluded.add(str(row.get("apollo_person_id") or ""))
    unique = {}
    for row in (source.get("apollo_email_available") or []) + (source.get("email_discovery_required") or []):
        person_id = str(row.get("apollo_person_id") or "")
        if not person_id or person_id in excluded:
            continue
        if row.get("high_value_tier") not in {"A", "B"} or row.get("apollo_has_email") is not True:
            continue
        if title_score(row.get("title", "")) < 0:
            continue
        current = unique.get(person_id)
        if current is None or int(row.get("domestic_enterprise_match_count") or 0) > int(current.get("domestic_enterprise_match_count") or 0):
            unique[person_id] = dict(row)
    tier_order = {"A": 0, "B": 1}
    ranked = sorted(
        unique.values(),
        key=lambda row: (
            tier_order[row["high_value_tier"]],
            -int(row.get("domestic_enterprise_match_count") or 0),
            -title_score(row.get("title", "")),
            not bool(row.get("name_is_complete")),
            row["apollo_person_id"],
        ),
    )[:limit]
    for index, row in enumerate(ranked, 1):
        row["selection_rank"] = index
        row["selection_title_score"] = title_score(row.get("title", ""))
    return ranked


def build_bulk_request(person_ids: list[str]) -> tuple[str, dict]:
    if not 1 <= len(person_ids) <= 10:
        raise ValueError("Apollo bulk enrichment requires 1-10 people")
    if any(not person_id for person_id in person_ids):
        raise ValueError("Apollo person ID is required")
    query = urllib.parse.urlencode({
        "reveal_personal_emails": "false",
        "reveal_phone_number": "false",
        "run_waterfall_email": "false",
        "run_waterfall_phone": "false",
    })
    return f"{APOLLO_BASE}/people/bulk_match?{query}", {"details": [{"id": person_id} for person_id in person_ids]}


def remaining_capacity(*, left: int, reserve: int, requested: int) -> int:
    return max(0, min(int(requested), int(left) - int(reserve)))


def may_continue(*, left_after: int, reserve: int) -> bool:
    return int(left_after) > int(reserve)


def quote_curl(value: str) -> str:
    return str(value).replace("\\", "\\\\").replace('"', '\\"')


def request_json(url: str, api_key: str, body: dict, retries: int = 5) -> dict:
    if not url.startswith(APOLLO_BASE + "/"):
        raise ValueError("Only Apollo API endpoints are allowed")
    payload = json.dumps(body, ensure_ascii=False, separators=(",", ":"))
    last_error = ""
    for attempt in range(retries):
        config = [
            'request = "POST"', f'url = "{quote_curl(url)}"', "silent", "show-error", "ipv4", "http1.1",
            'max-time = "90"', f'header = "x-api-key: {quote_curl(api_key)}"',
            'header = "Content-Type: application/json"', 'header = "Accept: application/json"',
            f'data = "{quote_curl(payload)}"',
        ]
        proc = subprocess.run(
            ["curl", "--config", "-", "--write-out", "\n%{http_code}"],
            input="\n".join(config), text=True, capture_output=True, timeout=100, check=False,
        )
        raw, separator, status = proc.stdout.rpartition("\n")
        if proc.returncode == 0 and separator and status.isdigit() and int(status) < 400:
            return json.loads(raw)
        last_error = f"curl={proc.returncode} http={status} {proc.stderr[-240:]} {raw[:240]}"
        if proc.returncode == 0 and status not in {"429", "500", "502", "503", "504"}:
            break
        time.sleep(min(15, 1.5 * (2 ** attempt)) + random.random())
    raise RuntimeError(last_error[:600])


def credit_stats(api_key: str) -> dict:
    data = request_json(f"{APOLLO_BASE}/usage_stats/credit_usage_stats", api_key, {})
    lead = (data.get("credit_usage_stats") or {}).get("lead_credit") or {}
    return {
        "limit": int(lead.get("limit") or 0),
        "consumed": int(lead.get("consumed") or 0),
        "left_over": int(lead.get("left_over") or 0),
        "cycle": data.get("current_credit_cycle") or {},
    }


def email_value(person: dict) -> str:
    value = str(person.get("email") or "").strip().lower()
    if value == "[email protected]" or not EMAIL.fullmatch(value):
        return ""
    return value


def result_row(candidate: dict, person: dict | None) -> dict:
    base = {
        "selection_rank": candidate["selection_rank"],
        "engineer_id": candidate["engineer_id"],
        "apollo_person_id": candidate["apollo_person_id"],
        "original_name": candidate.get("name", ""),
        "high_value_tier": candidate["high_value_tier"],
        "domestic_enterprise_match_count": candidate.get("domestic_enterprise_match_count", 0),
        "retrieved_at": stamp(),
    }
    if not person:
        return {**base, "status": "NO_MATCH", "email": "", "email_status": ""}
    email = email_value(person)
    domain = email.rsplit("@", 1)[-1] if email else ""
    return {
        **base,
        "status": "EMAIL_FOUND" if email else "MATCHED_NO_EMAIL",
        "name": str(person.get("name") or candidate.get("name") or ""),
        "title": str(person.get("title") or candidate.get("title") or ""),
        "current_organization": str((person.get("organization") or {}).get("name") or candidate.get("current_organization") or ""),
        "email": email,
        "email_status": str(person.get("email_status") or ""),
        "email_type": "APOLLO_NATIVE_WORK" if email and domain not in FREE_EMAIL_DOMAINS else "REVIEW_REQUIRED" if email else "",
        "match_confidence": str(person.get("match_confidence") or ""),
    }


def update_summary(state: dict) -> None:
    rows = list((state.get("results") or {}).values())
    state["summary"] = {
        "selected": len(state.get("selection") or []),
        "processed": len(rows),
        "email_found": sum(row["status"] == "EMAIL_FOUND" for row in rows),
        "matched_no_email": sum(row["status"] == "MATCHED_NO_EMAIL" for row in rows),
        "no_match": sum(row["status"] == "NO_MATCH" for row in rows),
        "failed": sum(row["status"] == "FAILED" for row in rows),
        "apollo_phone_requests": 0,
        "apollo_personal_email_requests": 0,
        "apollo_waterfall_requests": 0,
        "emailable_verification_calls": 0,
    }


def run(args: argparse.Namespace) -> dict:
    source = load_json(args.input)
    public = load_json(args.public)
    output = Path(args.output)
    selected = select_candidates(source, public, args.limit)
    if output.exists():
        state = load_json(output)
        if [row["apollo_person_id"] for row in state.get("selection") or []] != [row["apollo_person_id"] for row in selected]:
            raise RuntimeError("Selection changed; refusing to reuse checkpoint")
    else:
        state = {
            "metadata": {
                "created_at": stamp(),
                "source": str(Path(args.input).resolve()),
                "policy": "Apollo native work email only; no phone, personal email, waterfall, organization search, or email verification",
                "requested_limit": args.limit,
                "credit_reserve": args.reserve,
            },
            "selection": selected,
            "results": {},
            "batches": [],
        }
        update_summary(state)
        atomic_json(output, state)
    if not args.execute:
        return state

    key = load_properties(args.keys).get("APOLLO_API_KEY", "")
    if not key:
        raise RuntimeError("APOLLO_API_KEY missing")
    before = credit_stats(key)
    state["credit_before"] = state.get("credit_before") or before
    pending = [row for row in state["selection"] if row["apollo_person_id"] not in state["results"]]
    current = before
    while pending:
        room = remaining_capacity(left=current["left_over"], reserve=args.reserve, requested=len(pending))
        if room <= 0:
            state["stopped_reason"] = "CREDIT_RESERVE_REACHED"
            break
        batch = pending[: min(10, room)]
        ids = [row["apollo_person_id"] for row in batch]
        url, body = build_bulk_request(ids)
        reply = request_json(url, key, body)
        matches = {str(row.get("id") or ""): row for row in reply.get("matches") or [] if isinstance(row, dict)}
        for candidate in batch:
            person_id = candidate["apollo_person_id"]
            state["results"][person_id] = result_row(candidate, matches.get(person_id))
        after = credit_stats(key)
        state["credit_after"] = after
        state["batches"].append({
            "completed_at": stamp(), "size": len(batch),
            "left_before": current["left_over"], "left_after": after["left_over"],
            "credits_consumed_observed": current["left_over"] - after["left_over"],
        })
        if current["left_over"] - after["left_over"] > len(batch):
            state.setdefault("warnings", []).append({
                "observed_at": stamp(),
                "kind": "SHARED_BALANCE_JUMP",
                "message": "共享余额变化大于本批人数；请求仍仅包含原生工作邮箱参数，后续按实时余额保留线控制。",
                "batch_size": len(batch),
                "observed_delta": current["left_over"] - after["left_over"],
            })
        update_summary(state)
        atomic_json(output, state)
        current = after
        pending = [row for row in state["selection"] if row["apollo_person_id"] not in state["results"]]
        if state["summary"]["processed"] % 50 == 0 or not pending:
            print(json.dumps({"summary": state["summary"], "credit_after": after}, ensure_ascii=False), flush=True)
        time.sleep(0.2)
    state["credit_after"] = credit_stats(key)
    update_summary(state)
    atomic_json(output, state)
    return state


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", required=True)
    parser.add_argument("--public", required=True)
    parser.add_argument("--keys", required=True)
    parser.add_argument("--output", required=True)
    parser.add_argument("--limit", type=int, default=700)
    parser.add_argument("--reserve", type=int, default=82)
    parser.add_argument("--execute", action="store_true")
    args = parser.parse_args()
    state = run(args)
    print(json.dumps(state["summary"], ensure_ascii=False))


if __name__ == "__main__":
    main()
