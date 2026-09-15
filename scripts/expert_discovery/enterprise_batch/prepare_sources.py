#!/usr/bin/env python3
"""Merge enterprise source files into one normalized JSON dataset."""
from __future__ import annotations

import argparse
import json
import re
from pathlib import Path

import pandas as pd


SUFFIXES = (
    "股份有限公司", "有限责任公司", "有限公司", "科技股份", "科技", "集团", "公司"
)


def clean(value) -> str:
    if value is None or (isinstance(value, float) and pd.isna(value)):
        return ""
    return re.sub(r"\s+", " ", str(value)).strip()


def key(name: str) -> str:
    value = re.sub(r"[（）()\[\]【】·•\s]", "", clean(name)).casefold()
    changed = True
    while changed:
        changed = False
        for suffix in SUFFIXES:
            if len(value) > len(suffix) + 2 and value.endswith(suffix):
                value = value[: -len(suffix)]
                changed = True
                break
    return value


def add(store, name, source, fields, priority):
    name = clean(name)
    if not name:
        return
    k = key(name)
    row = store.setdefault(k, {
        "企业名称": name,
        "来源": [],
        "来源明细": [],
        "核心需求": "",
        "行业领域": "",
        "细分产品": "",
        "板块": "",
        "需求优先级": -1,
    })
    if source not in row["来源"]:
        row["来源"].append(source)
    detail = {k: clean(v) for k, v in fields.items() if clean(v)}
    if detail:
        row["来源明细"].append({"来源": source, **detail})
    for field in ("行业领域", "细分产品", "板块"):
        if detail.get(field) and not row.get(field):
            row[field] = detail[field]
    need = clean(detail.get("核心需求"))
    if need and (priority > row["需求优先级"] or len(need) > len(row["核心需求"])):
        row["核心需求"] = need
        row["需求优先级"] = priority


def read_company_list(path, store):
    df = pd.read_excel(path, sheet_name=0, header=2)
    for _, r in df.iterrows():
        add(store, r.get("企业名称"), "企业名单", {
            "板块": r.get("板块"),
            "核心需求": r.get("Unnamed: 3"),
        }, 30)


def read_talent_survey(path, store):
    df = pd.read_excel(path, sheet_name=0, header=1)
    for _, r in df.iterrows():
        industry, product = clean(r.get("行业领域")), clean(r.get("细分产品"))
        add(store, r.get("企业名称"), "千灯镇人才摸排表", {
            "行业领域": industry,
            "细分产品": product,
            "核心需求": "；".join(v for v in (industry, product) if v),
        }, 40)


def read_demand(path, store):
    df = pd.read_excel(path, sheet_name=0, header=2)
    for _, r in df.iterrows():
        fields = {clean(c): clean(r.get(c)) for c in df.columns}
        name = fields.get("企业名称", "")
        need_parts = []
        for label in ("所属产业", "技术需求", "核心技术需求", "人才需求", "岗位需求", "职位", "需求描述", "备注"):
            if fields.get(label):
                need_parts.append(f"{label}：{fields[label]}")
        if not need_parts:
            need_parts = [f"{k}：{v}" for k, v in fields.items() if k and v and k not in {"序号", "企业名称"}]
        add(store, name, "企业高端人才需求表", {"核心需求": "；".join(need_parts)}, 100)


def read_enterprises(path, store):
    data = json.loads(Path(path).read_text(encoding="utf-8"))
    for item in data:
        if not item.get("是否启用", True):
            continue
        try:
            info = json.loads(item.get("企业信息") or "{}")
        except (TypeError, ValueError):
            info = {}
        parts = []
        for label in ("核心技术", "核心产品", "研发方向", "经营范围"):
            value = clean(info.get(label))
            if value:
                parts.append(f"{label}：{value}")
        add(store, item.get("企业名称"), "enterprises.json", {
            "核心需求": "；".join(parts),
            "企业文件名": item.get("企业文件名"),
        }, 20)


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--company-list", required=True)
    p.add_argument("--talent-survey-xlsx", required=True)
    p.add_argument("--demand", required=True)
    p.add_argument("--enterprises-json", required=True)
    p.add_argument("--output", required=True)
    args = p.parse_args()
    store = {}
    read_enterprises(args.enterprises_json, store)
    read_company_list(args.company_list, store)
    read_talent_survey(args.talent_survey_xlsx, store)
    read_demand(args.demand, store)
    rows = sorted(store.values(), key=lambda r: r["企业名称"])
    for i, row in enumerate(rows, 1):
        row["企业编号"] = f"CN-{i:04d}"
        row.pop("需求优先级", None)
        if not row["核心需求"]:
            row["核心需求"] = "缺少明确核心需求，暂不执行人员搜索"
    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps({"企业": rows}, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps({"companies": len(rows), "output": str(output)}, ensure_ascii=False))


if __name__ == "__main__":
    main()
