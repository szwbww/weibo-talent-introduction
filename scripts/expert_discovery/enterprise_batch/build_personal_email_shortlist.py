#!/usr/bin/env python3
"""Build a personal-email-only shortlist from the existing engineer directory."""

import json
import re
from collections import defaultdict
from pathlib import Path


ROOT = Path(__file__).resolve().parents[3]
OUTPUT = ROOT / "outputs/personal-email-priority-20260916"
ENGINEERS = ROOT / "outputs/enterprise-rnd-filtered-20260914/engineer_directory.json"
BENCHMARKS = ROOT / "outputs/enterprise-rnd-filtered-20260914/benchmark_directory.json"
CURRENT_ES = OUTPUT / "current_es_snapshot.json"
IMPORTED_DOCS = ROOT / "outputs/enterprise-rnd-filtered-20260914/candidate_documents.json"

# Use the 80-person credit envelope requested by the operator.
TARGETS = {
    "Advanced semiconductors": {
        "NVIDIA": 6, "Lam Research": 5, "KLA": 5, "Applied Materials": 5,
        "Tokyo Electron": 4, "Wolfspeed": 3, "Entegris": 4,
    },
    "Artificial intelligence": {
        "OpenAI": 6, "Anthropic": 5, "Hugging Face": 6, "Agility Robotics": 5,
        "Boston Dynamics": 5, "C3.ai": 5,
    },
    "Biotechnology": {
        "Genentech": 4, "AbbVie": 4, "Catalent": 3, "Pfizer": 3, "Gilead Sciences": 2,
    },
}
THEME_AREAS = {
    "Advanced semiconductors": {"芯片设计与EDA", "半导体设备", "半导体材料", "半导体封装", "半导体检测量测", "PCB与电子互连"},
    "Artificial intelligence": {"人工智能与工业软件", "工业机器人"},
    "Biotechnology": {"生物医药", "农业生物技术"},
}
ALLOWED_COUNTRIES = {"美国", "加拿大", "日本", "韩国", "中国台湾", "新加坡", "以色列"}


def normalize(value):
    return re.sub(r"[^a-z0-9]", "", (value or "").lower())


def title_score(title):
    text = (title or "").lower()
    return sum(points for token, points in (
        ("principal", 50), ("chief", 50), ("staff", 45), ("lead", 40), ("director", 38),
        ("senior", 30), ("scientist", 25), ("research", 25), ("engineer", 20),
    ) if token in text)


def contacted_person_ids():
    current = json.loads(CURRENT_ES.read_text(encoding="utf-8"))["hits"]["hits"]
    status_by_orcid = {
        hit["_source"].get("orcidId"): hit["_source"].get("operatorStatus") or "NOT_CONTACTED"
        for hit in current
    }
    docs = json.loads(IMPORTED_DOCS.read_text(encoding="utf-8"))
    return {
        item["_source"].get("externalIds", {}).get("apolloPersonId")
        for item in docs
        if status_by_orcid.get(item["_source"].get("orcidId")) != "NOT_CONTACTED"
    }


def main():
    directory = json.loads(ENGINEERS.read_text(encoding="utf-8"))
    benchmark_directory = json.loads(BENCHMARKS.read_text(encoding="utf-8"))
    benchmarks = {row["benchmark_id"]: row for row in benchmark_directory["merged_benchmark_enterprises"]}
    engineers = {
        row["engineer_id"]: row for row in directory["engineers"]
        if row.get("apollo_person_id") and row.get("apollo_has_email") is True
    }
    blocked = contacted_person_ids()
    candidates = defaultdict(list)

    for relation in directory["enterprise_engineer_relations"]:
        engineer = engineers.get(relation.get("engineer_id"))
        if not engineer or engineer["apollo_person_id"] in blocked:
            continue
        for theme, areas in THEME_AREAS.items():
            if relation.get("matched_technical_area") not in areas:
                continue
            for benchmark_id in relation.get("source_benchmark_ids") or []:
                benchmark = benchmarks.get(benchmark_id)
                if not benchmark or benchmark.get("country") not in ALLOWED_COUNTRIES:
                    continue
                company = benchmark.get("benchmark_name", "")
                if company not in TARGETS[theme]:
                    continue
                if normalize(engineer.get("current_organization")) != normalize(company):
                    continue
                candidates[(theme, company)].append({
                    "apolloPersonId": engineer["apollo_person_id"],
                    "engineerId": engineer["engineer_id"],
                    "name": engineer.get("name", ""),
                    "companyNameEn": company,
                    "companyCountry": benchmark.get("country"),
                    "jobTitle": engineer.get("title", ""),
                    "matchedTechnicalAreaZh": relation.get("matched_technical_area"),
                    "priorityTheme": theme,
                    "apolloPersonalEmailAction": "REVEAL_PERSONAL_EMAIL_ONLY",
                    "esImportRule": "IMPORT_ONLY_IF_PERSONAL_EMAIL_RETURNED",
                    "titleScore": title_score(engineer.get("title")),
                })

    selected, availability = [], {}
    for theme, companies in TARGETS.items():
        for company, quota in companies.items():
            unique = {row["apolloPersonId"]: row for row in candidates[(theme, company)]}
            ranked = sorted(unique.values(), key=lambda row: (-row["titleScore"], row["name"], row["apolloPersonId"]))
            availability[f"{theme} / {company}"] = len(ranked)
            for rank, row in enumerate(ranked[:quota], 1):
                row["companyRank"] = rank
                selected.append(row)

    report = {
        "source": "Existing enterprise-engineer directory only; no new people search",
        "existing_engineers_with_apollo_person_id_and_email_signal": len(engineers),
        "excluded_previously_contacted_or_invalid": len(blocked),
        "shortlist_size": len(selected),
        "required_batch_send_round_size": 20,
        "last_confirmed_apollo_credits": 82,
        "planned_credit_envelope": 80,
        "credit_reserve": 0,
        "quantum_candidates_in_existing_engineer_directory": 0,
        "available_per_target_company": availability,
        "policy": {
            "request": "bulk_match?reveal_personal_emails=true&reveal_phone_number=false&run_waterfall_email=false",
            "accept": "A returned personal email only",
            "reject": "Business email, no personal email, GDPR-region suppression, or any already-contacted person",
            "send_gate": "Candidate + valid email + NOT_CONTACTED + Apollo tag + R&D type + template required fields",
        },
    }
    OUTPUT.mkdir(parents=True, exist_ok=True)
    (OUTPUT / "personal_email_enrichment_shortlist.json").write_text(json.dumps(selected, ensure_ascii=False, indent=2), encoding="utf-8")
    (OUTPUT / "personal_email_enrichment_shortlist_report.json").write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(report, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
