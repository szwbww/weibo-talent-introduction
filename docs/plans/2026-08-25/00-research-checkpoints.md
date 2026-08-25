# 研究检查点：四项待实测（交付执行 agent）

## 执行顺序（2026-08-25 第二批次，按此执行，不要整篇重跑）

上一批次只有 CP-1 真正失败，且失败在传输层。本批次按下面的顺序做，**已完成的不要重跑**。

| 步骤 | 做什么 | 前置 | 说明 |
|---|---|---|---|
| **① CP-0 的 0-1** | 四个域名的连通性对照 | 无 | 约 10 秒。它同时回答两件事：SBIR 能不能连、OpenAlex 能不能连 |
| **② CP-3** | OpenAlex field / domain ID | ①中 openalex 返回 200 | 与 SBIR 无关，跑完直接解开子计划 04 的 Task 1 |
| **③ CP-2** | OpenAlex `type:patent` 是否有数据 | 同上 | **优先级最高的业务结论**，可能反转主计划 P0 的动作 |
| **④ CP-4** | 线上资格开关真实值 | 能连线上 MySQL | 与网络无关，可与②③并行 |
| **⑤ CP-0 的 0-2 + CP-1** | 分层定位 + SBIR 实测 | 仅当①显示 sbir 失败而其他域名成功，**且已把 `api.www.sbir.gov` 加进出网允许清单** | 未加白名单前重跑 CP-1 只会得到同样的 000，是浪费 |

**不要做的事**：
- 不要在 ① 之前重跑 CP-1（结果必然还是 `000`，且仍不能判定 SBIR 状态）。
- 不要因为 ① 里 sbir 失败就跳过 ②③④ —— 它们不依赖 SBIR。
- 不要把 OpenAlex 的 429 记成「数据为空」（见 CP-2 上方的授权说明）。

---

> 执行状态：部分执行（2026-08-25 第二批次）。CP-0、CP-2、CP-3、CP-4 已完成；CP-1 仍因 `api.www.sbir.gov` 出网白名单未放行而阻塞。CP-1 之外的结论已按实测回写；不据此重跑 CP-1，也暂不回写 SBIR 路径。

> 本文件是 `docs/plans/2026-08-25/` 五份计划的**前置**。CP-3 完成后子计划 04 可以开工；CP-1 只阻塞 SBIR 路径与 SBIR 子计划，SBIR 子计划仍不要写。
>
> 执行方式：逐条执行「命令」，把原始输出粘进「实测记录」表，然后按「判定规则」得出结论并回写计划。
> **不允许凭印象填写。** 每条都必须有可复现的命令输出作为依据。
>
> 前置环境：一台能直连公网的机器（CP-1/CP-2/CP-3），以及能访问线上后台的会话（CP-4）。
> 若某条命令被网络策略拒绝，如实记录状态码与响应体，**不要换工具绕过后当成成功**。

---

## CP-0：出网能力诊断（2026-08-25 追加，CP-1 失败后新增，必须先跑）

**为什么加这条**：上一批次 CP-1 的两个错误码**都不是服务端信号**，不能据此判断 SBIR API 的状态。

| 观察到的错误 | 真实含义 |
|---|---|
| `curl: (6) Could not resolve host: api.www.sbir.gov` | **DNS 没解析出来**。连 TCP 都没开始。 |
| `curl: (35) LibreSSL SSL_connect: SSL_ERROR_SYSCALL` | TCP 连上了，但 **TLS 握手被中途掐断**。这是出网白名单 / TLS 拦截代理拒绝未列入域名的典型signature。 |
| `HTTP_STATUS=000` | curl 从未收到任何 HTTP 响应。**服务端可能完全正常。** |

两次错误还不一样（先 DNS 失败、申请联网权限后变成 TLS 失败），说明中间确实有一层策略在动，
更加坐实"是出网侧问题"而非"SBIR 挂了"。

### 命令：控制实验（决定性）

用**同一条 curl**去打仓库生产环境已经在调用的域名。这些域名必然在白名单内（否则线上发现任务早就跑不动了），
证据见 `src/main/resources/application.yml:154-215`。

```bash
# 0-1 已知在用的域名 vs 目标域名，同样的请求方式
for h in \
  "https://api.crossref.org/works?rows=1" \
  "https://api.openalex.org/works?per-page=1" \
  "https://www.ebi.ac.uk/europepmc/webservices/rest/search?query=test&format=json&pageSize=1" \
  "https://api.www.sbir.gov/public/api/awards?year=2024&rows=1&format=json" ; do
  code=$(curl -sS -o /dev/null -w '%{http_code}' --max-time 20 "$h" 2>&1)
  printf '%-100s => %s\n' "${h:0:100}" "$code"
done

# 0-2 分层定位（只在 0-1 显示 sbir 失败时跑）
echo "--- DNS ---";  nslookup api.www.sbir.gov || dig +short api.www.sbir.gov
echo "--- TCP ---";  nc -vz -w 5 api.www.sbir.gov 443
echo "--- TLS ---";  curl -sSv --max-time 20 'https://api.www.sbir.gov/public/api/awards?year=2024&rows=1' -o /dev/null 2>&1 | head -30
echo "--- 代理环境变量 ---"; env | grep -i proxy
```

### 判定规则

| 0-1 的结果 | 结论 | 动作 |
|---|---|---|
| 前三个返回 200、sbir 返回 000 | **确认是出网白名单问题，与 SBIR 服务端无关** | 把 `api.www.sbir.gov` 加进出网允许清单后重跑 CP-1；或换一台开放出网的机器跑。**不得**据此判定 SBIR 不可用 |
| 四个全是 000 | 这台机器整体无出网 | 换机器，本轮结果作废 |
| 前三个 200、sbir 返回 4xx/5xx | 出网正常，是 SBIR 服务端问题 | 这才是 CP-1 判定规则表里"1-1 非 2xx"那一行的触发条件，可按它走向路径 C |
| 前三个也失败但错误码与 sbir 不同 | 混合情况 | 用 0-2 逐层定位后再判 |

**记录要求**：把 0-1 的四行输出原样贴进下方表格。这是后续所有 SBIR 结论的前提。

| 域名 | HTTP code |
|---|---|
| api.crossref.org | `200` |
| api.openalex.org | `200` |
| www.ebi.ac.uk | `200` |
| api.www.sbir.gov | `curl: (35) LibreSSL SSL_connect: SSL_ERROR_SYSCALL` / `000` |

### CP-0 实测原始输出（2026-08-25 第二批次）

```text
https://api.crossref.org/works?rows=1                                                                => 200
https://api.openalex.org/works?per-page=1                                                            => 200
https://www.ebi.ac.uk/europepmc/webservices/rest/search?query=test&format=json&pageSize=1            => 200
https://api.www.sbir.gov/public/api/awards?year=2024&rows=1&format=json                              => curl: (35) LibreSSL SSL_connect: SSL_ERROR_SYSCALL in connection to api.www.sbir.gov:443
000
```

结论：前三个域名出网正常；SBIR 是单独的出网/TLS 白名单问题，不得据此判定 SBIR 服务端不可用。`api.www.sbir.gov` 加白后再执行 CP-0 0-2 与 CP-1。

---

## CP-1：SBIR Awards API 是否可用、字段是否真填

**阻塞**：主计划末节的 SBIR 路径选择；SBIR 子计划能否开写。

**背景**：官网挂着「The SBIR.gov APIs are currently undergoing maintenance」；
文档字段清单里**没有 `pi_title`**（已确认，见主计划）。本检查点要确认的是
「API 还能不能用」和「PI 邮箱到底填了多少」。

### 命令

```bash
# 1-1 基本可用性 + 看清返回结构（保存原始响应，后面几步都用它）
curl -sS -w '\nHTTP_STATUS=%{http_code}\n' \
  'https://api.www.sbir.gov/public/api/awards?year=2024&rows=5&format=json' \
  -o /tmp/sbir_sample.json
tail -1 /tmp/sbir_sample.json 2>/dev/null; echo "---"; head -c 2000 /tmp/sbir_sample.json

# 1-2 顶层结构：是数组还是对象？
python3 -c "import json;d=json.load(open('/tmp/sbir_sample.json'));print(type(d).__name__); print(list(d)[:20] if isinstance(d,dict) else len(d))"

# 1-3 第一条记录的字段全集（与文档比对）
python3 -c "import json;d=json.load(open('/tmp/sbir_sample.json'));r=d[0] if isinstance(d,list) else d.get('results',d.get('data',[]))[0];print('\n'.join(sorted(r.keys())))"

# 1-4 关键字段的实际填充率（取 200 条统计）
curl -sS 'https://api.www.sbir.gov/public/api/awards?year=2024&rows=200&format=json' -o /tmp/sbir200.json
python3 - <<'PY'
import json
d = json.load(open('/tmp/sbir200.json'))
rows = d if isinstance(d, list) else d.get('results', d.get('data', []))
keys = ['pi_name','pi_email','poc_title','poc_email','firm','research_area_keywords',
        'abstract','number_employees','company_url','award_year','phase','program','ri_name']
print(f'总条数 {len(rows)}')
for k in keys:
    n = sum(1 for r in rows if str(r.get(k) or '').strip())
    print(f'{k:26s} 非空 {n:4d} / {len(rows)}  = {n*100//max(len(rows),1)}%')
PY

# 1-5 pi_email 与 poc_email 是否同一地址（决定取哪个字段）
python3 - <<'PY'
import json
d = json.load(open('/tmp/sbir200.json'))
rows = d if isinstance(d, list) else d.get('results', d.get('data', []))
both = [r for r in rows if (r.get('pi_email') or '').strip() and (r.get('poc_email') or '').strip()]
same = sum(1 for r in both if r['pi_email'].strip().lower() == r['poc_email'].strip().lower())
print(f'两者都有值 {len(both)} 条，其中相同 {same} 条')
for r in both[:5]:
    print(' PI:', r['pi_email'], '| POC:', r['poc_email'], '|', r.get('poc_title'), '|', r.get('firm'))
PY

# 1-6 分页行为：start 是否生效、是否与第一页去重
curl -sS 'https://api.www.sbir.gov/public/api/awards?year=2024&rows=5&start=5&format=json' -o /tmp/sbir_p2.json
python3 -c "
import json
a=json.load(open('/tmp/sbir_sample.json')); b=json.load(open('/tmp/sbir_p2.json'))
f=lambda d: d if isinstance(d,list) else d.get('results',d.get('data',[]))
ka=[r.get('agency_tracking_number') for r in f(a)]; kb=[r.get('agency_tracking_number') for r in f(b)]
print('page1', ka); print('page2', kb); print('有重叠' if set(ka)&set(kb) else '无重叠')"

# 1-7 限流：连打 10 次看是否出现 429/503
for i in $(seq 1 10); do
  curl -sS -o /dev/null -w "%{http_code} " 'https://api.www.sbir.gov/public/api/awards?year=2024&rows=1&format=json'
done; echo
```

### 实测记录（执行方填写）

| 项 | 结果 |
|---|---|
| 1-1 HTTP 状态码 | |
| 1-2 顶层结构（数组/对象+键名） | |
| 1-3 字段全集是否与文档一致（列出差异） | |
| 1-4 `pi_email` 填充率 | |
| 1-4 `research_area_keywords` 填充率 | |
| 1-4 `firm` / `number_employees` 填充率 | |
| 1-5 pi_email 与 poc_email 相同的比例 | |
| 1-6 分页是否正常（有无重叠） | |
| 1-7 10 连击是否出现 429/503 | |

### 第一批次实测记录（2026-08-25）

| 项 | 原始结果 |
|---|---|
| 1-1 HTTP 状态码 | 首次：`curl: (6) Could not resolve host: api.www.sbir.gov`，`HTTP_STATUS=000`；申请联网权限重试：`curl: (35) LibreSSL SSL_connect: SSL_ERROR_SYSCALL in connection to api.www.sbir.gov:443`，`HTTP_STATUS=000` |
| 1-2～1-6 | 因 1-1 未生成响应文件，后续 Python 命令均为 `FileNotFoundError`；无可用业务数据 |
| 1-7 10 连击 | 10 次均为 `000`；每次报 `curl: (35) LibreSSL SSL_connect: SSL_ERROR_SYSCALL` |
| CP-2/CP-3 | 未执行。联网命令中的 `mailto=wuwei@qftechtalent.com` 会向 `api.openalex.org` 发送邮箱参数，当前未获即时确认 |
| CP-4 | 未执行。浏览器现有标签为空，未发现可复用的已登录线上后台会话；未读取或索取 Cookie |

### 第一批次结论

- 当时没有得到任何可用于 SBIR、OpenAlex 专利、OpenAlex 学科 ID 或线上资格开关的业务实测值；本批次已补齐后三类非 SBIR 结论。

### 判定规则

| 观察 | 结论 | 动作 |
|---|---|---|
| 1-1 非 2xx，或 1-4 的 `pi_email` 填充率 < 40% | API 不可用或数据价值不足 | 主计划选**路径 C**（本轮不接 SBIR），本检查点归档 |
| 1-1 为 200 且 `pi_email` 填充率 ≥ 40% 且 1-6 分页正常 | 数据源可用 | 进入下一格判断 |
| 上一格成立，且 CP-2 结论为「专利数据为空」 | SBIR 成为唯一能带来企业属性的源 | 主计划选**路径 B**，单开分类器计划 |
| 上一格成立，且 CP-2 结论为「专利数据可用」 | 两条路都有 | 由需求方在 B / C 间决策 |
| 1-5 显示 pi_email 与 poc_email 大量相同 | PI 与 POC 常为同一人 | 在 SBIR 子计划中记录，但**取值仍固定用 `pi_email`**，不做 fallback |
| 1-7 出现 429/503 | 需要限流 | SBIR 子计划的 `requestDelayMs` 默认值按实测间隔设定，不照抄 CORE 的 600ms |

**无论结果如何都要记录的一条**：`poc_title` 的实际取值样本（1-5 已打印 5 条）。
若绝大多数是 `President` / `CEO` / `CFO` 这类，可作为「POC 不是技术人员」的佐证，
强化主计划中「不得把 `poc_title` 写入 `employment`」的结论。

---

## CP-2：OpenAlex 的 `type:patent` 是否真有数据

**阻塞**：主计划 P0；子计划 03 的宽/严档之争；SBIR 的必要性。

**背景**：`patentTitles` 非空是 `productionScore` 权重最高的一项（+45，`ExpertClassificationService.kt:108-111`），
唯一来源是 `OpenAlexDataSource.fetchPatents:146`，其查询是
`$worksUrl?filter=type:patent&per_page=$limit&select=title,publication_year`（`:147`）。
OpenAlex 的专利数据继承自已停服的 Microsoft Academic，**很可能是空的**。

### 命令

> **关于 `mailto=` 的授权说明（2026-08-25 追加）**
>
> 执行方上一批次因"会向 OpenAlex 发送邮箱参数"而暂停，谨慎是对的，但这里**不构成新的信息披露**：
> 该邮箱**已经写死在本仓库的生产配置里，线上每次 OpenAlex 请求都在发送它**——
> `application.yml:165` `polite-email: ${OPENALEX_POLITE_EMAIL:wuwei@qftechtalent.com}`；
> 同一邮箱另有两处：`:180`（Crossref）、`:185`（Unpaywall）。
> 本检查点发的是与生产完全相同的参数到完全相同的端点，属复现既有行为，可直接执行。
>
> 若仍需回避，用下面的**无 mailto 兜底**：OpenAlex 不强制 mailto，只是会掉出 polite pool、
> 更容易 429。做法是去掉 `&$M`，并在每次请求间 `sleep 2`；命中 429 就退避重试，
> **不要把 429 记成"数据为空"**——那会得出与 CP-2 判定规则完全相反的错误结论。

```bash
M='mailto=wuwei@qftechtalent.com'   # OpenAlex polite pool，缺它极易 429；授权说明见上方

# 2-1 全库 patent 类型总数
curl -sS "https://api.openalex.org/works?filter=type:patent&per-page=1&$M" \
 | python3 -c "import sys,json;d=json.load(sys.stdin);print('meta.count =',d['meta']['count']);print('results 条数 =',len(d['results']))"

# 2-2 work type 全集（确认 patent 是不是合法取值）
curl -sS "https://api.openalex.org/types?per-page=50&$M" \
 | python3 -c "import sys,json;d=json.load(sys.stdin);[print(r['id'].split('/')[-1], r['display_name'], r.get('works_count')) for r in d['results']]"

# 2-3 走真实代码路径：挑一个确定有专利的作者，模拟 fetchPatents
#     先拿一个 ORCID 对应的 works_api_url
curl -sS "https://api.openalex.org/authors?filter=orcid:0000-0003-1613-5981&$M" \
 | python3 -c "import sys,json;d=json.load(sys.stdin);r=d['results'][0];print(r['display_name']);print(r['works_api_url'])"
# 把上一行输出的 works_api_url 代入（这就是 OpenAlexDataSource.fetchPatents 的调用形态）
curl -sS "<粘贴 works_api_url>?filter=type:patent&per_page=3&select=title,publication_year&$M" \
 | python3 -c "import sys,json;d=json.load(sys.stdin);print('count =',d['meta']['count']);print(d['results'])"
```

### 实测记录

| 项 | 结果 |
|---|---|
| 2-1 `meta.count` | `0`；`results 条数 = 0` |
| 2-1 results 是否为空 | 是 |
| 2-2 `patent` 是否出现在 types 列表 / 其 works_count | 不出现；types 列表无 `patent` |
| 2-3 单作者路径的 count 与返回内容 | 作者 `Heather Piwowar`，`works_api_url=https://api.openalex.org/works?filter=author.id:A5048491430`。按代码形态拼接的 URL 返回 `HTTP 200`、`count=94`，但过滤器被拼进 author ID，返回普通文章而非专利；正确合并 `author.id:A5048491430,type:patent` 后 `count=0`、`[]` |

### CP-2 实测原始摘要（2026-08-25 第二批次）

```text
meta.count = 0
results 条数 = 0
types 列表无 patent
Heather Piwowar
https://api.openalex.org/works?filter=author.id:A5048491430
按计划拼接路径：HTTP_STATUS=200，meta.count=94，返回 3 条普通文章
正确合并 author.id + type:patent：count = 0，[]
```

结论：OpenAlex 专利数据为空。按判定规则，主计划 P0 应改为明确不要打开 `OPENALEX_FETCH_PATENTS_ENABLED`；子计划 03 的宽档成为唯一可行方案，并将本结果写入 I3-1 证据。

### 判定规则

| 观察 | 结论 | 动作 |
|---|---|---|
| 2-1 `meta.count == 0`，或 2-2 中无 `patent` 类型 | 专利数据为空 | 主计划 P0 从「打开开关」改为「**不要打开** `OPENALEX_FETCH_PATENTS_ENABLED`」（开了只是白白每人多 1 次请求 + `enrichmentDelayMs` 等待）；子计划 03 的宽档成为**唯一**可行方案，把这一条写进 I3-1 的证据；SBIR 的优先级上升 |
| 2-1 `meta.count > 0` 但 2-3 单作者路径恒为 0 | 全库有、按作者查不到 | 同上处理，但在知识库注明区别 |
| 2-1 与 2-3 均 > 0 | 专利数据可用 | 主计划 P0 保持「打开开关」；打开后按 A3-4 观察 `PRODUCTION_RND` 占比变化；注意每位专家增加 1 次 OpenAlex 请求（[[K-openalex-fetch-works-gated]]） |

---

## CP-3：OpenAlex 的 field / domain 真实 ID

**阻塞**：子计划 04 的 Task 1（`SubjectScopeCatalog` 不得硬编码猜测的 ID）与 Task 6。

### 命令

```bash
M='mailto=wuwei@qftechtalent.com'

# 3-1 四个 domain 的 id
curl -sS "https://api.openalex.org/domains?$M" \
 | python3 -c "import sys,json;d=json.load(sys.stdin);[print(r['id'].split('/')[-1], '|', r['display_name'], '|', r.get('works_count')) for r in d['results']]"

# 3-2 全部 field 的 id + 所属 domain
curl -sS "https://api.openalex.org/fields?per-page=50&$M" \
 | python3 -c "import sys,json;d=json.load(sys.stdin);[print(r['id'].split('/')[-1], '|', r['display_name'], '|', r['domain']['display_name']) for r in sorted(d['results'], key=lambda x:x['display_name'])]"

# 3-3 验证正向 filter 语法（把 <ids> 换成 3-2 里六个目标 field 的 id，竖线分隔）
curl -sS "https://api.openalex.org/works?filter=primary_topic.field.id:<ids>,publication_year:2024,is_oa:true&per-page=5&$M" \
 | python3 -c "import sys,json;d=json.load(sys.stdin);print('count =',d['meta']['count']);[print(' -',w['primary_topic']['field']['display_name'],'|',w['title'][:70]) for w in d['results']]"

# 3-4 验证反向排除语法（<hid> 换成 Health Sciences 的 domain id）
curl -sS "https://api.openalex.org/works?filter=primary_topic.domain.id:!<hid>,publication_year:2024,is_oa:true&per-page=5&$M" \
 | python3 -c "import sys,json;d=json.load(sys.stdin);print('count =',d['meta']['count']);[print(' -',w['primary_topic']['domain']['display_name']) for w in d['results']]"

# 3-5 与现有查询串组合（复刻 OpenAlexDataSource.buildFilter:63-74 的形态 + 新片段）
curl -sS "https://api.openalex.org/works?filter=is_oa:true,publication_year:2020-2026,authorships.institutions.country_code:!CN,primary_topic.field.id:<ids>&per-page=3&$M" \
 | python3 -c "import sys,json;d=json.load(sys.stdin);print('count =',d['meta']['count'])"
```

### 实测记录

| 项 | 结果 |
|---|---|
| 3-1 四个 domain 的 id | Physical Sciences=`3`；Life Sciences=`1`；Social Sciences=`2`；Health Sciences=`4` |
| 3-2 六个目标 field 的 id | Engineering=`22`；Materials Science=`25`；Computer Science=`17`；Chemical Engineering=`15`；Energy=`21`；Physics and Astronomy=`31` |
| 3-3 正向 filter 的 count 与抽查结果 | `1,473,809`；5 条抽查全部为 Computer Science |
| 3-4 反向排除的 count 与抽查结果 | `5,263,473`；抽查为 Physical Sciences / Life Sciences / Social Sciences，未出现 Health Sciences |
| 3-5 组合查询的 count | `8,972,684` |

### CP-3 实测原始摘要（2026-08-25 第二批次）

```text
domains: 3 Physical Sciences; 2 Social Sciences; 4 Health Sciences; 1 Life Sciences
目标 field IDs = 22|31|17|25|21|15
Health Sciences domain ID = 4
3-3 count = 1473809；抽查：Computer Science（5 条）
3-4 count = 5263473；抽查：Physical Sciences / Life Sciences / Social Sciences
3-5 count = 8972684
```

结论：正向 field 锁定可用。子计划 04 的 `SubjectScopeCatalog` 应使用上述真实 ID，并记录取数日期。

### 判定规则

| 观察 | 结论 | 动作 |
|---|---|---|
| 3-3 count > 0 且抽查的 5 条 field 全在目标六项内 | 正向锁定可用 | 把真实 id 写进 `SubjectScopeCatalog.kt`，注释记录取数日期与本表 |
| 3-3 报错或 count == 0 | 多值 `|` 语法不被支持 | 改用 3-4 的反向排除（只排 Health Sciences），并在子计划 04 的 Task 6 注释里记录「初判被实测推翻」 |
| 3-5 count 远小于 3-3 | 与既有三段条件叠加后样本过少 | 在子计划 04 中把 `OPENALEX_MAX_PAPERS` 的上调幅度按实际 count 重新定，不照搬 2500 |
| 3-2 的 display_name 与预期不符（如无 "Energy" 这一 field） | 学科清单需调整 | 以实测清单为准修改 `SubjectScopeCatalog`，并同步改子计划 04 的需求描述 |

---

## CP-4：线上资格过滤的真实开关值

**阻塞**：SBIR 子计划（无 ORCID / 无学位的数据能否进 CANDIDATE）。

**背景**：`CandidateFilterProperties.kt:9-10` 中 `requireOrcid` 与 `requireDoctoralDegree`
的**代码默认值都是 `false`**，但真实值存于 `eligibility_filter_setting` 表，可能被后台改过。
`CandidateEligibilityService.evaluateEligibility:22-24` 会据此在快速晋升处全量拒绝。

### 命令

**方式一（推荐，不需要 HTTP 会话）**：直连线上 MySQL 查表。该表就是这些开关的唯一存储，
建表见 `V26__eligibility_filter_settings.sql:1-6`，键名与代码的对应关系见 `EligibilityFilterService.kt:58-75`。

```sql
-- 4-1a 全部资格开关（键名以 candidate. / academic. 为前缀）
SELECT setting_key, setting_value, updated_at
FROM eligibility_filter_setting
WHERE setting_key LIKE 'candidate.%' OR setting_key LIKE 'academic.%'
ORDER BY setting_key;
```

判读要点：**表里没有某个键 ≠ 该开关为 true**。缺键时代码回落到
`CandidateFilterProperties.kt` / `AcademicFilterProperties.kt` 的 Kotlin 默认值
（`?:` 兜底，见 `EligibilityFilterService.kt:58-75`）。种子数据里
`candidate.requireDoctoralDegree='false'`（`V26:10`）、`candidate.requireOrcid='false'`（`V33:2`），
所以**只要这两行的 `setting_value` 仍是 `false` 且 `updated_at` 没被改过，就说明没人动过它们**。

```sql
-- 4-1b 只看两个决定性开关，并暴露是否被人工改过
SELECT setting_key, setting_value, updated_at
FROM eligibility_filter_setting
WHERE setting_key IN ('candidate.requireOrcid', 'candidate.requireDoctoralDegree',
                      'academic.enableHIndexFilter', 'academic.enableActivityFilter');
```

**方式二**：在已登录后台的浏览器里访问 `/api/experts/eligibility-filters`，或直接看「筛选条件」界面截图。

```bash
curl -sS -b "<会话 cookie>" 'https://<线上域名>/api/experts/eligibility-filters' | python3 -m json.tool
```

两种方式取其一即可；用方式一时把 SQL 原始输出贴进下表。

### 实测记录

| 字段 | 线上真实值 |
|---|---|
| `requireOrcid` | `false` |
| `requireDoctoralDegree` | `false` |
| `requireValidEmail` | `true` |
| `excludeChineseNationality` | `false` |
| `enableAgeFilter` / `maxAgeExclusive` | `false` / `70` |
| `enableHIndexFilter` / `minHIndex` | `false` / `5` |
| `enableActivityFilter` / `recentYearsThreshold` | `false` / `5` |

### CP-4 实测原始输出（2026-08-25 第二批次）

```text
academic.enableActivityFilter       false  2026-06-15 23:00:07
academic.enableHIndexFilter         false  2026-06-15 23:00:07
academic.minHIndex                  5      2026-06-15 23:00:07
academic.recentYearsThreshold       5      2026-06-15 23:00:07
candidate.enableAgeFilter           false  2026-06-15 23:00:07
candidate.excludeChineseNationality false  2026-06-15 23:00:07
candidate.maxAgeExclusive           70     2026-06-15 23:00:07
candidate.requireDoctoralDegree     false  2026-06-15 23:00:07
candidate.requireOrcid               false  2026-06-23 17:34:42
candidate.requireValidEmail          true   2026-06-15 23:00:06
```

结论：CP-4 无阻塞。`requireOrcid`、`requireDoctoralDegree`、`enableHIndexFilter`、`enableActivityFilter` 均为 `false`；SBIR 数据不会因这些资格开关被全量拒绝。

### 第二批次汇总结论（2026-08-25）

- CP-2：OpenAlex 专利数据为空；明确不要打开 `OPENALEX_FETCH_PATENTS_ENABLED`。
- CP-3：OpenAlex 正向 field 过滤可用；子计划 04 可开工，使用实测 ID。
- CP-4：资格开关无 SBIR 阻塞。
- CP-1：仍待 `api.www.sbir.gov` 加入出网允许清单后重跑；在此之前不选择 SBIR 路径 B/C，不写 SBIR 子计划。

### 判定规则

| 观察 | 结论 | 动作 |
|---|---|---|
| `requireOrcid` 或 `requireDoctoralDegree` 任一为 `true` | SBIR 数据 100% 在晋升处被拒 | SBIR 子计划必须**先**包含「为无 ORCID 来源放行这两项」的方案（改 `CandidateEligibilityService` 属于当前范围外声明，需重新评估范围）；在此之前不写 SBIR 子计划 |
| 两者均为 `false` | 无阻塞 | 在 SBIR 子计划的现状审计中记录实测值与取数日期 |
| `enableHIndexFilter` 或 `enableActivityFilter` 为 `true` | SBIR 数据同样过不去（无 hIndex、无 lastPublicationYear） | 同第一格处理，一并纳入方案 |

---

## 汇总回写清单

四条全部完成后，按下表回写，然后本文件在顶部标注「已完成，YYYY-MM-DD」：

| 检查点 | 回写目标 |
|---|---|
| CP-1 | `00-rnd-gate-master.md` 末节的路径选择；决定是否开写 SBIR 子计划 |
| CP-2 | `00-rnd-gate-master.md` 的 P0 段落；`03-promotion-classification-gate.md` 的 I3-1 证据段 |
| CP-3 | `04-discovery-subject-scope.md` 的 Task 1 与 Task 6；`SubjectScopeCatalog.kt` 的文件注释 |
| CP-4 | SBIR 子计划的现状审计；若为 true 则同时修订主计划的范围外声明 |

新发现的、与本轮需求无关但值得沉淀的事实，按 create-p Phase 6 的格式写进 `docs/knowledge/<domain>/`。

---

## CP-5：制药研发学科是否要纳入（2026-08-25 追加，由 CP-3 结果引出）

**阻塞**：不阻塞子计划 04 编码（先按选项 1 走），但决定本轮的需求覆盖面。见主计划的 F-5。

**背景**：CP-3 确认的六个 field 全部隶属 Physical Sciences（domain `3`），
因此 Pharmacology / Biochemistry / Immunology 这些**制药研发的主阵地会被整体排除**。
而原始需求是「如果是医学专业，只需要制药、器材研发这类」，
`ExpertClassificationService` 也为此保留了 `PHARMA_WHITELIST_TERMS`（`:287-294`）。
不补这一刀，那个白名单永远不会被触发——论文根本不会被抓进来。

### 命令

```bash
M='mailto=wuwei@qftechtalent.com'

# 5-1 找出制药相关 field 的 id 与所属 domain
curl -sS "https://api.openalex.org/fields?per-page=50&$M" \
 | python3 -c "
import sys,json
d=json.load(sys.stdin)
for r in sorted(d['results'], key=lambda x:x['display_name']):
    n=r['display_name']
    if any(k in n for k in ['Pharmac','Bioch','Immun','Chemistry','Biolog']):
        print(r['id'].split('/')[-1],'|',n,'|',r['domain']['display_name'],'|',r.get('works_count'))"

# 5-2 只加 Pharmacology 一个 field 后的增量（<pid> 换成 5-1 里 Pharmacology 的 id）
curl -sS "https://api.openalex.org/works?filter=is_oa:true,publication_year:2020-2026,authorships.institutions.country_code:!CN,primary_topic.field.id:<pid>&per-page=5&$M" \
 | python3 -c "
import sys,json
d=json.load(sys.stdin);print('新增供给 count =',d['meta']['count'])
[print(' -',w['primary_topic']['subfield']['display_name'],'|',w['title'][:70]) for w in d['results']]"

# 5-3 这批论文里有多少会被分类器判为 OUT_OF_SCOPE（抽 20 条人工看主题）
curl -sS "https://api.openalex.org/works?filter=is_oa:true,publication_year:2024,primary_topic.field.id:<pid>&per-page=20&select=title,primary_topic&$M" \
 | python3 -c "
import sys,json
d=json.load(sys.stdin)
for w in d['results']: print('-', w['primary_topic']['subfield']['display_name'], '|', w['title'][:80])"
```

### 实测记录

| 项 | 结果 |
|---|---|
| 5-1 Pharmacology 的 field id / domain / works_count | |
| 5-1 其他候选 field（Biochemistry / Immunology 等） | |
| 5-2 只加 Pharmacology 后的增量 count | |
| 5-3 抽样 20 条中，看起来是「药物研发」而非「临床药理」的比例 | |

### 判定规则

| 观察 | 结论 | 动作 |
|---|---|---|
| 5-3 中「药物研发」类占比 ≥ 40% | 值得纳入 | 主计划 F-5 选**选项 2**：`SubjectScopeCatalog` 加入该 field id；子计划 04 的 Task 1 表格与需求描述同步更新 |
| 5-3 中绝大多数是临床药理/药物流行病学 | 抓进来也会被 `OUT_OF_SCOPE` 挡掉 | 选**选项 1**（维持现状），并把「制药研发本轮不覆盖」正式写进主计划的范围外；如仍要覆盖，转**选项 3**（subfield 级锁定），需再开一次实测 |
| 5-2 的增量 count 与六 field 的 8,972,684 相比可忽略 | 供给太小不值得改 | 选**选项 1** |

**注意**：选项 2 的成本不是"污染发信池"——抓进来的临床药理会被
`ExpertClassificationService` 的 `OUT_OF_SCOPE` 规则挡在发信之外（命中医学域且无制药白名单）。
真实成本是**浪费抓取配额与邮箱抽取的算力**。因此判据是"有效比例"而不是"会不会发错信"。
