#!/usr/bin/env python3
"""Cascade-filter benchmark companies and downstream engineer datasets by country."""

import argparse
import json
from collections import Counter, defaultdict
from pathlib import Path


ROOT = Path(__file__).resolve().parents[3]
DEFAULT_ALLOWED = {
    "美国", "加拿大", "日本", "韩国", "中国台湾", "新加坡", "以色列", "中国香港",
    "德国", "瑞士", "英国", "法国", "荷兰", "意大利", "瑞典", "丹麦", "奥地利",
    "芬兰", "西班牙", "挪威", "比利时", "爱尔兰", "希腊", "卢森堡", "葡萄牙",
    "波兰", "匈牙利", "列支敦士登", "立陶宛", "斯洛文尼亚", "斯洛伐克",
    "捷克", "塞尔维亚", "爱沙尼亚",
}


def load(path):
    with path.open(encoding="utf-8") as stream:
        return json.load(stream)


def dump(path, value):
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2), encoding="utf-8")


def args():
    parser = argparse.ArgumentParser()
    parser.add_argument("--benchmark-source", type=Path, default=ROOT / "outputs/apollo-benchmark-merge-20260912/海外对标企业关系数据_Apollo研发覆盖合并版_20260912.json")
    parser.add_argument("--engineer-source", type=Path, default=ROOT / "outputs/enterprise-engineer-email-availability-20260913/engineer_directory.json")
    parser.add_argument("--email-dir", type=Path, default=ROOT / "outputs/enterprise-engineer-email-availability-20260913")
    parser.add_argument("--candidate-documents", type=Path, default=ROOT / "outputs/enterprise-engineer-es-import-20260914/candidate_documents.json")
    parser.add_argument("--output-dir", type=Path, default=ROOT / "outputs/enterprise-rnd-filtered-20260914")
    return parser.parse_args()


def main():
    opt = args()
    opt.output_dir.mkdir(parents=True, exist_ok=True)
    benchmark = load(opt.benchmark_source)
    directory = load(opt.engineer_source)

    excluded_companies = [row for row in benchmark["merged_benchmark_enterprises"] if row.get("country") not in DEFAULT_ALLOWED]
    excluded_benchmark_ids = {row["benchmark_id"] for row in excluded_companies}
    kept_benchmark_ids = {row["benchmark_id"] for row in benchmark["merged_benchmark_enterprises"]} - excluded_benchmark_ids

    filtered_relations = [row for row in benchmark["merged_relations"] if row["benchmark_id"] in kept_benchmark_ids]
    relation_counts = Counter(row["benchmark_id"] for row in filtered_relations)
    domestic_counts = Counter(row["enterprise_id"] for row in filtered_relations)
    filtered_companies = []
    for row in benchmark["merged_benchmark_enterprises"]:
        if row["benchmark_id"] not in kept_benchmark_ids:
            continue
        copy = dict(row)
        copy["relation_count"] = relation_counts[row["benchmark_id"]]
        filtered_companies.append(copy)
    domestic = []
    for row in benchmark["domestic_enterprises"]:
        copy = dict(row)
        count = domestic_counts[row["enterprise_id"]]
        copy["benchmark_count"] = count
        copy["benchmark_completion_status"] = "已完成8家" if count >= 8 else "需补充%d家" % (8 - count)
        domestic.append(copy)
    filtered_benchmark = dict(benchmark)
    filtered_benchmark["domestic_enterprises"] = domestic
    filtered_benchmark["apollo_new_benchmark_enterprises"] = [row for row in benchmark["apollo_new_benchmark_enterprises"] if row["benchmark_id"] in kept_benchmark_ids]
    filtered_benchmark["apollo_new_relations"] = [row for row in benchmark["apollo_new_relations"] if row["benchmark_id"] in kept_benchmark_ids]
    filtered_benchmark["merged_benchmark_enterprises"] = filtered_companies
    filtered_benchmark["merged_relations"] = filtered_relations
    filtered_benchmark["metadata"] = dict(benchmark["metadata"])
    filtered_benchmark["metadata"].update({
        "merged_relations": len(filtered_relations),
        "unique_merged_benchmarks": len(filtered_companies),
        "apollo_new_relations": len(filtered_benchmark["apollo_new_relations"]),
        "unique_apollo_new_benchmarks": len(filtered_benchmark["apollo_new_benchmark_enterprises"]),
        "region_filter": "欧洲、美国、加拿大、日本、韩国、中国台湾、新加坡、以色列、中国香港",
    })

    engineer_benchmarks = defaultdict(set)
    for row in directory["benchmark_engineer_relations"]:
        engineer_benchmarks[row["engineer_id"]].add(row["benchmark_id"])
    excluded_engineer_ids = {
        engineer_id for engineer_id, ids in engineer_benchmarks.items()
        if ids and ids <= excluded_benchmark_ids
    }
    filtered_directory = dict(directory)
    filtered_directory["engineers"] = [row for row in directory["engineers"] if row["engineer_id"] not in excluded_engineer_ids]
    filtered_directory["benchmark_engineer_relations"] = [row for row in directory["benchmark_engineer_relations"] if row["benchmark_id"] in kept_benchmark_ids]
    filtered_directory["enterprise_engineer_relations"] = [row for row in directory["enterprise_engineer_relations"] if row["engineer_id"] not in excluded_engineer_ids]
    filtered_directory["benchmark_search_stats"] = [row for row in directory["benchmark_search_stats"] if row["benchmark_id"] in kept_benchmark_ids]
    summary = dict(directory["summary"])
    summary.update({
        "benchmarks_input": len(filtered_companies),
        "benchmarks_searched": len(filtered_directory["benchmark_search_stats"]),
        "apollo_visible_total": sum(int(row.get("apollo_total") or 0) for row in filtered_directory["benchmark_search_stats"]),
        "unique_engineers": len(filtered_directory["engineers"]),
        "benchmark_engineer_relations": len(filtered_directory["benchmark_engineer_relations"]),
        "enterprise_engineer_relations": len(filtered_directory["enterprise_engineer_relations"]),
    })
    filtered_directory["summary"] = summary

    enrichment = load(opt.email_dir / "apollo_email_enrichment.json")
    filtered_enrichment = dict(enrichment)
    filtered_enrichment["selection"] = [row for row in enrichment["selection"] if row["engineer_id"] not in excluded_engineer_ids]
    filtered_enrichment["results"] = {key: row for key, row in enrichment["results"].items() if row["engineer_id"] not in excluded_engineer_ids}
    statuses = Counter(row.get("status") for row in filtered_enrichment["results"].values())
    filtered_enrichment["summary"] = dict(enrichment["summary"])
    filtered_enrichment["summary"].update({
        "selected": len(filtered_enrichment["selection"]),
        "processed": len(filtered_enrichment["results"]),
        "email_found": statuses["EMAIL_FOUND"],
        "matched_no_email": statuses["MATCHED_NO_EMAIL"],
        "no_match": statuses["NO_MATCH"],
        "failed": statuses["FAILED"],
    })

    candidate_docs = load(opt.candidate_documents)
    filtered_candidate_docs = [row for row in candidate_docs if row["_source"]["id"] not in excluded_engineer_ids]
    es_delete_docs = [row for row in candidate_docs if row["_source"]["id"] in excluded_engineer_ids]

    checkpoint = load(opt.email_dir / "apollo_people_checkpoint.json")
    filtered_checkpoint = {key: value for key, value in checkpoint.items() if key in kept_benchmark_ids}

    high_value = load(opt.email_dir / "high_value_email_availability.json")
    filtered_high_value = dict(high_value)
    for key in ("public_email_confirmed", "apollo_email_available", "email_discovery_required"):
        filtered_high_value[key] = [row for row in high_value[key] if row["engineer_id"] not in excluded_engineer_ids]
    high_value_rows = filtered_high_value["public_email_confirmed"] + filtered_high_value["apollo_email_available"] + filtered_high_value["email_discovery_required"]
    high_value_summary = dict(high_value["summary"])
    high_value_summary.update({
        "high_value_total": len(high_value_rows),
        "public_email_confirmed": len(filtered_high_value["public_email_confirmed"]),
        "apollo_email_available": len(filtered_high_value["apollo_email_available"]),
        "apollo_email_unavailable": len(filtered_high_value["email_discovery_required"]),
        "complete_name": sum(bool(row.get("name_is_complete")) for row in high_value_rows),
        "obfuscated_name": sum(not bool(row.get("name_is_complete")) for row in high_value_rows),
    })
    high_value_summary["by_tier"] = {}
    for tier in ("A", "B", "C"):
        rows = [row for row in high_value_rows if row.get("high_value_tier") == tier]
        available_ids = {row["engineer_id"] for row in filtered_high_value["apollo_email_available"]}
        high_value_summary["by_tier"][tier] = {
            "total": len(rows),
            "apollo_email_available": sum(row["engineer_id"] in available_ids for row in rows),
            "apollo_email_unavailable": sum(row["engineer_id"] not in available_ids for row in rows),
            "complete_name": sum(bool(row.get("name_is_complete")) for row in rows),
            "obfuscated_name": sum(not bool(row.get("name_is_complete")) for row in rows),
        }
    filtered_high_value["summary"] = high_value_summary

    public = load(opt.email_dir / "public_email_discovery.json")
    filtered_public = dict(public)
    filtered_public["results"] = [row for row in public["results"] if row["engineer_id"] not in excluded_engineer_ids]
    public_status = Counter(row.get("status") for row in filtered_public["results"])
    filtered_public["summary"] = {
        "searched": len(filtered_public["results"]),
        "public_email_confirmed": public_status["PUBLIC_EMAIL_CONFIRMED"],
        "review_required": public_status["REVIEW_REQUIRED"],
        "no_confirmed_public_email": public_status["NO_CONFIRMED_PUBLIC_EMAIL"],
        "tier_a_searched": sum(row.get("high_value_tier") == "A" for row in filtered_public["results"]),
        "tier_b_searched": sum(row.get("high_value_tier") == "B" for row in filtered_public["results"]),
    }

    dump(opt.output_dir / "benchmark_directory.json", filtered_benchmark)
    dump(opt.output_dir / "engineer_directory.json", filtered_directory)
    dump(opt.output_dir / "apollo_email_enrichment.json", filtered_enrichment)
    dump(opt.output_dir / "apollo_people_checkpoint.json", filtered_checkpoint)
    dump(opt.output_dir / "high_value_email_availability.json", filtered_high_value)
    dump(opt.output_dir / "public_email_discovery.json", filtered_public)
    dump(opt.output_dir / "candidate_documents.json", filtered_candidate_docs)
    dump(opt.output_dir / "es_documents_to_delete.json", es_delete_docs)
    report = {
        "allowed_countries": sorted(DEFAULT_ALLOWED),
        "excluded_country_counts": dict(Counter(row.get("country") or "未知" for row in excluded_companies).most_common()),
        "benchmark_companies": {"before": len(benchmark["merged_benchmark_enterprises"]), "after": len(filtered_companies), "removed": len(excluded_companies)},
        "benchmark_relations": {"before": len(benchmark["merged_relations"]), "after": len(filtered_relations), "removed": len(benchmark["merged_relations"]) - len(filtered_relations)},
        "engineers": {"before": len(directory["engineers"]), "after": len(filtered_directory["engineers"]), "removed": len(excluded_engineer_ids)},
        "benchmark_engineer_relations": {"before": len(directory["benchmark_engineer_relations"]), "after": len(filtered_directory["benchmark_engineer_relations"])},
        "enterprise_engineer_relations": {"before": len(directory["enterprise_engineer_relations"]), "after": len(filtered_directory["enterprise_engineer_relations"])},
        "es_candidate_documents": {"before": len(candidate_docs), "after": len(filtered_candidate_docs), "to_delete": len(es_delete_docs)},
    }
    dump(opt.output_dir / "filter_report.json", report)
    print(json.dumps(report, ensure_ascii=False))


if __name__ == "__main__":
    main()
