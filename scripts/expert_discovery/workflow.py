"""Apollo People Search discovery, review proposals and optional email stages."""
from __future__ import annotations

import copy
import json
import re
import urllib.parse
from collections import Counter
from datetime import date as calendar_date
from pathlib import Path

from adapters import EXTRACTION_PROMPT, PROFILE_PROMPT, RANK_PROMPT, fetch_document
from core import Stop, atomic_json, clean, csv_write, digest, read_json, stamp

EMAIL = re.compile(r"[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9.-]*[A-Za-z0-9])?\.[A-Za-z]{2,}")
FREE_DOMAINS = {"gmail.com", "yahoo.com", "outlook.com", "hotmail.com", "qq.com", "163.com", "icloud.com"}
ROLE_NAMES = {"info", "support", "sales", "contact", "admin", "office", "editor", "secretariat", "noreply"}
PUBLIC_PROMPT_VERSION = digest(EXTRACTION_PROMPT)
PROFILE_PROMPT_VERSION = digest(PROFILE_PROMPT)
RANK_PROMPT_VERSION = digest(RANK_PROMPT)


def validate_config(raw):
    config = copy.deepcopy(raw)
    config.setdefault("strategy", "PUBLIC_SOURCES")
    if config["strategy"] not in {"PUBLIC_SOURCES", "APOLLO_PEOPLE"}:
        raise Stop("strategy 仅支持 PUBLIC_SOURCES 或 APOLLO_PEOPLE。")
    for field in ("company", "demand_company", "domain"):
        if not isinstance(config.get(field), str) or not clean(config[field]):
            raise Stop(f"配置缺少 {field}。")
        config[field] = clean(config[field])
    if not re.fullmatch(r"[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}", config["domain"]):
        raise Stop("domain 应为公司域名，不带协议。")
    config["domain"] = config["domain"].lower()
    for field in ("topics", "titles"):
        values = config.get(field)
        if not isinstance(values, list) or not values or any(not isinstance(v, str) or not clean(v) for v in values):
            raise Stop(f"{field} 必须是非空字符串数组。")
    for field in ("source_urls", "allowed_hosts"):
        values = config.setdefault(field, [])
        if not isinstance(values, list) or any(not isinstance(v, str) or not clean(v) for v in values):
            raise Stop(f"{field} 必须是字符串数组。")
        if config["strategy"] == "PUBLIC_SOURCES" and not values:
            raise Stop(f"{field} 必须是非空字符串数组。")
    config["allowed_hosts"] = [v.lower() for v in config["allowed_hosts"]]
    for url in config["source_urls"]:
        parsed = urllib.parse.urlsplit(url)
        if parsed.scheme != "https" or parsed.hostname not in config["allowed_hosts"] or parsed.username or parsed.password:
            raise Stop("所有来源必须是 allowed_hosts 中的 HTTPS 地址。")
    config.setdefault("aliases", [config["company"]])
    config.setdefault("countries", [])
    for key in ("aliases", "countries"):
        if not isinstance(config[key], list) or any(not isinstance(v, str) for v in config[key]):
            raise Stop(f"{key} 必须是字符串数组。")
    config.setdefault("include_historical", True)
    if not isinstance(config["include_historical"], bool):
        raise Stop("include_historical 必须是布尔值。")
    config.setdefault("overseas_only", True)
    if not isinstance(config["overseas_only"], bool):
        raise Stop("overseas_only 必须是布尔值。")
    config.setdefault("model", "deepseek-v4-flash")
    if not isinstance(config["model"], str) or not config["model"]:
        raise Stop("model 必须是模型名称。")
    defaults = {"fetch": 6, "deepseek": 3, "deepseek_profile": 1, "deepseek_rank": 1,
                "input_chars": 14000, "output_tokens": 2200,
                "apollo_enrich": 1, "email_verify": 1, "apollo_search_pages": 1}
    budgets = config.setdefault("budgets", {})
    if not isinstance(budgets, dict):
        raise Stop("budgets 必须是对象。")
    for key, default in defaults.items():
        budgets.setdefault(key, default)
        if type(budgets[key]) is not int or budgets[key] < 0:
            raise Stop(f"budgets.{key} 必须为非负整数。")
    if budgets["fetch"] > 50 or budgets["deepseek"] > 20 or not 1000 <= budgets["input_chars"] <= 30000 or not 256 <= budgets["output_tokens"] <= 6000:
        raise Stop("本地试验上限：50页、20次模型调用、1000~30000输入字符、256~6000输出token。")
    if config["strategy"] == "PUBLIC_SOURCES" and len(config["source_urls"]) > budgets["fetch"]:
        raise Stop("种子链接数量超过本轮读取预算。")
    config["queries"] = [f'"{config["company"]}" "{topic}"' for topic in config["topics"]]
    return config


def plan_proposal(job):
    cfg = job.data["config"]
    if cfg.get("strategy") == "APOLLO_PEOPLE":
        return job.propose("discover", [{"company": cfg["company"], "domain": cfg["domain"],
            "requirement": cfg["requirement"]}],
            {"deepseek.profile": 1, "apollo.search": cfg["budgets"]["apollo_search_pages"],
             "deepseek.rank": 1},
            "DeepSeek 将需求转换为人员检索条件；Apollo 仅执行人员搜索；DeepSeek 对名单排序。不会获取邮箱或验证邮箱。")
    return job.propose("discover", [{"url": url} for url in cfg["source_urls"]],
        {"public.fetch": cfg["budgets"]["fetch"], "deepseek.extract": cfg["budgets"]["deepseek"]},
        "指定来源检索；只沿批准域名读取相关链接。模型分析按上限执行。Apollo=0，Emailable=0。")


def quoted(quote, text, value=None):
    q = clean(quote)
    return bool(q and q in clean(text) and (not value or clean(value).casefold() in q.casefold()))


def email_flags(address):
    if not EMAIL.fullmatch(address):
        return ["INVALID_FORMAT"]
    user, host = address.rsplit("@", 1)
    flags = []
    if host.lower() in FREE_DOMAINS:
        flags.append("FREE_EMAIL_REVIEW")
    if user.lower() in ROLE_NAMES:
        flags.append("ROLE_EMAIL_REVIEW")
    return flags


def base_candidate(name, origin):
    return {"id": "P-" + digest([clean(name).casefold(), origin])[:12], "name": clean(name),
            "apollo_id": "", "linkedin_url": "", "country": "", "title": "",
            "evidence": [], "emails": [], "issues": [], "provider_profiles": [],
            "source_origins": [origin], "merged_ids": []}


def extract_checked(person, doc, cfg):
    if not isinstance(person, dict):
        raise Stop("模型候选不是对象。")
    name = clean(person.get("name"))
    if len(name) < 3 or not quoted(person.get("name_quote"), doc["text"], name):
        raise Stop("姓名缺少原文依据。")
    c = base_candidate(name, doc["url"])
    c["evidence"].append({"kind": "identity", "quote": clean(person["name_quote"]),
                           "source_url": doc["url"], "document_id": doc["id"]})
    date = clean(person.get("source_date"))
    try:
        calendar_date.fromisoformat(date)
        valid_date = True
    except ValueError:
        valid_date = False
    if date and not (valid_date and re.fullmatch(r"\d{4}-\d{2}-\d{2}", date) and
                     quoted(person.get("source_date_quote"), doc["text"], date)):
        c["issues"].append("SOURCE_DATE_UNSUPPORTED")
        date = ""
    def evidence(kind, quote, **fields):
        c["evidence"].append(dict(kind=kind, quote=clean(quote), source_url=doc["url"],
            document_id=doc["id"], source_date=date, fetched_at=doc["fetched_at"], **fields))
    company = clean(person.get("company"))
    if company and quoted(person.get("employment_quote"), doc["text"], company):
        claimed = person.get("employment_kind", "UNKNOWN")
        # A model cannot declare current employment solely from a dated source.
        status = "HISTORICAL" if claimed == "HISTORICAL" else "UNKNOWN"
        evidence("employment", person["employment_quote"], company=company,
                 employment_kind=status, model_claim=claimed)
        if claimed == "CURRENT":
            c["issues"].append("CURRENT_EMPLOYMENT_NEEDS_REVIEW")
    if person.get("title") and quoted(person.get("title_quote"), doc["text"], person["title"]):
        c["title"] = clean(person["title"])
        evidence("role", person["title_quote"])
    if person.get("country") and quoted(person.get("country_quote"), doc["text"], person["country"]):
        c["country"] = clean(person["country"])
        evidence("location", person["country_quote"])
    if quoted(person.get("technical_quote"), doc["text"]):
        evidence("technical", person["technical_quote"], attribution="MODEL_EXTRACTED_NEEDS_REVIEW")
    for option in person.get("emails", []) if isinstance(person.get("emails", []), list) else []:
        if not isinstance(option, dict):
            continue
        address = clean(option.get("address"))
        if EMAIL.fullmatch(address) and quoted(option.get("quote"), doc["text"], address):
            address = address.lower()
            c["emails"].append({"address": address, "source": "PUBLIC_DOCUMENT", "source_url": doc["url"],
                "source_date": date, "quote": clean(option["quote"]), "ownership": "NEEDS_REVIEW",
                "provider_status": "", "flags": email_flags(address)})
    return c


def add_candidate(job, incoming):
    candidates = job.data["candidates"]
    target = None
    for c in candidates:
        strong = (c["id"] == incoming["id"] or incoming["id"] in c.get("merged_ids", []) or
                  (c.get("apollo_id") and c["apollo_id"] == incoming.get("apollo_id")))
        same_name = c["name"].casefold() == incoming["name"].casefold()
        same_mail = {v["address"] for v in c["emails"]} & {v["address"] for v in incoming["emails"]}
        conflict = any(c.get(k) and incoming.get(k) and c[k] != incoming[k]
                       for k in ("apollo_id", "linkedin_url"))
        manual = any(v.startswith("MATCH_TO_REVIEW:") for v in incoming["issues"])
        if not conflict and (strong or (not manual and same_name and same_mail and not any(email_flags(e) for e in same_mail))):
            target = c
            break
    if target:
        merge_into(target, incoming)
    else:
        candidates.append(incoming)
    mark_conflicts(candidates)


def merge_into(target, incoming):
    for key in ("apollo_id", "linkedin_url", "country", "title"):
        if incoming.get(key) and target.get(key) and target[key] != incoming[key]:
            target["issues"].append("FIELD_CONFLICT:" + key)
        elif not target.get(key):
            target[key] = incoming.get(key, "")
    for key in ("evidence", "emails", "issues", "provider_profiles", "source_origins", "merged_ids"):
        for value in incoming.get(key, []):
            if value not in target[key]:
                target[key].append(copy.deepcopy(value))
    if incoming["id"] != target["id"] and incoming["id"] not in target["merged_ids"]:
        target["merged_ids"].append(incoming["id"])


def mark_conflicts(candidates):
    for c in candidates:
        c["issues"] = [v for v in c["issues"] if v not in {"EMAIL_IDENTITY_CONFLICT", "POSSIBLE_SAME_NAME"}]
    for i, first in enumerate(candidates):
        for second in candidates[i + 1:]:
            shared = {v["address"] for v in first["emails"]} & {v["address"] for v in second["emails"]}
            flag = "EMAIL_IDENTITY_CONFLICT" if shared else "POSSIBLE_SAME_NAME" if first["name"].casefold() == second["name"].casefold() else None
            if flag:
                for c in (first, second):
                    if flag not in c["issues"]:
                        c["issues"].append(flag)


def selected(job, ids):
    if not ids or len(ids) != len(set(ids)):
        raise Stop("请提供不重复的人员 ID。")
    by_id = {p["id"]: p for p in job.data["candidates"]}
    if any(i not in by_id for i in ids):
        raise Stop("人员 ID 不存在；请 review 最新名单。")
    return [by_id[i] for i in ids]


def assessment(candidate, cfg):
    evidence = candidate["evidence"]
    aliases = {clean(a).casefold() for a in [cfg["company"], *cfg["aliases"]]}
    employment = [e for e in evidence if e["kind"] == "employment" and
                  (clean(e.get("company", "")).casefold() in aliases or
                   any(company_names_equivalent(e.get("company", ""), alias) for alias in aliases))]
    technical = " ".join(e["quote"] for e in evidence if e["kind"] == "technical").casefold()
    topics = [t for t in cfg["topics"] if t.casefold() in technical]
    country = candidate.get("country", "").casefold()
    issues = list(candidate["issues"])
    if not employment:
        issues.append("TARGET_EMPLOYMENT_UNCONFIRMED")
    if not topics:
        issues.append("TOPIC_EVIDENCE_MISSING")
    if not cfg["include_historical"] and not any(e.get("employment_kind") == "APOLLO_REPORTED_CURRENT" for e in employment):
        issues.append("CURRENT_EMPLOYMENT_UNCONFIRMED")
    if cfg["overseas_only"]:
        if not country:
            issues.append("LOCATION_UNKNOWN")
        elif country in {"china", "cn", "中国", "中国大陆", "people's republic of china"}:
            issues.append("LOCATION_EXCLUDED")
    if cfg["countries"] and country not in {v.casefold() for v in cfg["countries"]}:
        issues.append("LOCATION_NOT_MATCHED")
    ready = bool(employment and topics) and not any(v in issues for v in
        ("EMAIL_IDENTITY_CONFLICT", "LOCATION_EXCLUDED", "LOCATION_NOT_MATCHED", "LOCATION_UNKNOWN", "CURRENT_EMPLOYMENT_UNCONFIRMED"))
    return {"status": "EVIDENCE_READY_FOR_REVIEW" if ready else "NEEDS_EVIDENCE_REVIEW",
            "topics": topics, "issues": sorted(set(issues)),
            "reason": f"目标企业经历证据{len(employment)}条；技术关键词命中：{', '.join(topics) or '无'}。需人工核对个人归属及具体职责。"}


def checked_profile(reply):
    profile = reply.get("profile")
    if not isinstance(profile, dict):
        raise Stop("DeepSeek 未返回 profile 对象。")
    result = {}
    for key, maximum in (("titles", 8), ("topics", 12), ("seniorities", 8), ("countries", 8)):
        values = profile.get(key)
        if not isinstance(values, list) or any(not isinstance(v, str) for v in values):
            raise Stop(f"DeepSeek profile.{key} 必须是字符串数组。")
        result[key] = [clean(v) for v in values if clean(v)][:maximum]
    allowed = {"owner", "founder", "c_suite", "partner", "vp", "head", "director",
               "manager", "senior", "entry", "intern"}
    result["seniorities"] = [v for v in result["seniorities"] if v in allowed]
    if not result["titles"]:
        raise Stop("DeepSeek 未生成可用职位条件。")
    return result


def normalized_company_name(value):
    tokens = re.findall(r"[\w]+", clean(value).casefold())
    suffixes = {"group", "holding", "holdings", "inc", "incorporated", "corp",
                "corporation", "ltd", "limited", "plc", "llc", "spa", "gmbh", "ag"}
    while len(tokens) > 1 and tokens[-1] in suffixes:
        tokens.pop()
    return " ".join(tokens)


def company_names_equivalent(first, second):
    return bool(normalized_company_name(first)
                and normalized_company_name(first) == normalized_company_name(second))


def matches_current_benchmark(person, cfg):
    org = person.get("organization") or {}
    name = clean(org.get("name")).casefold()
    domain = clean(org.get("primary_domain") or org.get("domain")).lower()
    aliases = {clean(v).casefold() for v in [cfg["company"], *cfg.get("aliases", [])]}
    # Apollo's domain filter also finds former employers. A clearly different current
    # organization must not enter a list described as people at the benchmark company.
    if domain:
        return domain.removeprefix("www.") == cfg["domain"].removeprefix("www.")
    if name:
        return name in aliases or any(company_names_equivalent(name, alias) for alias in aliases)
    return True


def safe_search_response(reply):
    if not isinstance(reply, dict):
        return reply
    result = copy.deepcopy(reply)
    contact_fields = {"email", "personal_email", "personal_emails", "phone", "phone_numbers",
                      "mobile_phone", "mobile_phone_number", "sanitized_phone"}
    if isinstance(result.get("people"), list):
        result["people"] = [{k: v for k, v in person.items() if k not in contact_fields}
                            if isinstance(person, dict) else person for person in result["people"]]
    return result


def do_apollo_people_discover(job, p, providers):
    cfg = job.data["config"]
    common = {"model": cfg["model"], "max_tokens": cfg["budgets"]["output_tokens"]}
    profile_params = dict(common, prompt_version=PROFILE_PROMPT_VERSION,
                          input={"requirement": cfg["requirement"],
                                 "benchmark_company": cfg["company"], "benchmark_domain": cfg["domain"]})
    profile_reply = job.request(p, "deepseek.profile", profile_params,
                                lambda: providers.call("deepseek.profile", profile_params))
    profile = checked_profile(profile_reply)
    job.data["search_profile"] = profile
    job.save()

    search_params = {"q_organization_domains_list": [cfg["domain"]],
                     "person_titles": profile["titles"], "include_similar_titles": True,
                     "page": 1, "per_page": 25}
    if profile["seniorities"]:
        search_params["person_seniorities"] = profile["seniorities"]
    if profile["countries"]:
        search_params["person_locations"] = profile["countries"]
    search_reply = job.request(p, "apollo.search", search_params,
                               lambda: safe_search_response(providers.call("apollo.search", search_params)))
    people = search_reply.get("people")
    if not isinstance(people, list):
        raise Stop("Apollo 搜索响应缺少 people。")
    for person in people:
        if isinstance(person, dict) and person.get("id") and matches_current_benchmark(person, cfg):
            candidate = apollo_candidate(person, "apollo-people-discovery", full=False)
            candidate["issues"].append("TECHNICAL_EVIDENCE_NOT_VERIFIED")
            candidate["ranking"] = {}
            add_candidate(job, candidate)
    job.save()
    if not job.data["candidates"]:
        return

    summaries = [{"id": c["apollo_id"], "name": c["name"], "title": c["title"],
                  "country": c["country"], "organization": next((e.get("company", "")
                  for e in c["evidence"] if e["kind"] == "employment"), "")}
                 for c in job.data["candidates"] if c.get("apollo_id")]
    rank_params = dict(common, prompt_version=RANK_PROMPT_VERSION,
                       input={"requirement": cfg["requirement"], "search_profile": profile,
                              "candidates": summaries})
    rank_reply = job.request(p, "deepseek.rank", rank_params,
                             lambda: providers.call("deepseek.rank", rank_params))
    rankings = rank_reply.get("rankings")
    if not isinstance(rankings, list):
        raise Stop("DeepSeek 未返回 rankings 数组。")
    by_apollo = {c.get("apollo_id"): c for c in job.data["candidates"] if c.get("apollo_id")}
    for item in rankings:
        if not isinstance(item, dict) or item.get("id") not in by_apollo:
            continue
        score = item.get("score")
        if type(score) is not int or not 0 <= score <= 100:
            continue
        matched = item.get("matched_requirements", [])
        limitations = item.get("limitations", [])
        if not isinstance(matched, list) or not isinstance(limitations, list):
            continue
        by_apollo[item["id"]]["ranking"] = {
            "score": score,
            "matched_requirements": [clean(v) for v in matched if isinstance(v, str) and clean(v)],
            "reason": clean(item.get("reason")),
            "limitations": [clean(v) for v in limitations if isinstance(v, str) and clean(v)],
        }
    job.data["candidates"].sort(key=lambda c: c.get("ranking", {}).get("score", -1), reverse=True)
    job.save()


def do_discover(job, p, providers):
    cfg = job.data["config"]
    if cfg.get("strategy") == "APOLLO_PEOPLE":
        return do_apollo_people_discover(job, p, providers)
    queue = [t["url"] for t in p["targets"]]
    visited, model_calls = set(), 0
    while queue and len(visited) < p["limits"]["public.fetch"]:
        url = queue.pop(0)
        if url in visited:
            continue
        visited.add(url)
        try:
            if providers.fixtures is not None:
                doc = copy.deepcopy(providers.fixtures["documents"][url])
                doc.update(url=url, id="D-" + digest([url, doc["text"]])[:16], fetched_at=stamp())
                doc = job.request(p, "public.fetch", {"url": url}, lambda: doc)
            else:
                doc = job.request(p, "public.fetch", {"url": url}, lambda: fetch_document(url, cfg["allowed_hosts"]))
        except Stop as exc:
            job.data["issues"].append({"source_url": url, "reason": str(exc)})
            job.save()
            continue
        if not any(d["id"] == doc["id"] for d in job.data["documents"]):
            job.data["documents"].append(doc)
            job.save()
        for link in doc.get("links", []):
            u = urllib.parse.urlsplit(link)
            if u.scheme == "https" and u.hostname in cfg["allowed_hosts"] and not u.username and not u.password:
                link = urllib.parse.urlunsplit((u.scheme, u.netloc, u.path, u.query, ""))
                relevant = any(t.lower() in urllib.parse.unquote(link).lower() for t in
                    ["research", "technical", "paper", ".pdf", "cable", "author", "team", "sitemap"] + cfg["topics"])
                if relevant and link not in visited and link not in queue:
                    queue.append(link)
        # Each document has a fixed input cap; all omitted content remains reviewable locally.
        text = doc["text"][:cfg["budgets"]["input_chars"]]
        if not any(t.casefold() in text.casefold() for t in cfg["topics"] + cfg["aliases"]):
            continue
        if model_calls >= p["limits"]["deepseek.extract"]:
            job.data["issues"].append({"source_url": url, "reason": "模型调用预算已用完，正文尚未分析。"})
            continue
        params = {"model": cfg["model"], "max_tokens": cfg["budgets"]["output_tokens"],
                  "prompt_version": PUBLIC_PROMPT_VERSION, "requirement": {k: cfg[k] for k in
                  ("company", "topics", "include_historical", "countries")},
                  "document": {"url": doc["url"], "text": text}}
        reply = job.request(p, "deepseek.extract", params, lambda: providers.call("deepseek.extract", params))
        model_calls += 1
        people = reply.get("people")
        if not isinstance(people, list):
            raise Stop("DeepSeek 未返回 people 数组。")
        for person in people[:10]:
            try:
                add_candidate(job, extract_checked(person, dict(doc, text=text), cfg))
            except Stop as exc:
                job.data["issues"].append({"source_url": url, "reason": str(exc)})
        job.save()


def apollo_candidate(person, origin, full=True):
    last_name = clean(person.get("last_name")) or clean(person.get("last_name_obfuscated"))
    name = clean(person.get("name")) or clean(clean(person.get("first_name")) + " " + last_name)
    c = base_candidate(clean(name) or "姓名待补全", origin + ":" + clean(person.get("id")))
    c.update(apollo_id=clean(person.get("id")), title=clean(person.get("title")),
             linkedin_url=clean(person.get("linkedin_url")), country=clean(person.get("country")))
    search_fields = {"id", "first_name", "last_name", "last_name_obfuscated", "name", "title", "headline",
                     "linkedin_url", "country", "city", "state", "organization",
                     "employment_history"}
    stored_profile = person if full else {k: copy.deepcopy(v) for k, v in person.items()
                                          if k in search_fields}
    c["provider_profiles"].append({"source": "APOLLO", "retrieved_at": stamp(),
                                   "profile": stored_profile})
    org = person.get("organization") or {}
    if org.get("name"):
        c["evidence"].append({"kind": "employment", "company": org["name"],
            "employment_kind": "APOLLO_REPORTED_CURRENT",
            "source_url": "https://docs.apollo.io/reference/people-enrichment" if full else
                          "https://docs.apollo.io/reference/people-api-search", "source_date": "",
            "quote": "Apollo 提供商报告：" + clean(org["name"]) + " / " + c["title"]})
    for entry in person.get("employment_history") or []:
        if entry.get("organization_name"):
            c["evidence"].append({"kind": "employment", "company": entry["organization_name"],
                "employment_kind": "APOLLO_REPORTED_CURRENT" if entry.get("current") else "HISTORICAL",
                "source_url": "https://docs.apollo.io/reference/people-enrichment",
                "source_date": clean(entry.get("start_date")), "quote": "Apollo 经历：" + json.dumps(entry, ensure_ascii=False)})
    address = clean(person.get("email")).lower()
    if full and EMAIL.fullmatch(address) and not email_flags(address):
        c["emails"].append({"address": address, "source": "APOLLO", "source_url": "",
            "source_date": "", "quote": "", "ownership": "APOLLO_ASSOCIATED",
            "provider_status": clean(person.get("email_status")), "flags": []})
    if not full:
        c["issues"].append("APOLLO_SEARCH_NAME_MAY_BE_INCOMPLETE")
    return c


def proposal_enrich(job, ids, reason):
    rows = selected(job, ids)
    limit = job.data["config"]["budgets"]["apollo_enrich"]
    if len(rows) > limit:
        raise Stop(f"补全人数超过配置上限 {limit}；本次不扩大额度。")
    targets = []
    for c in rows:
        a = assessment(c, job.data["config"])
        if "EMAIL_IDENTITY_CONFLICT" in c["issues"]:
            raise Stop("存在邮箱身份冲突，先复核。")
        if c.get("apollo_id"):
            params = {"id": c["apollo_id"]}
        elif c.get("linkedin_url"):
            params = {"linkedin_url": c["linkedin_url"]}
        else:
            if len(c["name"].split()) < 2:
                raise Stop("姓名不完整，不能可靠补全；先确认身份。")
            params = {"name": c["name"], "domain": job.data["config"]["domain"]}
        params.update(reveal_personal_emails=False, reveal_phone_number=False,
                      run_waterfall_email=False, run_waterfall_phone=False)
        targets.append({"candidate_id": c["id"], "name": c["name"], "params": params,
                        "evidence": a, "existing_emails": c["emails"], "reason": reason})
    return job.propose("enrich", targets, {"apollo.enrich": len(targets)}, reason)


def proposal_search(job, reason):
    cfg = job.data["config"]
    params = {"q_organization_domains_list": [cfg["domain"]], "person_titles": cfg["titles"],
              "include_similar_titles": True, "page": 1, "per_page": 25}
    if cfg["countries"]:
        params["person_locations"] = cfg["countries"]
    pages = cfg["budgets"]["apollo_search_pages"]
    if not 1 <= pages <= 3:
        raise Stop("试验的 Apollo 搜索页数必须为1~3；当前不执行。")
    return job.propose("apollo-search", [{"params": dict(params, page=i), "reason": reason}
        for i in range(1, pages + 1)], {"apollo.search": pages},
        reason + "；仅搜索，不自动补全或验证。域名匹配包含历史雇主线索，需核对身份。")


def proposal_verify(job, ids, addresses, reason):
    rows = selected(job, ids)
    if len(rows) != len(addresses):
        raise Stop("--ids 与 --emails 必须逐项对应，不能隐式挑选邮箱。")
    if len(set(a.lower() for a in addresses)) != len(addresses):
        raise Stop("同一邮箱只能选择一次。")
    if len(rows) > job.data["config"]["budgets"]["email_verify"]:
        raise Stop("超过本次配置的邮箱验证上限。")
    targets = []
    for c, address in zip(rows, addresses):
        address = address.lower()
        options = [o for o in c["emails"] if o["address"] == address]
        if not options or email_flags(address):
            raise Stop("邮箱不在该人员记录中，或格式/角色/个人邮箱需复核；不执行。")
        if "EMAIL_IDENTITY_CONFLICT" in c["issues"]:
            raise Stop("邮箱归属冲突，先确认人员身份。")
        targets.append({"candidate_id": c["id"], "name": c["name"], "email": address,
                        "email_sources": options, "evidence": assessment(c, job.data["config"]),
                        "reason": reason, "limitation": "仅检查投递性，不能证明本人在职或邮箱归属。"})
    return job.propose("verify", targets, {"emailable.account": 1, "emailable.verify": len(targets)}, reason)


def do_enrich(job, p, providers):
    for t in p["targets"]:
        reply = job.request(p, "apollo.enrich", t["params"], lambda: providers.call("apollo.enrich", t["params"]))
        person = reply.get("person")
        c = selected(job, [t["candidate_id"]])[0]
        if not isinstance(person, dict) or not person:
            c["issues"].append("APOLLO_NO_MATCH")
            job.save()
            continue
        incoming = apollo_candidate(person, "apollo-enrichment")
        exact = bool(c.get("apollo_id") and c["apollo_id"] == incoming["apollo_id"])
        exact |= bool(c.get("linkedin_url") and c["linkedin_url"].rstrip("/") == incoming["linkedin_url"].rstrip("/"))
        if not exact:
            # Name+company matches need a separate, visible human merge decision.
            incoming["issues"].append("MATCH_TO_REVIEW:" + c["id"])
            if c.get("apollo_id"):
                incoming["issues"].append("APOLLO_ID_CONFLICT")
            add_candidate(job, incoming)
        else:
            merge_into(c, incoming)
            if person.get("name"):
                c["name"] = clean(person["name"])
            c["issues"] = [v for v in c["issues"] if v != "APOLLO_SEARCH_NAME_MAY_BE_INCOMPLETE"]
        mark_conflicts(job.data["candidates"])
        job.save()


def do_search(job, p, providers):
    for t in p["targets"]:
        reply = job.request(p, "apollo.search", t["params"],
                            lambda: safe_search_response(providers.call("apollo.search", t["params"])))
        people = reply.get("people")
        if not isinstance(people, list):
            raise Stop("Apollo 搜索响应缺少 people。")
        for person in people:
            if isinstance(person, dict) and person.get("id"):
                add_candidate(job, apollo_candidate(person, "apollo-search", full=False))
        job.save()
        if not people:
            break


def do_verify(job, p, providers):
    # Read-only account check is uncached between proposals, reserved separately.
    pending = [t for t in p["targets"] if digest([job.data["mode"], "emailable.verify",
               verify_params(t["email"])]) not in job.data["requests"]]
    if pending:
        account_params = {"proposal": p["id"]}  # cache identity only, never sent to provider
        account = job.request(p, "emailable.account", account_params,
                              lambda: providers.call("emailable.account", {}))
        credits = account.get("available_credits")
        if type(credits) not in (int, float) or credits < len(pending):
            raise Stop("Emailable 余额未知或不足，本次未发起验证。")
    for t in p["targets"]:
        params = verify_params(t["email"])
        result = job.request(p, "emailable.verify", params, lambda: providers.call("emailable.verify", params))
        if clean(result.get("email")).lower() != t["email"] or result.get("state") not in {"deliverable", "undeliverable", "risky", "unknown"}:
            raise Stop("验证响应邮箱或状态不匹配；保留响应待确认，不继续其他邮箱。")
        c = selected(job, [t["candidate_id"]])[0]
        flags = [k for k in ("accept_all", "disposable", "free", "role", "mailbox_full", "no_reply") if result.get(k)]
        outcome = "DELIVERABLE_REVIEW" if result["state"] == "deliverable" and not flags else "UNDELIVERABLE" if result["state"] == "undeliverable" else "REVIEW_REQUIRED"
        for option in c["emails"]:
            if option["address"] == t["email"]:
                option["verification"] = {"provider": "EMAILABLE", "mode": job.data["mode"],
                    "checked_at": job.data["requests"][digest([job.data["mode"], "emailable.verify", params])]["completed_at"],
                    "state": result["state"], "outcome": outcome, "flags": flags,
                    "reason": clean(result.get("reason")), "response": result}
        job.save()


def verify_params(address):
    return {"email": address, "smtp": "true", "accept_all": "true", "timeout": 10}


def execute(job, identity, action, providers):
    # A repeat of a completed operation is an offline no-op, even without keys.
    proposal = job.proposal(identity)
    if proposal["state"] == "DONE" and proposal["action"] == action:
        return "已完成，复用结果；新增调用0。"
    preflight_action = "apollo-discover" if action == "discover" and job.data["config"].get("strategy") == "APOLLO_PEOPLE" else action
    providers.preflight(preflight_action)
    p = job.begin(identity, action)
    if p is None:
        return "已完成，新增调用0。"
    try:
        {"discover": do_discover, "enrich": do_enrich, "apollo-search": do_search,
         "verify": do_verify}[action](job, p, providers)
    except (Exception, KeyboardInterrupt) as exc:
        job.finish(p, type(exc).__name__ + ": " + (str(exc) if isinstance(exc, Stop) else "执行中断，查看请求状态；不自动重试。"))
        raise
    job.finish(p)
    return "该阶段完成；请 review。后续阶段不会自动执行。"


def import_existing(job, path):
    """Import local candidate metadata or successful Apollo cache, with no new calls."""
    path = Path(path)
    raw = path.read_text(encoding="utf-8-sig")
    fingerprint = digest(raw)
    if fingerprint in job.data["imports"]:
        return 0
    if job.data["mode"] == "DEMO":
        raise Stop("DEMO 不导入真实人员；请使用独立 LIVE 任务。")
    if path.suffix == ".ndjson":
        records = [json.loads(line) for line in raw.splitlines() if line.strip()]
        count = 0
        for entry in records:
            if "index" in entry:
                continue
            if not entry.get("givenNames") and not entry.get("familyNames"):
                continue
            url = (entry.get("externalIds") or {}).get("sourceUrl", "")
            c = base_candidate(clean(entry.get("givenNames")) + " " + clean(entry.get("familyNames")), url or fingerprint)
            c.update(country=clean(entry.get("country")), title=clean(entry.get("employment")))
            c["issues"].append("IMPORTED_METADATA_NOT_RECHECKED")
            # Metadata and old MX checks are NOT promoted to raw-source evidence or live verification.
            c["provider_profiles"].append({"source": "LOCAL_IMPORT", "profile": entry})
            address = clean(entry.get("email")).lower()
            if EMAIL.fullmatch(address):
                c["emails"].append({"address": address, "source": "IMPORTED_PUBLIC_METADATA",
                    "source_url": url, "source_date": "", "quote": "", "ownership": "NEEDS_REVIEW",
                    "provider_status": "OLD_MX_CHECK_ONLY", "flags": email_flags(address)})
            add_candidate(job, c)
            count += 1
    else:
        old = json.loads(raw)
        if old.get("config", {}).get("mode") != "LIVE":
            raise Stop("只导入明确标为 LIVE 的旧 Apollo checkpoint，避免模拟数据污染。")
        count = 0
        for request in old.get("requests", {}).values():
            if request.get("state") == "DONE" and request.get("kind") == "enrich":
                person = request.get("response", {}).get("person")
                if isinstance(person, dict) and person:
                    c = apollo_candidate(person, "cached-apollo")
                    c["issues"].append("IMPORTED_CACHED_PROFILE")
                    for profile in c["provider_profiles"]:
                        profile["retrieved_at"] = request.get("completed_at", "")
                    add_candidate(job, c)
                    params = request.get("params")
                    if isinstance(params, dict) and params:
                        key = digest(["LIVE", "apollo.enrich", params])
                        job.data["requests"].setdefault(key, {
                            "kind": "apollo.enrich", "params": copy.deepcopy(params),
                            "proposal_id": "import:" + fingerprint, "state": "DONE",
                            "response": copy.deepcopy(request["response"]),
                            "completed_at": request.get("completed_at", ""),
                            "imported": True})
                    count += 1
    job.data["imports"].append(fingerprint)
    job.save()
    return count


def render(job):
    job.require()
    cfg = job.data["config"]
    lines = [f"模式：{job.data['mode']}（DEMO 为模拟数据，不是真实验证）",
             f"需求：{cfg['demand_company']}；对标：{cfg['company']}；方向：{', '.join(cfg['topics'])}",
             f"任务快照：{job.snapshot()[:16]}", ""]
    rows = []
    for c in job.data["candidates"]:
        a = assessment(c, cfg)
        ranking = c.get("ranking", {})
        lines.extend([f"{c['id']} | {c['name']} | {c['title']} | 所在地：{c['country'] or '未知'}",
                      f"  判断：{a['status']}；{a['reason']}", "  待确认：" + ", ".join(a["issues"])])
        if ranking:
            lines.append(f"  匹配分：{ranking.get('score')}；理由：{ranking.get('reason') or '未提供'}；"
                         f"命中：{', '.join(ranking.get('matched_requirements', [])) or '无'}；"
                         f"限制：{', '.join(ranking.get('limitations', [])) or '无'}")
        if not c["emails"]:
            lines.append("  无邮箱；技术/身份证据充分后可 propose enrich，目前不调用 Apollo。")
        for option in c["emails"]:
            v = option.get("verification", {})
            suggestion = "已有本任务验证，复用结果" if v else "已有Apollo verified，名单测试可暂缓独立验证" if option.get("provider_status") == "verified" else "如确认此人值得联系，可申请1次验证；验证不确认身份"
            lines.append(f"  邮箱：{option['address']} | 来源：{option['source']} | 归属：{option['ownership']} | "
                         f"验证：{v.get('state', 'NOT_CHECKED')} | 建议：{suggestion}")
            rows.append(dict(id=c["id"], name=c["name"], title=c["title"], country=c["country"],
                assessment=a["status"], match_reason=a["reason"], issues=a["issues"], email=option["address"],
                email_source=option["source"], ownership=option["ownership"], verification=v, mode=job.data["mode"],
                fit_score=ranking.get("score", ""), fit_reason=ranking.get("reason", ""),
                fit_limitations=ranking.get("limitations", [])))
        if not c["emails"]:
            rows.append(dict(id=c["id"], name=c["name"], title=c["title"], country=c["country"],
                assessment=a["status"], match_reason=a["reason"], issues=a["issues"], mode=job.data["mode"],
                fit_score=ranking.get("score", ""), fit_reason=ranking.get("reason", ""),
                fit_limitations=ranking.get("limitations", [])))
        for e in c["evidence"]:
            lines.append(f"  证据[{e['kind']}] {e.get('source_date') or '日期未知'} {e.get('source_url', '')}\n    {e['quote']}")
        for profile in c["provider_profiles"]:
            if profile["source"] == "LOCAL_IMPORT":
                entry = profile["profile"]
                lines.append("  导入线索（未重核）：" + clean(entry.get("researchFields")) + " | " + clean((entry.get("externalIds") or {}).get("sourceUrl")))
        lines.append("")
    lines.append("提案（PROPOSED 尚未批准；APPROVED 才可执行）：")
    for p in job.data["proposals"].values():
        lines.append(json.dumps({k: p[k] for k in ("id", "action", "state", "limits", "reason", "targets")}, ensure_ascii=False, indent=2))
    counts = Counter(r["kind"] for r in job.data["requests"].values() if not r.get("imported"))
    lines.append("累计请求预留次数（含失败/超时）：" + json.dumps(dict(counts), ensure_ascii=False))
    lines.append("读取/提取问题：" + json.dumps(job.data["issues"], ensure_ascii=False))
    lines.append("无邮件发送、无线上数据库写入。")
    return "\n".join(lines), rows


def export(job):
    text, rows = render(job)
    root = job.root
    (root / "review.txt").write_text(text, encoding="utf-8")
    csv_write(root / "candidates.csv", rows, ["mode", "id", "name", "title", "country", "assessment",
              "fit_score", "fit_reason", "fit_limitations", "match_reason", "issues", "email",
              "email_source", "ownership", "verification"])
    with (root / "evidence.jsonl").open("w", encoding="utf-8") as handle:
        for c in job.data["candidates"]:
            handle.write(json.dumps({"id": c["id"], "name": c["name"], "evidence": c["evidence"]}, ensure_ascii=False) + "\n")
    counts = Counter(r["kind"] for r in job.data["requests"].values() if not r.get("imported"))
    tokens = [r["response"].get("usage") for r in job.data["requests"].values()
              if r["kind"].startswith("deepseek.") and r["state"] == "DONE"]
    atomic_json(root / "summary.json", {"mode": job.data["mode"], "people": len(job.data["candidates"]),
        "requests_reserved": dict(counts), "model_usage": tokens,
        "imported_cached_requests": sum(bool(r.get("imported")) for r in job.data["requests"].values()),
        "request_states": dict(Counter(r["state"] for r in job.data["requests"].values())),
        "snapshot": job.snapshot(), "exported_at": stamp()})
    return text
