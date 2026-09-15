#!/usr/bin/env python3
"""Merge a base Apollo run with retry runs by benchmark domain."""
from __future__ import annotations

import argparse
import json
from pathlib import Path


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--runs", nargs="+", required=True)
    p.add_argument("--output", required=True)
    args = p.parse_args()
    runs = [json.loads(Path(path).read_text(encoding="utf-8")) for path in args.runs]
    base = runs[0]
    company_by_id = {r["企业编号"]: r for r in base["companies"]}
    engineers = []
    links = []
    successful_domains = set()
    final_failures = []
    for run in runs:
        failed = {x.get("domain") for x in run.get("failures", []) if x.get("stage") == "apollo"}
        run_domains = {r.get("海外对标域名") for r in run.get("companies", [])
                       if r.get("海外对标域名") and r.get("对标状态") == "已检索"}
        successful = run_domains - failed
        for row in run.get("companies", []):
            if row.get("海外对标域名") in successful:
                target = company_by_id[row["企业编号"]]
                target["Apollo可见人数"] = row.get("Apollo可见人数")
                target["本次收录人数"] = row.get("本次收录人数", 0)
                target["对标状态"] = "已检索"
        for row in run.get("engineers", []):
            if row["企业域名"] in successful and row["企业域名"] not in successful_domains:
                engineers.append(row)
        for row in run.get("links", []):
            domain = company_by_id[row["企业编号"]].get("海外对标域名")
            if domain in successful and domain not in successful_domains:
                links.append(row)
        successful_domains.update(successful)
    all_mapped_domains = {r["海外对标域名"] for r in base["companies"] if r.get("海外对标域名")}
    remaining = all_mapped_domains - successful_domains
    for row in company_by_id.values():
        if row.get("海外对标域名") in remaining:
            row["对标状态"] = "网络失败待重试"
            row["本次收录人数"] = 0
    for run in reversed(runs):
        for failure in run.get("failures", []):
            if failure.get("stage") == "apollo" and failure.get("domain") in remaining:
                if failure.get("domain") not in {x.get("domain") for x in final_failures}:
                    final_failures.append(failure)
    result = {
        "summary": {
            "companies": len(company_by_id),
            "mapped_companies": sum(bool(r.get("海外对标域名")) for r in company_by_id.values()),
            "unique_benchmarks": len(all_mapped_domains),
            "searched_benchmarks": len(successful_domains),
            "failed_benchmarks": len(remaining),
            "engineers": len(engineers),
            "company_engineer_links": len(links),
            "apollo_enrichment_credits_used": 0,
            "email_verification_credits_used": 0,
        },
        "companies": list(company_by_id.values()),
        "engineers": engineers,
        "links": links,
        "failures": final_failures,
    }
    Path(args.output).write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(result["summary"], ensure_ascii=False))


if __name__ == "__main__":
    main()
