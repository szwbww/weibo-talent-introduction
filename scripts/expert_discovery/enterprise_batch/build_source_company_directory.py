#!/usr/bin/env python3
"""Build a bilingual directory of the employers behind imported Apollo experts."""

import argparse
import csv
import json
import subprocess
from collections import Counter
from pathlib import Path


ROOT = Path(__file__).resolve().parents[3]
DEFAULT_INPUT = ROOT / "outputs/enterprise-rnd-filtered-20260914/candidate_documents.json"
DEFAULT_OUTPUT = ROOT / "outputs/enterprise-rnd-source-companies-20260915"
DEFAULT_KEYS = ROOT / "scripts/properties/apllo.properties"


def load_properties(path):
    values = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if line and not line.startswith("#") and "=" in line:
            key, value = line.split("=", 1)
            values[key.strip()] = value.strip()
    return values


def call_deepseek(api_key, companies):
    prompt = {
        "task": "Translate each overseas company name into a concise, conventional Chinese company name.",
        "rules": [
            "Return exactly one item for each input English name.",
            "Use a widely used Chinese company name when one exists.",
            "Otherwise use a concise Chinese transliteration or translation.",
            "Do not invent corporate legal names, business descriptions, countries, or affiliations.",
            "Keep english_name exactly as supplied.",
        ],
        "companies": companies,
        "output": {"companies": [{"english_name": "input name", "chinese_name": "中文常用译名"}]},
    }
    body = json.dumps({
        "model": "deepseek-chat",
        "messages": [
            {"role": "system", "content": "You return strict JSON only."},
            {"role": "user", "content": json.dumps(prompt, ensure_ascii=False)},
        ],
        "response_format": {"type": "json_object"},
        "temperature": 0,
        "max_tokens": 4000,
    }, ensure_ascii=False)
    config = [
        'request = "POST"',
        'url = "https://api.deepseek.com/chat/completions"',
        "silent", "show-error", 'ipv4', 'http1.1', 'max-time = "120"',
        'header = "Authorization: Bearer ' + api_key.replace('"', '\\"') + '"',
        'header = "Content-Type: application/json"',
        'data = "' + body.replace("\\", "\\\\").replace('"', '\\"') + '"',
    ]
    result = subprocess.run(["curl", "--config", "-", "--write-out", "\n%{http_code}"],
                            input="\n".join(config), text=True, capture_output=True, check=False, timeout=140)
    raw, sep, status = result.stdout.rpartition("\n")
    if result.returncode or not sep or status != "200":
        raise RuntimeError("DeepSeek translation request failed: " + result.stderr[-300:])
    content = json.loads(raw)["choices"][0]["message"]["content"]
    return json.loads(content)["companies"]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", type=Path, default=DEFAULT_INPUT)
    parser.add_argument("--output-dir", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument("--keys", type=Path, default=DEFAULT_KEYS)
    args = parser.parse_args()

    docs = json.loads(args.input.read_text(encoding="utf-8"))
    counts = Counter(item["_source"].get("institution", "").strip() for item in docs)
    counts.pop("", None)
    english_names = sorted(counts, key=str.casefold)
    api_key = load_properties(args.keys).get("DEEPSEEK_API_KEY", "")
    if not api_key:
        raise RuntimeError("DEEPSEEK_API_KEY missing")

    translated = {}
    for start in range(0, len(english_names), 25):
        batch = english_names[start:start + 25]
        result = call_deepseek(api_key, batch)
        returned = {row.get("english_name"): (row.get("chinese_name") or "").strip() for row in result}
        missing = [name for name in batch if not returned.get(name)]
        if missing:
            raise RuntimeError("DeepSeek translation missing " + ", ".join(missing[:3]))
        translated.update({name: returned[name] for name in batch})

    rows = [{
        "companyNameEn": name,
        "companyNameZh": translated[name],
        "expertCount": counts[name],
    } for name in sorted(english_names, key=lambda name: (-counts[name], name.casefold()))]
    args.output_dir.mkdir(parents=True, exist_ok=True)
    (args.output_dir / "source_companies.json").write_text(json.dumps(rows, ensure_ascii=False, indent=2), encoding="utf-8")
    with (args.output_dir / "source_companies.csv").open("w", encoding="utf-8-sig", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=list(rows[0]))
        writer.writeheader()
        writer.writerows(rows)
    report = {"companies": len(rows), "experts": sum(counts.values()), "translation_source": "DeepSeek common Chinese name"}
    (args.output_dir / "report.json").write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(report, ensure_ascii=False))


if __name__ == "__main__":
    main()
