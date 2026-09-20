#!/usr/bin/env python3
"""Reveal personal emails for an approved Apollo shortlist.

This program never reads, stores, or imports Apollo's generic/work-email fields.
It accepts an email only when Apollo returns it in an explicitly personal-email
field. Results are written locally for review; this program never updates ES or
sends an email.
"""

from __future__ import annotations

import argparse
import json
import os
import random
import re
import subprocess
import time
import urllib.parse
from datetime import datetime, timezone
from pathlib import Path
from typing import Any


APOLLO_BASE = "https://api.apollo.io/api/v1"
EMAIL = re.compile(r"^[^@\s]+@[^@\s]+\.[^@\s]+$")
PERSONAL_FIELDS = {
    "personal_email",
    "personal_emails",
    "personal_email_address",
    "personal_email_addresses",
}


def stamp() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="seconds")


def atomic_json(path: Path, value: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temp = path.with_suffix(path.suffix + ".tmp")
    temp.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    temp.replace(path)


def load_properties(path: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if line and not line.startswith("#") and "=" in line:
            key, value = line.split("=", 1)
            values[key.strip()] = value.strip().strip('"').strip("'")
    return values


def api_key(keys: str | None) -> str:
    if keys:
        path = Path(keys)
        if path.is_file():
            value = load_properties(path).get("APOLLO_API_KEY", "")
            if value:
                return value
    return os.environ.get("APOLLO_API_KEY", "").strip()


def quote_config(value: str) -> str:
    return value.replace("\\", "\\\\").replace('"', '\\"')


def curl_json(method: str, url: str, key: str, body: dict[str, Any] | None = None) -> dict[str, Any]:
    if not url.startswith(APOLLO_BASE + "/"):
        raise ValueError("Apollo endpoint required")
    payload = json.dumps(body, ensure_ascii=False, separators=(",", ":")) if body is not None else ""
    last_error = ""
    for attempt in range(5):
        config = [
            f'request = "{method}"',
            f'url = "{quote_config(url)}"',
            "silent",
            "show-error",
            "ipv4",
            "http1.1",
            'max-time = "90"',
            f'header = "x-api-key: {quote_config(key)}"',
            'header = "Accept: application/json"',
        ]
        if body is not None:
            config.extend([
                'header = "Content-Type: application/json"',
                f'data = "{quote_config(payload)}"',
            ])
        proc = subprocess.run(
            ["curl", "--config", "-", "--write-out", "\n%{http_code}"],
            input="\n".join(config), text=True, capture_output=True, timeout=100, check=False,
        )
        raw, separator, status = proc.stdout.rpartition("\n")
        if proc.returncode == 0 and separator and status.isdigit() and int(status) < 400:
            try:
                return json.loads(raw)
            except json.JSONDecodeError as exc:
                raise RuntimeError("Apollo returned non-JSON response") from exc
        last_error = "curl=%s http=%s %s" % (proc.returncode, status, (proc.stderr or raw)[-400:])
        if proc.returncode == 0 and status not in {"429", "500", "502", "503", "504"}:
            break
        time.sleep(min(15, 1.5 * (2 ** attempt)) + random.random())
    raise RuntimeError(last_error)


def credit_stats(key: str) -> dict[str, Any]:
    data = curl_json("POST", APOLLO_BASE + "/usage_stats/credit_usage_stats", key, {})
    lead = (data.get("credit_usage_stats") or {}).get("lead_credit") or {}
    return {
        "limit": int(lead.get("limit") or 0),
        "consumed": int(lead.get("consumed") or 0),
        "left_over": int(lead.get("left_over") or 0),
        "checked_at": stamp(),
    }


def batch_url() -> str:
    query = urllib.parse.urlencode({
        "reveal_personal_emails": "true",
        "reveal_phone_number": "false",
        "run_waterfall_email": "false",
        "run_waterfall_phone": "false",
    })
    return APOLLO_BASE + "/people/bulk_match?" + query


def explicit_personal_emails(value: Any) -> list[str]:
    """Read a value from an explicit Apollo personal-email field only."""
    found: list[str] = []
    if isinstance(value, str):
        candidate = value.strip().lower()
        if EMAIL.fullmatch(candidate):
            found.append(candidate)
    elif isinstance(value, list):
        for item in value:
            found.extend(explicit_personal_emails(item))
    elif isinstance(value, dict):
        for key in ("email", "value", "address"):
            if key in value:
                found.extend(explicit_personal_emails(value[key]))
    return list(dict.fromkeys(found))


def extract_personal_email(person: dict[str, Any]) -> str:
    found: list[str] = []
    for field in PERSONAL_FIELDS:
        if field in person:
            found.extend(explicit_personal_emails(person[field]))
    return next(iter(dict.fromkeys(found)), "")


def match_map(reply: dict[str, Any]) -> dict[str, dict[str, Any]]:
    rows = reply.get("matches") or reply.get("people") or []
    return {
        str(row.get("id") or row.get("apollo_person_id") or ""): row
        for row in rows if isinstance(row, dict) and (row.get("id") or row.get("apollo_person_id"))
    }


def result(candidate: dict[str, Any], person: dict[str, Any] | None) -> dict[str, Any]:
    base = {
        "selectionRank": candidate["selectionRank"],
        "engineerId": candidate["engineerId"],
        "apolloPersonId": candidate["apolloPersonId"],
        "name": candidate.get("name", ""),
        "companyNameEn": candidate.get("companyNameEn", ""),
        "companyCountry": candidate.get("companyCountry", ""),
        "jobTitle": candidate.get("jobTitle", ""),
        "matchedTechnicalAreaZh": candidate.get("matchedTechnicalAreaZh", ""),
        "priorityTheme": candidate.get("priorityTheme", ""),
        "retrievedAt": stamp(),
    }
    if person is None:
        return {**base, "status": "NO_MATCH", "personalEmail": ""}
    personal = extract_personal_email(person)
    return {
        **base,
        "status": "PERSONAL_EMAIL_FOUND" if personal else "NO_PERSONAL_EMAIL",
        "personalEmail": personal,
    }


def build_state(shortlist: list[dict[str, Any]]) -> dict[str, Any]:
    return {
        "metadata": {
            "createdAt": stamp(),
            "policy": "Personal email only. Generic/company email fields are never read or saved. No phone, waterfall, ES import, or email sending.",
            "apolloRequest": {
                "reveal_personal_emails": True,
                "reveal_phone_number": False,
                "run_waterfall_email": False,
                "run_waterfall_phone": False,
            },
        },
        "selection": shortlist,
        "results": {},
        "batches": [],
    }


def update_summary(state: dict[str, Any]) -> None:
    rows = list(state["results"].values())
    state["summary"] = {
        "approved": len(state["selection"]),
        "processed": len(rows),
        "personalEmailFound": sum(row["status"] == "PERSONAL_EMAIL_FOUND" for row in rows),
        "noPersonalEmail": sum(row["status"] == "NO_PERSONAL_EMAIL" for row in rows),
        "noMatch": sum(row["status"] == "NO_MATCH" for row in rows),
        "apolloCompanyEmailsStored": 0,
        "esImports": 0,
        "emailsSent": 0,
    }


def run(args: argparse.Namespace) -> dict[str, Any]:
    raw_shortlist = json.loads(Path(args.input).read_text(encoding="utf-8"))
    if not isinstance(raw_shortlist, list) or not raw_shortlist:
        raise RuntimeError("shortlist must be a non-empty JSON array")
    shortlist = [dict(row, selectionRank=index) for index, row in enumerate(raw_shortlist, 1)]
    ids = [str(row.get("apolloPersonId") or "") for row in shortlist]
    if len(ids) != len(set(ids)) or any(not value for value in ids):
        raise RuntimeError("shortlist contains missing or duplicate Apollo person IDs")
    output = Path(args.output)
    if output.exists():
        state = json.loads(output.read_text(encoding="utf-8"))
        old_ids = [row["apolloPersonId"] for row in state.get("selection", [])]
        if old_ids != ids:
            raise RuntimeError("selection changed; refusing to reuse checkpoint")
    else:
        state = build_state(shortlist)
        update_summary(state)
        atomic_json(output, state)
    if not args.execute:
        return state

    key = api_key(args.keys)
    if not key:
        raise RuntimeError("APOLLO_API_KEY is unavailable")
    pending = [row for row in shortlist if row["apolloPersonId"] not in state["results"]]
    before = credit_stats(key)
    state["creditBefore"] = state.get("creditBefore") or before
    if before["left_over"] < len(pending):
        state["stoppedReason"] = "INSUFFICIENT_CREDITS_FOR_APPROVED_LIST"
        state["creditAfter"] = before
        update_summary(state)
        atomic_json(output, state)
        return state
    current = before
    while pending:
        if current["left_over"] <= 0:
            state["stoppedReason"] = "CREDITS_DEPLETED"
            break
        batch = pending[:10]
        reply = curl_json("POST", batch_url(), key, {"details": [{"id": row["apolloPersonId"]} for row in batch]})
        matches = match_map(reply)
        for candidate in batch:
            state["results"][candidate["apolloPersonId"]] = result(candidate, matches.get(candidate["apolloPersonId"]))
        after = credit_stats(key)
        state["creditAfter"] = after
        state["batches"].append({
            "completedAt": stamp(),
            "size": len(batch),
            "leftBefore": current["left_over"],
            "leftAfter": after["left_over"],
            "creditsObserved": current["left_over"] - after["left_over"],
        })
        update_summary(state)
        atomic_json(output, state)
        current = after
        pending = [row for row in shortlist if row["apolloPersonId"] not in state["results"]]
        print(json.dumps({"summary": state["summary"], "creditAfter": after}, ensure_ascii=False), flush=True)
        time.sleep(0.2)
    state["creditAfter"] = credit_stats(key)
    update_summary(state)
    atomic_json(output, state)
    return state


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", required=True)
    parser.add_argument("--output", required=True)
    parser.add_argument("--keys", help="properties file containing APOLLO_API_KEY")
    parser.add_argument("--execute", action="store_true")
    args = parser.parse_args()
    state = run(args)
    print(json.dumps(state["summary"], ensure_ascii=False))


if __name__ == "__main__":
    main()
