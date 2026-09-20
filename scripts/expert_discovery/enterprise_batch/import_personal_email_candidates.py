#!/usr/bin/env python3
"""Import Apollo-returned personal-email experts into the ES CANDIDATE index.

Only records with an explicitly returned personal email are eligible.  No work
email is read from either Apollo result file.  This script does not send mail.
"""

import argparse
import hashlib
import json
import re
from collections import Counter, defaultdict
from datetime import datetime, timezone
from pathlib import Path

from import_candidate_es import EsClient, bulk_index, lookup_existing, read_es_config
from research_field_normalization import assert_english_research_field, primary_research_field


ROOT = Path(__file__).resolve().parents[3]
EMAIL_RE = re.compile(r"^[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}$", re.I)
CJK_RE = re.compile(r"[\u3400-\u9fff]")
COUNTRY_EN = {
    "美国": "United States", "加拿大": "Canada", "日本": "Japan", "韩国": "South Korea",
    "中国台湾": "Taiwan", "新加坡": "Singapore", "以色列": "Israel",
}
PERSONAL_TAG = "Apollo已验证个人邮箱"


def args():
    parser = argparse.ArgumentParser()
    parser.add_argument("--output-dir", type=Path, default=ROOT / "outputs/personal-email-es-import-20260916")
    parser.add_argument("--config", type=Path, default=ROOT / "config/application-local.yml")
    parser.add_argument("--index", default="orcid_info_candidate")
    parser.add_argument("--execute", action="store_true")
    return parser.parse_args()


def fingerprint(*values):
    return hashlib.sha256("\0".join(values).encode("utf-8")).hexdigest()


def result_rows():
    paths = (
        ROOT / "outputs/personal-email-enrichment-20260916/personal_email_results.json",
        ROOT / "outputs/personal-email-recharge-20260916/personal_email_results.json",
        ROOT / "outputs/personal-email-recharge-2-20260916/personal_email_results.json",
    )
    rows = []
    for path in paths:
        data = json.loads(path.read_text(encoding="utf-8"))
        rows.extend(row for row in (data.get("results") or {}).values() if row.get("status") == "PERSONAL_EMAIL_FOUND")
    return rows


def safe_name(name):
    """Avoid displaying Apollo's masked last name in a personalized mail."""
    parts = [part for part in (name or "").split() if part]
    if not parts:
        return "", ""
    given = parts[0].replace("*", "").strip()
    if any("*" in part for part in parts):
        return given, ""
    return " ".join(parts[:-1]), parts[-1] if len(parts) > 1 else ""


def es_date(value, fallback):
    if not value:
        return fallback
    try:
        return datetime.fromisoformat(str(value).replace("Z", "+00:00")).strftime("%Y-%m-%d %H:%M:%S")
    except ValueError:
        return fallback


def english_content(value, field, rejected, row):
    text = str(value or "").strip()
    if not text or CJK_RE.search(text):
        rejected.append({"engineerId": row.get("engineerId"), "reason": "INVALID_ENGLISH_" + field})
        return ""
    return text


def prepare():
    directory = json.loads((ROOT / "outputs/enterprise-rnd-filtered-20260914/engineer_directory.json").read_text(encoding="utf-8"))
    relations = defaultdict(list)
    for relation in directory.get("enterprise_engineer_relations", []):
        relations[relation.get("engineer_id")].append(relation)
    now = datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M:%S")
    docs, rejected, seen = [], [], set()
    for row in result_rows():
        email = str(row.get("personalEmail") or "").strip().lower()
        engineer_id = str(row.get("engineerId") or "")
        if not EMAIL_RE.fullmatch(email):
            rejected.append({"engineerId": engineer_id, "reason": "INVALID_PERSONAL_EMAIL"})
            continue
        if email in seen:
            rejected.append({"engineerId": engineer_id, "reason": "DUPLICATE_PERSONAL_EMAIL"})
            continue
        title = english_content(row.get("jobTitle"), "TITLE", rejected, row)
        company = english_content(row.get("companyNameEn"), "COMPANY", rejected, row)
        if not title or not company:
            continue
        field, field_source = primary_research_field([row.get("matchedTechnicalAreaZh", "")], title)
        try:
            assert_english_research_field(field)
        except ValueError:
            rejected.append({"engineerId": engineer_id, "reason": "INVALID_ENGLISH_RESEARCH_FIELD"})
            continue
        given, family = safe_name(row.get("name"))
        if not given or CJK_RE.search(given) or CJK_RE.search(family):
            rejected.append({"engineerId": engineer_id, "reason": "INVALID_NAME"})
            continue
        seen.add(email)
        linked_relations = relations.get(engineer_id, [])
        country = COUNTRY_EN.get(row.get("companyCountry"), row.get("companyCountry") or "")
        source = {
            "orcidId": "EMAIL-" + email,
            "id": engineer_id,
            "email": email,
            "givenNames": given,
            "familyNames": family,
            "country": country,
            "keyword": title,
            "employment": title + " at " + company,
            "institution": company,
            "researchFields": field,
            "emailSource": "APOLLO_PERSONAL_EMAIL",
            "emailVerifiedLevel": 2,
            "dataSource": "APOLLO_ENTERPRISE_RND",
            "externalIds": {
                "apolloPersonId": row.get("apolloPersonId"),
                "engineerId": engineer_id,
                "domesticEnterpriseRelationIds": [item.get("enterprise_engineer_rel_id") for item in linked_relations if item.get("enterprise_engineer_rel_id")],
            },
            "candidateValidatedAt": now,
            "discoveredAt": es_date(row.get("retrievedAt"), now),
            "updatedAt": now,
            "filterResult": "PASS",
            "funnelLevel": "CANDIDATE",
            "tags": ["企业研发专家", PERSONAL_TAG],
            "enrichedAt": es_date(row.get("retrievedAt"), now),
            "enrichmentSource": "APOLLO_PERSONAL_EMAIL_REVEAL",
            "expertClassification": {
                "type": "PRODUCTION_RND",
                "productionScore": max(50, min(100, int(row.get("titleScore") or 50))),
                "researchScore": 0,
                "positiveEvidence": [
                    "APOLLO_PERSONAL_EMAIL_REVEAL",
                    "BENCHMARK_COMPANY_EMPLOYMENT",
                    "CURATED_ENTERPRISE_RND_TITLE",
                    "DOMESTIC_TECHNICAL_NEED_MATCH",
                ],
                "negativeEvidence": [],
                "version": "apollo-personal-email-rnd-v1",
                "sourceFingerprint": fingerprint(title, company, field, email),
                "classifiedAt": now,
                "primaryResearchFieldSource": field_source,
            },
        }
        # Tags are an operator-facing classification label. Every outbound-content
        # field below is independently constrained to English.
        english_fields = ("givenNames", "familyNames", "country", "keyword", "employment", "institution", "researchFields")
        if any(CJK_RE.search(str(source.get(key) or "")) for key in english_fields):
            rejected.append({"engineerId": engineer_id, "reason": "CHINESE_IN_OUTBOUND_CONTENT"})
            continue
        docs.append({"_id": source["orcidId"], "_source": {key: value for key, value in source.items() if value not in (None, "", [])}})
    return docs, rejected


def verify(client, index, docs):
    response = client.request("POST", "/" + index + "/_mget", {
        "docs": [{"_id": item["_id"], "_source": ["email", "researchFields", "tags", "operatorStatus", "expertClassification.type", "dataSource"]} for item in docs]
    })
    failures = []
    for item in response.get("docs", []):
        source = item.get("_source") or {}
        if not item.get("found") or not EMAIL_RE.fullmatch(str(source.get("email") or "")):
            failures.append(item.get("_id"))
            continue
        assert_english_research_field(source.get("researchFields") or "")
        if source.get("operatorStatus") not in (None, "") or source.get("expertClassification", {}).get("type") != "PRODUCTION_RND" or PERSONAL_TAG not in (source.get("tags") or []):
            failures.append(item.get("_id"))
    return failures


def main():
    options = args()
    docs, rejected = prepare()
    options.output_dir.mkdir(parents=True, exist_ok=True)
    (options.output_dir / "candidate_documents.json").write_text(json.dumps(docs, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    (options.output_dir / "rejected.json").write_text(json.dumps(rejected, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    report = {"prepared": len(docs), "rejected": len(rejected), "executed": False, "esImports": 0, "emailsSent": 0}
    if options.execute:
        client = EsClient(read_es_config(options.config))
        existing = lookup_existing(client, options.index, docs)
        by_email = defaultdict(list)
        for hit in existing:
            by_email[str(hit.get("_source", {}).get("email") or "").lower()].append(hit.get("_id"))
        conflicts, writable = [], []
        for item in docs:
            current_ids = [item_id for item_id in by_email[item["_source"]["email"]] if item_id != item["_id"]]
            if current_ids:
                conflicts.append({"id": item["_id"], "existingIds": current_ids})
            else:
                writable.append(item)
        (options.output_dir / "existing_email_conflicts.json").write_text(json.dumps(conflicts, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        status_counts, failures = bulk_index(client, options.index, writable)
        verification_failures = verify(client, options.index, writable)
        (options.output_dir / "bulk_failures.json").write_text(json.dumps(failures, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        (options.output_dir / "verification_failures.json").write_text(json.dumps(verification_failures, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        report.update({
            "executed": True,
            "existingEmailConflicts": len(conflicts),
            "writable": len(writable),
            "bulkStatusCounts": dict(Counter(status_counts)),
            "bulkFailures": len(failures),
            "verifiedSendReady": len(writable) - len(verification_failures),
            "verificationFailures": len(verification_failures),
            "esImports": len(writable),
        })
    (options.output_dir / "report.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, ensure_ascii=False))


if __name__ == "__main__":
    main()
