#!/usr/bin/env python3
"""Create ContactOut role rules and a scored enterprise registry for QianDeng."""

import json
from collections import defaultdict
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "outputs/benchmark-expansion-20260912/海外对标企业关系数据_每企8家_20260912.json"
OUT = ROOT / "outputs/qiandeng-contactout-config-20260920"
TODAY = "2026-09-20"
FORTUNE = "https://fortune.com/ranking/global500/2026"

DEVELOPED = {"美国", "德国", "日本", "中国台湾", "韩国", "新加坡", "瑞士", "奥地利", "法国", "丹麦", "荷兰", "加拿大"}
GLOBAL_500 = {
    "BASF", "Bayer", "Broadcom", "Cisco", "Dow", "Foxconn Technology Group",
    "Honeywell", "Intel", "Microsoft", "NVIDIA", "Samsung Electronics", "Siemens",
}

# Product score under the user's existing scoring system. The score is never
# inflated simply because a company is a large group: only explicit Fortune
# GLOBAL 500 records use the L1 direct route.
PRODUCT_SCORE = {
    "芯片封测": (20, 60, "集成电路先进材料与工艺"),
    "封装胶、膜": (20, 60, "集成电路先进材料与工艺"),
    "封装载板": (20, 60, "集成电路先进材料与工艺"),
    "光刻胶": (20, 60, "集成电路先进材料与工艺"),
    "高纯湿电子化学品": (20, 60, "集成电路先进材料与工艺"),
    "光模块": (20, 40, "人工智能（未证实细分方向）"),
    "高速数据线": (20, 40, "人工智能（未证实细分方向）"),
    "红外检测设备": (10, 40, "人工智能（未证实细分方向）"),
    "机器人零部件": (10, 60, "具身智能"),
    "标准零部件": (10, 0, "非重点产业"),
    "高端代工": (10, 40, "人工智能（未证实细分方向）"),
    "种子": (0, 0, "非重点产业"),
}

# Query names used by ContactOut differ from former legal names in several
# historic benchmark rows. One record owns every old/current search name.
RENAMES = {
    "ASM Pacific Technology": ("ASMPT", ["ASM Pacific Technology"], "asmpt.com"),
    "Cabot Microelectronics": ("Entegris", ["Cabot Microelectronics"], "entegris.com"),
    "FLIR Systems": ("Teledyne FLIR", ["FLIR Systems", "FLIR"], "flir.com"),
    "Foxconn Technology Group": ("Foxconn Technology Group", ["Foxconn", "Hon Hai Precision Industry"], "foxconn.com"),
    "Hitachi Chemical": ("Resonac", ["Hitachi Chemical", "Showa Denko Materials", "日立化成"], "resonac.com"),
    "Jabil Inc.": ("Jabil", ["Jabil Inc."], "jabil.com"),
    "Flex Ltd.": ("Flex", ["Flex Ltd."], "flex.com"),
    "Xilinx": ("Advanced Micro Devices, Inc.", ["Xilinx", "AMD"], "amd.com"),
    "日立化成": ("Resonac", ["Hitachi Chemical", "Showa Denko Materials", "日立化成"], "resonac.com"),
    "揖斐电": ("Ibiden", ["揖斐电"], "ibiden.com"),
    "住友电木": ("Sumitomo Bakelite", ["住友电木"], "sumibe.co.jp"),
}

EXTRA_ALIASES = {
    "Coherent Corp.": ["Coherent"],
    "KWS SAAT SE": ["KWS"],
    "Merck KGaA": ["Merck Group", "Merck"],
    "Misumi Group": ["MISUMI"],
    "Powertech Technology": ["PTI", "Powertech"],
    "Shinko Electric Industries Co., Ltd.": ["Shinko Electric"],
    "Sumitomo Electric": ["Sumitomo Electric Industries"],
    "Tokyo Ohka Kogyo": ["TOK", "Tokyo Ohka Kogyo Co., Ltd."],
}

# User-confirmed semiconductor-material records. These values override the
# earlier broad benchmark defaults whenever the company already exists.
USER_CONFIRMED = {
    "Henkel": {
        "id": "henkel", "aliases": ["Henkel AG & Co. KGaA", "汉高"],
        "headquartersCountry": "Germany", "developedEconomy": True, "global500": False,
        "highSalaryIndustryPoints": 10, "priorityIndustryPoints": 60,
        "priorityIndustry": "先进材料；已证实半导体封装胶、底部填充及先进封装材料业务",
        "sources": ["https://next.henkel-adhesives.com/us/en/industries/semiconductor/semiconductor-packaging.html"],
    },
    "Nitto Denko Corporation": {
        "id": "nitto", "aliases": ["Nitto", "Nitto Denko", "Nitto Denko Corp", "日东电工"],
        "headquartersCountry": "Japan", "developedEconomy": True, "global500": False,
        "highSalaryIndustryPoints": 10, "priorityIndustryPoints": 60,
        "priorityIndustry": "先进材料；已证实半导体划片、减薄、固晶膜业务",
        "sources": ["https://www.nitto.com/us/en/markets/industry/electronic/semicon/"],
    },
    "LINTEC Corporation": {
        "id": "lintec", "aliases": ["LINTEC", "琳得科"],
        "headquartersCountry": "Japan", "developedEconomy": True, "global500": False,
        "highSalaryIndustryPoints": 10, "priorityIndustryPoints": 60,
        "priorityIndustry": "先进材料；已证实半导体划片及固晶胶带业务",
        "sources": ["https://www.lintec-global.com/ir/library/annual/pdf/Integrated_Report_2025_all.pdf"],
    },
    "Resonac": {
        "id": "resonac", "aliases": ["Resonac Corporation", "Resonac Corp", "瑞萨科"],
        "headquartersCountry": "Japan", "developedEconomy": True, "global500": False,
        "highSalaryIndustryPoints": 10, "priorityIndustryPoints": 60,
        "priorityIndustry": "先进材料；已证实半导体固晶膜与划片固晶膜业务",
        "sources": ["https://ap.resonac.com/product/die-attach-film-daf/"],
    },
    "NAMICS Corporation": {
        "id": "namics", "aliases": ["NAMICS", "NAMICS Corp", "纳美仕"],
        "headquartersCountry": "Japan", "developedEconomy": True, "global500": False,
        "highSalaryIndustryPoints": 20, "priorityIndustryPoints": 60,
        "priorityIndustry": "半导体电子材料；已证实封装底部填充材料业务",
        "sources": ["https://www.namics.co.jp/en/products/use/automotive-applications/semiconductor-packages-automotive-applications/"],
    },
    "Sumitomo Bakelite": {
        "id": "sumitomo-bakelite", "aliases": ["Sumitomo Bakelite Co., Ltd.", "住友电木"],
        "headquartersCountry": "Japan", "developedEconomy": True, "global500": False,
        "highSalaryIndustryPoints": 10, "priorityIndustryPoints": 60,
        "priorityIndustry": "先进材料；已证实半导体封装树脂与固晶胶业务",
        "sources": ["https://www.sumibe.co.jp/english/product/it-materials/"],
    },
    "Shin-Etsu Chemical": {
        "id": "shin-etsu", "aliases": ["Shin-Etsu Chemical Co., Ltd.", "Shin Etsu", "信越化学"],
        "headquartersCountry": "Japan", "developedEconomy": True, "global500": False,
        "highSalaryIndustryPoints": 10, "priorityIndustryPoints": 60,
        "priorityIndustry": "先进材料；已证实半导体芯片贴装材料业务",
        "sources": ["https://www.shinetsu.co.jp/en/products/electronics-materials/die-attach-material/"],
    },
    "Qnity": {
        "id": "qnity", "aliases": ["Qnity Electronics", "Qnity Electronics, Inc."],
        "headquartersCountry": "United States", "developedEconomy": True, "global500": False,
        "highSalaryIndustryPoints": 20, "priorityIndustryPoints": 60,
        "priorityIndustry": "半导体电子材料；已证实先进封装、互连材料与工艺业务",
        "sources": ["https://www.qnityelectronics.com/news/qnity-powers-the-transition-from-shrink-to-stack-with-advanced-packaging-solutions.html"],
    },
}

ROLE_RULES = {
    "schemaVersion": 3,
    "scope": "千灯海外对标企业的生产技术／研发人才",
    "revision": "2026-09-20-qiandeng-technical-terms-v1",
    "classification": {
        "technicalTerms": [
            "Engineer", "Engineering", "Scientist", "Scientific", "Chemist", "Chemistry", "Research", "R&D", "Technical", "Technology", "Technologist", "Materials", "Process", "Production", "Manufacturing", "Development",
            "Packaging", "Assembly", "Reliability", "Yield", "Failure Analysis", "Integration", "Lithography", "Metrology", "Photonics", "Optics", "Interconnect", "Infrared", "Robotics", "Mechatronics", "Controls", "Automation", "Breeding", "Genomics", "Agronomy", "Plant Science", "Crop Science", "Molecular Biology",
            "研发", "研究", "工程", "工艺", "生产", "技术", "科学", "化学",
        ],
        "level2Terms": ["Senior", "Sr", "高级", "资深"],
        "level3Terms": ["Staff", "Principal", "Lead", "Manager", "主任", "经理", "负责人"],
        "level4Terms": ["Distinguished", "Chief", "Director", "Head", "VP", "首席", "总监", "总工程师"],
        "level4TitleEquals": ["Fellow", "Technical Fellow", "Corporate Fellow", "Distinguished Fellow", "CTO", "Chief Technology Officer", "Chief Scientific Officer"],
        "juniorTerms": ["Junior", "Intern", "Internship", "Trainee", "Apprentice", "初级", "实习", "见习", "学徒"],
        "ambiguousTerms": ["Associate", "Assistant", "Postdoctoral", "Postdoc", "助理", "博士后"],
        "nonResearchTerms": ["Sales", "Recruiter", "Recruitment", "Human Resources", "Payroll", "Operator", "Technician", "销售", "招聘", "人事", "操作工", "技工"],
    },
    "rules": [
        {"id": "operator", "enabled": True, "reason": "当前偏生产操作，未见高级技术／研发任职线索，请复核", "currentTitleEquals": ["Manufacturing Operator", "Manufacturing Operator I", "Manufacturing Operator II", "Manufacturing Operator III", "Manufacturing Operator IV", "Production Operator", "Machine Operator", "Assembly Operator"]},
        {"id": "sales", "enabled": True, "reason": "当前偏销售，未见高级技术／研发任职线索，请复核", "currentTitleEquals": ["Sales Representative", "Sales Manager", "Senior Sales Manager", "Account Executive"]},
        {"id": "recruitment", "enabled": True, "reason": "当前偏招聘／人事，未见高级技术／研发任职线索，请复核", "currentTitleEquals": ["Recruiter", "Senior Recruiter", "Talent Acquisition Manager", "Human Resources Manager"]},
        {"id": "administration", "enabled": True, "reason": "当前偏行政，未见高级技术／研发任职线索，请复核", "currentTitleEquals": ["Administrative Assistant", "Office Administrator", "Receptionist"]},
    ],
}


def canonical(name, domain):
    if name in RENAMES:
        target, aliases, target_domain = RENAMES[name]
        return target, aliases, target_domain
    return name, EXTRA_ALIASES.get(name, []), domain


def main():
    source = json.loads(SOURCE.read_text(encoding="utf-8"))
    qiandeng = {row["enterprise_id"]: row for row in source["domestic_enterprises"] if "千灯镇人才摸排表" in row.get("source_names", "")}
    benchmarks = {row["benchmark_id"]: row for row in source["benchmark_enterprises"]}
    pooled = {}
    mapping = defaultdict(list)

    for relation in source["domestic_benchmark_relations"]:
        enterprise = qiandeng.get(relation["enterprise_id"])
        if not enterprise:
            continue
        benchmark = benchmarks[relation["benchmark_id"]]
        name, aliases, domain = canonical(benchmark["benchmark_name"], benchmark["official_domain"])
        high, priority, priority_name = PRODUCT_SCORE[enterprise["product"]]
        key = name.casefold()
        existing = pooled.setdefault(key, {"name": name, "aliases": set(), "country": benchmark["country"], "domain": domain, "scores": []})
        existing["aliases"].update(aliases)
        if benchmark["benchmark_name"] != name:
            existing["aliases"].add(benchmark["benchmark_name"])
        existing["scores"].append((high, priority, priority_name))
        mapping[enterprise["enterprise_name"]].append({"product": enterprise["product"], "company": name, "role": relation["benchmark_type"], "rank": relation["benchmark_priority"]})

    companies = []
    for item in sorted(pooled.values(), key=lambda row: row["name"].casefold()):
        high, priority, priority_name = max(item["scores"], key=lambda value: value[0] + value[1])
        global500 = item["name"] in GLOBAL_500
        sources = ["https://" + item["domain"]]
        if global500:
            sources.insert(0, FORTUNE)
        companies.append({
            "id": "qiandeng-" + "".join(ch for ch in item["name"].lower() if ch.isalnum())[:55],
            "name": item["name"],
            "aliases": sorted(item["aliases"]),
            "headquartersCountry": item["country"],
            "developedEconomy": item["country"] in DEVELOPED,
            "global500": global500,
            **({"global500Year": 2026} if global500 else {}),
            "highSalaryIndustryPoints": high,
            "priorityIndustryPoints": priority,
            "priorityIndustry": priority_name,
            "checkedAt": TODAY,
            "sources": sources,
        })

    by_name = {item["name"]: item for item in companies}
    for name, override in USER_CONFIRMED.items():
        existing = by_name.get(name)
        if existing is None:
            existing = {"name": name, "aliases": [], "checkedAt": TODAY}
            companies.append(existing)
            by_name[name] = existing
        aliases = sorted(set(existing.get("aliases", [])) | set(override["aliases"]))
        existing.update(override)
        existing["aliases"] = aliases
        existing["checkedAt"] = TODAY
    companies.sort(key=lambda item: item["name"].casefold())
    enterprise_config = {"schemaVersion": 1, "revision": "2026-09-20-qiandeng-benchmark-manual-v2", "companies": companies}
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / "千灯_ContactOut_通用岗位规则.json").write_text(json.dumps(ROLE_RULES, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    (OUT / "千灯_ContactOut_海外对标企业评分库.json").write_text(json.dumps(enterprise_config, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    lines = ["# 千灯海外对标企业名单", "", "共 %d 家国内企业、%d 条对标关系、%d 家可在 ContactOut 评分库匹配的海外企业。" % (len(qiandeng), sum(len(rows) for rows in mapping.values()), len(companies)), ""]
    for enterprise in sorted(mapping):
        rows = sorted(mapping[enterprise], key=lambda row: row["rank"])
        lines.append("## %s（%s）" % (enterprise, rows[0]["product"]))
        lines.append("")
        per_company = {}
        for row in rows:
            existing = per_company.setdefault(row["company"], {"rank": row["rank"], "roles": set()})
            existing["rank"] = min(existing["rank"], row["rank"])
            existing["roles"].add(row["role"])
        lines.extend("- %s（%s）" % (name, "、".join(sorted(value["roles"])))
                     for name, value in sorted(per_company.items(), key=lambda item: item[1]["rank"]))
        lines.append("")
    (OUT / "千灯_海外对标企业名单.md").write_text("\n".join(lines), encoding="utf-8")
    score_summary = ["# 千灯 ContactOut 企业评分名单", "", "共 %d 家。L1 仅限明确 Fortune Global 500；其余按分数决定 L2/L3/L4。" % len(companies), "", "| 企业 | 分数 | 门槛 | 重点产业/业务 |", "|---|---:|---|---|"]
    for item in companies:
        score = 100 if item["global500"] else (20 if item["developedEconomy"] else 10) + item["highSalaryIndustryPoints"] + item["priorityIndustryPoints"]
        level = "L1" if item["global500"] else ("L2" if score >= 80 else "L3" if score >= 60 else "L4")
        score_summary.append("| %s | %d | %s | %s |" % (item["name"], score, level, item["priorityIndustry"]))
    (OUT / "千灯_ContactOut_企业评分完整名单.md").write_text("\n".join(score_summary) + "\n", encoding="utf-8")
    print(json.dumps({"domesticEnterprises": len(qiandeng), "relations": sum(len(rows) for rows in mapping.values()), "uniqueCompanies": len(companies)}, ensure_ascii=False))


if __name__ == "__main__":
    main()
