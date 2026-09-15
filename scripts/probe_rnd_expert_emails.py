#!/usr/bin/env python3
"""Probe public sources for production-R&D experts with public email addresses.

No third-party packages are required. The script writes raw responses plus two
normalized CSV files: every usable email candidate and a strict production-R&D
subset.
"""

from __future__ import annotations

import argparse
import csv
import json
import os
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import asdict, dataclass, field
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Iterable


SOURCE_ORDER = ("nsf", "sbir", "osti", "ietf", "orcid")
SOURCE_LABELS = {
    "nsf": "NSF",
    "sbir": "SBIR",
    "osti": "OSTI",
    "ietf": "IETF",
    "orcid": "ORCID",
}
EMAIL_RE = re.compile(r"[A-Z0-9._%+\-]+@[A-Z0-9.\-]+\.[A-Z]{2,}", re.IGNORECASE)
ACADEMIC_OR_PUBLIC_RE = re.compile(
    r"\b(university|college|school|academy|government|ministry|department of|"
    r"national laboratory|national lab|foundation|association|society|"
    r"independent|self[- ]employed|consultant)\b",
    re.IGNORECASE,
)
PERSONAL_EMAIL_DOMAINS = {
    "aol.com",
    "gmail.com",
    "hotmail.com",
    "icloud.com",
    "live.com",
    "me.com",
    "outlook.com",
    "proton.me",
    "protonmail.com",
    "qq.com",
    "yahoo.com",
    "yandex.com",
}


@dataclass
class Candidate:
    expert_name: str
    email: str
    company: str
    role: str
    project_title: str
    project_url: str
    sources: list[str] = field(default_factory=list)
    evidence: list[str] = field(default_factory=list)
    confidence_score: int = 0
    qualification: str = "NEEDS_RND_CONFIRMATION"
    email_domain: str = ""
    company_domain: str = ""
    domain_match: str = "UNKNOWN"


@dataclass
class SourceResult:
    source: str
    status: str
    endpoint: str
    payload: Any
    records_received: int = 0
    note: str = ""


def clean(value: Any) -> str:
    if value is None:
        return ""
    return " ".join(str(value).split())


def values(value: Any) -> list[str]:
    if value is None:
        return []
    if isinstance(value, list):
        return [clean(item) for item in value if clean(item)]
    text = clean(value)
    return [text] if text else []


def valid_email(value: Any) -> str:
    match = EMAIL_RE.search(clean(value))
    if not match:
        return ""
    email = match.group(0).lower().rstrip(".")
    if email.endswith((".invalid", ".test")):
        return ""
    return email


def website_domain(value: Any) -> str:
    text = clean(value).lower()
    if not text:
        return ""
    parsed = urllib.parse.urlparse(text if "://" in text else f"//{text}")
    domain = (parsed.hostname or "").rstrip(".")
    return domain[4:] if domain.startswith("www.") else domain


def apply_domain_check(candidate: Candidate) -> Candidate:
    candidate.email_domain = candidate.email.partition("@")[2].lower()
    if candidate.email_domain in PERSONAL_EMAIL_DOMAINS:
        candidate.domain_match = "PERSONAL_EMAIL"
    elif not candidate.company_domain:
        candidate.domain_match = "UNKNOWN"
    elif (
        candidate.email_domain == candidate.company_domain
        or candidate.email_domain.endswith(f".{candidate.company_domain}")
    ):
        candidate.domain_match = "MATCH"
    else:
        candidate.domain_match = "MISMATCH"
    return candidate


def is_company_affiliation(value: str) -> bool:
    text = clean(value)
    return bool(text) and not ACADEMIC_OR_PUBLIC_RE.search(text)


def first_company(items: Any) -> str:
    return next((item for item in values(items) if is_company_affiliation(item)), "")


def citation_link(record: dict[str, Any]) -> str:
    for link in record.get("links") or []:
        if isinstance(link, dict) and link.get("rel") == "citation":
            return clean(link.get("href"))
    return ""


def normalize_nsf(payload: Any) -> list[Candidate]:
    response = payload.get("response", {}) if isinstance(payload, dict) else {}
    awards = response.get("award") or []
    if isinstance(awards, dict):
        awards = [awards]
    result = []
    for award in awards:
        email = valid_email(award.get("piEmail"))
        program = clean(award.get("fundProgramName"))
        title = clean(award.get("title"))
        abstract = clean(award.get("abstractText"))
        if not email or not re.search(r"\b(SBIR|STTR)\b", " ".join((program, title, abstract)), re.I):
            continue
        award_id = clean(award.get("id"))
        result.append(
            Candidate(
                expert_name=clean(award.get("pdPIName")),
                email=email,
                company=clean(award.get("awardeeName") or award.get("awardee")),
                role="Principal Investigator",
                project_title=title,
                project_url=(
                    f"https://www.nsf.gov/awardsearch/showAward?AWD_ID={award_id}"
                    if award_id
                    else ""
                ),
                sources=["NSF"],
                evidence=[f"{program or 'SBIR/STTR'} award", "company-funded R&D project"],
                confidence_score=98,
                qualification="PRODUCTION_RND",
            )
        )
    return result


def unwrap_sbir(payload: Any) -> list[dict[str, Any]]:
    if isinstance(payload, list):
        return [item for item in payload if isinstance(item, dict)]
    if isinstance(payload, dict):
        for key in ("awards", "results", "data", "items"):
            if isinstance(payload.get(key), list):
                return [item for item in payload[key] if isinstance(item, dict)]
    return []


def normalize_sbir(payload: Any) -> list[Candidate]:
    result = []
    for award in unwrap_sbir(payload):
        email = valid_email(award.get("pi_email"))
        if not email:
            continue
        phase = clean(award.get("phase"))
        company_website = award.get("company_url") or award.get("company_website")
        result.append(
            Candidate(
                expert_name=clean(award.get("pi_name")),
                email=email,
                company=clean(award.get("firm") or award.get("company")),
                role=clean(award.get("pi_title")) or "Principal Investigator",
                project_title=clean(award.get("award_title")),
                project_url=clean(award.get("award_link")),
                sources=["SBIR"],
                evidence=[f"SBIR/STTR {phase}".strip(), "company R&D principal investigator"],
                confidence_score=100,
                qualification="PRODUCTION_RND",
                company_domain=website_domain(company_website),
            )
        )
    return result


def normalize_osti(payload: Any) -> list[Candidate]:
    records = payload if isinstance(payload, list) else []
    result = []
    for record in records:
        company = first_company(record.get("research_orgs"))
        sponsors = " | ".join(values(record.get("sponsor_orgs")))
        is_sbir = bool(re.search(r"\b(SBIR|STTR)\b", sponsors, re.I))
        if not company or not is_sbir:
            continue
        for author in record.get("authors") or []:
            if isinstance(author, dict):
                author_text = " ".join(values(author))
                name = clean(author.get("name") or author.get("full_name"))
            else:
                author_text = clean(author)
                name = EMAIL_RE.sub("", author_text)
                name = re.sub(r"\s*[<(\[].*$", "", name).strip(" ,;-")
            email = valid_email(author_text)
            if not email:
                continue
            result.append(
                Candidate(
                    expert_name=name,
                    email=email,
                    company=company,
                    role="Technical report author",
                    project_title=clean(record.get("title")),
                    project_url=citation_link(record),
                    sources=["OSTI"],
                    evidence=["DOE SBIR/STTR technical output", "company research organization"],
                    confidence_score=92,
                    qualification="PRODUCTION_RND",
                )
            )
    return result


def email_from_ietf_resource(value: Any) -> str:
    text = urllib.parse.unquote(clean(value)).rstrip("/")
    return valid_email(text.rsplit("/", 1)[-1])


def normalize_ietf(payload: Any) -> list[Candidate]:
    objects = payload.get("objects", []) if isinstance(payload, dict) else []
    result = []
    for author in objects:
        company = clean(author.get("affiliation"))
        email = valid_email(author.get("email_address")) or email_from_ietf_resource(
            author.get("email")
        )
        if not email or not is_company_affiliation(company):
            continue
        document = clean(author.get("document"))
        document_name = urllib.parse.unquote(document.rstrip("/").rsplit("/", 1)[-1])
        result.append(
            Candidate(
                expert_name=clean(author.get("person_name") or author.get("name")),
                email=email,
                company=company,
                role="IETF standards author",
                project_title=document_name,
                project_url=(
                    f"https://datatracker.ietf.org/doc/{document_name}/" if document_name else ""
                ),
                sources=["IETF"],
                evidence=["IETF technical document author", "company affiliation"],
                confidence_score=80,
                qualification="PRODUCTION_RND",
            )
        )
    return result


def normalize_orcid(payload: Any) -> list[Candidate]:
    records = payload.get("expanded-result", []) if isinstance(payload, dict) else []
    result = []
    for record in records:
        company = first_company(record.get("institution-name"))
        if not company:
            continue
        name = clean(
            record.get("credit-name")
            or " ".join(
                part
                for part in (
                    clean(record.get("given-names")),
                    clean(record.get("family-names")),
                )
                if part
            )
        )
        orcid_id = clean(record.get("orcid-id"))
        for raw_email in values(record.get("email")):
            email = valid_email(raw_email)
            if not email:
                continue
            result.append(
                Candidate(
                    expert_name=name,
                    email=email,
                    company=company,
                    role="ORCID researcher",
                    project_title="",
                    project_url=f"https://orcid.org/{orcid_id}" if orcid_id else "",
                    sources=["ORCID"],
                    evidence=["public ORCID email", "company affiliation; R&D role not proven"],
                    confidence_score=55,
                    qualification="NEEDS_RND_CONFIRMATION",
                )
            )
    return result


NORMALIZERS = {
    "nsf": normalize_nsf,
    "sbir": normalize_sbir,
    "osti": normalize_osti,
    "ietf": normalize_ietf,
    "orcid": normalize_orcid,
}


def request_json(
    url: str,
    *,
    timeout: float,
    headers: dict[str, str] | None = None,
    data: bytes | None = None,
) -> Any:
    request_headers = {
        "Accept": "application/json",
        "User-Agent": "rnd-expert-email-probe/1.0",
    }
    request_headers.update(headers or {})
    request = urllib.request.Request(url, headers=request_headers, data=data)
    with urllib.request.urlopen(request, timeout=timeout) as response:
        charset = response.headers.get_content_charset() or "utf-8"
        body = response.read().decode(charset, errors="replace")
    return json.loads(body)


def query_url(base: str, params: dict[str, Any]) -> str:
    return f"{base}?{urllib.parse.urlencode(params)}"


def count_records(source: str, payload: Any) -> int:
    if source == "nsf" and isinstance(payload, dict):
        awards = payload.get("response", {}).get("award") or []
        return 1 if isinstance(awards, dict) else len(awards)
    if source == "sbir":
        return len(unwrap_sbir(payload))
    if source in {"ietf", "orcid"} and isinstance(payload, dict):
        key = "objects" if source == "ietf" else "expanded-result"
        return len(payload.get(key) or [])
    return len(payload) if isinstance(payload, list) else 0


def fetch_nsf(args: argparse.Namespace) -> SourceResult:
    url = query_url(
        "https://api.nsf.gov/services/v1/awards.json",
        {
            "fundProgramName": "SBIR Phase II",
            "dateStart": f"01/01/{args.since_year}",
            "rpp": min(args.limit, 25),
            "offset": 0,
        },
    )
    payload = request_json(url, timeout=args.timeout)
    return SourceResult("nsf", "ok", url, payload, count_records("nsf", payload))


def read_sbir_csv(path: Path, *, since_year: int, limit: int) -> list[dict[str, Any]]:
    result = []
    with path.open(encoding="utf-8-sig", newline="") as handle:
        for row in csv.DictReader(handle):
            normalized = {key.strip().lower().replace(" ", "_"): value for key, value in row.items()}
            year_text = clean(normalized.get("award_year"))
            if year_text.isdigit() and int(year_text) < since_year:
                continue
            result.append(normalized)
            if len(result) >= limit:
                break
    return result


def fetch_sbir(args: argparse.Namespace) -> SourceResult:
    if args.sbir_csv:
        path = Path(args.sbir_csv).expanduser().resolve()
        payload = read_sbir_csv(path, since_year=args.since_year, limit=args.limit)
        return SourceResult("sbir", "ok", str(path), payload, len(payload), "local bulk CSV")
    url = query_url(
        "https://api.www.sbir.gov/public/api/awards",
        {"year": args.since_year, "rows": args.limit, "start": 0},
    )
    payload = request_json(url, timeout=args.timeout)
    return SourceResult("sbir", "ok", url, payload, count_records("sbir", payload))


def fetch_osti(args: argparse.Namespace) -> SourceResult:
    url = query_url(
        "https://www.osti.gov/api/v1/records",
        {
            "q": "SBIR OR STTR",
            "publication_date_start": f"01/01/{args.since_year}",
            "rows": args.limit,
            "page": 1,
        },
    )
    payload = request_json(url, timeout=args.timeout)
    return SourceResult("osti", "ok", url, payload, count_records("osti", payload))


def fetch_ietf(args: argparse.Namespace) -> SourceResult:
    url = query_url(
        "https://datatracker.ietf.org/api/v1/doc/documentauthor/",
        {
            "limit": min(args.limit * 5, 100),
            "offset": 0,
            "format": "json",
            "document__time__gte": f"{args.since_year}-01-01T00:00:00Z",
        },
    )
    payload = request_json(url, timeout=args.timeout)
    objects = payload.get("objects", []) if isinstance(payload, dict) else []
    enriched = []
    for author in objects:
        if len(enriched) >= args.limit:
            break
        if not is_company_affiliation(clean(author.get("affiliation"))):
            continue
        item = dict(author)
        person_path = clean(item.get("person"))
        if person_path:
            person_url = urllib.parse.urljoin("https://datatracker.ietf.org", person_path)
            try:
                person = request_json(person_url, timeout=args.timeout)
                item["person_name"] = clean(person.get("name") or person.get("ascii"))
            except (OSError, ValueError, urllib.error.HTTPError, urllib.error.URLError):
                item["person_name"] = ""
            time.sleep(args.delay)
        enriched.append(item)
    payload = {"meta": payload.get("meta", {}), "objects": enriched}
    return SourceResult("ietf", "ok", url, payload, len(enriched))


def obtain_orcid_token(args: argparse.Namespace) -> str:
    token = clean(os.environ.get("ORCID_ACCESS_TOKEN"))
    if token:
        return token
    client_id = clean(os.environ.get("ORCID_CLIENT_ID"))
    client_secret = clean(os.environ.get("ORCID_CLIENT_SECRET"))
    if not client_id or not client_secret:
        return ""
    data = urllib.parse.urlencode(
        {
            "client_id": client_id,
            "client_secret": client_secret,
            "grant_type": "client_credentials",
            "scope": "/read-public",
        }
    ).encode("utf-8")
    payload = request_json(
        "https://orcid.org/oauth/token",
        timeout=args.timeout,
        headers={"Content-Type": "application/x-www-form-urlencoded"},
        data=data,
    )
    return clean(payload.get("access_token")) if isinstance(payload, dict) else ""


def fetch_orcid(args: argparse.Namespace) -> SourceResult:
    token = obtain_orcid_token(args)
    if not token:
        return SourceResult(
            "orcid",
            "skipped",
            "https://pub.orcid.org/v3.0/expanded-search/",
            {},
            note="set ORCID_ACCESS_TOKEN or ORCID_CLIENT_ID + ORCID_CLIENT_SECRET",
        )
    query = args.orcid_query or (
        'affiliation-org-name:(Inc OR Corporation OR LLC OR Ltd OR GmbH OR "Co., Ltd.")'
    )
    url = query_url(
        "https://pub.orcid.org/v3.0/expanded-search/",
        {"q": query, "rows": args.limit, "start": 0},
    )
    payload = request_json(
        url,
        timeout=args.timeout,
        headers={
            "Accept": "application/vnd.orcid+json",
            "Authorization": f"Bearer {token}",
        },
    )
    return SourceResult("orcid", "ok", url, payload, count_records("orcid", payload))


FETCHERS = {
    "nsf": fetch_nsf,
    "sbir": fetch_sbir,
    "osti": fetch_osti,
    "ietf": fetch_ietf,
    "orcid": fetch_orcid,
}


def load_fixture(source: str, fixture_dir: Path) -> SourceResult:
    path = fixture_dir / f"{source}.json"
    payload = json.loads(path.read_text(encoding="utf-8"))
    return SourceResult(
        source,
        "fixture",
        str(path),
        payload,
        count_records(source, payload),
    )


def merge_candidates(candidates: Iterable[Candidate]) -> list[Candidate]:
    merged: dict[str, Candidate] = {}
    for candidate in candidates:
        email = valid_email(candidate.email)
        if not email:
            continue
        candidate.email = email
        apply_domain_check(candidate)
        current = merged.get(email)
        if current is None:
            candidate.sources = list(dict.fromkeys(candidate.sources))
            candidate.evidence = list(dict.fromkeys(candidate.evidence))
            merged[email] = candidate
            continue
        for attr in (
            "expert_name",
            "company",
            "role",
            "project_title",
            "project_url",
            "company_domain",
        ):
            if not getattr(current, attr) and getattr(candidate, attr):
                setattr(current, attr, getattr(candidate, attr))
        current.sources = list(dict.fromkeys(current.sources + candidate.sources))
        current.evidence = list(dict.fromkeys(current.evidence + candidate.evidence))
        current.confidence_score = max(current.confidence_score, candidate.confidence_score)
        if candidate.qualification == "PRODUCTION_RND":
            current.qualification = "PRODUCTION_RND"
        apply_domain_check(current)
    source_rank = {label: index for index, label in enumerate(SOURCE_LABELS.values())}
    for candidate in merged.values():
        candidate.sources.sort(key=lambda value: source_rank.get(value, 999))
    return sorted(
        merged.values(),
        key=lambda item: (-item.confidence_score, item.email),
    )


CSV_FIELDS = (
    "expert_name",
    "email",
    "company",
    "email_domain",
    "company_domain",
    "domain_match",
    "role",
    "project_title",
    "project_url",
    "sources",
    "evidence",
    "confidence_score",
    "qualification",
)


def csv_row(candidate: Candidate) -> dict[str, Any]:
    row = asdict(candidate)
    row["sources"] = "|".join(candidate.sources)
    row["evidence"] = "|".join(candidate.evidence)
    return row


def write_csv(path: Path, candidates: list[Candidate]) -> None:
    with path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=CSV_FIELDS)
        writer.writeheader()
        writer.writerows(csv_row(candidate) for candidate in candidates)


def mask_email(email: str) -> str:
    local, separator, domain = email.partition("@")
    return f"{local[:1]}***{separator}{domain}" if separator else "***"


def parse_sources(value: str) -> list[str]:
    requested = [item.strip().lower() for item in value.split(",") if item.strip()]
    unknown = [item for item in requested if item not in SOURCE_ORDER]
    if unknown:
        raise argparse.ArgumentTypeError(f"unknown sources: {','.join(unknown)}")
    return list(dict.fromkeys(requested))


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Probe public APIs for production-R&D experts with public emails."
    )
    parser.add_argument("--sources", default=",".join(SOURCE_ORDER))
    parser.add_argument("--limit", type=int, default=25)
    parser.add_argument("--since-year", type=int, default=datetime.now().year - 1)
    parser.add_argument("--timeout", type=float, default=20.0)
    parser.add_argument("--delay", type=float, default=0.05)
    parser.add_argument("--output-dir", default="tmp/rnd-expert-email-probe")
    parser.add_argument("--sbir-csv", help="Optional official SBIR bulk CSV path.")
    parser.add_argument("--orcid-query", help="Override the ORCID affiliation query.")
    parser.add_argument("--show-emails", action="store_true", help="Print unmasked emails.")
    parser.add_argument("--fixture-dir", help=argparse.SUPPRESS)
    return parser


def probe(args: argparse.Namespace) -> int:
    try:
        requested = parse_sources(args.sources)
    except argparse.ArgumentTypeError as error:
        print(error, file=sys.stderr)
        return 2
    if args.limit < 1:
        print("--limit must be positive", file=sys.stderr)
        return 2

    output_dir = Path(args.output_dir).expanduser().resolve()
    raw_dir = output_dir / "raw"
    raw_dir.mkdir(parents=True, exist_ok=True)
    fixture_dir = Path(args.fixture_dir).resolve() if args.fixture_dir else None

    statuses: dict[str, dict[str, Any]] = {}
    all_candidates: list[Candidate] = []
    for source in requested:
        try:
            result = (
                load_fixture(source, fixture_dir)
                if fixture_dir
                else FETCHERS[source](args)
            )
        except (OSError, ValueError, json.JSONDecodeError, urllib.error.HTTPError, urllib.error.URLError) as error:
            result = SourceResult(source, "error", "", {}, note=f"{type(error).__name__}: {error}")

        if result.status in {"ok", "fixture"}:
            (raw_dir / f"{source}.json").write_text(
                json.dumps(result.payload, ensure_ascii=False, indent=2), encoding="utf-8"
            )
            candidates = NORMALIZERS[source](result.payload)
        else:
            candidates = []
        strict_count = sum(item.qualification == "PRODUCTION_RND" for item in candidates)
        statuses[source] = {
            "status": result.status,
            "endpoint": result.endpoint,
            "records_received": result.records_received,
            "email_candidates": len(candidates),
            "strict_candidates": strict_count,
            "email_yield": (
                round(len(candidates) / result.records_received, 4)
                if result.records_received
                else 0
            ),
            "note": result.note,
        }
        all_candidates.extend(candidates)

    merged = merge_candidates(all_candidates)
    strict = [item for item in merged if item.qualification == "PRODUCTION_RND"]
    write_csv(output_dir / "experts_all.csv", merged)
    write_csv(output_dir / "experts_strict.csv", strict)

    ranking = sorted(
        requested,
        key=lambda source: (
            -statuses[source]["strict_candidates"],
            -statuses[source]["email_yield"],
            SOURCE_ORDER.index(source),
        ),
    )
    summary = {
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "criteria": "public personal email plus production-R&D evidence",
        "sources": statuses,
        "totals": {
            "all_unique_emails": len(merged),
            "strict_unique_emails": len(strict),
            "domain_match_counts": {
                status: sum(item.domain_match == status for item in merged)
                for status in ("MATCH", "MISMATCH", "PERSONAL_EMAIL", "UNKNOWN")
            },
        },
        "measured_source_ranking": [SOURCE_LABELS[item] for item in ranking],
        "files": {
            "all": str(output_dir / "experts_all.csv"),
            "strict": str(output_dir / "experts_strict.csv"),
            "raw": str(raw_dir),
        },
    }
    (output_dir / "summary.json").write_text(
        json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8"
    )

    for source in requested:
        item = statuses[source]
        print(
            f"[{SOURCE_LABELS[source]}] {item['status']} records={item['records_received']} "
            f"emails={item['email_candidates']} strict={item['strict_candidates']}"
            + (f" note={item['note']}" if item["note"] else "")
        )
    print(f"unique emails={len(merged)} strict={len(strict)}")
    for candidate in merged[:10]:
        shown_email = candidate.email if args.show_emails else mask_email(candidate.email)
        print(
            f"- {shown_email} | {candidate.expert_name or '?'} | "
            f"{candidate.company or '?'} | {candidate.domain_match} | "
            f"{candidate.qualification}"
        )
    print(f"output={output_dir}")
    return 0


def main() -> int:
    return probe(build_parser().parse_args())


if __name__ == "__main__":
    raise SystemExit(main())
