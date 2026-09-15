#!/usr/bin/env python3
"""Company name -> Apollo R&D leads -> NeverBounce checks. Python 3.10+, stdlib.

Produces review files only: no mail delivery or writes to the talent system.
See probe_corporate_experts.md for credentials, budgets and qualification limits.
"""
from __future__ import annotations

import argparse
from collections import Counter
from contextlib import contextmanager
import csv
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import re
import sys
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request

ENDPOINTS = {
    "organization": "https://api.apollo.io/api/v1/mixed_companies/search",
    "search": "https://api.apollo.io/api/v1/mixed_people/api_search",
    "enrich": "https://api.apollo.io/api/v1/people/match",
    "verify": "https://api.neverbounce.com/v4.2/single/check",
}
DEFAULT_TITLES = ["R&D", "Research", "Scientist", "Product Development", "Process Development"]
RND = re.compile(r"\br\s*&\s*d\b|\bresearch\b|\bscientist\b|\b(?:product|process|materials?) development\b|\bdevelopment engineer\b", re.I)
NON_RND = re.compile(r"\bsales\b|\bmarketing\b|\bbusiness development\b|\brecruit\w*\b|\bhuman resources\b|\bfinancial\b", re.I)
SENIOR = re.compile(r"\bdirector\b|\bhead\b|\bchief\b|\bprincipal\b|\bsenior\b|\bvice president\b|\bvp\b|\bmanager\b", re.I)
PERSON_FIELDS = ["run_mode", "person_id", "name", "company", "company_domain", "title", "country",
                 "affiliation_status", "rnd_relevance", "technical_match", "qualification_status",
                 "evidence", "linkedin_url", "source_url", "retrieved_at", "status", "reason",
                 "email", "email_source", "email_domain_match", "email_ownership", "verification_result"]
CHECK_FIELDS = ["run_mode", "person_id", "name", "email", "email_source", "pattern",
                "verification_result", "flags", "checked_at", "reason"]
EMAIL_RE = re.compile(r"^[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}$")
ALLOWED_PATTERNS = ["{first}.{last}", "{f}{last}", "{first}{last}", "{first}_{last}", "{first}"]
FREE_EMAIL_DOMAINS = {"gmail.com", "googlemail.com", "hotmail.com", "outlook.com", "live.com", "msn.com",
                      "yahoo.com", "yahoo.co.uk", "aol.com", "icloud.com", "me.com", "qq.com", "163.com",
                      "126.com", "proton.me", "protonmail.com", "mail.ru", "yandex.com"}


def clean(value):
    return " ".join(str(value or "").split())


def now():
    return datetime.now(timezone.utc).isoformat()


def domain(value):
    value = clean(value).lower()
    host = urllib.parse.urlsplit(value if "://" in value else "//" + value).hostname or ""
    return host.removeprefix("www.").rstrip(".")


def company_name(value):
    words = re.findall(r"[^\W_]+", clean(value).casefold())
    suffixes = {"group", "inc", "incorporated", "ltd", "limited", "corp", "corporation", "gmbh", "plc", "spa"}
    while words and words[-1] in suffixes:
        words.pop()
    return " ".join(words)


def digest(value):
    return hashlib.sha256(json.dumps(value, sort_keys=True, ensure_ascii=False).encode()).hexdigest()


def redact(value, secrets):
    if isinstance(value, dict):
        return {k: "[REDACTED]" if re.sub(r"[^a-z]", "", k.lower()) in {"key", "apikey", "token", "accesstoken", "authorization"}
                else redact(v, secrets) for k, v in value.items()}
    if isinstance(value, list):
        return [redact(item, secrets) for item in value]
    if isinstance(value, str):
        for secret in secrets:
            if secret:
                value = value.replace(secret, "[REDACTED]")
        return value
    return value


def write_json(path, value):
    path = Path(path)
    fd, temporary = tempfile.mkstemp(prefix=".pending-", dir=path.parent)
    try:
        with os.fdopen(fd, "w", encoding="utf-8") as stream:
            json.dump(value, stream, ensure_ascii=False, indent=2)
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(temporary, path)
    finally:
        if os.path.exists(temporary):
            os.unlink(temporary)


def write_csv(path, rows, fields):
    fd, temporary = tempfile.mkstemp(prefix=".csv-", dir=path.parent)
    try:
        with os.fdopen(fd, "w", encoding="utf-8-sig", newline="") as stream:
            writer = csv.DictWriter(stream, fieldnames=fields, extrasaction="ignore")
            writer.writeheader()
            for row in rows:
                # Prevent spreadsheet formulas in third-party names/titles.
                writer.writerow({k: "'" + str(v) if str(v).lstrip().startswith(("=", "+", "-", "@")) else v
                                 for k, v in row.items()})
        os.replace(temporary, path)
    finally:
        if os.path.exists(temporary):
            os.unlink(temporary)


@contextmanager
def output_lock(output):
    """OS lock is released even on interruption; checkpoints remain resumable."""
    import fcntl  # This initial CLI targets macOS/Linux, like the project scripts.
    output.mkdir(parents=True, exist_ok=True, mode=0o700)
    fd = os.open(output / ".run.lock", os.O_CREAT | os.O_RDWR, 0o600)
    with os.fdopen(fd, "w") as stream:
        try:
            fcntl.flock(stream, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            raise ValueError("该 output 目录已有任务运行，请等待或使用其他目录。") from None
        yield


def load_keys(path):
    values = {}
    if path:
        for raw in Path(path).read_text(encoding="utf-8-sig").splitlines():
            line = raw.strip()
            if not line or line.startswith(("#", "!")):
                continue
            key, sep, value = line.removeprefix("export ").partition("=")
            key = key.strip()
            if sep and key in {"APOLLO_API_KEY", "NEVERBOUNCE_API_KEY"}:
                value = value.strip()
                if len(value) >= 2 and value[0] == value[-1] and value[0] in "\"'":
                    value = value[1:-1]
                values[key] = value
    for key in ["APOLLO_API_KEY", "NEVERBOUNCE_API_KEY"]:
        if os.environ.get(key):
            values[key] = os.environ[key]
        if not values.get(key):
            raise ValueError(f"缺少 {key}；请设置环境变量或使用 --env-file 本地配置路径。")
    return values


class ApiFailure(Exception):
    def __init__(self, code, message, fatal=False, response=None):
        self.code, self.message, self.fatal, self.response = code, message, fatal, response
        super().__init__(message)


class BudgetLimit(Exception):
    pass


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def request_spec(kind, params, keys):
    """Keep query names and auth aligned with the providers' documented APIs."""
    query = []
    for key, value in params.items():
        if isinstance(value, list):
            query.extend((key if key.endswith("[]") else key + "[]", item) for item in value)
        else:
            query.append((key, str(value).lower() if isinstance(value, bool) else str(value)))
    headers = {"Accept": "application/json", "User-Agent": "CorporateExpertProbe/1.0"}
    if kind == "verify":
        query.append(("key", keys["NEVERBOUNCE_API_KEY"]))
        method = "GET"
    else:
        headers["x-api-key"] = keys["APOLLO_API_KEY"]
        method = "POST"
    return urllib.request.Request(ENDPOINTS[kind] + "?" + urllib.parse.urlencode(query),
                                  data=b"" if method == "POST" else None, headers=headers, method=method)


class LiveTransport:
    def __init__(self, keys, delay):
        self.keys, self.delay = keys, delay
        self.opener = urllib.request.build_opener(NoRedirect())

    def call(self, kind, params):
        time.sleep(self.delay)
        request = request_spec(kind, params, self.keys)
        try:
            with self.opener.open(request, timeout=40) as response:
                result = json.loads(response.read(8_000_000))
        except urllib.error.HTTPError as exc:
            # Never print the URL/body: NeverBounce authenticates in the query.
            status = exc.code
            hints = {401: "密钥无效", 403: "账号或接口权限不足；检查 API Key 权限及工作邮箱注册要求",
                     402: "额度不足", 429: "触发限流，停止调用以免重复扣费"}
            message = f"{kind}: HTTP {status}，{hints.get(status, '请求失败，结果可能不确定')}"
            try:
                details = json.loads(exc.read(4096))
                if isinstance(details, dict):
                    detail = details.get("error") or details.get("message") or details.get("error_code")
                    if isinstance(detail, str):
                        message += "; " + clean(redact(detail, self.keys.values()))[:500]
            except (ValueError, OSError):
                pass
            raise ApiFailure("HTTP_" + str(status), message, fatal=True) from None
        except (urllib.error.URLError, TimeoutError, OSError, ValueError):
            raise ApiFailure("REQUEST_UNCERTAIN", f"{kind}: 网络超时或响应不可解析；已记录请求，不自动重试。") from None
        if not isinstance(result, dict):
            raise ApiFailure("REQUEST_UNCERTAIN", f"{kind}: 返回结构异常，不自动重试。")
        if kind == "verify" and result.get("status") != "success":
            # General failure is not a diagnosis: preserve the provider's message,
            # after redacting keys, for both console output and checkpoint replay.
            safe_response = redact(result, self.keys.values())
            code = clean(safe_response.get("status"))
            code = code if re.fullmatch(r"[a-z_]{1,50}", code) else "api_error"
            detail = safe_response.get("message")
            detail = clean(detail)[:1000] if isinstance(detail, str) else "提供商未返回可读的 message，原因尚未确认。"
            raise ApiFailure("NB_" + code, "NeverBounce 返回 " + code + "：" + detail,
                             fatal=True, response=safe_response)
        if kind != "verify" and (result.get("error") or result.get("error_code")):
            raise ApiFailure("APOLLO_ERROR", "Apollo 返回业务错误；检查接口权限、额度和参数。", fatal=True)
        return redact(result, self.keys.values())


class FixtureTransport:
    """Explicit offline replay mode for testing without sending data or spending credits."""
    def __init__(self, path):
        self.fixture = json.loads(Path(path).read_text(encoding="utf-8"))

    def call(self, kind, params):
        if kind == "organization":
            result = {"organizations": self.fixture["organizations"]}
        elif kind == "search":
            pages = self.fixture["search_pages"]
            result = {"people": pages[params["page"] - 1], "pagination": {"total_pages": len(pages)}}
        elif kind == "enrich":
            result = {"person": self.fixture["people"].get(params["id"])}
        else:
            if params["email"] not in self.fixture["verification"]:
                raise ValueError("离线样本缺少验证响应：" + params["email"])
            result = self.fixture["verification"][params["email"]]
        if result.get("_error"):
            raise ApiFailure("REQUEST_UNCERTAIN", "离线模拟网络超时。")
        return result


class Journal:
    def __init__(self, output, config, limits, transport):
        self.path = output / "checkpoint.json"
        self.limits, self.transport = limits, transport
        self.state = {"version": 1, "config": config, "requests": {}, "created_at": now()}
        if self.path.exists():
            self.state = json.loads(self.path.read_text(encoding="utf-8"))
            if self.state.get("version") != 1 or self.state.get("config") != config:
                raise ValueError("搜索条件或运行模式已改变，请使用新的 --output-dir；原 output 保留。")
        self.before = self.counts()

    def counts(self):
        counts = Counter(item["kind"] for item in self.state["requests"].values())
        return {kind: counts[kind] for kind in ENDPOINTS}

    def call(self, kind, params):
        key = digest([kind, params])
        previous = self.state["requests"].get(key)
        if previous:
            if previous["state"] == "DONE":
                return previous["response"], previous["completed_at"]
            raise ApiFailure(previous.get("error_code", "REQUEST_UNCERTAIN"),
                             previous.get("error", "上次请求中断，结果不确定；不自动重复调用。"),
                             previous.get("fatal", False))
        if self.counts()[kind] >= self.limits[kind]:
            raise BudgetLimit(kind)
        item = {"kind": kind, "params": params, "state": "PENDING", "started_at": now()}
        self.state["requests"][key] = item
        write_json(self.path, self.state)  # Reserve BEFORE any potentially charged request.
        try:
            response = self.transport.call(kind, params)
        except ApiFailure as exc:
            item.update(state="ERROR", error_code=exc.code, error=exc.message, fatal=exc.fatal)
            if exc.response is not None:
                item["response"] = exc.response
            write_json(self.path, self.state)
            raise
        item.update(state="DONE", response=response, completed_at=now())
        write_json(self.path, self.state)
        return response, item["completed_at"]


def select_company(organizations, requested, explicit_domain):
    organizations = {clean(o.get("id")): o for o in organizations if isinstance(o, dict) and o.get("id")}
    if explicit_domain:
        matches = [o for o in organizations.values() if domain(o.get("primary_domain") or o.get("website_url")) == explicit_domain]
    else:
        matches = [o for o in organizations.values() if company_name(o.get("name")) == company_name(requested)]
    if len(matches) != 1:
        raise ValueError("公司名称未唯一匹配；查看 organizations.csv，用 --domain 指定目标官网域名并使用新 output。")
    chosen = dict(matches[0])
    chosen["primary_domain"] = domain(chosen.get("primary_domain") or chosen.get("website_url"))
    if not chosen["primary_domain"]:
        raise ValueError("匹配公司缺少官网域名；请补充 --domain 并使用新 output。")
    return chosen


def person_id(person):
    return clean(person.get("id") or person.get("person_id"))


def priority(person, keywords):
    title = clean(person.get("title"))
    text = (title + " " + clean(person.get("headline"))).lower()
    return (100 if RND.search(title) else 0) + (20 if SENIOR.search(title) else 0) + sum(10 for k in keywords if k.lower() in text)


def affiliation(person, company):
    org = person.get("organization") or {}
    top = clean(person.get("organization_id"))
    nested = clean(org.get("id"))
    if top and nested and top != nested:
        return "AFFILIATION_CONFLICT"
    current_id = top or nested
    history = person.get("employment_history") or []
    same = [h for h in history if clean(h.get("organization_id")) == company["id"]]
    if current_id == company["id"]:
        if same and all(h.get("current") is False or bool(h.get("end_date")) for h in same):
            return "AFFILIATION_CONFLICT"
        return "APOLLO_CURRENT_MATCH"
    if current_id:
        return "OTHER_COMPANY"
    return "AFFILIATION_UNKNOWN"


def guess_addresses(person, mail_domain, patterns):
    first, last = clean(person.get("first_name")), clean(person.get("last_name"))
    # No guessing from obfuscated names, initials, compounds or transliterations.
    if not re.fullmatch(r"[A-Za-z]{2,}", first) or not re.fullmatch(r"[A-Za-z]{2,}", last):
        return []
    names = {"first": first.lower(), "last": last.lower(), "f": first[0].lower()}
    return [(p.format(**names) + "@" + mail_domain, "GUESSED", p) for p in patterns]


def email_options(person, company, patterns, max_addresses):
    choices = []
    email = clean(person.get("email")).lower()
    # A provider-supplied work email may use an old/alternate corporate domain.
    # Retain it with an explicit review flag; do not silently replace it with a guess.
    if EMAIL_RE.fullmatch(email) and domain(email.split("@", 1)[1]) not in FREE_EMAIL_DOMAINS:
        choices.append((email, "APOLLO", ""))
    choices += guess_addresses(person, company["primary_domain"], patterns)
    unique = {}
    for choice in choices:
        unique.setdefault(choice[0], choice)
    return list(unique.values())[:max_addresses]


def base_row(person, company, mode):
    row = {field: "" for field in PERSON_FIELDS}
    row.update(run_mode=mode, person_id=person_id(person),
               name=clean(person.get("name")) or clean(person.get("first_name")) + " " + clean(person.get("last_name")),
               company=company["name"], company_domain=company["primary_domain"],
               title=clean(person.get("title")), country=clean(person.get("country")),
               qualification_status="NEEDS_RND_EVIDENCE", email_ownership="UNCONFIRMED",
               linkedin_url=clean(person.get("linkedin_url")),
               source_url=ENDPOINTS["search"], status="NOT_SELECTED")
    return row


def inspect_person(person, company, row, args):
    row.update(name=clean(person.get("name")) or clean(person.get("first_name")) + " " + clean(person.get("last_name")),
               title=clean(person.get("title")), country=clean(person.get("country")),
               linkedin_url=clean(person.get("linkedin_url")))
    row["affiliation_status"] = affiliation(person, company)
    if row["affiliation_status"] != "APOLLO_CURRENT_MATCH":
        row.update(status=row["affiliation_status"], reason="现职未能匹配目标 Apollo 公司 ID。")
        return False
    if not row["country"]:
        row.update(status="COUNTRY_UNKNOWN", reason="所在地未知，不能认定为海外人员。")
        return False
    if row["country"].casefold() in {"china", "cn", "中国", "people's republic of china"}:
        row.update(status="EXCLUDED_LOCATION", reason="当前所在地为中国；所在地不等于国籍。")
        return False
    if args.locations and row["country"].casefold() not in {s.casefold() for s in args.locations}:
        row.update(status="LOCATION_MISMATCH", reason="所在地未匹配指定国家（使用英文国家全名）。")
        return False
    if not RND.search(row["title"]) or NON_RND.search(row["title"]):
        row.update(status="ROLE_MISMATCH", reason="缺少明确研发岗位，或属于销售、市场、人事岗位。")
        return False
    row["rnd_relevance"] = "HIGH" if SENIOR.search(row["title"]) else "MEDIUM"
    text = (row["title"] + " " + clean(person.get("headline"))).lower()
    hits = [k for k in args.keywords if k.lower() in text]
    row["technical_match"] = "TITLE_KEYWORD_MATCH" if hits else "NEEDS_TOPIC_CONFIRMATION"
    row["evidence"] = f"Apollo 当前公司={company['name']}; 职位={row['title']}; 关键词命中={','.join(hits) or '无'}; 个人专利/产品证据未核实"
    return True


def verify_person(person, company, row, checks, journal, args, mode):
    options = email_options(person, company, args.patterns, args.max_addresses)
    if not options:
        row.update(status="NAME_INCOMPLETE", reason="无公司域名邮箱，且完整姓名不足以可靠生成候选地址。")
        return
    for address, source, pattern in options:
        try:
            reply, timestamp = journal.call("verify", {"email": address, "credits_info": 1, "timeout": 20})
            result = clean(reply.get("result"))
            flags = reply.get("flags") or []
            if not isinstance(flags, list):
                flags = []
            if result not in {"valid", "invalid", "disposable", "catchall", "unknown"}:
                result = "REQUEST_UNCERTAIN"
            if "accepts_all" in flags:
                result = "catchall"
            if "disposable_email" in flags:
                result = "disposable"
            reason = ""
        except BudgetLimit:
            row.update(status="VERIFICATION_LIMIT", reason="达到累计验证请求上限；提高上限后可续跑。")
            return
        except ApiFailure as exc:
            if exc.fatal:
                row.update(status="API_BLOCKED", reason=exc.message)
                raise
            result, flags, timestamp, reason = exc.code, [], now(), exc.message
        check = dict(run_mode=mode, person_id=row["person_id"], name=row["name"], email=address,
                     email_source=source, pattern=pattern, verification_result=result, flags="|".join(flags),
                     checked_at=timestamp, reason=reason)
        checks.append(check)
        domain_match = "MATCH" if domain(address.split("@", 1)[1]) == company["primary_domain"] else "DIFFERENT_DOMAIN_REVIEW"
        row.update(email=address, email_source=source, verification_result=result, email_domain_match=domain_match)
        if result == "valid":
            row.update(status="READY_FOR_REVIEW", email_ownership="APOLLO_ASSOCIATED" if source == "APOLLO" else "UNCONFIRMED",
                       reason="验证有效；仍需审核个人研发证据和邮箱归属，未继续尝试其他格式。")
            if domain_match != "MATCH":
                row["reason"] += " 邮箱域名不同于当前官网，需核实公司历史域名或关联关系。"
            if "role_account" in flags or "free_email_host" in flags or "spamtrap_network" in flags:
                row.update(status="EMAIL_FLAGS_REVIEW", reason="验证返回角色邮箱/免费邮箱/风险标记，需复核。")
            return
        if result not in {"invalid", "disposable"}:
            row.update(status="EMAIL_UNCERTAIN", reason=reason or "全收域或未知结果，停止该人的其他地址尝试。")
            return
    row.update(status="NO_VALID_EMAIL", reason="本轮候选邮箱均未通过，未找到可复核地址。")


def export_results(output, rows, checks, company, journal, mode, error=""):
    by_email = {}
    for row in rows:
        if row["email"]:
            by_email.setdefault(row["email"], set()).add(row["person_id"])
    for row in rows:
        if len(by_email.get(row["email"], set())) > 1:
            row["email_ownership"] = "CONFLICT"
            row["reason"] = "同一邮箱关联多名人员，需人工确认归属。"
    write_csv(output / "people.csv", rows, PERSON_FIELDS)
    write_csv(output / "email_checks.csv", checks, CHECK_FIELDS)
    review = [r for r in rows if r["status"] == "READY_FOR_REVIEW"]
    write_csv(output / "review.csv", review, PERSON_FIELDS)
    counts = journal.counts()
    summary = {"run_mode": mode, "company": company, "updated_at": now(), "error": error,
               "people_found": len(rows), "ready_for_review": len(review), "confirmed_production_rnd": 0,
               "statuses": dict(Counter(r["status"] for r in rows)),
               "attempts": counts, "new_attempts": {k: counts[k] - journal.before[k] for k in counts},
               "apollo_credit_estimate_upper": counts["organization"] + counts["enrich"],
               "neverbounce_verification_attempts": counts["verify"],
               "billing_note": "估算仅适用当前普通补全定价；请求预留不等于实际扣费。离线模式扣费为零。",
               "scope_note": "仅目标 Apollo 公司主体；不自动扩展子公司。海外按个人所在地筛选，不推断国籍。研发资格与邮箱归属仍待审核。"}
    write_json(output / "summary.json", summary)
    return summary


def run(args, keys):
    output = Path(args.output_dir)
    mode = "OFFLINE_FIXTURE" if args.fixture_file else "LIVE"
    config = {"company": args.company, "domain": args.domain, "titles": args.titles,
              "keywords": args.keywords, "locations": args.locations, "patterns": args.patterns,
              "max_addresses": args.max_addresses, "mode": mode,
              "fixture_file": str(Path(args.fixture_file).resolve()) if args.fixture_file else None}
    limits = {"organization": 1, "search": args.max_pages, "enrich": args.limit, "verify": args.max_verifications}
    transport = FixtureTransport(args.fixture_file) if args.fixture_file else LiveTransport(keys, args.delay)
    with output_lock(output):
        journal = Journal(output, config, limits, transport)
        rows, checks, company = [], [], None
        try:
            params = {"q_organization_name": company_name(args.company), "page": 1, "per_page": 100}
            if args.domain:
                params = {"q_organization_domains_list": [args.domain], "page": 1, "per_page": 100}
            response, _ = journal.call("organization", params)
            organizations = response.get("organizations") or response.get("accounts") or []
            write_csv(output / "organizations.csv", organizations, ["id", "name", "primary_domain", "website_url", "industry"])
            company = select_company(organizations, args.company, args.domain)
            print(f"公司：{company['name']} | {company['primary_domain']} | Apollo ID: {company['id']}", flush=True)
            candidates = {}
            for page in range(1, args.max_pages + 1):
                params = {"organization_ids": [company["id"]], "person_titles": args.titles,
                          "include_similar_titles": True, "page": page, "per_page": 25}
                if args.locations:
                    params["person_locations"] = args.locations
                response, _ = journal.call("search", params)
                people = response.get("people")
                if not isinstance(people, list):
                    raise ValueError("Apollo 搜索响应缺少 people 列表；查看 checkpoint.json 中的脱密钥响应。")
                for person in people:
                    if isinstance(person, dict) and person_id(person):
                        candidates.setdefault(person_id(person), person)
                total_pages = (response.get("pagination") or {}).get("total_pages")
                if not people or (isinstance(total_pages, int) and page >= total_pages) or (total_pages is None and len(people) < 25):
                    break
            ordered = sorted(candidates.values(), key=lambda p: (-priority(p, args.keywords), person_id(p)))
            rows = [base_row(p, company, mode) for p in ordered]
            for person, row in zip(ordered, rows):
                if NON_RND.search(row["title"]):
                    row.update(status="ROLE_MISMATCH", reason="搜索返回商业/销售岗位，跳过补全。")
                    continue
                try:
                    reply, timestamp = journal.call("enrich", {"id": row["person_id"], "reveal_personal_emails": False,
                                                              "reveal_phone_number": False, "run_waterfall_email": False,
                                                              "run_waterfall_phone": False})
                except BudgetLimit:
                    row.update(status="ENRICHMENT_LIMIT", reason="达到累计人员补全上限。")
                    continue
                except ApiFailure as exc:
                    row.update(status=exc.code, reason=exc.message)
                    if exc.fatal:
                        raise
                    continue
                full = reply.get("person")
                if not isinstance(full, dict) or not full:
                    row.update(status="NO_ENRICHMENT", reason="Apollo 未返回完整人员资料。")
                    continue
                if person_id(full) and person_id(full) != row["person_id"]:
                    row.update(status="PERSON_ID_CONFLICT", reason="补全结果人员 ID 与搜索结果不同。")
                    continue
                row.update(retrieved_at=timestamp, source_url=ENDPOINTS["enrich"])
                if inspect_person(full, company, row, args):
                    verify_person(full, company, row, checks, journal, args, mode)
                export_results(output, rows, checks, company, journal, mode)
            summary = export_results(output, rows, checks, company, journal, mode)
            print(f"完成：找到 {len(rows)} 条线索，{summary['ready_for_review']} 条邮箱有效待审核。")
            print(f"本次新增调用：{json.dumps(summary['new_attempts'], ensure_ascii=False)}")
            print(f"结果目录：{output.resolve()}")
            return 0
        except (ApiFailure, BudgetLimit, ValueError, KeyboardInterrupt) as exc:
            message = "已中断；请求已预留，可用相同命令续跑。" if isinstance(exc, KeyboardInterrupt) else str(exc)
            export_results(output, rows, checks, company, journal, mode, error=message)
            print(message, file=sys.stderr)
            return 130 if isinstance(exc, KeyboardInterrupt) else 2


def parse_args(argv=None):
    parser = argparse.ArgumentParser(description="按企业名称搜索海外研发人员，补全/推测工作邮箱并验证；仅导出待审核线索。")
    parser.add_argument("--company", required=True, help="目标公司名称，例如 Prysmian Group")
    parser.add_argument("--domain", default="", help="公司重名时指定官网域名")
    parser.add_argument("--titles", nargs="+", default=DEFAULT_TITLES)
    parser.add_argument("--keywords", nargs="+", default=[], help="仅对人员职位/简介做关键词排序，不冒充研发能力证明")
    parser.add_argument("--locations", nargs="+", default=[], help="英文国家全名；默认海外，排除 China 和未知所在地")
    parser.add_argument("--limit", type=int, default=1, help="累计人员补全请求上限（默认1）")
    parser.add_argument("--max-verifications", type=int, default=1, help="累计验证请求上限（默认1）")
    parser.add_argument("--max-pages", type=int, default=1, help="人员搜索页数上限，每页25人（默认1页）")
    parser.add_argument("--max-addresses", type=int, default=3, help="每人最多验证几个地址，包括 Apollo 邮箱")
    parser.add_argument("--patterns", nargs="+", default=ALLOWED_PATTERNS[:3], choices=ALLOWED_PATTERNS)
    parser.add_argument("--env-file", help="本地 properties/env 文件；只读取两个指定 Key，不执行文件内容")
    parser.add_argument("--output-dir", default="exports/corporate-expert-probe")
    parser.add_argument("--delay", type=float, default=0.5, help="每次真实请求前等待秒数")
    parser.add_argument("--dry-run", action="store_true", help="仅显示配置，不读取 Key、不调用 API")
    parser.add_argument("--fixture-file", help="明确离线模式：从 JSON 样本重放，不调用 API")
    args = parser.parse_args(argv)
    args.company = clean(args.company)
    args.domain = domain(args.domain)
    if not company_name(args.company):
        parser.error("--company 不能为空")
    for key in ["limit", "max_verifications", "max_pages", "max_addresses"]:
        if getattr(args, key) < 1:
            parser.error("请求上限必须大于零")
    if args.max_pages > 500 or not 0 <= args.delay <= 30:
        parser.error("max-pages 不得超过500；delay 必须在0到30之间")
    return args


def main(argv=None):
    args = parse_args(argv)
    if args.dry_run:
        print(json.dumps({"company": args.company, "titles": args.titles, "keywords": args.keywords,
                          "max_enrichments": args.limit, "max_verifications": args.max_verifications,
                          "endpoints": ENDPOINTS, "note": "仅预览；零 API 请求，零扣费。"}, ensure_ascii=False, indent=2))
        return 0
    try:
        keys = {} if args.fixture_file else load_keys(args.env_file)
        return run(args, keys)
    except (ValueError, OSError) as exc:
        # These local exceptions contain paths/config errors, never secret values.
        print(f"启动失败：{exc}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())
