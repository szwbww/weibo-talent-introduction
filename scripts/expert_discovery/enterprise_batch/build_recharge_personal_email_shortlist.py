#!/usr/bin/env python3
"""Build a 400-person Apollo personal-email shortlist without consuming credits."""

import argparse
import json
import re
from collections import Counter, defaultdict
from datetime import datetime, timezone
from pathlib import Path

from research_field_normalization import assert_english_research_field, primary_research_field


ROOT = Path(__file__).resolve().parents[3]
CORE_AREAS = {
    "Advanced semiconductors": {
        "芯片设计与EDA", "半导体设备", "半导体材料", "半导体封装",
        "半导体检测量测", "PCB与电子互连",
    },
    "Artificial intelligence": {"人工智能与工业软件", "工业机器人"},
    "Quantum technology": {
        "量子通信", "超导", "硅基", "离子阱", "量子计算", "量子精密测量", "量子信息",
    },
    "Biotechnology": {"生物医药", "农业生物技术"},
}
ALLOWED_COUNTRIES = {"美国", "加拿大", "日本", "韩国", "中国台湾", "新加坡", "以色列"}
COUNTRY_SCORE = {"美国": 30, "加拿大": 25, "日本": 25, "韩国": 22, "中国台湾": 22, "新加坡": 20, "以色列": 20}


def normalize(value):
    return re.sub(r"[^a-z0-9]", "", (value or "").lower())


def title_score(title):
    text = (title or "").lower()
    if any(value in text for value in ("sales", "marketing", "recruit", "human resources", "business development")):
        return -1000
    points = (
        ("chief scientist", 100), ("distinguished", 95), ("principal", 85),
        ("fellow", 80), ("chief", 75), ("staff", 70), ("director", 65),
        ("lead", 60), ("senior", 45), ("scientist", 40), ("research", 40),
        ("r&d", 40), ("engineer", 30), ("process", 15), ("manufacturing", 15),
    )
    return sum(score for word, score in points if word in text)


def existing_attempt_ids(paths):
    attempted = set()
    for path in paths:
        if not path.exists():
            continue
        data = json.loads(path.read_text(encoding="utf-8"))
        attempted.update((data.get("results") or {}).keys())
    return attempted


def existing_contacted_ids(es_path, imported_path):
    if not es_path.exists() or not imported_path.exists():
        return set()
    hits = json.loads(es_path.read_text(encoding="utf-8")).get("hits", {}).get("hits", [])
    status_by_orcid = {
        hit.get("_source", {}).get("orcidId"): hit.get("_source", {}).get("operatorStatus") or "NOT_CONTACTED"
        for hit in hits
    }
    documents = json.loads(imported_path.read_text(encoding="utf-8"))
    return {
        doc.get("_source", {}).get("externalIds", {}).get("apolloPersonId")
        for doc in documents
        if status_by_orcid.get(doc.get("_source", {}).get("orcidId")) != "NOT_CONTACTED"
    }


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--limit", type=int, default=400)
    parser.add_argument("--max-per-company", type=int, default=10)
    parser.add_argument("--output-dir", default=str(ROOT / "outputs/personal-email-recharge-2-20260916"))
    args = parser.parse_args()

    source_dir = ROOT / "outputs/enterprise-rnd-filtered-20260914"
    directory = json.loads((source_dir / "engineer_directory.json").read_text(encoding="utf-8"))
    benchmarks = {
        row["benchmark_id"]: row
        for row in json.loads((source_dir / "benchmark_directory.json").read_text(encoding="utf-8")).get("merged_benchmark_enterprises", [])
    }
    engineers = {
        row["engineer_id"]: row for row in directory.get("engineers", [])
        if row.get("apollo_person_id") and row.get("apollo_has_email") is True
    }
    output_dir = Path(args.output_dir)
    prior_attempt_files = (
        ROOT / "outputs/personal-email-enrichment-20260916/personal_email_results.json",
        ROOT / "outputs/personal-email-recharge-20260916/personal_email_results.json",
    )
    excluded = existing_attempt_ids(prior_attempt_files)
    excluded |= existing_contacted_ids(
        ROOT / "outputs/personal-email-priority-20260916/current_es_snapshot.json",
        source_dir / "candidate_documents.json",
    )

    candidates = defaultdict(list)
    company_domestic = defaultdict(set)
    for relation in directory.get("enterprise_engineer_relations", []):
        theme = next((name for name, areas in CORE_AREAS.items() if relation.get("matched_technical_area") in areas), None)
        if theme is None:
            continue
        engineer = engineers.get(relation.get("engineer_id"))
        if not engineer or engineer["apollo_person_id"] in excluded:
            continue
        score = title_score(engineer.get("title"))
        if score < 0:
            continue
        for benchmark_id in relation.get("source_benchmark_ids") or []:
            benchmark = benchmarks.get(benchmark_id)
            if not benchmark or benchmark.get("country") not in ALLOWED_COUNTRIES:
                continue
            if normalize(engineer.get("current_organization")) != normalize(benchmark.get("benchmark_name")):
                continue
            company_key = (benchmark_id, theme)
            company_domestic[company_key].add(relation.get("enterprise_id"))
            candidates[engineer["apollo_person_id"]].append({
                "engineer": engineer,
                "benchmark": benchmark,
                "relation": relation,
                "theme": theme,
                "titleScore": score,
            })

    # Retain each engineer's strongest exact employer and technology relation.
    best = []
    for person_id, options in candidates.items():
        options.sort(key=lambda row: (
            -row["titleScore"],
            -len(company_domestic[(row["benchmark"]["benchmark_id"], row["theme"])]),
            -COUNTRY_SCORE[row["benchmark"]["country"]],
            row["benchmark"]["benchmark_name"],
        ))
        best.append(options[0])

    for row in best:
        key = (row["benchmark"]["benchmark_id"], row["theme"])
        row["domesticMatchCount"] = len(company_domestic[key])
        row["priorityScore"] = row["titleScore"] * 1000 + row["domesticMatchCount"] * 10 + COUNTRY_SCORE[row["benchmark"]["country"]]

    by_company = defaultdict(list)
    for row in best:
        by_company[(row["benchmark"]["benchmark_id"], row["theme"])].append(row)
    for rows in by_company.values():
        rows.sort(key=lambda row: (-row["priorityScore"], row["engineer"]["name"], row["engineer"]["apollo_person_id"]))

    # Round-robin across the most relevant companies; prevents a few large US firms
    # from consuming the entire credit envelope.
    company_order = sorted(
        by_company,
        key=lambda key: (
            -len(company_domestic[key]),
            -max(row["titleScore"] for row in by_company[key]),
            by_company[key][0]["benchmark"]["benchmark_name"],
        ),
    )
    selected = []
    rounds = 0
    while len(selected) < args.limit:
        added = 0
        for key in company_order:
            rows = by_company[key]
            if rounds >= min(len(rows), args.max_per_company) or len(selected) >= args.limit:
                continue
            row = rows[rounds]
            engineer, benchmark, relation = row["engineer"], row["benchmark"], row["relation"]
            primary_field, primary_field_source = primary_research_field(
                [relation.get("matched_technical_area", "")], engineer.get("title", "")
            )
            assert_english_research_field(primary_field)
            selected.append({
                "selectionRank": len(selected) + 1,
                "engineerId": engineer["engineer_id"],
                "apolloPersonId": engineer["apollo_person_id"],
                "name": engineer.get("name", ""),
                "companyNameEn": benchmark.get("benchmark_name", ""),
                "companyCountry": benchmark.get("country", ""),
                "jobTitle": engineer.get("title", ""),
                "matchedTechnicalAreaZh": relation.get("matched_technical_area", ""),
                "primaryResearchField": primary_field,
                "primaryResearchFieldSource": primary_field_source,
                "priorityTheme": row["theme"],
                "domesticEnterpriseMatchCount": row["domesticMatchCount"],
                "titleScore": row["titleScore"],
                "selectionReason": "核心技术方向；当前任职于海外对标企业；重点国家；高研发职位；未进行过个人邮箱补全",
                "apolloPersonalEmailAction": "REVEAL_PERSONAL_EMAIL_ONLY",
                "esImportRule": "IMPORT_ONLY_IF_PERSONAL_EMAIL_RETURNED",
                "onlineImportPlan": {
                    "funnelLevel": "CANDIDATE",
                    "operatorStatus": "NOT_CONTACTED (omit field in ES)",
                    "expertType": "PRODUCTION_RND",
                    "tag": "Apollo已验证个人邮箱",
                    "requiresCurrentTaskTagChange": True,
                },
            })
            added += 1
        if not added:
            break
        rounds += 1

    report = {
        "generatedAt": datetime.now(timezone.utc).isoformat(timespec="seconds"),
        "selectionSize": len(selected),
        "excludedPreviousApolloPersonalEmailAttempts": len(existing_attempt_ids(prior_attempt_files)),
        "excludedContactedOrInvalidInES": len(existing_contacted_ids(
            ROOT / "outputs/personal-email-priority-20260916/current_es_snapshot.json", source_dir / "candidate_documents.json")),
        "selectionRules": {
            "countries": sorted(ALLOWED_COUNTRIES),
            "coreThemes": sorted(CORE_AREAS),
            "maxPerCompany": args.max_per_company,
            "personalEmailOnly": True,
            "apolloRequest": "bulk_match?reveal_personal_emails=true&reveal_phone_number=false&run_waterfall_email=false",
        },
        "onlineSendReadiness": {
            "requiredEmail": "personalEmail returned by Apollo",
            "funnelLevel": "CANDIDATE",
            "operatorStatus": "NOT_CONTACTED (ES field omitted)",
            "expertType": "PRODUCTION_RND",
            "templateRequiredField": "primaryResearchField (English)",
            "personalEmailTag": "Apollo已验证个人邮箱",
            "currentTaskTag": "Apollo已验证个人邮箱",
            "blockedUntilTaskTagIsChanged": False,
            "reachabilityFilter": "UNKNOWN_ONLY is present in DB config but is not passed into deployed RecipientScope filtering",
        },
        "eligibleBeforeCompanyCapByTheme": dict(sorted(Counter(row["theme"] for row in best).items())),
        "eligibleBeforeCompanyCapByCountry": dict(sorted(Counter(row["benchmark"]["country"] for row in best).items())),
        "selectedByTheme": dict(sorted(Counter(row["priorityTheme"] for row in selected).items())),
        "selectedByCountry": dict(sorted(Counter(row["companyCountry"] for row in selected).items())),
        "selectedByCompany": dict(sorted(Counter(row["companyNameEn"] for row in selected).items())),
        "selectedByTechnicalArea": dict(sorted(Counter(row["matchedTechnicalAreaZh"] for row in selected).items())),
    }
    output_dir.mkdir(parents=True, exist_ok=True)
    (output_dir / "personal_email_recharge_shortlist.json").write_text(json.dumps(selected, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    (output_dir / "personal_email_recharge_shortlist_report.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
