#!/usr/bin/env python3
"""Convert the current discovery result into normalized master and relation tables."""
from __future__ import annotations

import argparse
import json
from pathlib import Path


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()
    raw = json.loads(Path(args.input).read_text(encoding="utf-8"))

    domains = sorted({r.get("海外对标域名") for r in raw["companies"] if r.get("海外对标域名")})
    benchmark_ids = {domain: f"BM-{i:04d}" for i, domain in enumerate(domains, 1)}

    domestic = []
    for r in sorted(raw["companies"], key=lambda x: x["企业编号"]):
        domestic.append({
            "enterprise_id": r["企业编号"],
            "enterprise_name": r["企业名称"],
            "source_names": "；".join(r.get("来源") or []),
            "area": r.get("板块", ""),
            "industry": r.get("行业领域", ""),
            "product": r.get("细分产品", ""),
            "core_need": r.get("核心需求", ""),
            "need_summary": r.get("BEL需求摘要", r.get("需求摘要", "")),
            "technical_area": r.get("技术领域", ""),
            "record_status": "待补充需求" if r.get("技术领域") == "待分类" else "有效",
        })

    first_company_by_domain = {}
    for r in raw["companies"]:
        domain = r.get("海外对标域名")
        if domain and domain not in first_company_by_domain:
            first_company_by_domain[domain] = r
    benchmark_counts = {}
    for e in raw["engineers"]:
        domain = e["企业域名"]
        benchmark_counts[domain] = benchmark_counts.get(domain, 0) + 1
    benchmarks = []
    for domain in domains:
        r = first_company_by_domain[domain]
        if r.get("对标状态") == "网络失败待重试":
            search_status = "网络失败待重试"
        elif benchmark_counts.get(domain, 0):
            search_status = "已检索到候选"
        else:
            search_status = "已检索暂无候选"
        benchmarks.append({
            "benchmark_id": benchmark_ids[domain],
            "benchmark_name": r.get("海外对标企业", ""),
            "official_domain": domain,
            "country": "",
            "technical_area": r.get("技术领域", ""),
            "data_source": "首轮本地规则映射；Apollo People Search",
            "benchmark_review_status": "待复核",
            "people_search_status": search_status,
            "candidate_count": benchmark_counts.get(domain, 0),
            "notes": "当前为技术领域代表企业，后续需按每家国内企业的具体产品和工艺细化",
        })

    engineers = []
    seen_engineers = set()
    for r in sorted(raw["engineers"], key=lambda x: x["工程师编号"]):
        eid = r["工程师编号"]
        if eid in seen_engineers:
            continue
        seen_engineers.add(eid)
        engineers.append({
            "engineer_id": eid,
            "name": r.get("姓名", ""),
            "title": r.get("职位", ""),
            "country": r.get("国家", ""),
            "city": r.get("城市", ""),
            "linkedin_url": r.get("LinkedIn", ""),
            "apollo_person_id": r.get("Apollo人员ID", ""),
            "profile_review_status": "待复核",
            "email_enrichment_status": "未补全",
            "email_verification_status": "未验证",
            "notes": r.get("复核说明", ""),
        })

    domestic_benchmark = []
    for r in raw["companies"]:
        domain = r.get("海外对标域名")
        if not domain:
            continue
        domestic_benchmark.append({
            "enterprise_benchmark_rel_id": f"EB-{r['企业编号'][3:]}-{benchmark_ids[domain][3:]}",
            "enterprise_id": r["企业编号"],
            "benchmark_id": benchmark_ids[domain],
            "benchmark_priority": 1,
            "matched_technical_area": r.get("技术领域", ""),
            "match_reason": r.get("对标理由", ""),
            "target_titles": r.get("目标职位", ""),
            "mapping_method": "本地关键词规则",
            "relation_review_status": "待复核",
        })

    benchmark_engineer = []
    for r in raw["engineers"]:
        domain = r["企业域名"]
        benchmark_engineer.append({
            "benchmark_engineer_rel_id": f"BE-{benchmark_ids[domain][3:]}-{r['工程师编号'][3:15]}",
            "benchmark_id": benchmark_ids[domain],
            "engineer_id": r["工程师编号"],
            "employment_title": r.get("职位", ""),
            "employment_status": "Apollo报告当前任职",
            "evidence_source": "Apollo People Search",
            "relation_review_status": "待复核",
        })

    domestic_engineer = []
    for i, r in enumerate(raw["links"], 1):
        domestic_engineer.append({
            "enterprise_engineer_rel_id": f"EE-{i:06d}",
            "enterprise_id": r["企业编号"],
            "engineer_id": r["工程师编号"],
            "matched_need": r.get("核心需求摘要", ""),
            "matched_technical_area": r.get("技术领域", ""),
            "match_reason": r.get("匹配理由", ""),
            "match_score": None,
            "relation_review_status": "待复核",
            "email_enrichment_decision": "待决定",
            "email_enrichment_reason": "",
        })

    result = {
        "metadata": {
            "as_of_date": "2026-09-11",
            "version": "current-review-v1",
            "email_enrichment_calls": 0,
            "email_verification_calls": 0,
            "limitations": "海外对标为首轮技术领域代表映射，尚未逐企业扩展至2至3家精确对标企业。",
        },
        "domestic_enterprises": domestic,
        "benchmark_enterprises": benchmarks,
        "engineers": engineers,
        "domestic_benchmark_relations": domestic_benchmark,
        "benchmark_engineer_relations": benchmark_engineer,
        "domestic_engineer_relations": domestic_engineer,
    }
    assert len(domestic) == 490
    assert len(benchmarks) == 38
    assert len(engineers) == 642
    assert len(domestic_benchmark) == 384
    assert len(benchmark_engineer) == 642
    assert len(domestic_engineer) == 6345
    assert len({r["enterprise_id"] for r in domestic}) == len(domestic)
    assert len({r["benchmark_id"] for r in benchmarks}) == len(benchmarks)
    assert len({r["engineer_id"] for r in engineers}) == len(engineers)
    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps({k: len(v) for k, v in result.items() if isinstance(v, list)}, ensure_ascii=False))


if __name__ == "__main__":
    main()
