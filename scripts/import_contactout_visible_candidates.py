#!/usr/bin/env python3
"""Prepare and optionally import manually revealed ContactOut candidates to ES.

This importer never creates ExpertContact records, aliases, mail records, or
outbound messages.  It writes only ES CANDIDATE documents, tagged exactly
``ConcatOut``.  All non-primary displayed emails are retained as pending
aliases in ``externalIds`` for later, explicit contact creation.
"""

import argparse
import csv
import hashlib
import json
import re
import sys
from collections import Counter, defaultdict
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ENTERPRISE_BATCH = ROOT / "scripts" / "expert_discovery" / "enterprise_batch"
sys.path.insert(0, str(ENTERPRISE_BATCH))

from import_candidate_es import EsClient, bulk_index, read_es_config


EMAIL_RE = re.compile(r"^[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}$", re.I)
PERSONAL_DOMAINS = {
    "gmail.com", "yahoo.com", "yahoo.co.in", "hotmail.com", "outlook.com",
    "live.com", "me.com", "icloud.com", "comcast.net", "aol.com",
    "proton.me", "protonmail.com",
}
ALLOWED_TYPES = {"PRODUCTION_RND", "HYBRID_RND", "ACADEMIC_RND"}
SOURCE_TAG = "ConcatOut"


def parse_args():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--csv", type=Path, required=True)
    parser.add_argument("--output-dir", type=Path,
                        default=ROOT / "outputs/contactout-es-import")
    parser.add_argument("--config", type=Path,
                        default=ROOT / "config/application-local.yml")
    parser.add_argument("--index", default="orcid_info_candidate")
    parser.add_argument("--check-online", action="store_true",
                        help="Read ES for duplicate emails, without writing.")
    parser.add_argument("--execute", action="store_true")
    return parser.parse_args()


def clean(value):
    return str(value or "").strip()


def normalized_emails(value):
    seen, result = set(), []
    for part in re.split(r"[;,\n]", clean(value)):
        email = part.strip().lower()
        if EMAIL_RE.fullmatch(email) and email not in seen:
            seen.add(email)
            result.append(email)
    return result


def domain(email):
    return email.rsplit("@", 1)[-1].lower()


def choose_primary(emails):
    personal = [email for email in emails if domain(email) in PERSONAL_DOMAINS]
    return (personal[0] if personal else emails[0]), bool(personal)


def split_name(name):
    parts = clean(name).split()
    if len(parts) <= 1:
        return clean(name), ""
    return " ".join(parts[:-1]), parts[-1]


def es_date(value, fallback):
    value = clean(value)
    if not value:
        return fallback
    try:
        return datetime.fromisoformat(value.replace("Z", "+00:00")).strftime("%Y-%m-%d %H:%M:%S")
    except ValueError:
        return fallback


def research_field(title, profile):
    text = (title + " " + profile).lower()
    if any(word in text for word in ("semiconductor", "wafer", "plasma", "thin film", "ald", "cvd", "etch", "metrology")):
        return "Semiconductor Process Technology"
    if any(word in text for word in ("algorithm", "computer vision", "machine learning", "pytorch", "artificial intelligence")):
        return "Engineering Algorithms and Artificial Intelligence"
    if any(word in text for word in ("cloud", "azure", "devops", "microservice", "software", "integration")):
        return "Software and Cloud Engineering"
    if any(word in text for word in ("robot", "mechanical", "ceramic", "reliability", "quality", "product")):
        return "Engineering Design and Product Reliability"
    if "research fellow" in text or "postdoc" in text:
        return "Applied Scientific Research"
    return "Engineering and Technology"


def classification_type(title, profile):
    text = (title + " " + profile).lower()
    if "research fellow" in text or "postdoc" in text:
        return "ACADEMIC_RND"
    return "HYBRID_RND"


def stable_id(row):
    key = "\0".join((clean(row.get("linkedin")), clean(row.get("name")), clean(row.get("company"))))
    return "CONTACTOUT-" + hashlib.sha256(key.encode("utf-8")).hexdigest()[:24]


def prepare(csv_path):
    now = datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M:%S")
    docs, rejected, all_seen = [], [], set()
    with csv_path.open(encoding="utf-8-sig", newline="") as stream:
        rows = list(csv.DictReader(stream))
    for row_number, row in enumerate(rows, start=2):
        name = clean(row.get("name"))
        company = clean(row.get("company"))
        title = clean(row.get("jobTitle"))
        emails = normalized_emails(row.get("emails"))
        if not name or not company or not title or not emails:
            rejected.append({"row": row_number, "name": name, "reason": "MISSING_REQUIRED_VISIBLE_DATA"})
            continue
        doc_id = stable_id(row)
        if doc_id in all_seen:
            rejected.append({"row": row_number, "name": name, "reason": "DUPLICATE_PERSON"})
            continue
        all_seen.add(doc_id)
        primary, personal_primary = choose_primary(emails)
        given, family = split_name(name)
        profile = clean(row.get("profileText"))
        field = research_field(title, profile)
        classification = classification_type(title, profile)
        assert classification in ALLOWED_TYPES
        aliases = [email for email in emails if email != primary]
        source = {
            "orcidId": doc_id,
            "id": doc_id,
            "email": primary,
            "givenNames": given,
            "familyNames": family,
            "country": clean(row.get("location")) or None,
            "keyword": title,
            "employment": f"{title} at {company}",
            "institution": company,
            "researchFields": field,
            "emailSource": "CONTACTOUT_VISIBLE",
            "emailVerifiedLevel": 1,
            "dataSource": "CONTACTOUT_VISIBLE",
            "externalIds": {
                "contactoutLinkedinUrl": clean(row.get("linkedin")) or None,
                "contactoutSourceUrl": clean(row.get("sourceUrl")) or None,
                "contactoutCapturedAt": clean(row.get("capturedAt")) or None,
                "contactoutVisibleEmails": emails,
                "pendingEmailAliases": aliases,
                "primaryEmailIsPersonalDomain": personal_primary,
            },
            "candidateValidatedAt": now,
            "discoveredAt": es_date(row.get("firstCapturedAt"), now),
            "updatedAt": now,
            "filterResult": "PASS",
            "funnelLevel": "CANDIDATE",
            "tags": [SOURCE_TAG],
            "enrichedAt": now,
            "enrichmentSource": "CONTACTOUT_MANUAL_VISIBLE_EXPORT",
            "expertClassification": {
                "type": classification,
                "productionScore": 70,
                "researchScore": 0,
                "positiveEvidence": ["CONTACTOUT_VISIBLE_EMAIL", "CURRENT_OR_LISTED_SENIOR_TECHNICAL_TITLE"],
                "negativeEvidence": [],
                "version": "contactout-visible-v1",
                "sourceFingerprint": hashlib.sha256((name + "\0" + title + "\0" + company).encode("utf-8")).hexdigest(),
                "classifiedAt": now,
            },
        }
        source = {key: value for key, value in source.items() if value not in (None, "", [])}
        docs.append({"_id": doc_id, "_source": source})
    return docs, rejected


def find_email_conflicts(client, index, docs):
    all_emails = sorted({email for doc in docs for email in doc["_source"]["externalIds"]["contactoutVisibleEmails"]})
    response = client.request("POST", f"/{index}/_search", {
        "size": min(1000, len(all_emails)),
        "_source": ["email", "orcidId"],
        "query": {"terms": {"email": all_emails}},
    })
    by_email = defaultdict(list)
    for hit in response.get("hits", {}).get("hits", []):
        email = clean((hit.get("_source") or {}).get("email")).lower()
        if email:
            by_email[email].append(hit.get("_id"))
    return {email: ids for email, ids in by_email.items()}


def verify(client, index, docs):
    response = client.request("POST", f"/{index}/_mget", {
        "docs": [{"_id": doc["_id"], "_source": ["email", "researchFields", "funnelLevel", "tags", "operatorStatus", "expertClassification.type"]} for doc in docs]
    })
    failures = []
    for doc in response.get("docs", []):
        source = doc.get("_source") or {}
        if (not doc.get("found") or not EMAIL_RE.fullmatch(clean(source.get("email")))
                or not clean(source.get("researchFields"))
                or source.get("funnelLevel") != "CANDIDATE"
                or source.get("tags") != [SOURCE_TAG]
                or clean(source.get("operatorStatus"))
                or (source.get("expertClassification") or {}).get("type") not in ALLOWED_TYPES):
            failures.append(doc.get("_id"))
    return failures


def main():
    args = parse_args()
    args.output_dir.mkdir(parents=True, exist_ok=True)
    docs, rejected = prepare(args.csv)
    (args.output_dir / "candidate_documents.json").write_text(json.dumps(docs, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    (args.output_dir / "rejected.json").write_text(json.dumps(rejected, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    report = {"prepared": len(docs), "rejected": len(rejected), "executed": False, "esImports": 0, "emailsSent": 0, "tag": SOURCE_TAG}
    if args.check_online or args.execute:
        client = EsClient(read_es_config(args.config))
        conflicts = find_email_conflicts(client, args.index, docs)
        writable = [doc for doc in docs if not any(email in conflicts for email in doc["_source"]["externalIds"]["contactoutVisibleEmails"])]
        skipped = [doc["_id"] for doc in docs if doc not in writable]
        (args.output_dir / "existing_email_conflicts.json").write_text(json.dumps(conflicts, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        report.update({
            "existingEmailConflicts": len(conflicts),
            "skippedExisting": len(skipped),
            "writable": len(writable),
        })
    if args.execute:
        status_counts, failures = bulk_index(client, args.index, writable)
        verification_failures = verify(client, args.index, writable)
        (args.output_dir / "bulk_failures.json").write_text(json.dumps(failures, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        (args.output_dir / "verification_failures.json").write_text(json.dumps(verification_failures, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        report.update({
            "executed": True,
            "bulkStatusCounts": dict(Counter(status_counts)),
            "bulkFailures": len(failures),
            "verifiedHardGate": len(writable) - len(verification_failures),
            "verificationFailures": len(verification_failures),
            "esImports": len(writable),
        })
    (args.output_dir / "report.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, ensure_ascii=False))


if __name__ == "__main__":
    main()
