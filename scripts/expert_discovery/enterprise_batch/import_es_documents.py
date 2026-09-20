#!/usr/bin/env python3
"""Safely upsert prevalidated candidate documents into an ES index.

Input documents must already satisfy the caller's eligibility rules.  Existing
records with the same email but a different document id are reported and left
unchanged.  This utility never sends email.
"""

import argparse
import base64
import json
import os
import re
import ssl
import urllib.error
import urllib.request
from collections import Counter, defaultdict
from pathlib import Path


def read_es_config(path):
    text = path.read_text(encoding="utf-8")
    section = text.split("elasticsearch:", 1)[1]
    values = {}
    for key in ("base-url", "username", "password"):
        match = re.search(r"^\s+" + re.escape(key) + r":\s*(.+?)\s*$", section, re.M)
        if not match:
            raise RuntimeError("Missing Elasticsearch " + key + " in " + str(path))
        raw_value = match.group(1).strip("'\"")
        placeholder = re.fullmatch(r"\$\{([^:}]+)(?::([^}]*))?\}", raw_value)
        values[key] = os.environ.get(placeholder.group(1), placeholder.group(2) or "") if placeholder else raw_value
    return values


class EsClient:
    def __init__(self, config):
        self.base_url = config["base-url"].rstrip("/")
        token = base64.b64encode((config["username"] + ":" + config["password"]).encode()).decode()
        self.auth = "Basic " + token
        self.context = ssl.create_default_context()

    def request(self, method, path, body=None, content_type="application/json"):
        data = None if body is None else (body if isinstance(body, bytes) else json.dumps(body).encode())
        request = urllib.request.Request(
            self.base_url + path,
            data=data,
            method=method,
            headers={"Authorization": self.auth, "Content-Type": content_type},
        )
        try:
            with urllib.request.urlopen(request, context=self.context, timeout=45) as response:
                payload = response.read()
                return json.loads(payload) if payload else {}
        except urllib.error.HTTPError as exc:
            raise RuntimeError("ES HTTP {}: {}".format(exc.code, exc.read().decode(errors="replace")[:1000]))


def lookup_existing(client, index, docs):
    emails = [item["_source"]["email"] for item in docs]
    return client.request("POST", "/{}/_search".format(index), {
        "size": min(1000, len(emails)),
        "_source": ["email", "orcidId", "dataSource"],
        "query": {"terms": {"email": emails}},
    }).get("hits", {}).get("hits", [])


def bulk_index(client, index, docs):
    lines = []
    for item in docs:
        lines.append(json.dumps({"update": {"_index": index, "_id": item["_id"]}}, ensure_ascii=False))
        lines.append(json.dumps({"doc": item["_source"], "doc_as_upsert": True}, ensure_ascii=False))
    if not lines:
        return Counter(), []
    response = client.request("POST", "/_bulk?refresh=wait_for", ("\n".join(lines) + "\n").encode(), "application/x-ndjson")
    counts, failures = Counter(), []
    for item in response.get("items", []):
        result = item["update"]
        counts[str(result.get("status"))] += 1
        if int(result.get("status", 500)) >= 300:
            failures.append({"id": result.get("_id"), "status": result.get("status"), "error": result.get("error")})
    return counts, failures


def args():
    parser = argparse.ArgumentParser()
    parser.add_argument("--documents", type=Path, required=True)
    parser.add_argument("--config", type=Path, required=True)
    parser.add_argument("--index", default="orcid_info_candidate")
    parser.add_argument("--output-dir", type=Path, required=True)
    parser.add_argument("--repair-not-contacted", action="store_true")
    parser.add_argument("--verify-not-contacted", action="store_true")
    return parser.parse_args()


def main():
    options = args()
    documents = json.loads(options.documents.read_text(encoding="utf-8"))
    options.output_dir.mkdir(parents=True, exist_ok=True)
    client = EsClient(read_es_config(options.config))
    repaired = 0
    if options.repair_not_contacted:
        response = client.request("POST", "/{}/_update_by_query?refresh=true&conflicts=proceed".format(options.index), {
            "query": {"ids": {"values": [item["_id"] for item in documents]}},
            "script": {
                "lang": "painless",
                "source": "if (ctx._source.operatorStatus == 'NOT_CONTACTED') { ctx._source.remove('operatorStatus'); }",
            },
        })
        repaired = int(response.get("updated", 0))
    existing = lookup_existing(client, options.index, documents)
    by_email = defaultdict(list)
    for hit in existing:
        by_email[str(hit.get("_source", {}).get("email") or "").lower()].append(hit.get("_id"))
    conflicts, writable = [], []
    for item in documents:
        email = item["_source"]["email"].lower()
        existing_ids = [record_id for record_id in by_email[email] if record_id != item["_id"]]
        if existing_ids:
            conflicts.append({"id": item["_id"], "existingIds": existing_ids})
        else:
            writable.append(item)
    status_counts, failures = bulk_index(client, options.index, writable)
    verified_not_contacted = None
    if options.verify_not_contacted:
        response = client.request("POST", "/{}/_count".format(options.index), {
            "query": {"bool": {
                "filter": [
                    {"ids": {"values": [item["_id"] for item in documents]}},
                    {"exists": {"field": "email"}},
                    {"exists": {"field": "researchFields"}},
                    {"terms": {"tags": ["Apollo已验证个人邮箱"]}},
                    {"terms": {"expertClassification.type": ["PRODUCTION_RND", "HYBRID_RND", "ACADEMIC_RND"]}},
                ],
                "must_not": [{"exists": {"field": "operatorStatus"}}],
            }},
        })
        verified_not_contacted = int(response.get("count", 0))
    report = {
        "received": len(documents),
        "writable": len(writable),
        "existingEmailConflicts": len(conflicts),
        "bulkStatusCounts": dict(Counter(status_counts)),
        "bulkFailures": len(failures),
        "notContactedFieldsRemoved": repaired,
        "verifiedNotContactedScope": verified_not_contacted,
        "emailsSent": 0,
    }
    (options.output_dir / "existing_email_conflicts.json").write_text(json.dumps(conflicts, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    (options.output_dir / "bulk_failures.json").write_text(json.dumps(failures, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    (options.output_dir / "report.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, ensure_ascii=False))


if __name__ == "__main__":
    main()
