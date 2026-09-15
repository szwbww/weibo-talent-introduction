#!/usr/bin/env python3
"""Build one review CSV from academic and enterprise expert sources.

The script intentionally avoids scraping university, ResearchGate, and LinkedIn
pages. OpenAlex produces academic candidates that still need title/email review;
public R&D APIs and optional ContactOut produce enterprise candidates.
"""

from __future__ import annotations

import argparse
import csv
import json
import os
import re
import sys
import urllib.error
import urllib.parse
from dataclasses import asdict, dataclass, field
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Iterable

from probe_rnd_expert_emails import (
    FETCHERS,
    NORMALIZERS,
    SOURCE_LABELS,
    Candidate,
    clean,
    load_fixture,
    mask_email,
    parse_sources,
    request_json,
    valid_email,
)


OPENALEX_ENDPOINT = "https://api.openalex.org/works"
CONTACTOUT_ENDPOINT = "https://api.contactout.com/v1/people/search"
SENIOR_RND_TITLE_RE = re.compile(
    r"\b(?:chief|principal|distinguished|staff|senior)\s+"
    r"(?:scientist|researcher|engineer)\b|"
    r"\b(?:director|head|vice president|vp)\b.*\b(?:research|r&d|engineering|science)\b|"
    r"\b(?:research|r&d|engineering|science)\b.*\b(?:director|head|vice president|vp)\b",
    re.IGNORECASE,
)


@dataclass
class ExpertRecord:
    expert_name: str
    expert_type: str
    organization: str
    title: str = ""
    country: str = ""
    research_topic: str = ""
    work_title: str = ""
    source_url: str = ""
    openalex_id: str = ""
    orcid: str = ""
    linkedin_url: str = ""
    email: str = ""
    email_source: str = ""
    email_acquisition_type: str = ""
    email_status: str = ""
    qualification: str = ""
    sources: list[str] = field(default_factory=list)
    evidence: list[str] = field(default_factory=list)
    confidence_score: int = 0


CSV_FIELDS = (
    "expert_name",
    "expert_type",
    "organization",
    "title",
    "country",
    "research_topic",
    "work_title",
    "source_url",
    "openalex_id",
    "orcid",
    "linkedin_url",
    "email",
    "email_source",
    "email_acquisition_type",
    "email_status",
    "qualification",
    "sources",
    "evidence",
    "confidence_score",
)


def normalize_openalex(payload: Any, *, country: str = "") -> list[ExpertRecord]:
    works = payload.get("results", []) if isinstance(payload, dict) else []
    result: list[ExpertRecord] = []
    wanted_country = clean(country).upper()
    for work in works:
        if not isinstance(work, dict):
            continue
        topic = work.get("primary_topic") or {}
        location = work.get("primary_location") or {}
        source_url = clean(location.get("landing_page_url") or work.get("doi") or work.get("id"))
        for authorship in work.get("authorships") or []:
            if not isinstance(authorship, dict):
                continue
            institutions = [
                institution
                for institution in authorship.get("institutions") or []
                if isinstance(institution, dict)
                and clean(institution.get("type")).lower() == "education"
                and (
                    not wanted_country
                    or clean(institution.get("country_code")).upper() == wanted_country
                )
            ]
            if not institutions:
                continue
            author = authorship.get("author") or {}
            author_id = clean(author.get("id"))
            name = clean(author.get("display_name"))
            if not author_id or not name:
                continue
            institution = institutions[0]
            is_corresponding = bool(authorship.get("is_corresponding"))
            evidence = ["OpenAlex academic affiliation"]
            if is_corresponding:
                evidence.append("corresponding author")
            result.append(
                ExpertRecord(
                    expert_name=name,
                    expert_type="ACADEMIC",
                    organization=clean(institution.get("display_name")),
                    country=clean(institution.get("country_code")).upper(),
                    research_topic=clean(topic.get("display_name")),
                    work_title=clean(work.get("display_name") or work.get("title")),
                    source_url=source_url,
                    openalex_id=author_id,
                    orcid=clean(author.get("orcid")),
                    qualification="NEEDS_TITLE_AND_EMAIL",
                    sources=["OPENALEX"],
                    evidence=evidence,
                    confidence_score=65 if is_corresponding else 55,
                )
            )
    return result


def normalize_public_rnd(candidates: Iterable[Candidate]) -> list[ExpertRecord]:
    result = []
    for candidate in candidates:
        email = valid_email(candidate.email)
        if not email:
            continue
        result.append(
            ExpertRecord(
                expert_name=clean(candidate.expert_name),
                expert_type="ENTERPRISE",
                organization=clean(candidate.company),
                title=clean(candidate.role),
                research_topic=clean(candidate.project_title),
                work_title=clean(candidate.project_title),
                source_url=clean(candidate.project_url),
                email=email,
                email_source="|".join(candidate.sources),
                email_acquisition_type="PUBLIC_SOURCE",
                email_status="PUBLIC",
                qualification=clean(candidate.qualification),
                sources=list(candidate.sources),
                evidence=list(candidate.evidence),
                confidence_score=candidate.confidence_score,
            )
        )
    return result


def contactout_profiles(payload: Any) -> list[tuple[str, dict[str, Any]]]:
    profiles = payload.get("profiles", {}) if isinstance(payload, dict) else {}
    if isinstance(profiles, dict):
        return [
            (clean(url), profile)
            for url, profile in profiles.items()
            if isinstance(profile, dict)
        ]
    if isinstance(profiles, list):
        return [
            (clean(profile.get("linkedin_url") or profile.get("linkedin")), profile)
            for profile in profiles
            if isinstance(profile, dict)
        ]
    return []


def normalize_contactout(payload: Any) -> list[ExpertRecord]:
    result = []
    for linkedin_url, profile in contactout_profiles(payload):
        title = clean(profile.get("title") or profile.get("headline"))
        if not SENIOR_RND_TITLE_RE.search(title):
            continue
        contact_info = profile.get("contact_info") or {}
        work_emails = contact_info.get("work_emails") or []
        if isinstance(work_emails, str):
            work_emails = [work_emails]
        email = next((valid_email(value) for value in work_emails if valid_email(value)), "")
        if not email:
            continue
        statuses = contact_info.get("work_email_status") or {}
        if isinstance(statuses, dict):
            normalized_statuses = {
                clean(key).casefold(): clean(value)
                for key, value in statuses.items()
            }
            email_status = normalized_statuses.get(email.casefold(), "").upper() or "UNVERIFIED"
        else:
            email_status = "UNVERIFIED"
        company = profile.get("company") or {}
        if isinstance(company, dict):
            organization = clean(company.get("name"))
        else:
            organization = clean(company)
        skills = profile.get("skills") or []
        if isinstance(skills, str):
            skills = [skills]
        result.append(
            ExpertRecord(
                expert_name=clean(profile.get("full_name") or profile.get("name")),
                expert_type="ENTERPRISE",
                organization=organization,
                title=title,
                country=clean(profile.get("location")),
                research_topic=" | ".join(clean(item) for item in skills[:5] if clean(item)),
                source_url=linkedin_url,
                linkedin_url=linkedin_url,
                email=email,
                email_source="CONTACTOUT",
                email_acquisition_type="ENRICHED",
                email_status=email_status,
                qualification="PRODUCTION_RND",
                sources=["CONTACTOUT"],
                evidence=["senior R&D title", "ContactOut work email"],
                confidence_score=85 if email_status == "VERIFIED" else 70,
            )
        )
    return result


def record_key(record: ExpertRecord) -> str:
    if record.email:
        return f"email:{record.email.casefold()}"
    if record.openalex_id:
        return f"openalex:{record.openalex_id.casefold()}"
    return f"person:{record.expert_name.casefold()}|{record.organization.casefold()}"


def merge_records(records: Iterable[ExpertRecord]) -> list[ExpertRecord]:
    merged: dict[str, ExpertRecord] = {}
    for record in records:
        key = record_key(record)
        current = merged.get(key)
        if current is None:
            record.sources = list(dict.fromkeys(record.sources))
            record.evidence = list(dict.fromkeys(record.evidence))
            merged[key] = record
            continue
        for attr in (
            "organization",
            "title",
            "country",
            "research_topic",
            "work_title",
            "source_url",
            "orcid",
            "linkedin_url",
            "email",
            "email_source",
            "email_acquisition_type",
            "email_status",
            "qualification",
        ):
            if not getattr(current, attr) and getattr(record, attr):
                setattr(current, attr, getattr(record, attr))
        current.sources = list(dict.fromkeys(current.sources + record.sources))
        current.evidence = list(dict.fromkeys(current.evidence + record.evidence))
        current.confidence_score = max(current.confidence_score, record.confidence_score)
    return sorted(
        merged.values(),
        key=lambda item: (-item.confidence_score, item.expert_name.casefold()),
    )


def build_openalex_url(args: argparse.Namespace) -> str:
    filters = [f"from_publication_date:{args.since_year}-01-01"]
    if args.country:
        filters.append(f"authorships.institutions.country_code:{args.country.upper()}")
    params: dict[str, Any] = {
        "search": args.academic_keywords,
        "filter": ",".join(filters),
        "per-page": min(args.limit, 200),
        "select": (
            "id,doi,display_name,publication_year,primary_topic,"
            "primary_location,authorships"
        ),
    }
    api_key = clean(os.environ.get("OPENALEX_API_KEY"))
    if api_key:
        params["api_key"] = api_key
    return f"{OPENALEX_ENDPOINT}?{urllib.parse.urlencode(params)}"


def fetch_openalex(args: argparse.Namespace) -> tuple[str, Any]:
    url = build_openalex_url(args)
    return url, request_json(url, timeout=args.timeout)


def contactout_payload(args: argparse.Namespace) -> dict[str, Any]:
    payload: dict[str, Any] = {
        "page": 1,
        "page_size": min(args.limit, 25),
        "job_title": args.contactout_title,
        "current_titles_only": True,
        "include_related_job_titles": True,
        "data_types": ["work_email"],
        "reveal_info": True,
    }
    if args.contactout_company:
        payload["company"] = args.contactout_company
    if args.contactout_location:
        payload["location"] = args.contactout_location
    if args.contactout_skill:
        payload["skills"] = args.contactout_skill
    return payload


def fetch_contactout(args: argparse.Namespace, token: str) -> Any:
    data = json.dumps(contactout_payload(args), separators=(",", ":")).encode("utf-8")
    return request_json(
        CONTACTOUT_ENDPOINT,
        timeout=args.timeout,
        headers={"Content-Type": "application/json", "token": token},
        data=data,
    )


def csv_row(record: ExpertRecord) -> dict[str, Any]:
    row = asdict(record)
    row["sources"] = "|".join(record.sources)
    row["evidence"] = "|".join(record.evidence)
    return row


def write_csv(path: Path, records: list[ExpertRecord]) -> None:
    with path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=CSV_FIELDS)
        writer.writeheader()
        writer.writerows(csv_row(record) for record in records)


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Probe academic and enterprise expert sources into one review CSV."
    )
    parser.add_argument("--academic-keywords", default="")
    parser.add_argument("--country", default="")
    parser.add_argument("--enterprise-sources", default="nsf,sbir,osti,ietf")
    parser.add_argument("--contactout-title", action="append", default=[])
    parser.add_argument("--contactout-company", action="append", default=[])
    parser.add_argument("--contactout-location", action="append", default=[])
    parser.add_argument("--contactout-skill", action="append", default=[])
    parser.add_argument("--limit", type=int, default=25)
    parser.add_argument("--since-year", type=int, default=datetime.now().year - 1)
    parser.add_argument("--timeout", type=float, default=20.0)
    parser.add_argument("--delay", type=float, default=0.05)
    parser.add_argument("--output-dir", default="tmp/expert-pipeline-probe")
    parser.add_argument("--sbir-csv")
    parser.add_argument("--orcid-query")
    parser.add_argument("--show-emails", action="store_true")
    parser.add_argument("--fixture-dir", help=argparse.SUPPRESS)
    return parser


def probe(args: argparse.Namespace) -> int:
    if args.limit < 1:
        print("--limit must be positive", file=sys.stderr)
        return 2
    if args.country and not re.fullmatch(r"[A-Za-z]{2}", args.country):
        print("--country must be a two-letter code", file=sys.stderr)
        return 2
    try:
        enterprise_sources = parse_sources(args.enterprise_sources)
    except argparse.ArgumentTypeError as error:
        print(error, file=sys.stderr)
        return 2

    fixture_dir = Path(args.fixture_dir).resolve() if args.fixture_dir else None
    output_dir = Path(args.output_dir).expanduser().resolve()
    output_dir.mkdir(parents=True, exist_ok=True)
    records: list[ExpertRecord] = []
    statuses: dict[str, dict[str, Any]] = {}

    if args.academic_keywords:
        try:
            if fixture_dir:
                openalex_payload = json.loads(
                    (fixture_dir / "openalex.json").read_text(encoding="utf-8")
                )
                endpoint = str(fixture_dir / "openalex.json")
                status = "fixture"
            else:
                endpoint, openalex_payload = fetch_openalex(args)
                status = "ok"
            academic = normalize_openalex(openalex_payload, country=args.country)
            records.extend(academic)
            statuses["openalex"] = {
                "status": status,
                "endpoint": endpoint,
                "candidates": len(academic),
            }
        except (OSError, ValueError, json.JSONDecodeError, urllib.error.HTTPError, urllib.error.URLError) as error:
            statuses["openalex"] = {
                "status": "error",
                "endpoint": "",
                "candidates": 0,
                "note": f"{type(error).__name__}: {error}",
            }

    for source in enterprise_sources:
        try:
            source_result = (
                load_fixture(source, fixture_dir)
                if fixture_dir
                else FETCHERS[source](args)
            )
            candidates = NORMALIZERS[source](source_result.payload)
            public_records = normalize_public_rnd(candidates)
            records.extend(public_records)
            statuses[source] = {
                "status": source_result.status,
                "endpoint": source_result.endpoint,
                "candidates": len(public_records),
            }
        except (OSError, ValueError, json.JSONDecodeError, urllib.error.HTTPError, urllib.error.URLError) as error:
            statuses[source] = {
                "status": "error",
                "endpoint": "",
                "candidates": 0,
                "note": f"{type(error).__name__}: {error}",
            }

    if args.contactout_title:
        token = clean(os.environ.get("CONTACTOUT_API_TOKEN"))
        try:
            fixture_path = fixture_dir / "contactout.json" if fixture_dir else None
            if fixture_path and fixture_path.exists():
                contactout_data = json.loads(fixture_path.read_text(encoding="utf-8"))
                status = "fixture"
                endpoint = str(fixture_path)
            elif token:
                contactout_data = fetch_contactout(args, token)
                status = "ok"
                endpoint = CONTACTOUT_ENDPOINT
            else:
                contactout_data = {}
                status = "skipped"
                endpoint = CONTACTOUT_ENDPOINT
            enriched = normalize_contactout(contactout_data)
            records.extend(enriched)
            statuses["contactout"] = {
                "status": status,
                "endpoint": endpoint,
                "candidates": len(enriched),
                "note": "" if status != "skipped" else "set CONTACTOUT_API_TOKEN",
            }
        except (OSError, ValueError, json.JSONDecodeError, urllib.error.HTTPError, urllib.error.URLError) as error:
            statuses["contactout"] = {
                "status": "error",
                "endpoint": CONTACTOUT_ENDPOINT,
                "candidates": 0,
                "note": f"{type(error).__name__}: {error}",
            }

    merged = merge_records(records)
    output_path = output_dir / "experts_combined.csv"
    write_csv(output_path, merged)
    summary = {
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "criteria": {
            "academic_keywords": args.academic_keywords,
            "country": args.country.upper(),
            "since_year": args.since_year,
        },
        "sources": statuses,
        "totals": {
            "all": len(merged),
            "academic_needs_review": sum(
                record.qualification == "NEEDS_TITLE_AND_EMAIL" for record in merged
            ),
            "with_email": sum(bool(record.email) for record in merged),
        },
        "files": {"combined": str(output_path)},
    }
    (output_dir / "summary.json").write_text(
        json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8"
    )

    for source, item in statuses.items():
        print(f"[{source.upper()}] {item['status']} candidates={item['candidates']}")
    print(f"total={len(merged)} with_email={summary['totals']['with_email']}")
    for record in merged[:10]:
        shown_email = (
            record.email
            if args.show_emails
            else mask_email(record.email) if record.email else "(needs email)"
        )
        print(
            f"- {shown_email} | {record.expert_name} | {record.organization} | "
            f"{record.qualification}"
        )
    print(f"output={output_path}")
    return 0


def main() -> int:
    return probe(build_parser().parse_args())


if __name__ == "__main__":
    raise SystemExit(main())
