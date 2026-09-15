#!/usr/bin/env python3
"""Apply the reviewed English-only primary research fields to existing ES docs."""

import argparse
import json
from datetime import datetime, timezone
from pathlib import Path

from import_candidate_es import EsClient, read_es_config
from research_field_normalization import assert_english_research_field


ROOT = Path(__file__).resolve().parents[3]
DEFAULT_INPUT = ROOT / "outputs/enterprise-rnd-english-fields-20260915/primary_research_fields.json"
DEFAULT_OUTPUT = ROOT / "outputs/enterprise-rnd-english-fields-20260915/es_update_report.json"


def parse_args():
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", type=Path, default=DEFAULT_INPUT)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument("--config", type=Path, default=ROOT / "config/application-local.yml")
    parser.add_argument("--index", default="orcid_info_candidate")
    parser.add_argument("--execute", action="store_true")
    return parser.parse_args()


def main():
    args = parse_args()
    rows = json.loads(args.input.read_text(encoding="utf-8"))
    by_id = {row["orcidId"]: row["primaryResearchField"] for row in rows}
    if len(by_id) != len(rows):
        raise RuntimeError("Input has duplicate orcidId values")
    for value in by_id.values():
        assert_english_research_field(value)

    report = {"requested": len(rows), "executed": False}
    if not args.execute:
        args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
        print(json.dumps(report, ensure_ascii=False))
        return

    client = EsClient(read_es_config(args.config))
    lookup = client.request("POST", f"/{args.index}/_mget", {
        "docs": [
            {"_id": doc_id, "_source": ["dataSource", "researchFields"]}
            for doc_id in by_id
        ],
    })
    missing = []
    wrong_source = []
    for item in lookup.get("docs", []):
        doc_id = item.get("_id")
        if not item.get("found"):
            missing.append(doc_id)
        elif item.get("_source", {}).get("dataSource") != "APOLLO_ENTERPRISE_RND":
            wrong_source.append(doc_id)
    if missing or wrong_source:
        raise RuntimeError(f"Refusing update: missing={len(missing)}, wrong_source={len(wrong_source)}")

    now = datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M:%S")
    lines = []
    for doc_id, field in by_id.items():
        lines.append(json.dumps({"update": {"_index": args.index, "_id": doc_id, "retry_on_conflict": 3}}))
        lines.append(json.dumps({"doc": {"researchFields": field, "updatedAt": now}}, ensure_ascii=False))
    response = client.request(
        "POST", "/_bulk?refresh=wait_for", ("\n".join(lines) + "\n").encode(), "application/x-ndjson"
    )
    failures = [item["update"] for item in response.get("items", []) if item["update"].get("status", 500) >= 300]
    if failures:
        raise RuntimeError(f"Bulk update failed for {len(failures)} records")

    verify = client.request("POST", f"/{args.index}/_mget", {
        "docs": [{"_id": doc_id, "_source": ["researchFields"]} for doc_id in by_id],
    })
    mismatch = []
    for item in verify.get("docs", []):
        actual = item.get("_source", {}).get("researchFields")
        if actual != by_id[item.get("_id")]:
            mismatch.append(item.get("_id"))
        else:
            assert_english_research_field(actual)
    report = {
        "requested": len(rows),
        "executed": True,
        "updated": len(rows),
        "missing": len(missing),
        "wrong_source": len(wrong_source),
        "verification_mismatch": len(mismatch),
    }
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    if mismatch:
        raise RuntimeError(f"Verification failed for {len(mismatch)} records")
    print(json.dumps(report, ensure_ascii=False))


if __name__ == "__main__":
    main()
