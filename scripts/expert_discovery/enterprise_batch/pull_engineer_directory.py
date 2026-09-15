#!/usr/bin/env python3
"""Pull one Apollo People Search page per known benchmark; never enrich emails."""
from __future__ import annotations

import argparse
import concurrent.futures
import hashlib
import json
import random
import re
import subprocess
import threading
import time
import urllib.parse
from collections import defaultdict
from pathlib import Path


WRITE_LOCK = threading.Lock()


def load_properties(path: str) -> dict[str, str]:
    values: dict[str, str] = {}
    for raw in Path(path).read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if line and not line.startswith("#") and "=" in line:
            key, value = line.split("=", 1)
            values[key.strip()] = value.strip()
    return values


def atomic_json(path: Path, value) -> None:
    temp = path.with_suffix(path.suffix + ".tmp")
    temp.write_text(json.dumps(value, ensure_ascii=False, indent=2), encoding="utf-8")
    temp.replace(path)


def normalize_domain(value: str) -> str:
    text = re.sub(r"^https?://", "", str(value or "").strip().lower())
    return text.split("/", 1)[0].split(":", 1)[0].removeprefix("www.")


def normalize_name(value: str) -> str:
    text = re.sub(r"[^a-z0-9]+", " ", str(value or "").lower()).strip()
    suffixes = {"group", "holding", "holdings", "inc", "corp", "corporation", "ltd",
                "limited", "plc", "llc", "spa", "gmbh", "ag", "company", "co", "se", "sa"}
    parts = text.split()
    while len(parts) > 1 and parts[-1] in suffixes:
        parts.pop()
    return " ".join(parts)


def quote_curl(value: str) -> str:
    return str(value).replace("\\", "\\\\").replace('"', '\\"')


def curl_json(url: str, api_key: str, timeout: int = 75, retries: int = 4) -> dict:
    last_error = ""
    for attempt in range(retries):
        config = [
            'request = "POST"', f'url = "{quote_curl(url)}"', "silent", "show-error",
            "ipv4", "http1.1", f'max-time = "{timeout}"',
            f'header = "x-api-key: {quote_curl(api_key)}"',
            'header = "Content-Type: application/json"', 'header = "Accept: application/json"',
            'data = "{}"',
        ]
        try:
            proc = subprocess.run(
                ["curl", "--config", "-", "--write-out", "\n%{http_code}"],
                input="\n".join(config), text=True, capture_output=True,
                timeout=timeout + 10, check=False,
            )
            raw, separator, status = proc.stdout.rpartition("\n")
            if proc.returncode == 0 and separator and status.isdigit() and int(status) < 400:
                return json.loads(raw)
            last_error = f"curl={proc.returncode} http={status} {proc.stderr[-200:]} {raw[:200]}"
            if proc.returncode == 0 and status not in {"429", "500", "502", "503", "504"}:
                break
        except Exception as exc:
            last_error = str(exc)
        time.sleep(min(12.0, 1.5 * (2 ** attempt)) + random.random())
    raise RuntimeError(last_error[:500])


def apollo_people_search(api_key: str, domain: str, titles: list[str], per_page: int) -> dict:
    params = [
        ("q_organization_domains_list[]", domain),
        ("include_similar_titles", "true"),
        ("page", "1"),
        ("per_page", str(per_page)),
    ]
    params.extend(("person_titles[]", title) for title in titles[:12])
    url = "https://api.apollo.io/api/v1/mixed_people/api_search?" + urllib.parse.urlencode(params)
    return curl_json(url, api_key)


def belongs_to_benchmark(person: dict, benchmark: dict) -> bool:
    organization = person.get("organization") or {}
    actual_domain = normalize_domain(organization.get("primary_domain") or organization.get("domain"))
    expected_domain = normalize_domain(benchmark.get("official_domain"))
    if actual_domain:
        return actual_domain == expected_domain
    actual_name = normalize_name(organization.get("name"))
    expected_name = normalize_name(benchmark.get("benchmark_name"))
    return not actual_name or actual_name == expected_name


def person_name(person: dict) -> str:
    if person.get("name"):
        return str(person["name"]).strip()
    last = person.get("last_name") or person.get("last_name_obfuscated") or ""
    return " ".join(str(v).strip() for v in (person.get("first_name"), last) if str(v or "").strip())


def stable_id(prefix: str, *parts: str) -> str:
    digest = hashlib.sha1("\x1f".join(parts).encode("utf-8")).hexdigest()[:14].upper()
    return f"{prefix}-{digest}"


def build_specs(source: dict) -> list[dict]:
    benchmarks = {row["benchmark_id"]: row for row in source["merged_benchmark_enterprises"]}
    profiles = source.get("apollo_search_profiles") or {}
    relations_by_benchmark: dict[str, list[dict]] = defaultdict(list)
    for relation in source["merged_relations"]:
        relations_by_benchmark[relation["benchmark_id"]].append(relation)
    specs = []
    for benchmark_id, benchmark in benchmarks.items():
        titles: list[str] = []
        for relation in relations_by_benchmark.get(benchmark_id, []):
            candidates = relation.get("query_titles") or (profiles.get(relation["enterprise_id"]) or {}).get("titles") or []
            for title in candidates:
                cleaned = str(title).strip()
                if cleaned and cleaned.casefold() not in {x.casefold() for x in titles}:
                    titles.append(cleaned)
        if not titles:
            titles = ["R&D Engineer", "Research Engineer", "Product Development Engineer",
                      "Process Engineer", "Manufacturing Engineer", "Principal Engineer"]
        specs.append({"benchmark": benchmark, "titles": titles[:12]})
    return sorted(specs, key=lambda item: item["benchmark"]["benchmark_id"])


def total_entries(reply: dict) -> int:
    value = reply.get("total_entries")
    if value is None:
        value = (reply.get("pagination") or {}).get("total_entries")
    try:
        return int(value or 0)
    except (TypeError, ValueError):
        return 0


def search_one(spec: dict, api_key: str, per_page: int, fixture: dict | None) -> tuple[str, dict]:
    benchmark = spec["benchmark"]
    benchmark_id = benchmark["benchmark_id"]
    domain = normalize_domain(benchmark.get("official_domain"))
    try:
        reply = fixture.get(domain, {"people": [], "pagination": {"total_entries": 0}}) if fixture is not None \
            else apollo_people_search(api_key, domain, spec["titles"], per_page)
        seen: set[str] = set()
        people = []
        for person in reply.get("people") or []:
            if not isinstance(person, dict) or not person.get("id") or not belongs_to_benchmark(person, benchmark):
                continue
            person_id = str(person["id"])
            if person_id in seen:
                continue
            seen.add(person_id)
            organization = person.get("organization") or {}
            people.append({
                "apollo_person_id": person_id,
                "name": person_name(person) or "姓名待补全",
                "title": str(person.get("title") or "").strip(),
                "seniority": str(person.get("seniority") or "").strip(),
                "country": str(person.get("country") or "").strip(),
                "city": str(person.get("city") or "").strip(),
                "linkedin_url": str(person.get("linkedin_url") or "").strip(),
                "current_organization": str(organization.get("name") or benchmark.get("benchmark_name") or "").strip(),
                "apollo_has_email": person.get("has_email") is True,
            })
        return benchmark_id, {
            "status": "SUCCESS", "domain": domain, "titles": spec["titles"],
            "apollo_total": total_entries(reply), "people": people,
        }
    except Exception as exc:
        return benchmark_id, {
            "status": "FAILED", "domain": domain, "titles": spec["titles"],
            "apollo_total": None, "people": [], "error": str(exc)[:500],
        }


def build_directory(source: dict, checkpoint: dict) -> dict:
    benchmarks = {row["benchmark_id"]: row for row in source["merged_benchmark_enterprises"]}
    relations_by_benchmark: dict[str, list[dict]] = defaultdict(list)
    for relation in source["merged_relations"]:
        relations_by_benchmark[relation["benchmark_id"]].append(relation)

    engineers: dict[str, dict] = {}
    benchmark_engineer = []
    enterprise_engineer: dict[tuple[str, str], dict] = {}
    benchmark_stats = []
    for benchmark_id, benchmark in benchmarks.items():
        result = checkpoint.get(benchmark_id) or {"status": "PENDING", "people": []}
        benchmark_stats.append({
            "benchmark_id": benchmark_id,
            "benchmark_name": benchmark.get("benchmark_name", ""),
            "official_domain": benchmark.get("official_domain", ""),
            "search_status": result.get("status", "PENDING"),
            "apollo_total": result.get("apollo_total"),
            "retrieved": len(result.get("people") or []),
            "error": result.get("error", ""),
        })
        for person in result.get("people") or []:
            apollo_id = person["apollo_person_id"]
            engineer_id = "AP-" + apollo_id
            engineers.setdefault(engineer_id, {
                "engineer_id": engineer_id,
                **person,
                "profile_review_status": "待复核",
                "email_enrichment_status": "未补全",
                "email_verification_status": "未验证",
            })
            benchmark_engineer.append({
                "benchmark_engineer_rel_id": stable_id("BE", benchmark_id, engineer_id),
                "benchmark_id": benchmark_id,
                "engineer_id": engineer_id,
                "employment_title": person.get("title", ""),
                "employment_status": "Apollo报告当前任职",
                "evidence_source": "Apollo People Search",
                "relation_review_status": "待复核",
            })
            for relation in relations_by_benchmark.get(benchmark_id, []):
                key = (relation["enterprise_id"], engineer_id)
                row = enterprise_engineer.get(key)
                reason = str(relation.get("match_reason") or "").strip()
                if row is None:
                    enterprise_engineer[key] = {
                        "enterprise_engineer_rel_id": stable_id("EE", *key),
                        "enterprise_id": relation["enterprise_id"],
                        "engineer_id": engineer_id,
                        "matched_technical_area": relation.get("matched_technical_area", ""),
                        "match_reason": reason,
                        "source_benchmark_ids": [benchmark_id],
                        "match_score": None,
                        "relation_review_status": "待复核",
                        "email_enrichment_decision": "待决定",
                        "email_enrichment_reason": "",
                    }
                elif benchmark_id not in row["source_benchmark_ids"]:
                    row["source_benchmark_ids"].append(benchmark_id)

    successful = sum(row["search_status"] == "SUCCESS" for row in benchmark_stats)
    failed = sum(row["search_status"] == "FAILED" for row in benchmark_stats)
    return {
        "metadata": {
            "source": "Apollo People Search",
            "people_search_only": True,
            "contains_email": False,
        },
        "summary": {
            "benchmarks_input": len(benchmarks),
            "benchmarks_searched": successful,
            "benchmarks_failed": failed,
            "benchmarks_pending": len(benchmarks) - successful - failed,
            "apollo_visible_total": sum(row["apollo_total"] or 0 for row in benchmark_stats),
            "unique_engineers": len(engineers),
            "benchmark_engineer_relations": len(benchmark_engineer),
            "enterprise_engineer_relations": len(enterprise_engineer),
            "apollo_enrichment_calls": 0,
            "email_verification_calls": 0,
        },
        "engineers": sorted(engineers.values(), key=lambda row: row["engineer_id"]),
        "benchmark_engineer_relations": benchmark_engineer,
        "enterprise_engineer_relations": sorted(enterprise_engineer.values(), key=lambda row: row["enterprise_engineer_rel_id"]),
        "benchmark_search_stats": benchmark_stats,
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", required=True)
    parser.add_argument("--output-dir", required=True)
    parser.add_argument("--keys")
    parser.add_argument("--fixture")
    parser.add_argument("--per-page", type=int, default=25)
    parser.add_argument("--workers", type=int, default=4)
    parser.add_argument("--limit", type=int, default=0)
    args = parser.parse_args()

    source = json.loads(Path(args.input).read_text(encoding="utf-8"))
    output_dir = Path(args.output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)
    checkpoint_path = output_dir / "apollo_people_checkpoint.json"
    checkpoint = json.loads(checkpoint_path.read_text(encoding="utf-8")) if checkpoint_path.exists() else {}
    fixture = json.loads(Path(args.fixture).read_text(encoding="utf-8")) if args.fixture else None
    api_key = ""
    if fixture is None:
        if not args.keys:
            raise SystemExit("--keys is required for live search")
        api_key = load_properties(args.keys).get("APOLLO_API_KEY", "")
        if not api_key:
            raise SystemExit("missing APOLLO_API_KEY")

    specs = build_specs(source)
    if args.limit:
        specs = specs[:args.limit]
        allowed = {item["benchmark"]["benchmark_id"] for item in specs}
        source = dict(source)
        source["merged_benchmark_enterprises"] = [row for row in source["merged_benchmark_enterprises"] if row["benchmark_id"] in allowed]
        source["merged_relations"] = [row for row in source["merged_relations"] if row["benchmark_id"] in allowed]
    pending = [spec for spec in specs if (checkpoint.get(spec["benchmark"]["benchmark_id"]) or {}).get("status") != "SUCCESS"]

    completed = 0
    with concurrent.futures.ThreadPoolExecutor(max_workers=max(1, args.workers)) as executor:
        futures = [executor.submit(search_one, spec, api_key, max(1, min(args.per_page, 100)), fixture) for spec in pending]
        for future in concurrent.futures.as_completed(futures):
            benchmark_id, result = future.result()
            with WRITE_LOCK:
                checkpoint[benchmark_id] = result
                completed += 1
                if completed % 20 == 0 or completed == len(pending):
                    atomic_json(checkpoint_path, checkpoint)
                    print(f"Apollo人员搜索 {completed}/{len(pending)}", flush=True)

    directory = build_directory(source, checkpoint)
    atomic_json(output_dir / "engineer_directory.json", directory)
    print(json.dumps(directory["summary"], ensure_ascii=False), flush=True)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
