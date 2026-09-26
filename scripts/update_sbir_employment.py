#!/usr/bin/env python3
"""Update only `employment` for the reviewed SBIR import documents."""

from __future__ import annotations

import argparse
import csv
import json
import os
import subprocess
import sys
from pathlib import Path


DEFAULT_INDEXES = ("orcid_info", "orcid_info_candidate")


def employment(row: dict[str, str]) -> str:
    role = row["pi_title"].strip()
    company = row["company"].strip()
    if role.casefold() == "pi":
        role = "Principal Investigator"
    if not role or "@" in role:
        role = "SBIR/STTR Phase II project contact"
    if not company:
        raise ValueError(f"Missing company for {row['es_doc_id']}")
    return f"{role} at {company}"


def rows(path: Path) -> list[dict[str, str]]:
    with path.open(encoding="utf-8-sig", newline="") as handle:
        values = list(csv.DictReader(handle))
    ids = [row["es_doc_id"] for row in values]
    if len(ids) != len(set(ids)):
        raise ValueError("SBIR import has duplicate document IDs")
    return values


def bulk_body(index: str, batch: list[dict[str, str]]) -> bytes:
    lines: list[str] = []
    for row in batch:
        lines.append(json.dumps({"update": {"_index": index, "_id": row["es_doc_id"]}}))
        lines.append(json.dumps({"doc": {"employment": employment(row)}}))
    return ("\n".join(lines) + "\n").encode()


def run_bulk(args: argparse.Namespace, body: bytes) -> dict[str, object]:
    remote = (
        "curl -ksSf --user "
        f"'elastic:{args.es_password}' "
        "-H 'Content-Type: application/x-ndjson' "
        f"'{args.es_url.rstrip('/')}/_bulk?refresh=false' --data-binary @-"
    )
    result = subprocess.run(
        ["ssh", args.ssh_host, remote],
        input=body,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        check=False,
    )
    if result.returncode:
        raise RuntimeError(result.stderr.decode(errors="replace"))
    payload = json.loads(result.stdout)
    failures = [item for item in payload["items"] if item["update"].get("result") != "updated"]
    if payload.get("errors") or failures:
        raise RuntimeError(json.dumps(failures[:5]))
    return payload


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", type=Path, default=Path("tmp/sbir-import/experts_import.csv"))
    parser.add_argument("--ssh-host", default="root@150.158.92.103")
    parser.add_argument("--es-url", default="https://es-fcxvip4d.public.tencentelasticsearch.com:9200")
    parser.add_argument("--batch-size", type=int, default=250)
    parser.add_argument("--apply", action="store_true")
    args = parser.parse_args()
    args.es_password = os.environ.get("SBIR_ES_PASSWORD", "")
    if not args.es_password:
        raise ValueError("Set SBIR_ES_PASSWORD before applying")
    values = rows(args.input)
    print(json.dumps({"documents": len(values), "indexes": DEFAULT_INDEXES, "apply": args.apply}))
    if not args.apply:
        return 0
    for index in DEFAULT_INDEXES:
        updated = 0
        for start in range(0, len(values), args.batch_size):
            batch = values[start : start + args.batch_size]
            run_bulk(args, bulk_body(index, batch))
            updated += len(batch)
        print(json.dumps({"index": index, "updated": updated}))
    return 0


if __name__ == "__main__":
    sys.exit(main())
