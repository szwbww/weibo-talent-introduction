#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
对标企业 OpenAlex 人才池盘点脚本（只用标准库，无需 pip）

两阶段设计，中间必须人工核验：

  阶段一 resolve  ——  用企业名去 OpenAlex 搜机构，把候选写成 CSV 给人看
  （人工编辑 CSV，把选中的行 keep 列改成 1，错的留 0）
  阶段二 scan     ——  只扫 keep=1 的机构，产出每家的论文量 / 作者量 / 可抽邮箱比例

用法：
  python3 openalex_benchmark_scan.py resolve --email you@yourdomain.com
  # 打开 out/01_institution_candidates.csv，人工把对的行 keep 改成 1
  python3 openalex_benchmark_scan.py scan --email you@yourdomain.com

注意：
  * --email 必须填真实邮箱，OpenAlex 的 polite pool 靠它给更高限速（10 req/s）。
  * 本脚本只读公开学术元数据，不抓取任何雇员名录、社交网站或非公开数据。
  * 输出里的「可抽邮箱作者数」是估算，真实抽取邮箱由你系统的 PDF/全文链路完成。
"""

import argparse
import csv
import json
import os
import sys
import time
import urllib.parse
import urllib.request
import urllib.error
from collections import OrderedDict

API = "https://api.openalex.org"

# ---------------------------------------------------------------------------
# 种子清单：板块 -> 国内企业 -> 国际对标企业搜索词
# 仁硕 / 聚富 待补全称后加进来即可，格式照抄。
# ---------------------------------------------------------------------------
SEEDS = [
    # (板块, 国内对标源企业, 国际企业搜索词, 备注/业务线)
    # 搜索词必须用 OpenAlex 的「裸名」——它把企业统一存成「名字 (国家)」，
    # search= 是 AND 语义，带 Inc / Corporation / Group / Incorporated 必然零匹配。
    ("海缆与光通信", "亨通光电", "SubCom",                  "海缆系统总包"),
    ("海缆与光通信", "亨通光电", "Alcatel Submarine",       "海缆系统总包"),
    ("海缆与光通信", "亨通光电", "NEC",                     "海缆系统总包/中继器"),
    ("海缆与光通信", "亨通光电", "Prysmian",                "海底电力缆"),
    ("海缆与光通信", "亨通光电", "Nexans",                  "海底电力缆"),
    ("海缆与光通信", "亨通光电", "NKT Photonics",           "海底电力缆(注意排掉 NKT Therapeutics)"),
    ("海缆与光通信", "亨通光电", "Sumitomo Electric",       "光纤/海缆"),
    ("海缆与光通信", "亨通光电", "Corning",                 "光纤/预制棒"),
    ("海缆与光通信", "亨通光电", "OFS",                     "光纤"),
    ("海缆与光通信", "亨通光电", "Furukawa Electric",       "光纤/光器件"),
    ("海缆与光通信", "亨通光电", "Fujikura",                "光纤/熔接"),
    ("海缆与光通信", "亨通光电", "Sterlite",                "光缆"),
    ("海缆与光通信", "亨通光电", "Heraeus",                 "石英/预制棒材料"),
    ("海缆与光通信", "亨通光电", "Shin-Etsu",               "石英/预制棒材料"),
    ("海缆与光通信", "亨通光电", "Lumentum",                "光模块/光器件"),
    ("海缆与光通信", "亨通光电", "Coherent",                "光模块/光器件"),

    ("电致变色调光", "光羿科技", "Gentex",                  "EC 器件龙头(汽车后视镜)"),
    ("电致变色调光", "光羿科技", "Saint-Gobain",            "建筑EC/车玻璃"),
    ("电致变色调光", "光羿科技", "SageGlass",               "建筑EC"),
    ("电致变色调光", "光羿科技", "Halio",                   "建筑EC(原 Kinestral)"),
    ("电致变色调光", "光羿科技", "Gauzy",                   "LC/SPD 竞争路线"),
    ("电致变色调光", "光羿科技", "Research Frontiers",      "SPD 授权方"),
    ("电致变色调光", "光羿科技", "Merck",                   "液晶材料(注意：制药主业，需按学科筛)"),
    ("电致变色调光", "光羿科技", "ChromoGenics",            "EC 卷对卷"),
    ("电致变色调光", "光羿科技", "Crown Electrokinetics",   "EC 薄膜"),
    ("电致变色调光", "光羿科技", "Asahi Glass",             "车玻璃 Tier1 (AGC 旧名)"),
    ("电致变色调光", "光羿科技", "AGC",                     "车玻璃 Tier1"),
    ("电致变色调光", "光羿科技", "Nippon Sheet Glass",      "车玻璃 Tier1"),
    ("电致变色调光", "光羿科技", "Vitro Automotive",        "车玻璃 Tier1(裸 Vitro 会撞 in vitro 医学机构)"),
    ("电致变色调光", "光羿科技", "Guardian Industries",     "车玻璃/镀膜"),

    # ("待定板块", "仁硕",   "TODO",  ""),
    # ("待定板块", "聚富",   "TODO",  ""),
]

# OpenAlex 现在也收录 GitHub 仓库、数据集等非论文记录，它们 OA 率近 100%，
# 不排掉会把 works_open_access 这个指标彻底污染（实测 Fujikura 7126 篇几乎全是这类）。
WORK_TYPES = "type:article|review|preprint|proceedings-article|book-chapter"

# OpenAlex 对企业机构的挂靠消歧非常脏：地名（Corning, NY）、常用词（Coherent/Vitro/View）、
# 方法学里提到的耗材品牌，都会被误挂成该企业的论文。实测 Lumentum、OFS 抽样标题 100% 无关。
# 所以机构不能单独用，必须和学科关键词求交集——这才是真正的分母。
SECTOR_KEYWORDS = {
    "海缆与光通信": [
        "optical fiber", "optical fibre", "submarine cable", "undersea cable", "subsea cable",
        "photonic", "erbium", "optical amplifier", "wavelength division", "fiber laser",
        "fibre laser", "optical preform", "silica glass", "optical transmission",
        "coherent optical", "fusion splicing", "optical cable", "optical transceiver",
        "silicon photonics", "optical interconnect",
    ],
    "电致变色调光": [
        "electrochromic", "smart window", "smart glass", "thermochromic", "tungsten oxide",
        "transparent conductive", "indium tin oxide", "liquid crystal", "dimming",
        "glazing", "solar control coating", "electrolyte film", "sputtered coating",
        "switchable glazing", "optical modulation glass",
    ],
}


def domain_filter(sector):
    kws = SECTOR_KEYWORDS.get(sector)
    if not kws:
        return None
    return "title_and_abstract.search:" + "|".join(kws)

CAND_CSV = "01_institution_candidates.csv"
SCAN_CSV = "02_institution_scan.csv"
AUTH_CSV = "03_author_sample.csv"

# ---------------------------------------------------------------------------
# HTTP
# ---------------------------------------------------------------------------

class Client:
    def __init__(self, email, delay=0.15, timeout=30, verbose=True):
        self.email = email
        self.delay = delay
        self.timeout = timeout
        self.verbose = verbose
        self.calls = 0

    def get(self, path, params):
        params = dict(params)
        params["mailto"] = self.email
        url = "%s%s?%s" % (API, path, urllib.parse.urlencode(params))
        backoff = 2.0
        for attempt in range(6):
            try:
                req = urllib.request.Request(
                    url, headers={"User-Agent": "benchmark-scan/1.0 (mailto:%s)" % self.email}
                )
                with urllib.request.urlopen(req, timeout=self.timeout) as resp:
                    self.calls += 1
                    time.sleep(self.delay)
                    return json.loads(resp.read().decode("utf-8"))
            except urllib.error.HTTPError as e:
                if e.code in (429, 503):
                    if self.verbose:
                        sys.stderr.write("  [%s] 限速/不可用，%.0fs 后重试 (%d/6)\n" % (e.code, backoff, attempt + 1))
                    time.sleep(backoff)
                    backoff = min(backoff * 2, 60)
                    continue
                if e.code == 404:
                    return None
                sys.stderr.write("  HTTP %s: %s\n" % (e.code, url))
                return None
            except Exception as e:
                if self.verbose:
                    sys.stderr.write("  网络错误 %s，%.0fs 后重试 (%d/6)\n" % (e, backoff, attempt + 1))
                time.sleep(backoff)
                backoff = min(backoff * 2, 60)
        sys.stderr.write("  放弃：%s\n" % url)
        return None


MED_WORDS = ("medic", "health", "hospital", "clinic", "therapeut", "pharma", "bio",
             "vitro", "vivo", "diagnos", "cancer", "genom", "nurs", "dental", "vaccin")

def risk_flag(term, inst):
    """给候选机构自动打风险标记，人工只需重点看有标记的行。"""
    name = (inst.get("display_name") or "")
    low = name.lower()
    flags = []
    norm = lambda x: x.lower().replace("-", " ").replace(".", " ")
    head = norm(term).split()[0]
    if head not in norm(name):
        flags.append("名字对不上")
    if any(w in low for w in MED_WORDS):
        flags.append("疑似医学/生物机构")
    t = (inst.get("type") or "")
    if t not in ("company", "facility"):
        flags.append("type=%s" % (t or "空"))
    if not inst.get("country_code"):
        flags.append("无国家")
    return " / ".join(flags) if flags else ""


def short_id(openalex_url):
    return (openalex_url or "").rstrip("/").split("/")[-1]


# ---------------------------------------------------------------------------
# 阶段一：解析机构候选
# ---------------------------------------------------------------------------

def cmd_resolve(args):
    client = Client(args.email, delay=args.delay)
    out_path = os.path.join(args.out_dir, CAND_CSV)
    rows = []

    for sector, cn_company, term, note in SEEDS:
        if term == "TODO":
            continue
        sys.stderr.write("解析机构：%s\n" % term)
        data = client.get("/institutions", {"search": term, "per_page": args.candidates})
        results = (data or {}).get("results", [])
        if not results:
            rows.append({
                "keep": "0", "risk": "零匹配", "sector": sector, "cn_company": cn_company, "search_term": term,
                "note": note, "institution_id": "", "display_name": "(无匹配)",
                "country": "", "type": "", "ror": "", "works_count": "0", "homepage": "",
            })
            continue
        for r in results:
            rows.append({
                "keep": "0",
                "risk": risk_flag(term, r),
                "sector": sector,
                "cn_company": cn_company,
                "search_term": term,
                "note": note,
                "institution_id": short_id(r.get("id")),
                "display_name": r.get("display_name", ""),
                "country": r.get("country_code") or "",
                "type": r.get("type") or "",
                "ror": r.get("ror") or "",
                "works_count": r.get("works_count", 0),
                "homepage": (r.get("homepage_url") or ""),
            })

    fields = ["keep", "risk", "sector", "cn_company", "search_term", "note", "institution_id",
              "display_name", "country", "type", "ror", "works_count", "homepage"]
    write_csv(out_path, fields, rows)

    print("")
    print("已写出候选机构 %d 行 -> %s" % (len(rows), out_path))
    print("")
    print("下一步（必须人工做，别跳）：")
    print("  1. 用 Excel / 表格软件打开这个 CSV")
    print("  2. 逐行看 display_name + country + type + homepage，确认是不是你要的那家企业")
    print("     ——  注意排掉同名的博物馆、医院、地名、基金会，以及 type=education 的同名大学")
    print("     ——  一家企业可以勾多行（母公司 + 研发子公司都留）")
    print("  3. 把选中行的 keep 列改成 1，存盘")
    print("  4. 跑：python3 %s scan --email %s" % (os.path.basename(__file__), args.email))


# ---------------------------------------------------------------------------
# 阶段二：扫描已确认机构
# ---------------------------------------------------------------------------

def cmd_scan(args):
    client = Client(args.email, delay=args.delay)
    cand_path = os.path.join(args.out_dir, CAND_CSV)
    if not os.path.exists(cand_path):
        sys.exit("找不到 %s，先跑 resolve" % cand_path)

    with open(cand_path, "r", encoding="utf-8-sig") as f:
        picked = [r for r in csv.DictReader(f)
                  if str(r.get("keep", "")).strip() in ("1", "y", "Y", "true", "TRUE")
                  and r.get("institution_id")]

    if not picked:
        sys.exit("候选表里一行 keep=1 都没有。先人工确认机构，把 keep 改成 1。")

    # 同一机构可能被多个搜索词勾中（如 AGC / Asahi Glass），去重，否则重复扫且作者重复计数
    seen_ids = set()
    deduped = []
    for r in picked:
        iid = r["institution_id"].strip()
        if iid in seen_ids:
            sys.stderr.write("  跳过重复机构 %s (%s)\n" % (r["display_name"], iid))
            continue
        seen_ids.add(iid)
        deduped.append(r)
    picked = deduped

    year_to = args.to_year
    year_from = args.from_year
    scan_rows = []
    author_rows = []

    for i, r in enumerate(picked, 1):
        inst_id = r["institution_id"].strip()
        name = r["display_name"]
        sys.stderr.write("[%d/%d] 扫描 %s (%s) %d-%d\n" % (i, len(picked), name, inst_id, year_from, year_to))

        base = "authorships.institutions.lineage:%s,publication_year:%d-%d,%s" % (inst_id, year_from, year_to, WORK_TYPES)

        total = count_works(client, base)
        dom_f = domain_filter(r["sector"])
        dom_base = base + "," + dom_f if dom_f else base
        dom = count_works(client, dom_base) if dom_f else total
        oa = count_works(client, dom_base + ",is_oa:true")
        top_field, top_ratio, med_ratio = field_mix(client, dom_base, dom)
        # 只从对口论文里抽作者，否则样本里全是被误挂的无关作者
        sample = sample_authors(client, dom_base, inst_id, args.max_works, args.delay)

        n_auth = len(sample)
        n_orcid = sum(1 for a in sample.values() if a["orcid"])
        scan_rows.append(OrderedDict([
            ("sector", r["sector"]),
            ("cn_company", r["cn_company"]),
            ("institution_id", inst_id),
            ("display_name", name),
            ("country", r["country"]),
            ("note", r.get("note", "")),
            ("works_all_%d_%d" % (year_from, year_to), total),
            ("works_ondomain", dom),
            ("ondomain_ratio", "%.2f" % (dom / total) if total else "0.00"),
            ("ondomain_open_access", oa),
            ("oa_ratio", "%.2f" % (oa / dom) if dom else "0.00"),
            ("sampled_works", min(dom, args.max_works)),
            ("distinct_authors_in_sample", n_auth),
            ("authors_with_orcid", n_orcid),
            ("orcid_ratio", "%.2f" % (n_orcid / n_auth) if n_auth else "0.00"),
            ("top_field", top_field),
            ("top_field_ratio", "%.2f" % top_ratio),
            ("medical_ratio", "%.2f" % med_ratio),
            ("verdict", verdict(total, dom, oa, n_auth, med_ratio)),
        ]))

        for aid, a in sample.items():
            author_rows.append(OrderedDict([
                ("sector", r["sector"]),
                ("cn_company", r["cn_company"]),
                ("institution", name),
                ("author_id", aid),
                ("author_name", a["name"]),
                ("orcid", a["orcid"] or ""),
                ("works_in_sample", a["n"]),
                ("latest_year", a["year"]),
                ("sample_work_title", a["title"][:180]),
            ]))

    scan_path = os.path.join(args.out_dir, SCAN_CSV)
    write_csv(scan_path, list(scan_rows[0].keys()), scan_rows)
    auth_path = os.path.join(args.out_dir, AUTH_CSV)
    if author_rows:
        write_csv(auth_path, list(author_rows[0].keys()), author_rows)

    print_table(scan_rows, year_from, year_to)
    print("")
    print("机构汇总 -> %s" % scan_path)
    print("作者样本 -> %s  (%d 人)" % (auth_path, len(author_rows)))
    print("API 调用次数：%d" % client.calls)
    print("")
    print("怎么读这张表：")
    print("  works_ondomain     = 机构 × 学科关键词求交集后的论文数。**只看这一列，别看全量。**")
    print("                       OpenAlex 对企业的挂靠消歧很脏（地名/常用词/耗材品牌都会误挂），")
    print("                       全量越大越可疑：实测 Lumentum 95 人、OFS 75 人抽样标题 100% 无关。")
    print(u"  ondomain_ratio     = 对口率。低于 2% 说明这家的机构信号基本是噪声。")
    print("  ondomain_open_access = 对口且有开放全文，这才是真正的邮箱来源分母。")
    print("  distinct_authors   = 抽样论文里挂该机构的作者去重数（抽样上限 --max-works，默认 %d）。" % args.max_works)
    print("                       总数远大于此，这个数只用来判断「值不值得建这条源」。")
    print("  authors_with_orcid = 有 ORCID 的人。这批人能直接接进你现有的 ORCID 链路。")
    print("  verdict            = 建议：论文路 / 专利路 / 放弃。")


def field_mix(client, filter_str, total):
    """按 OpenAlex 学科领域分组，返回 (最大领域, 占比, 医学生物类占比)。"""
    if not total:
        return ("", 0.0, 0.0)
    d = client.get("/works", {"filter": filter_str, "group_by": "primary_topic.field.id", "per_page": 200})
    groups = (d or {}).get("group_by") or []
    if not groups:
        return ("", 0.0, 0.0)
    named = [(g.get("key_display_name") or "?", int(g.get("count") or 0)) for g in groups]
    tot = sum(c for _, c in named) or 1
    named.sort(key=lambda x: -x[1])
    med = sum(c for n, c in named
              if any(w in n.lower() for w in ("medic", "health", "biochem", "immun", "neuro",
                                              "pharma", "nurs", "dent", "veterin", "agricult")))
    return (named[0][0], named[0][1] / tot, med / tot)


def count_works(client, filter_str):
    d = client.get("/works", {"filter": filter_str, "per_page": 1})
    return int(((d or {}).get("meta") or {}).get("count") or 0)


def sample_authors(client, filter_str, inst_id, max_works, delay):
    """翻页抓最近的论文，收集挂该机构的去重作者。"""
    authors = {}
    cursor = "*"
    fetched = 0
    use_sort = True
    while cursor and fetched < max_works:
        per = min(200, max_works - fetched)
        params = {
            "filter": filter_str,
            "per_page": per,
            "cursor": cursor,
            "select": "id,title,publication_year,authorships",
        }
        if use_sort:
            params["sort"] = "publication_year:desc"
        d = client.get("/works", params)
        if not d and use_sort and fetched == 0:
            # 某些 filter 组合下 sort+cursor 会被拒；退回不排序再试一次
            use_sort = False
            continue
        if not d:
            break
        results = d.get("results", [])
        if not results:
            break
        for w in results:
            year = w.get("publication_year") or 0
            title = w.get("title") or ""
            for a in w.get("authorships", []):
                inst_ids = [short_id(x.get("id")) for x in (a.get("institutions") or [])]
                lineage = []
                for x in (a.get("institutions") or []):
                    lineage += [short_id(u) for u in (x.get("lineage") or [])]
                if inst_id not in inst_ids and inst_id not in lineage:
                    continue
                au = a.get("author") or {}
                aid = short_id(au.get("id"))
                if not aid:
                    continue
                cur = authors.get(aid)
                if cur:
                    cur["n"] += 1
                    if year > cur["year"]:
                        cur["year"], cur["title"] = year, title
                else:
                    authors[aid] = {
                        "name": au.get("display_name") or "",
                        "orcid": (au.get("orcid") or "").replace("https://orcid.org/", ""),
                        "n": 1, "year": year, "title": title,
                    }
        fetched += len(results)
        cursor = (d.get("meta") or {}).get("next_cursor")
    return authors


def verdict(total, dom, oa, n_auth, med_ratio=0.0):
    if total == 0:
        return "放弃论文路：该机构零学术产出，只能走专利/标准组织"
    if dom == 0:
        return "整条不可用：该机构名下零篇对口论文，全是 OpenAlex 误挂"
    ratio = dom / total
    if ratio < 0.02 and total > 200:
        return "机构信号极脏（对口率<2%）：只能用对口子集，别信总量"
    if med_ratio >= 0.5:
        return "疑似认错机构：对口集里仍过半医学/生物，回去核对 01 表这一行"
    if oa < 20:
        return "论文路薄：优先走专利发明人，论文当补充"
    if n_auth >= 50 and oa >= 100:
        return "论文路可行：直接建源，量够"
    return "论文路可行但量小：并进相邻企业一起跑"


def write_csv(path, fields, rows):
    os.makedirs(os.path.dirname(path) or ".", exist_ok=True)
    with open(path, "w", newline="", encoding="utf-8-sig") as f:
        w = csv.DictWriter(f, fieldnames=fields)
        w.writeheader()
        for r in rows:
            w.writerow(r)


def print_table(rows, y0, y1):
    wkey = "works_all_%d_%d" % (y0, y1)
    print("")
    print("%-12s %-30s %-4s %7s %7s %7s %7s %6s  %s" % (
        "板块", "机构", "国", "全量", "对口", "开放", "作者", "ORCID", "对口率"))
    print("-" * 118)
    for r in rows:
        print("%-12s %-30s %-4s %7s %7s %7s %7s %6s  %s" % (
            trunc(r["sector"], 12), trunc(r["display_name"], 30), r["country"],
            r[wkey], r["works_ondomain"], r["ondomain_open_access"],
            r["distinct_authors_in_sample"], r["authors_with_orcid"], r["ondomain_ratio"]))


def trunc(s, n):
    s = s or ""
    w = 0
    out = ""
    for ch in s:
        cw = 2 if ord(ch) > 0x2E80 else 1
        if w + cw > n:
            break
        out += ch
        w += cw
    return out


def main():
    p = argparse.ArgumentParser(description="对标企业 OpenAlex 人才池盘点")
    p.add_argument("mode", choices=["resolve", "scan"])
    p.add_argument("--email", required=True, help="真实邮箱，用于 OpenAlex polite pool")
    p.add_argument("--out-dir", default=os.path.join(os.path.dirname(os.path.abspath(__file__)), "out"))
    p.add_argument("--candidates", type=int, default=5, help="resolve 时每个搜索词返回几个候选")
    p.add_argument("--from-year", type=int, default=2021)
    p.add_argument("--to-year", type=int, default=2026)
    p.add_argument("--max-works", type=int, default=400, help="scan 时每机构抽样论文上限")
    p.add_argument("--delay", type=float, default=0.15, help="请求间隔秒")
    args = p.parse_args()
    os.makedirs(args.out_dir, exist_ok=True)
    if args.mode == "resolve":
        cmd_resolve(args)
    else:
        cmd_scan(args)


if __name__ == "__main__":
    main()
