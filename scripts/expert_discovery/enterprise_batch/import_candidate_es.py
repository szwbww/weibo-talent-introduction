#!/usr/bin/env python3
"""Prepare, import and verify Apollo enterprise R&D experts in ES CANDIDATE."""

import argparse
import base64
import hashlib
import json
import os
import re
import ssl
import urllib.error
import urllib.request
from collections import Counter, defaultdict
from datetime import datetime, timezone
from pathlib import Path

from research_field_normalization import assert_english_research_field, primary_research_field


ROOT = Path(__file__).resolve().parents[3]
DEFAULT_INPUT_DIR = ROOT / "outputs/enterprise-engineer-email-availability-20260913"
DEFAULT_OUTPUT_DIR = ROOT / "outputs/enterprise-engineer-es-import-20260914"
EMAIL_RE = re.compile(r"^[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}$", re.I)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input-dir", type=Path, default=DEFAULT_INPUT_DIR)
    parser.add_argument("--output-dir", type=Path, default=DEFAULT_OUTPUT_DIR)
    parser.add_argument("--config", type=Path, default=ROOT / "config/application-local.yml")
    parser.add_argument("--prepared-documents", type=Path)
    parser.add_argument("--index", default="orcid_info_candidate")
    parser.add_argument("--execute", action="store_true")
    parser.add_argument("--limit", type=int)
    return parser.parse_args()


def load_json(path: Path):
    with path.open(encoding="utf-8") as stream:
        return json.load(stream)


def split_name(name):
    parts = name.strip().split()
    if len(parts) < 2:
        single = parts[0] if parts else ""
        return single, single
    return " ".join(parts[:-1]), parts[-1]


def compact_unique(values, limit=12):
    seen = set()
    result = []
    for value in values:
        value = (value or "").strip()
        if value and value not in seen:
            seen.add(value)
            result.append(value)
            if len(result) == limit:
                break
    return result


def stable_email_id(email):
    return "EMAIL-" + email.lower()


def fingerprint(*values):
    return hashlib.sha256("\0".join(values).encode("utf-8")).hexdigest()


def es_date(value, fallback):
    if not value:
        return fallback
    try:
        return datetime.fromisoformat(value.replace("Z", "+00:00")).strftime("%Y-%m-%d %H:%M:%S")
    except ValueError:
        return fallback


def prepare_documents(input_dir, limit):
    enrichment = load_json(input_dir / "apollo_email_enrichment.json")
    directory = load_json(input_dir / "engineer_directory.json")
    selection = {row["engineer_id"]: row for row in enrichment["selection"]}

    relations = defaultdict(list)
    for row in directory["enterprise_engineer_relations"]:
        relations[row["engineer_id"]].append(row)

    benchmark_relations = defaultdict(list)
    for row in directory["benchmark_engineer_relations"]:
        benchmark_relations[row["engineer_id"]].append(row)

    now = datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M:%S")
    docs = []
    rejected = []
    seen_emails = set()
    rows = sorted(enrichment["results"].values(), key=lambda row: row["selection_rank"])
    for row in rows:
        if row.get("status") != "EMAIL_FOUND":
            rejected.append({"engineer_id": row.get("engineer_id"), "reason": row.get("status")})
            continue
        email = (row.get("email") or "").strip().lower()
        name = (row.get("name") or "").strip()
        if not EMAIL_RE.fullmatch(email):
            rejected.append({"engineer_id": row.get("engineer_id"), "reason": "INVALID_EMAIL_FORMAT"})
            continue
        if email in seen_emails:
            rejected.append({"engineer_id": row.get("engineer_id"), "reason": "DUPLICATE_EMAIL"})
            continue
        if not name or "***" in name:
            rejected.append({"engineer_id": row.get("engineer_id"), "reason": "INCOMPLETE_NAME"})
            continue
        seen_emails.add(email)

        engineer_id = row["engineer_id"]
        selected = selection.get(engineer_id, {})
        title = (row.get("title") or selected.get("title") or "").strip()
        organization = (row.get("current_organization") or selected.get("current_organization") or "").strip()
        technical_areas = compact_unique(r.get("matched_technical_area") for r in relations[engineer_id])
        research_fields, _ = primary_research_field(technical_areas, title)
        assert_english_research_field(research_fields)
        given, family = split_name(name)
        production_score = max(50, min(100, int(selected.get("selection_title_score") or 50)))
        evidence = ["APOLLO_VERIFIED_WORK_EMAIL", "BENCHMARK_COMPANY_EMPLOYMENT"]
        if title:
            evidence.append("CURATED_ENTERPRISE_RND_TITLE")
        if relations[engineer_id]:
            evidence.append("DOMESTIC_TECHNICAL_NEED_MATCH")

        doc = {
            "orcidId": stable_email_id(email),
            "id": engineer_id,
            "email": email,
            "givenNames": given,
            "familyNames": family,
            "country": (selected.get("country") or "").strip() or None,
            "keyword": title or research_fields,
            "employment": f"{title} at {organization}" if title and organization else title or organization,
            "institution": organization or None,
            "researchFields": research_fields,
            "emailSource": "APOLLO_NATIVE_WORK",
            "emailVerifiedLevel": 3,
            "dataSource": "APOLLO_ENTERPRISE_RND",
            "externalIds": {
                "apolloPersonId": row.get("apollo_person_id"),
                "linkedinUrl": selected.get("linkedin_url"),
                "engineerId": engineer_id,
                "domesticEnterpriseRelationIds": [r["enterprise_engineer_rel_id"] for r in relations[engineer_id]],
                "benchmarkRelationIds": [r["benchmark_engineer_rel_id"] for r in benchmark_relations[engineer_id]],
            },
            "candidateValidatedAt": now,
            "discoveredAt": es_date(row.get("retrieved_at"), now),
            "updatedAt": now,
            "filterResult": "PASS",
            "tags": ["企业研发专家", "Apollo已验证工作邮箱", f"高价值-{row.get('high_value_tier', 'B')}"],
            "enrichedAt": es_date(row.get("retrieved_at"), now),
            "enrichmentSource": "APOLLO_PEOPLE_MATCH",
            "expertClassification": {
                "type": "PRODUCTION_RND",
                "productionScore": production_score,
                "researchScore": 0,
                "positiveEvidence": evidence,
                "negativeEvidence": [],
                "version": "apollo-enterprise-rnd-v1",
                "sourceFingerprint": fingerprint(title, organization, research_fields, email),
                "classifiedAt": now,
            },
        }
        doc = {key: value for key, value in doc.items() if value not in (None, "", [])}
        docs.append({"_id": stable_email_id(email), "_source": doc})
        if limit and len(docs) >= limit:
            break
    return docs, rejected


def read_es_config(path: Path):
    if os.environ.get("ES_PASSWORD"):
        return {
            "base-url": os.environ.get("ES_BASE_URL", "https://es-fcxvip4d.public.tencentelasticsearch.com:9200"),
            "username": os.environ.get("ES_USERNAME", "elastic"),
            "password": os.environ["ES_PASSWORD"],
        }
    text = path.read_text(encoding="utf-8")
    section = text.split("elasticsearch:", 1)[1]
    values = {}
    for key in ("base-url", "username", "password"):
        match = re.search(rf"^\s+{re.escape(key)}:\s*(.+?)\s*$", section, re.M)
        if not match:
            raise RuntimeError(f"Missing Elasticsearch {key} in {path}")
        values[key] = match.group(1).strip("'\"")
    return values


class EsClient:
    def __init__(self, config):
        self.base_url = config["base-url"].rstrip("/")
        token = base64.b64encode(f'{config["username"]}:{config["password"]}'.encode()).decode()
        self.auth = f"Basic {token}"
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
            raise RuntimeError(f"ES HTTP {exc.code}: {exc.read().decode(errors='replace')[:1000]}") from exc


def lookup_existing(client: EsClient, index: str, docs):
    emails = [item["_source"]["email"] for item in docs]
    body = {"size": min(1000, len(emails)), "_source": ["email", "orcidId", "dataSource"],
            "query": {"terms": {"email": emails}}}
    response = client.request("POST", f"/{index}/_search", body)
    return response.get("hits", {}).get("hits", [])


def bulk_index(client: EsClient, index: str, docs):
    lines = []
    for item in docs:
        lines.append(json.dumps({"update": {"_index": index, "_id": item["_id"]}}, ensure_ascii=False))
        lines.append(json.dumps({"doc": item["_source"], "doc_as_upsert": True}, ensure_ascii=False))
    response = client.request("POST", "/_bulk?refresh=wait_for", ("\n".join(lines) + "\n").encode(), "application/x-ndjson")
    failures = []
    counts = Counter()
    for item in response.get("items", []):
        result = item["update"]
        counts[str(result.get("status"))] += 1
        if int(result.get("status", 500)) >= 300:
            failures.append({"id": result.get("_id"), "status": result.get("status"), "error": result.get("error")})
    return counts, failures


def verify(client: EsClient, index: str, docs):
    ids = [item["_id"] for item in docs]
    body = {
        "size": 0,
        "track_total_hits": True,
        "query": {"bool": {"filter": [
            {"ids": {"values": ids}},
            {"exists": {"field": "email"}},
            {"exists": {"field": "researchFields"}},
            {"term": {"expertClassification.type": "PRODUCTION_RND"}},
        ], "must_not": [{"exists": {"field": "operatorStatus"}}]}},
    }
    response = client.request("POST", f"/{index}/_search", body)
    return response.get("hits", {}).get("total", {}).get("value", 0)


def main():
    args = parse_args()
    args.output_dir.mkdir(parents=True, exist_ok=True)
    if args.prepared_documents:
        docs = load_json(args.prepared_documents)
        docs = docs[:args.limit] if args.limit else docs
        rejected = []
    else:
        docs, rejected = prepare_documents(args.input_dir, args.limit)
    for item in docs:
        assert_english_research_field(item["_source"].get("researchFields", ""))
    payload_path = args.output_dir / "candidate_documents.json"
    payload_path.write_text(json.dumps(docs, ensure_ascii=False, indent=2), encoding="utf-8")
    (args.output_dir / "rejected.json").write_text(json.dumps(rejected, ensure_ascii=False, indent=2), encoding="utf-8")

    report = {"prepared": len(docs), "rejected": len(rejected), "executed": False}
    if args.execute:
        client = EsClient(read_es_config(args.config))
        existing = lookup_existing(client, args.index, docs)
        (args.output_dir / "existing_before_import.json").write_text(
            json.dumps(existing, ensure_ascii=False, indent=2), encoding="utf-8"
        )
        existing_by_email = defaultdict(list)
        for hit in existing:
            existing_by_email[(hit.get("_source", {}).get("email") or "").lower()].append(hit.get("_id"))
        conflicts = []
        writable = []
        for item in docs:
            other_ids = [doc_id for doc_id in existing_by_email[item["_source"]["email"]] if doc_id != item["_id"]]
            if other_ids:
                conflicts.append({"id": item["_id"], "email": item["_source"]["email"], "existing_ids": other_ids})
            else:
                writable.append(item)
        (args.output_dir / "existing_email_conflicts.json").write_text(
            json.dumps(conflicts, ensure_ascii=False, indent=2), encoding="utf-8"
        )
        counts, failures = bulk_index(client, args.index, writable)
        eligible = verify(client, args.index, writable)
        report.update({
            "executed": True,
            "existing_before_import": len(existing),
            "existing_email_conflicts": len(conflicts),
            "writable": len(writable),
            "bulk_status_counts": dict(counts),
            "bulk_failures": len(failures),
            "eligible_by_intrinsic_batch_gate": eligible,
        })
        (args.output_dir / "bulk_failures.json").write_text(
            json.dumps(failures, ensure_ascii=False, indent=2), encoding="utf-8"
        )
    (args.output_dir / "report.json").write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(report, ensure_ascii=False))


if __name__ == "__main__":
    main()
