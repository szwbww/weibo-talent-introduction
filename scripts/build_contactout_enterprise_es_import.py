#!/usr/bin/env python3
"""Build (and optionally import) reviewed ContactOut enterprise experts.

The review report is the selection authority: only people in its “高级生产技术／研发
初筛保留” section enter ES.  Tags are deliberately limited to ``ConcatOut`` and,
for the report's nine direct Debang matches, ``德邦材料``.  This program creates
only ES CANDIDATE documents; it never creates contacts, aliases, tasks, or mail.
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
sys.path.insert(0, str(ROOT / "scripts" / "expert_discovery" / "enterprise_batch"))

from import_candidate_es import EsClient, bulk_index, read_es_config


EMAIL_RE = re.compile(r"^[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}$", re.I)
LINKEDIN_RE = re.compile(r"\[[^]]+\]\((https?://(?:www\.)?linkedin\.com/in/[^)]+)\)", re.I)
PERIOD_RE = re.compile(r"(?:(?:\bin\s+)?(?:19|20)\d{2}\s*)?[-–—]\s*(?:present|(?:19|20)\d{2})\b", re.I)
AT_RE = re.compile(r"^(?P<title>.+?)\s+at\s+(?P<company>.+?)\s+" + PERIOD_RE.pattern, re.I)
PERSONAL_DOMAINS = {
    "gmail.com", "yahoo.com", "yahoo.co.in", "hotmail.com", "outlook.com",
    "live.com", "me.com", "icloud.com", "comcast.net", "aol.com",
    "proton.me", "protonmail.com",
}
SOURCE_TAG = "ConcatOut"
DEBANG_TAG = "德邦材料"


def args():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--csv", type=Path, action="append", default=[],
                        help="One or more ContactOut visible-email CSV exports")
    parser.add_argument("--review-report", type=Path)
    parser.add_argument("--prepared-documents", type=Path,
                        help="Previously validated candidate_documents.json; skips CSV preparation")
    parser.add_argument("--output-dir", type=Path,
                        default=ROOT / "outputs" / "contactout-enterprise-es-import")
    parser.add_argument("--config", type=Path,
                        default=ROOT / "config" / "application-local.yml")
    parser.add_argument("--index", default="orcid_info_candidate")
    parser.add_argument("--check-online", action="store_true")
    parser.add_argument("--execute", action="store_true")
    return parser.parse_args()


def clean(value):
    return str(value or "").strip()


def canonical_linkedin(value):
    return clean(value).lower().split("?", 1)[0].rstrip("/")


def normalized_emails(value):
    seen, result = set(), []
    for part in re.split(r"[;,\n]", clean(value)):
        email = part.strip().lower()
        if EMAIL_RE.fullmatch(email) and email not in seen:
            seen.add(email)
            result.append(email)
    return result


def choose_primary(emails):
    personal = [email for email in emails if email.rsplit("@", 1)[1] in PERSONAL_DOMAINS]
    return (personal[0] if personal else emails[0]), bool(personal)


def split_name(name):
    parts = clean(name).split()
    return (" ".join(parts[:-1]), parts[-1]) if len(parts) >= 2 else (clean(name), "")


def stable_doc_id(email):
    return "EMAIL-" + email.lower()


def es_date(value, fallback):
    """Return a timestamp compatible with the candidate-index date mapping."""
    value = clean(value)
    if not value:
        return fallback
    try:
        normalized = value.replace("T", " ").replace("Z", "").split("+", 1)[0].split(".", 1)[0]
        return datetime.strptime(normalized, "%Y-%m-%d %H:%M:%S").strftime("%Y-%m-%d %H:%M:%S")
    except (TypeError, ValueError):
        return fallback


def source_id(row):
    key = "\0".join((canonical_linkedin(row.get("linkedin")), clean(row.get("name")), clean(row.get("company"))))
    return "CONTACTOUT-" + hashlib.sha256(key.encode("utf-8")).hexdigest()[:24]


def links_after(text, heading, until=None):
    start = text.find(heading)
    if start < 0:
        raise ValueError(f"Review heading missing: {heading}")
    end = text.find(until, start) if until else -1
    section = text[start:] if end < 0 else text[start:end]
    return {canonical_linkedin(link) for link in LINKEDIN_RE.findall(section)}


def review_selection(report):
    text = report.read_text(encoding="utf-8")
    senior = links_after(text, "## 高级生产技术／研发初筛保留")
    debang = links_after(text, "## 德邦优先复核", "## 德邦相关方向储备")
    if not senior or not debang:
        raise ValueError("Review selection is empty")
    if not debang.issubset(senior):
        raise ValueError("Debang selection is not a subset of senior selection")
    return senior, debang


def read_rows(paths):
    rows = []
    for path in paths:
        with path.open(encoding="utf-8-sig", newline="") as stream:
            reader = csv.DictReader(stream)
            needed = {"name", "company", "emails", "linkedin", "profileText"}
            missing = needed - set(reader.fieldnames or [])
            if missing:
                raise ValueError(f"{path} missing columns: {', '.join(sorted(missing))}")
            for line, row in enumerate(reader, start=2):
                record = dict(row)
                record["_sourceFile"] = path.name
                record["_sourceLine"] = line
                rows.append(record)
    return rows


def merge_rows(rows):
    """Merge exported duplicates by LinkedIn; profiles and emails are unioned."""
    by_link = {}
    for row in rows:
        link = canonical_linkedin(row.get("linkedin"))
        if not link:
            raise ValueError(f"Missing LinkedIn URL: {row.get('_sourceFile')}:{row.get('_sourceLine')}")
        existing = by_link.get(link)
        if existing is None:
            by_link[link] = dict(row)
            continue
        existing["emails"] = ";".join(dict.fromkeys(normalized_emails(existing.get("emails")) + normalized_emails(row.get("emails"))))
        for key in ("profileText", "jobTitle", "company", "location", "sourceUrl", "capturedAt", "firstCapturedAt"):
            if len(clean(row.get(key))) > len(clean(existing.get(key))):
                existing[key] = row.get(key)
    return by_link


def employment_from_profile(profile, name):
    lines = [clean(line) for line in clean(profile).splitlines() if clean(line)]
    for index, line in enumerate(lines):
        inline = AT_RE.match(line)
        if inline:
            return clean(inline.group("title")), clean(inline.group("company"))
        if not index or not line.casefold().startswith("at"):
            continue
        # ContactOut may put title, “at”, company, and “- Present” on separate lines.
        company_parts = []
        inline_company = line[2:].strip() if line.casefold().startswith("at ") else ""
        candidates = ([inline_company] if inline_company else []) + lines[index + 1:index + 5]
        for candidate in candidates:
            end = PERIOD_RE.search(candidate)
            if end:
                company_parts.append(candidate[:end.start()])
                break
            company_parts.append(candidate)
        company = " ".join(part.strip(" -–—") for part in company_parts if part.strip(" -–—"))
        title_parts = []
        for candidate in reversed(lines[max(0, index - 4):index]):
            if candidate.casefold() == clean(name).casefold() or "," in candidate or len(candidate) <= 3:
                break
            title_parts.insert(0, candidate)
        if title_parts and company:
            return " ".join(title_parts), company
    return "", ""


def employment(row):
    title, company = clean(row.get("jobTitle")), clean(row.get("company"))
    if title and company:
        return f"{title} at {company}"
    extracted_title, extracted_company = employment_from_profile(row.get("profileText"), row.get("name"))
    title = title or extracted_title
    company = company or extracted_company
    return f"{title} at {company}" if title and company else ""


def build_documents(rows, senior_links, debang_links):
    now = datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M:%S")
    docs, rejected = [], []
    for link in sorted(senior_links):
        row = rows.get(link)
        if not row:
            rejected.append({"linkedin": link, "reason": "REVIEW_SELECTION_NOT_FOUND_IN_SOURCE"})
            continue
        name, role = clean(row.get("name")), employment(row)
        emails = normalized_emails(row.get("emails"))
        if not name or not role or not emails:
            rejected.append({"linkedin": link, "name": name, "reason": "MISSING_NAME_EMAIL_OR_EMPLOYMENT"})
            continue
        primary, primary_is_personal = choose_primary(emails)
        aliases = [email for email in emails if email != primary]
        given, family = split_name(name)
        tags = [SOURCE_TAG] + ([DEBANG_TAG] if link in debang_links else [])
        fingerprint = hashlib.sha256((name + "\0" + role + "\0" + primary).encode("utf-8")).hexdigest()
        doc = {
            "orcidId": stable_doc_id(primary),
            "id": source_id(row),
            "email": primary,
            "givenNames": given,
            "familyNames": family,
            "country": clean(row.get("location")) or None,
            "keyword": clean(row.get("jobTitle")) or role,
            "employment": role,
            "institution": clean(row.get("company")) or None,
            "emailSource": "CONTACTOUT_VISIBLE",
            "emailVerifiedLevel": 1,
            "dataSource": "CONTACTOUT_VISIBLE",
            "externalIds": {
                "contactoutLinkedinUrl": link,
                "contactoutSourceUrl": clean(row.get("sourceUrl")) or None,
                "contactoutVisibleEmails": emails,
                "pendingEmailAliases": aliases,
                "primaryEmailIsPersonalDomain": primary_is_personal,
                "contactoutProfileText": clean(row.get("profileText")),
            },
            "candidateValidatedAt": now,
            "discoveredAt": es_date(row.get("firstCapturedAt"), now),
            "updatedAt": now,
            "filterResult": "PASS",
            "funnelLevel": "CANDIDATE",
            "tags": tags,
            "enrichedAt": now,
            "enrichmentSource": "CONTACTOUT_VISIBLE_REVIEW_20260920",
            "expertClassification": {
                "type": "PRODUCTION_RND",
                "productionScore": 70,
                "researchScore": 0,
                "positiveEvidence": ["CONTACTOUT_VISIBLE_EMAIL", "REVIEWED_SENIOR_PRODUCTION_OR_RND_ROLE"],
                "negativeEvidence": [],
                "version": "contactout-enterprise-review-20260920-v1",
                "sourceFingerprint": fingerprint,
                "classifiedAt": now,
            },
        }
        docs.append({"_id": stable_doc_id(primary), "_source": {k: v for k, v in doc.items() if v not in (None, "", [])}})
    email_counts = Counter(item["_source"]["email"] for item in docs)
    for email, count in email_counts.items():
        if count > 1:
            raise ValueError(f"Duplicate selected primary email: {email}")
    return docs, rejected


def find_conflicts(client, index, docs):
    all_emails = sorted({email for doc in docs for email in doc["_source"]["externalIds"]["contactoutVisibleEmails"]})
    response = client.request("POST", f"/{index}/_search", {
        "size": min(10000, len(all_emails)),
        "_source": ["email", "orcidId", "tags"],
        "query": {"terms": {"email": all_emails}},
    })
    conflicts = defaultdict(list)
    for hit in response.get("hits", {}).get("hits", []):
        email = clean((hit.get("_source") or {}).get("email")).lower()
        if email:
            conflicts[email].append(hit.get("_id"))
    return dict(conflicts)


def verify(client, index, docs):
    response = client.request("POST", f"/{index}/_mget", {
        "docs": [{"_id": doc["_id"], "_source": ["email", "employment", "tags", "operatorStatus"]} for doc in docs]
    })
    failures = []
    requested = {doc["_id"]: doc["_source"]["tags"] for doc in docs}
    for item in response.get("docs", []):
        source = item.get("_source") or {}
        expected_tags = requested.get(item.get("_id"), [])
        if (not item.get("found") or not EMAIL_RE.fullmatch(clean(source.get("email")))
                or not clean(source.get("employment")) or source.get("tags") != expected_tags
                or clean(source.get("operatorStatus"))):
            failures.append(item.get("_id"))
    return failures


def main():
    options = args()
    if options.prepared_documents:
        docs = json.loads(options.prepared_documents.read_text(encoding="utf-8"))
        rejected = []
        senior = {item["_source"].get("externalIds", {}).get("contactoutLinkedinUrl") for item in docs}
        debang = {item["_source"].get("externalIds", {}).get("contactoutLinkedinUrl") for item in docs
                  if item["_source"].get("tags") == [SOURCE_TAG, DEBANG_TAG]}
    else:
        if not options.csv or not options.review_report:
            raise ValueError("--csv and --review-report are required unless --prepared-documents is supplied")
        senior, debang = review_selection(options.review_report)
        rows = merge_rows(read_rows(options.csv))
        docs, rejected = build_documents(rows, senior, debang)
    options.output_dir.mkdir(parents=True, exist_ok=True)
    (options.output_dir / "candidate_documents.json").write_text(json.dumps(docs, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    (options.output_dir / "rejected.json").write_text(json.dumps(rejected, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    report = {
        "selectedSeniorProductionRnd": len(senior), "selectedDebang": len(debang),
        "prepared": len(docs), "rejected": len(rejected), "executed": False,
        "emailsSent": 0, "tagsOnly": [SOURCE_TAG, DEBANG_TAG],
    }
    if options.check_online or options.execute:
        client = EsClient(read_es_config(options.config))
        conflicts = find_conflicts(client, options.index, docs)
        writable = [doc for doc in docs if not any(email in conflicts for email in doc["_source"]["externalIds"]["contactoutVisibleEmails"])]
        (options.output_dir / "existing_email_conflicts.json").write_text(json.dumps(conflicts, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        report.update({"existingEmailConflicts": len(conflicts), "writable": len(writable)})
    if options.execute:
        statuses, failures = bulk_index(client, options.index, writable)
        verification_failures = verify(client, options.index, writable)
        (options.output_dir / "bulk_failures.json").write_text(json.dumps(failures, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        (options.output_dir / "verification_failures.json").write_text(json.dumps(verification_failures, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        report.update({"executed": True, "bulkStatusCounts": dict(statuses), "bulkFailures": len(failures),
                       "verified": len(writable) - len(verification_failures), "verificationFailures": len(verification_failures)})
    (options.output_dir / "report.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, ensure_ascii=False))


if __name__ == "__main__":
    main()
