#!/usr/bin/env python3

import argparse
import csv
import json
import sys
from datetime import datetime, timezone
from pathlib import Path


def fit_research_fields(value: str, max_chars: int) -> tuple[str, bool]:
    value = value.strip()
    if len(value) <= max_chars:
        return value, False

    kept: list[str] = []
    for title in (part.strip() for part in value.split("; ")):
        if not title:
            continue
        candidate = "; ".join([*kept, title])
        if len(candidate) > max_chars:
            break
        kept.append(title)
    if kept:
        return "; ".join(kept), True
    return value[:max_chars].rstrip(), True


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Build a no-write ES Bulk file for SBIR researchFields backfill."
    )
    parser.add_argument("--input", required=True, help="Research-fields preview CSV")
    parser.add_argument("--output", required=True, help="Output Bulk NDJSON")
    parser.add_argument("--summary", required=True, help="Output summary JSON")
    parser.add_argument("--index", default="orcid_info_candidate")
    parser.add_argument("--max-chars", type=int, default=512)
    return parser


def run(args: argparse.Namespace) -> int:
    source = Path(args.input).expanduser().resolve()
    output = Path(args.output).expanduser().resolve()
    summary_path = Path(args.summary).expanduser().resolve()
    if not source.is_file():
        print(f"file not found: {source}", file=sys.stderr)
        return 2
    if args.max_chars < 1:
        print("--max-chars must be positive", file=sys.stderr)
        return 2

    updates: list[tuple[str, str]] = []
    seen_ids: set[str] = set()
    trimmed = 0
    with source.open(encoding="utf-8-sig", newline="") as handle:
        reader = csv.DictReader(handle)
        required = {"es_doc_id", "proposedResearchFields"}
        if not required.issubset(reader.fieldnames or []):
            print("input must contain es_doc_id and proposedResearchFields", file=sys.stderr)
            return 2
        for line_number, row in enumerate(reader, start=2):
            doc_id = (row.get("es_doc_id") or "").strip()
            value = (row.get("proposedResearchFields") or "").strip()
            if not doc_id or not value:
                print(f"empty id or researchFields at CSV line {line_number}", file=sys.stderr)
                return 2
            if doc_id in seen_ids:
                print(f"duplicate es_doc_id at CSV line {line_number}: {doc_id}", file=sys.stderr)
                return 2
            seen_ids.add(doc_id)
            fitted, changed = fit_research_fields(value, args.max_chars)
            trimmed += int(changed)
            updates.append((doc_id, fitted))

    output.parent.mkdir(parents=True, exist_ok=True)
    summary_path.parent.mkdir(parents=True, exist_ok=True)
    with output.open("w", encoding="utf-8") as handle:
        for doc_id, value in updates:
            action = {"update": {"_index": args.index, "_id": doc_id}}
            payload = {"doc": {"researchFields": value}}
            handle.write(json.dumps(action, ensure_ascii=False, separators=(",", ":")) + "\n")
            handle.write(json.dumps(payload, ensure_ascii=False, separators=(",", ":")) + "\n")

    summary = {
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "input": str(source),
        "bulk_file": str(output),
        "target_index": args.index,
        "operation": "update",
        "updated_fields": ["researchFields"],
        "documents": len(updates),
        "bulk_lines": len(updates) * 2,
        "trimmed_documents": trimmed,
        "max_chars": args.max_chars,
        "writes_elasticsearch": False,
    }
    summary_path.write_text(
        json.dumps(summary, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    print(
        f"documents={len(updates)} bulk_lines={len(updates) * 2} "
        f"trimmed={trimmed} output={output}"
    )
    print("ES was not contacted or modified")
    return 0


def main() -> int:
    return run(build_parser().parse_args())


if __name__ == "__main__":
    raise SystemExit(main())
