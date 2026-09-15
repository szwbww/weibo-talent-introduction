#!/usr/bin/env python3
"""Create an English-only primaryResearchField preview; never writes ES."""

import argparse
import csv
import json
from collections import Counter
from pathlib import Path

from research_field_normalization import assert_english_research_field, primary_research_field


ROOT = Path(__file__).resolve().parents[3]
DEFAULT_INPUT = ROOT / "outputs/enterprise-rnd-filtered-20260914/candidate_documents.json"
DEFAULT_OUTPUT = ROOT / "outputs/enterprise-rnd-english-fields-20260915"


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", type=Path, default=DEFAULT_INPUT)
    parser.add_argument("--output-dir", type=Path, default=DEFAULT_OUTPUT)
    args = parser.parse_args()

    docs = json.loads(args.input.read_text(encoding="utf-8"))
    rows = []
    for item in docs:
        source = item["_source"]
        previous = source.get("researchFields", "")
        areas = [value.strip() for value in previous.split(",") if value.strip()]
        primary, rule = primary_research_field(areas, source.get("keyword", ""))
        assert_english_research_field(primary)
        rows.append({
            "orcidId": source["orcidId"],
            "name": " ".join(filter(None, [source.get("givenNames"), source.get("familyNames")])),
            "email": source.get("email", ""),
            "currentOrganization": source.get("institution", ""),
            "jobTitle": source.get("keyword", ""),
            "previousResearchFields": previous,
            "primaryResearchField": primary,
            "normalizationRule": rule,
        })

    args.output_dir.mkdir(parents=True, exist_ok=True)
    (args.output_dir / "primary_research_fields.json").write_text(
        json.dumps(rows, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    with (args.output_dir / "primary_research_fields.csv").open("w", encoding="utf-8-sig", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=list(rows[0]))
        writer.writeheader()
        writer.writerows(rows)

    counts = Counter(row["primaryResearchField"] for row in rows)
    report = {
        "records": len(rows),
        "english_only": all(not any("\u3400" <= char <= "\u9fff" for char in row["primaryResearchField"]) for row in rows),
        "pending_label_in_primary": sum("待分类" in row["primaryResearchField"] for row in rows),
        "normalization_rules": dict(Counter(row["normalizationRule"] for row in rows)),
        "primary_research_field_counts": dict(sorted(counts.items(), key=lambda item: (-item[1], item[0]))),
    }
    (args.output_dir / "report.json").write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(report, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
