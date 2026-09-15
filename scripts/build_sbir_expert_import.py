#!/usr/bin/env python3
"""Build a reviewable, non-destructive SBIR Phase II expert import bundle.

The current no-abstract export is authoritative.  The older export is used only
to enrich matching current awards with abstracts.  This script never connects
to or writes Elasticsearch; optional existing-email files provide offline ES
deduplication before a create-only bulk file is produced.
"""

from __future__ import annotations

import argparse
import base64
import csv
import hashlib
import json
import os
import re
import ssl
import sys
import urllib.error
import urllib.request
from collections import defaultdict
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Iterable

from probe_rnd_expert_emails import (
    Candidate,
    apply_domain_check,
    clean,
    valid_email,
    website_domain,
)


IMPORT_FIELDS = (
    "es_doc_id",
    "expert_name",
    "email",
    "company",
    "email_domain",
    "company_domain",
    "domain_match",
    "pi_title",
    "award_count",
    "award_years",
    "agencies",
    "programs",
    "project_titles",
    "project_abstracts",
    "award_ids",
    "data_source",
    "email_source",
    "email_verified_level",
    "qualification",
    "source_files",
)

REJECT_FIELDS = (
    "expert_name",
    "email",
    "company",
    "phase",
    "award_year",
    "award_title",
    "domain_match",
    "reject_reason",
    "source_file",
)

PHASE_TWO_RE = re.compile(r"^phase\s*ii$", re.IGNORECASE)


@dataclass
class Award:
    expert_name: str
    email: str
    company: str
    pi_title: str
    phase: str
    program: str
    agency: str
    award_year: str
    proposal_award_date: str
    award_title: str
    agency_tracking_number: str
    contract: str
    company_domain: str
    email_domain: str
    domain_match: str
    abstract: str = ""
    abstract_matched: bool = False


def normalized_row(row: dict[str, Any]) -> dict[str, str]:
    return {
        clean(key).lower().replace(" ", "_"): clean(value)
        for key, value in row.items()
        if key is not None
    }


def read_rows(path: Path) -> Iterable[dict[str, str]]:
    with path.open(encoding="utf-8-sig", newline="") as handle:
        reader = csv.DictReader(handle)
        if not reader.fieldnames:
            raise ValueError(f"CSV has no header: {path}")
        for row in reader:
            yield normalized_row(row)


def normalized_key_part(value: Any) -> str:
    return clean(value).casefold()


def exact_award_key(row: dict[str, str]) -> tuple[str, ...]:
    return tuple(
        normalized_key_part(row.get(field))
        for field in (
            "agency",
            "agency_tracking_number",
            "contract",
            "proposal_award_date",
            "company",
            "award_title",
            "phase",
        )
    )


def fallback_award_key(row: dict[str, str]) -> tuple[str, ...] | None:
    key = tuple(
        normalized_key_part(row.get(field))
        for field in ("agency", "agency_tracking_number", "contract")
    )
    return key if all(key) else None


def build_abstract_indexes(
    archive_path: Path | None,
) -> tuple[dict[tuple[str, ...], str], dict[tuple[str, ...], str]]:
    if archive_path is None:
        return {}, {}

    exact: dict[tuple[str, ...], str] = {}
    fallback_values: dict[tuple[str, ...], set[str]] = defaultdict(set)
    for row in read_rows(archive_path):
        abstract = clean(row.get("abstract"))
        if not abstract:
            continue
        exact[exact_award_key(row)] = abstract
        fallback = fallback_award_key(row)
        if fallback:
            fallback_values[fallback].add(abstract)

    fallback_unique = {
        key: next(iter(values))
        for key, values in fallback_values.items()
        if len(values) == 1
    }
    return exact, fallback_unique


def find_abstract(
    row: dict[str, str],
    exact: dict[tuple[str, ...], str],
    fallback: dict[tuple[str, ...], str],
) -> str:
    value = exact.get(exact_award_key(row))
    if value:
        return value
    fallback_key = fallback_award_key(row)
    return fallback.get(fallback_key, "") if fallback_key else ""


def award_year(row: dict[str, str]) -> int | None:
    value = clean(row.get("award_year"))
    return int(value) if value.isdigit() else None


def load_existing_emails(paths: Iterable[Path]) -> set[str]:
    emails: set[str] = set()
    for path in paths:
        text = path.read_text(encoding="utf-8-sig")
        for match in re.finditer(
            r"[A-Z0-9._%+\-]+@[A-Z0-9.\-]+\.[A-Z]{2,}", text, re.IGNORECASE
        ):
            email = valid_email(match.group(0))
            if email:
                emails.add(email)
    return emails


def fetch_existing_emails_from_es(
    *,
    es_url: str,
    indexes: str,
    emails: Iterable[str],
    username: str,
    password: str,
    timeout: float,
    batch_size: int,
    insecure: bool = False,
    host_header: str = "",
) -> set[str]:
    if not re.fullmatch(r"[A-Za-z0-9_.*,-]+", indexes):
        raise ValueError("--es-indexes contains unsupported characters")
    if host_header and not re.fullmatch(r"[A-Za-z0-9.-]+(?::[0-9]+)?", host_header):
        raise ValueError("--es-host-header contains unsupported characters")
    normalized = sorted({valid_email(email) for email in emails if valid_email(email)})
    existing: set[str] = set()
    endpoint = f"{es_url.rstrip('/')}/{indexes}/_search"
    ssl_context = ssl._create_unverified_context() if insecure else None
    for start in range(0, len(normalized), batch_size):
        batch = normalized[start : start + batch_size]
        requested_batch = set(batch)
        payload = {
            "size": 0,
            "query": {"terms": {"email": batch}},
            "aggs": {
                "existing_emails": {
                    "terms": {
                        "field": "email",
                        "size": len(batch),
                        "shard_size": max(len(batch) * 2, 10),
                    }
                }
            },
        }
        headers = {"Content-Type": "application/json"}
        if host_header:
            headers["Host"] = host_header
        if username or password:
            token = base64.b64encode(f"{username}:{password}".encode("utf-8")).decode("ascii")
            headers["Authorization"] = f"Basic {token}"
        request = urllib.request.Request(
            endpoint,
            data=json.dumps(payload, separators=(",", ":")).encode("utf-8"),
            headers=headers,
            method="POST",
        )
        with urllib.request.urlopen(
            request, timeout=timeout, context=ssl_context
        ) as response:
            result = json.loads(response.read())
        buckets = (
            result.get("aggregations", {})
            .get("existing_emails", {})
            .get("buckets", [])
        )
        for bucket in buckets:
            email = valid_email(bucket.get("key")) if isinstance(bucket, dict) else ""
            if email in requested_batch:
                existing.add(email)
    return existing


def to_award(row: dict[str, str], abstract: str) -> Award:
    email = valid_email(row.get("pi_email"))
    company_domain = website_domain(row.get("company_website"))
    candidate = apply_domain_check(
        Candidate(
            expert_name=clean(row.get("pi_name")),
            email=email,
            company=clean(row.get("company")),
            role=clean(row.get("pi_title")),
            project_title=clean(row.get("award_title")),
            project_url="",
            company_domain=company_domain,
        )
    )
    return Award(
        expert_name=candidate.expert_name,
        email=candidate.email,
        company=candidate.company,
        pi_title=candidate.role,
        phase=clean(row.get("phase")),
        program=clean(row.get("program")),
        agency=clean(row.get("agency")),
        award_year=clean(row.get("award_year")),
        proposal_award_date=clean(row.get("proposal_award_date")),
        award_title=candidate.project_title,
        agency_tracking_number=clean(row.get("agency_tracking_number")),
        contract=clean(row.get("contract")),
        company_domain=candidate.company_domain,
        email_domain=candidate.email_domain,
        domain_match=candidate.domain_match,
        abstract=abstract,
        abstract_matched=bool(abstract),
    )


def rejection_for(row: dict[str, str], award: Award, existing_emails: set[str]) -> str:
    if not PHASE_TWO_RE.fullmatch(award.phase):
        return "PHASE_NOT_II"
    if not award.email:
        return "INVALID_EMAIL"
    if award.domain_match == "PERSONAL_EMAIL":
        return "PERSONAL_EMAIL"
    if award.domain_match == "MISMATCH":
        return "DOMAIN_MISMATCH"
    if award.domain_match == "UNKNOWN":
        return "DOMAIN_UNKNOWN"
    if award.email in existing_emails:
        return "EXISTS_IN_ES"
    return ""


def reject_row(row: dict[str, str], award: Award, reason: str) -> dict[str, Any]:
    return {
        "expert_name": award.expert_name,
        "email": award.email or clean(row.get("pi_email")),
        "company": award.company,
        "phase": award.phase,
        "award_year": award.award_year,
        "award_title": award.award_title,
        "domain_match": award.domain_match,
        "reject_reason": reason,
        "source_file": "SBIR_CURRENT",
    }


def distinct(values: Iterable[str]) -> list[str]:
    return list(dict.fromkeys(value for value in values if value))


def date_sort_value(value: str) -> tuple[int, int, int]:
    for pattern in ("%m/%d/%Y", "%Y-%m-%d"):
        try:
            parsed = datetime.strptime(value, pattern)
            return parsed.year, parsed.month, parsed.day
        except ValueError:
            pass
    return 0, 0, 0


def award_sort_value(award: Award) -> tuple[int, tuple[int, int, int]]:
    year = int(award.award_year) if award.award_year.isdigit() else 0
    return year, date_sort_value(award.proposal_award_date)


def email_doc_id(email: str) -> str:
    digest = hashlib.sha256(email.lower().encode("utf-8")).hexdigest()[:19]
    return f"EMAIL-{digest}"


def split_name(value: str) -> tuple[str, str]:
    parts = clean(value).split()
    if len(parts) < 2:
        return (parts[0], "") if parts else ("", "")
    return " ".join(parts[:-1]), parts[-1]


def join_field(values: Iterable[str]) -> str:
    return "|".join(distinct(values))


def expert_csv_row(email: str, awards: list[Award]) -> dict[str, Any]:
    ordered = sorted(awards, key=award_sort_value, reverse=True)
    primary = ordered[0]
    years = sorted({award.award_year for award in awards if award.award_year})
    abstracts = distinct(award.abstract for award in ordered)
    source_files = ["SBIR_CURRENT"]
    if abstracts:
        source_files.append("SBIR_ARCHIVE_ABSTRACT")
    award_ids = distinct(
        award.agency_tracking_number or award.contract for award in ordered
    )
    return {
        "es_doc_id": email_doc_id(email),
        "expert_name": primary.expert_name,
        "email": email,
        "company": primary.company,
        "email_domain": primary.email_domain,
        "company_domain": primary.company_domain,
        "domain_match": primary.domain_match,
        "pi_title": primary.pi_title or "Principal Investigator",
        "award_count": len(awards),
        "award_years": "|".join(years),
        "agencies": join_field(award.agency for award in ordered),
        "programs": join_field(award.program for award in ordered),
        "project_titles": join_field(award.award_title for award in ordered),
        "project_abstracts": "|".join(abstracts),
        "award_ids": "|".join(award_ids),
        "data_source": "SBIR",
        "email_source": "SBIR_PUBLIC_AWARD",
        "email_verified_level": 2,
        "qualification": "PRODUCTION_RND",
        "source_files": "|".join(source_files),
    }


def bulk_document(row: dict[str, Any], now: str) -> dict[str, Any]:
    given_names, family_names = split_name(str(row["expert_name"]))
    titles = str(row["project_titles"]).split("|") if row["project_titles"] else []
    role = str(row["pi_title"] or "Principal Investigator")
    company = str(row["company"])
    return {
        "orcidId": row["es_doc_id"],
        "email": row["email"],
        "givenNames": given_names,
        "familyNames": family_names,
        "keyword": "SBIR Phase II engineering; " + " | ".join(titles),
        "employment": f"{role}; SBIR Phase II research and development; {company}",
        "institution": company,
        "institutionType": "company",
        "emailSource": row["email_source"],
        "emailVerifiedLevel": row["email_verified_level"],
        "dataSource": row["data_source"],
        "externalIds": {
            "sbirAwardIds": str(row["award_ids"]).split("|") if row["award_ids"] else [],
            "sbirAwardYears": str(row["award_years"]).split("|") if row["award_years"] else [],
        },
        "recentWorkTitles": titles,
        "discoveredAt": now,
        "updatedAt": now,
        "filterResult": "PASSED",
        "tags": [
            "discovered",
            "sbir",
            "phase_ii",
            "production_rnd_evidence",
            "SBIR导入",
        ],
    }


def write_csv(path: Path, fields: tuple[str, ...], rows: Iterable[dict[str, Any]]) -> None:
    with path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=fields)
        writer.writeheader()
        writer.writerows(rows)


def es_status_message(es_url: str, match_count: int) -> str:
    if es_url:
        return f"ES queried read-only; matches={match_count}; no ES data modified"
    return "ES was not contacted or modified"


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Merge SBIR exports and build a strict, create-only ES import bundle."
    )
    parser.add_argument("--current", required=True, help="Latest award_data_no_abstract.csv")
    parser.add_argument("--archive", help="Older award_data.csv used only for Abstract")
    parser.add_argument("--since-year", type=int, default=datetime.now().year - 3)
    parser.add_argument(
        "--existing-emails",
        action="append",
        default=[],
        help="TXT/CSV export of emails already in ES; repeatable.",
    )
    parser.add_argument("--raw-index", default="orcid_info")
    parser.add_argument("--candidate-index", default="orcid_info_candidate")
    parser.add_argument(
        "--es-url",
        help="Optional ES base URL. Performs read-only email deduplication.",
    )
    parser.add_argument(
        "--es-indexes",
        default="orcid_info,orcid_info_candidate,orcid_info_application",
        help="Comma-separated indexes queried for existing emails.",
    )
    parser.add_argument("--es-username", default=os.environ.get("ES_USERNAME", "elastic"))
    parser.add_argument(
        "--es-timeout", type=float, default=30.0, help="Seconds per ES query batch."
    )
    parser.add_argument(
        "--es-batch-size", type=int, default=500, help="Emails per ES terms query."
    )
    parser.add_argument(
        "--es-insecure",
        action="store_true",
        help="Disable TLS certificate verification; intended only for a local SSH tunnel.",
    )
    parser.add_argument(
        "--es-host-header",
        default="",
        help="Override HTTP Host for a local SSH tunnel.",
    )
    parser.add_argument("--output-dir", default="tmp/sbir-import")
    return parser


def run(args: argparse.Namespace) -> int:
    current_path = Path(args.current).expanduser().resolve()
    archive_path = Path(args.archive).expanduser().resolve() if args.archive else None
    output_dir = Path(args.output_dir).expanduser().resolve()
    existing_paths = [Path(value).expanduser().resolve() for value in args.existing_emails]

    if args.since_year < 1900:
        print("--since-year must be >= 1900", file=sys.stderr)
        return 2
    if args.es_batch_size < 1:
        print("--es-batch-size must be positive", file=sys.stderr)
        return 2
    for path in [current_path, archive_path, *existing_paths]:
        if path is not None and not path.is_file():
            print(f"file not found: {path}", file=sys.stderr)
            return 2

    exact_abstracts, fallback_abstracts = build_abstract_indexes(archive_path)
    existing_emails = load_existing_emails(existing_paths)
    grouped: dict[str, list[Award]] = defaultdict(list)
    rejected: list[dict[str, Any]] = []
    rejection_counts: dict[str, int] = defaultdict(int)
    current_rows = 0
    rows_since_year = 0
    before_since_year = 0
    abstract_matches = 0

    for row in read_rows(current_path):
        current_rows += 1
        year = award_year(row)
        if year is not None and year < args.since_year:
            before_since_year += 1
            continue
        rows_since_year += 1
        abstract = find_abstract(row, exact_abstracts, fallback_abstracts)
        if abstract:
            abstract_matches += 1
        award = to_award(row, abstract)
        reason = rejection_for(row, award, existing_emails)
        if reason:
            rejected.append(reject_row(row, award, reason))
            rejection_counts[reason] += 1
            continue
        grouped[award.email].append(award)

    es_existing_emails: set[str] = set()
    if args.es_url:
        try:
            es_existing_emails = fetch_existing_emails_from_es(
                es_url=args.es_url,
                indexes=args.es_indexes,
                emails=grouped,
                username=args.es_username,
                password=os.environ.get("ES_PASSWORD", ""),
                timeout=args.es_timeout,
                batch_size=args.es_batch_size,
                insecure=args.es_insecure,
                host_header=args.es_host_header,
            )
        except (OSError, ValueError, json.JSONDecodeError, urllib.error.HTTPError) as error:
            print(f"ES deduplication failed; no output written: {error}", file=sys.stderr)
            return 1
        for email in sorted(es_existing_emails):
            awards = grouped.pop(email, [])
            for award in awards:
                rejected.append(reject_row({}, award, "EXISTS_IN_ES"))
                rejection_counts["EXISTS_IN_ES"] += 1
        existing_emails.update(es_existing_emails)

    import_rows = [
        expert_csv_row(email, awards)
        for email, awards in sorted(grouped.items())
    ]
    output_dir.mkdir(parents=True, exist_ok=True)
    write_csv(output_dir / "experts_import.csv", IMPORT_FIELDS, import_rows)
    write_csv(output_dir / "experts_rejected.csv", REJECT_FIELDS, rejected)
    write_csv(
        output_dir / "es_existing_emails.csv",
        ("email",),
        ({"email": email} for email in sorted(es_existing_emails)),
    )

    now = datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M:%S")
    with (output_dir / "experts_bulk.ndjson").open("w", encoding="utf-8") as handle:
        for row in import_rows:
            raw_action = {"create": {"_index": args.raw_index, "_id": row["es_doc_id"]}}
            raw_document = bulk_document(row, now)
            candidate_action = {
                "create": {"_index": args.candidate_index, "_id": row["es_doc_id"]}
            }
            candidate_document = dict(raw_document)
            candidate_document["candidateValidatedAt"] = now
            handle.write(
                json.dumps(raw_action, ensure_ascii=False, separators=(",", ":")) + "\n"
            )
            handle.write(
                json.dumps(raw_document, ensure_ascii=False, separators=(",", ":"))
                + "\n"
            )
            handle.write(
                json.dumps(candidate_action, ensure_ascii=False, separators=(",", ":"))
                + "\n"
            )
            handle.write(
                json.dumps(candidate_document, ensure_ascii=False, separators=(",", ":"))
                + "\n"
            )

    summary = {
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "current_file": str(current_path),
        "archive_file": str(archive_path) if archive_path else None,
        "since_year": args.since_year,
        "raw_index": args.raw_index,
        "candidate_index": args.candidate_index,
        "current_rows": current_rows,
        "rows_since_year": rows_since_year,
        "rows_before_since_year": before_since_year,
        "archive_abstract_matches": abstract_matches,
        "existing_email_count": len(existing_emails),
        "es_existing_email_count": len(es_existing_emails),
        "es_indexes_queried": args.es_indexes if args.es_url else None,
        "accepted_award_rows": sum(len(awards) for awards in grouped.values()),
        "import_experts": len(import_rows),
        "rejected_rows": len(rejected),
        "rejection_counts": dict(sorted(rejection_counts.items())),
        "bulk_operation": "create",
        "bulk_targets": [args.raw_index, args.candidate_index],
        "writes_elasticsearch": False,
        "files": {
            "import": str(output_dir / "experts_import.csv"),
            "rejected": str(output_dir / "experts_rejected.csv"),
            "es_existing_emails": str(output_dir / "es_existing_emails.csv"),
            "bulk": str(output_dir / "experts_bulk.ndjson"),
        },
    }
    (output_dir / "summary.json").write_text(
        json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8"
    )

    print(
        f"current={current_rows} since_year={rows_since_year} "
        f"accepted_awards={summary['accepted_award_rows']} experts={len(import_rows)} "
        f"rejected={len(rejected)} abstract_matches={abstract_matches}"
    )
    print(f"output={output_dir}")
    print(es_status_message(args.es_url or "", len(es_existing_emails)))
    return 0


def main() -> int:
    return run(build_parser().parse_args())


if __name__ == "__main__":
    raise SystemExit(main())
