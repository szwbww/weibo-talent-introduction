# 02 确定性检索层：归一化 / 短语组 / 覆盖键 / 强制 / 剔除 / 预筛

> 顺序权威：`00-execution-order.md`。**依赖 01**（表与快照必须先落地）。
> 全局不变量 G-1 ~ G-4 适用，本文不重复定义。

## 需求描述

**Observable outcome**

1. 给定一封来信，服务端能纯确定性地算出四样东西，全程不调用 LLM：
   命中的短语组、必须覆盖的 coverage key 列表、有序的强制事实列表、预筛候选事实列表（≤18 条）。
2. 上述四样东西与 `scripts/spike_deepseek_reply.py` 在同一封来信上的输出**逐条相同**，
   唯一例外是 D-3 的 `COMPENSATION` 强制行。这一点由平价测试用真实来信语料自动断言。
3. 「问报酬」的三种措辞（`compensation` / `salary` / `remuneration`）都必出 `KB-FUND-033`。

**What must NOT change**

1. 系统对外行为：本计划仍然没有任何调用方，落地后线上表现零变化。
2. `qa_rule` 及旧链路的任何读写路径。
3. 01 建的表结构与种子数据。

**Out of scope**

- LLM 调用与整封生成（→ 03）。
- 任何前端（→ 04/05/06）。
- 把短语组做成运营可编辑的界面（→ 04）。

## 关键不变量

### I-7: 分词与归一化必须与脚本逐字等价
- Rule: `normalize(s)` = 转小写 → 按正则 `[a-z0-9]+`（**ASCII 类，不得用 Unicode 词类**）取出全部词元
  → 用单个空格连接 → 首尾各补一个空格。短语命中判定 = `normalize(text).contains(normalize(phrase))`。
  词重叠打分用同一分词器产出的 `Set<String>` 求交集大小。
- Applies to: `RagTextNormalizer`、`RagPhraseMatcher`、`RagPrefilterService` 的全部打分与匹配。
- Violation consequence: 首尾不补空格会让 `paid` 命中 `unpaid`；用 Unicode 词类会让非 ASCII 字符
  参与分词。两者都导致**静默**分叉——预筛结果不同但没有任何报错。
- 来源: original（`spike_deepseek_reply.py` 的 `_TOKEN_RE = re.compile(r"[a-z0-9]+")` 与
  `_normalized()` 的 `" " + " ".join(...) + " "`）

### I-8: 预筛执行顺序不可交换
- Rule: 顺序恒为
  ① 按 `-score, fact_code` 排序
  ② 若 `requested` 非空则只留覆盖键相交的事实，否则只留 `score >= 2` 的事实
  ③ 应用反向剔除
  ④ 强制事实**前置合并**（`mandatory + selected.filter { it !in mandatoryIds }`）
  ⑤ 截断到 18 条。
  **强制事实绕过第 ③ 步剔除**——它取自全量 enabled 事实表，不取自 `selected`。
- Applies to: `RagPrefilterService.prefilter()`。
- Violation consequence: 把 ④ 放到 ③ 之前，`KB-FUND-033` 在「compensation + more details」场景下会被剔掉，
  薪资段落消失。实测：脚本正是靠这个顺序让 033 在该场景下仍然出现。
- 来源: original（`spike_deepseek_reply.py` `prefilter_facts()` 的语句顺序）

### I-9: 有序去重保留首次出现
- Rule: 强制事实列表与最终候选列表的去重一律「保留首次出现的位置」
  （等价于 Python 的 `dict.fromkeys`）。
- Applies to: `RagMandatoryResolver.resolve()`、`RagPrefilterService.prefilter()`。
- Violation consequence: `KB-FUND-033` 同时被 `DETAIL_INQUIRY`(10) 与 `COMPENSATION`(15) 命中时，
  若保留末次出现，它会从「PROG-002 之后」挪到列表末尾，进而改变生成提示词里 VERBATIM 令牌的落段顺序。
- 来源: original

### I-10: requested_coverage_keys 是覆盖判定的唯一分母
- Rule: 「这封信必须覆盖哪些话题」只能由 `rag_intent_coverage` + 命中的短语组算出，
  **不得**由模型返回的 `coverage[]` 或任何 LLM 输出参与。模型自述只能展示，不能进入判定。
- Applies to: `RagPrefilterService.requestedCoverageKeys()`；03 的审计；05 的前端。
- Violation consequence: 分母来自模型自述时，模型越读不懂就越显示「依据充分」。
- 来源: K-grounded-denominator-is-matched-intents（本仓已成文的历史缺陷）

### I-11: COMPENSATION 强制行是本轮唯一刻意偏离
- Rule: 除 `rag_mandatory_rule` 中 `sort_order=15` 的 `COMPENSATION -> KB-FUND-033` 一行外，
  确定性层的行为必须与脚本逐字相同。平价测试对该行单独登记为「已知且刻意的偏离」。
  反向剔除规则**原样保留不改**——033 先被剔除、再作为强制项加回；
  `KB-FUND-036`（住房补贴 / 创业启动资金，HIGH + REVIEW）继续被压住。
- Applies to: `RagMandatoryResolver`、平价测试。
- Violation consequence: 顺手把剔除规则也改了 → 036 的推销内容重新出现在只问报酬的回信里。
- 来源: original（D-3）

### I-12: CV 请求判定的四个条件是「与」关系
- Rule: `shouldRequestCv` 恒为
  `expertReplyCount >= 2 && cvStatus == MISSING && positiveIntent && asksNextStep`。
  其中 `positiveIntent = expertTags 含 WILLING_TO_CONTINUE || 命中 POSITIVE_INTENT 短语组`。
  四个条件缺一即 false。命中时向 `requested` 追加 `application.required_materials`。
- Applies to: `RagPrefilterService.requestedCoverageKeys()`。
- Violation consequence: 改成「或」会让首封回信就主动索要 CV。
- 来源: original（`spike_deepseek_reply.py` `should_request_cv()`）

## 现状审计

### `rag_*` 五张表（01 产出，本计划只读）
- `RagKnowledgeBase.snapshot()` 提供不可变的 `facts / phraseGroups / intentCoverage /
  mandatoryRules / exclusions`。本计划的所有服务只从快照读，不查库。
- Write paths: 仅 01 的 V112 迁移；04 将增加管理页写入。本计划零写入。
- Read paths（本计划新增，均为进程内内存读）：
  1. `RagPhraseMatcher.matchedGroups()` — 读 `phraseGroups`
  2. `RagPrefilterService.requestedCoverageKeys()` — 读 `intentCoverage`
  3. `RagMandatoryResolver.resolve()` — 读 `mandatoryRules` + `facts`
  4. `RagPrefilterService.prefilter()` — 读 `facts` + `exclusions`
- Interaction points: **本计划为零**。确定性层没有任何生产调用方，只被测试调用。
  03 落地时才接上。这是刻意的，保证 02 可独立部署与回滚。

### 脚本侧的权威实现（逐条对照点）
| 脚本函数 | 行为 | 对应新类 |
|---|---|---|
| `_normalized(value)` | 小写 → `[a-z0-9]+` → 单空格连接 → 首尾补空格 | `RagTextNormalizer.normalize` |
| `_contains_any(text, phrases)` | 归一化后子串命中 | `RagPhraseMatcher.containsAny` |
| `is_detail_inquiry(email)` | `_contains_any(_DETAIL_INQUIRY_PHRASES)` | 短语组 `DETAIL_INQUIRY` |
| `mandatory_fact_ids(email)` | 5 个 if 分支 + `dict.fromkeys` 去重 | `RagMandatoryResolver.resolve`（6 行规则） |
| `should_request_cv(...)` | 四条件与 | `RagPrefilterService`（I-12） |
| `requested_coverage_keys(...)` | 遍历 `_INTENT_COVERAGE` + CV 追加 | `RagPrefilterService.requestedCoverageKeys` |
| `_lexical_score(query, fact, requested)` | `覆盖命中*100 + 短语命中*12 + 词重叠*1` | `RagPrefilterService.lexicalScore` |
| `prefilter_facts(...)` | 见 I-8 五步 | `RagPrefilterService.prefilter` |

### 实测基线（平价测试的期望值来源）
用 `scripts/spike_deepseek_reply.py` 在 8 个场景上实跑，加上 D-3 的 `COMPENSATION` 强制行后：

| 场景（来信片段） | detail | 强制事实（有序） | 033 命中 | 036 命中 |
|---|---|---|---|---|
| `the compensation for this advisory role` | 否 | `KB-FUND-033` | 是 | 否 |
| `the salary for this advisory role` | 否 | `KB-FUND-033` | 是 | 否 |
| `What remuneration is offered` | 否 | `KB-FUND-033` | 是 | 否 |
| `more details from you, including the compensation` | 是 | `KB-PROG-002, KB-FUND-033` | 是 | 否 |
| `What is the compensation, and is there any government funding` | 否 | `KB-FUND-033` | 是 | 否 |
| `the compensation structure and payment schedule` | 否 | `KB-FUND-033` | 是 | 否 |
| `the official name of the programme` | 否 | `KB-PROG-003, KB-COMP-007` | 否 | 否 |
| 日本教授完整样例（`SAMPLE_INBOUND_EMAIL`） | 是 | `KB-PROG-002, KB-FUND-033, KB-PROG-003, KB-GOV-004, KB-COMP-007, KB-IP-039, KB-CONF-036` | 是 | 否 |

最后一行是脚本的原始样例，**其强制顺序在加入 D-3 后一字未变**——这是 I-9 的直接证据。
日本教授样例的 `requested_coverage_keys` 共 16 个，预筛候选 18 条。

## 实现方案

### T1 — 归一化与短语匹配
新建 `rag/service/RagTextNormalizer.kt`：
- `normalize(value: String): String` — 严格实现 I-7。正则写死为 `Regex("[a-z0-9]+")`。
- `tokens(value: String): Set<String>` — 同一正则产出的词元集合。

新建 `rag/service/RagPhraseMatcher.kt`：
- `containsAny(text: String, phrases: List<String>): Boolean`
- `matchedGroups(text: String, groups: List<RagPhraseGroup>): List<String>` — 返回命中的 `group_code`，
  按 `group_code` 升序（供 UI 与日志稳定展示）。

遵循 I-7。

### T2 — 覆盖键与 CV 判定
在 `rag/service/RagPrefilterService.kt` 中：
- `requestedCoverageKeys(inbound, context): List<String>` — 遍历 `intentCoverage`，
  命中组的 coverage key 按规则表 `sort_order` 追加，去重保留首次（I-9）；
  末尾按 I-12 追加 `application.required_materials`。
- `shouldRequestCv(inbound, context): Boolean` — 严格四条件与（I-12）。

遵循 I-9、I-10、I-12。

### T3 — 强制事实解析
新建 `rag/service/RagMandatoryResolver.kt`：
- `resolve(inbound): List<String>` — 按 `rag_mandatory_rule.sort_order` 升序遍历，
  规则的 `match_groups` 为 any-of（任一命中即生效），命中则按序追加其 `fact_codes`；
  最后按 I-9 去重；再过滤掉 `enabled=false` 的 fact_code（I-2）。
- 只从全量 enabled 事实取，**不接触** `selected`（I-8 第 ④ 步的前提）。

遵循 I-2、I-8、I-9、I-11、G-1。

### T4 — 打分与预筛
在 `RagPrefilterService` 中：
- `lexicalScore(query, fact, requested)` =
  `(requested ∩ fact.coverageKeys).size * coverageWeight
   + fact.questionVariants.count { normalize(query).contains(normalize(it)) } * phraseWeight
   + (tokens(query) ∩ tokens(fact.retrievalText)).size * overlapWeight`
  三个权重取自 `RagProperties`（默认 100 / 12 / 1）。
- `prefilter(inbound, context): List<RagFact>` — 严格实现 I-8 五步。
  第 ③ 步遍历 `rag_prefilter_exclusion`：`when_groups` 全部命中且 `unless_groups` 均未命中时，
  按 `target_type` 剔除对应 `fact_code` 或含该 `coverage_key` 的事实。

遵循 I-7、I-8、I-9、I-11。

### T5 — 平价测试与语料
新建 `scripts/dump_rag_parity_fixtures.py`：
- 读入一组来信文本，调用脚本的 `prefilter_facts` / `mandatory_fact_ids`（含 D-3 补丁）/
  `requested_coverage_keys`，输出 JSON 到 `src/test/resources/rag-parity/fixtures.json`。
- 语料要求：**不少于 20 封真实来信**，从历史 `mail_record` 的 INBOUND 正文中导出并脱敏
  （去掉姓名、邮箱、机构名；这些字段不参与短语匹配，脱敏不影响结果）。
  外加上表 8 个构造场景，共 ≥28 条。

新建 `src/test/kotlin/com/weibo/talentintroduction/rag/RagPrefilterParityTest.kt`：
- 逐条读 fixture，断言 Kotlin 的三项输出与 fixture 完全相同：
  预筛 `fact_code` 有序列表、强制 `fact_code` 有序列表、`requested_coverage_keys` 有序列表。
- 对 D-3 单独一个测试方法 `compensationMandatoryIsTheOnlyDeliberateDeviation`，
  显式登记这条偏离并断言上表 8 行的期望值。

遵循 I-7 ~ I-12。

## 变更文件清单

| # | 文件 | 动作 |
|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/rag/service/RagTextNormalizer.kt` | 新增 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/rag/service/RagPhraseMatcher.kt` | 新增 |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/rag/service/RagMandatoryResolver.kt` | 新增 |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/rag/service/RagPrefilterService.kt` | 新增 |
| 5 | `scripts/dump_rag_parity_fixtures.py` | 新增 |
| 6 | `src/test/resources/rag-parity/fixtures.json` | 新增 |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/rag/RagPrefilterParityTest.kt` | 新增 |
| 8 | `src/test/kotlin/com/weibo/talentintroduction/rag/RagTextNormalizerTest.kt` | 新增 |

文件数 8，子系统 1（后端检索层）。无前端改动，故无 `## 样式契约`。

## 验证命令

> 本项目必须用 JDK 11（zulu-11）。裸 `mvn` 会构建失败。

```bash
# 全量测试（回归门禁）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test

# 本计划新增的两个测试类
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=RagPrefilterParityTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=RagTextNormalizerTest

# 只跑 D-3 那一条偏离登记
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=RagPrefilterParityTest#compensationMandatoryIsTheOnlyDeliberateDeviation

# 重新生成平价语料（改动确定性层后必跑，用于确认期望值本身没变）
python3 scripts/dump_rag_parity_fixtures.py

# 构建
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package

# 空白/换行卫生
git diff --check
```

通过判据：`mvn test` 退出码 0 且 `Tests run: N, Failures: 0, Errors: 0`；
`dump_rag_parity_fixtures.py` 重跑后 `git diff --stat src/test/resources/rag-parity/fixtures.json` 无变化；
`git diff --check` 无输出。
来源：`CLAUDE.md:10-27` Commands 章节。

## 验收标准

- **I-7**：`RagTextNormalizerTest` 断言
  `normalize("Compensation, please?") == " compensation please "`（首尾各一个空格）；
  断言 `containsAny("I am unpaid", listOf("paid")) == false`（首尾空格生效）；
  断言 `containsAny("I am paid", listOf("paid")) == true`。
- **I-8**：平价测试中「`more details` + `compensation`」场景断言最终候选**包含** `KB-FUND-033`
  且**不包含** `KB-FUND-034`；另加一个白盒测试，把强制合并挪到剔除之前时断言 033 消失
  （以注释形式记录该顺序为何不可交换，不必真的写反向实现）。
- **I-9**：日本教授样例断言强制列表逐字等于
  `[KB-PROG-002, KB-FUND-033, KB-PROG-003, KB-GOV-004, KB-COMP-007, KB-IP-039, KB-CONF-036]`。
- **I-10**：`grep -rn "coverage\[" src/main/kotlin/com/weibo/talentintroduction/rag/` 在本计划范围内无输出
  （确定性层不引用任何模型输出）。
- **I-11**：`compensationMandatoryIsTheOnlyDeliberateDeviation` 断言上表 8 行的
  `033 命中` 与 `036 命中` 两列；并断言「完全不问钱」那行强制列表中**不含** `KB-FUND-033`。
- **I-12**：四条件各构造一条 false 用例（轮次=1 / CV=RECEIVED / 无继续意愿 / 未问下一步），
  断言 `shouldRequestCv` 均为 false；四条件齐备时为 true。
- 平价整体：≥28 条 fixture 全部三项输出完全相同，零差异。
- 回归：执行「验证命令」节的全量测试命令通过。

## 人工验收清单

### A-1: 问报酬的三种措辞都出薪资原文
- 前置条件: 01 已落地，02 的测试类可运行。
- 操作步骤:
  1. 执行 `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=RagPrefilterParityTest#compensationMandatoryIsTheOnlyDeliberateDeviation`
  2. 查看测试输出。
- 预期结果: 测试通过；输出中 `compensation` / `salary` / `remuneration` 三行的强制事实均含
  `KB-FUND-033`，且三行的 `KB-FUND-036` 均未命中。
- 覆盖: 需求 observable outcome 3；I-11

### A-2: 与脚本的平价
- 前置条件: 本机可运行 `python3 scripts/spike_deepseek_reply.py`。
- 操作步骤:
  1. 执行 `python3 scripts/dump_rag_parity_fixtures.py`
  2. 执行 `git diff --stat src/test/resources/rag-parity/fixtures.json`
  3. 执行 `JAVA_HOME=... mvn test -Dtest=RagPrefilterParityTest`
- 预期结果: 第 2 步无输出（语料未变，说明脚本侧行为稳定）；第 3 步全部用例通过，
  输出中出现 `Tests run:` 且 `Failures: 0, Errors: 0`。
- 覆盖: 需求 observable outcome 2；I-7 ~ I-12

### A-3: 不问钱的信不会多出薪资段落
- 前置条件: 同 A-1。
- 操作步骤: 在 `RagPrefilterParityTest` 的输出中找到「只问项目官方名称」那条场景。
- 预期结果: 该场景强制事实为 `[KB-PROG-003, KB-COMP-007]`，**不含** `KB-FUND-033`；
  预筛候选中也不含 `KB-FUND-033`。
- 覆盖: I-11（偏离范围受控）

### A-4: 回归 —— 线上行为零变化
- 前置条件: 02 已部署到测试环境。
- 操作步骤:
  1. 打开「收发件箱」→ 待处理来信 → 可信工作台，生成一次草稿。
  2. 打开「AI 回复训练 → 历史邮件模拟回复」，生成一次。
  3. 查看应用日志中是否出现任何 `Rag` 前缀的日志行。
- 预期结果: 前两步行为与本计划实施前完全一致；第 3 步**没有**任何 `Rag` 前缀日志——
  确定性层此时还没有生产调用方。
- 覆盖: What must NOT change 第 1、2 条

### A-5: 回归 —— 停用事实不进候选
- 前置条件: 同 A-1。
- 操作步骤: 在平价测试输出里搜索 `KB-APP-017`。
- 预期结果: 全部 ≥28 条场景中 `KB-APP-017` 一次都不出现。
- 覆盖: I-2（01 定义，02 消费）
