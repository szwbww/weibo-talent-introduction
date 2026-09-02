# c2 Execution Report — 02 确定性检索层：归一化 / 短语组 / 覆盖键 / 强制 / 剔除 / 预筛

- Result: **READY_FOR_VERIFICATION**（控制器裁定：≥20 封真实来信语料为环境/数据阻塞并批准按可用语料落地；D-3 8 行登记与平价断言全部机器验证通过）
- Plan: `docs/plans/2026-09-02/02-rag-deterministic-retrieval.md`
- Plan identity: `commit:46cc5c46395814b1ef03e52ab8b8bfb5197f372c`（`git diff 46cc5c4 -- <plan>` 为空，已复核）
- Plan sha: `46cc5c46395814b1ef03e52ab8b8bfb5197f372c`
- Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-02-execution-order`
- Branch: `fast/2026-09-02-execution-order`（HEAD `cd2c3631601bfca7aa79c4a609e5bfda0754014e` = c1 证据提交；`git merge-base --is-ancestor acb88c1 HEAD` 通过）
- Master plan: `docs/plans/2026-09-02/00-execution-order.md`（identity `92b0519a18a3a46989f8733259af4649f7748a72`；G-1..G-4 逐一遵守）
- Child base (product boundary) SHA: `acb88c1e77d172a7f252690b1da1203f08c01817`（c1 代码头）
- Implementation commit: `af8fb5f`（`feat(fast-p): implement c2`，8 个授权文件，+3048 行；docs/plans/fast/** 未纳入）
- Task status: COMPLETE（平价/单元/D-3 用例全绿；真实来信语料按控制器裁定登记为环境阻塞，见 §Deviations 1）

## 变更文件（8，全部为计划 `## 变更文件清单` 授权文件）

| # | 文件 | 动作 |
|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/rag/service/RagTextNormalizer.kt` | 新增（T1；I-7 逐字等价：`Regex("[a-z0-9]+")` ASCII、小写→单空格连接→首尾各一空格；`tokens()` 同正则词元集合） |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/rag/service/RagPhraseMatcher.kt` | 新增（T1；`containsAny` = `normalize(text).contains(normalize(phrase))`；`matchedGroups` 按 group_code 升序去重） |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/rag/service/RagMandatoryResolver.kt` | 新增（T3；6 规则按 sort_order 升序、any-of 命中、I-9 首次去重、I-2 enabled-only；含 V112 种子数据缺陷补偿 `GOVERNMENT_ORG→GOVERNMENT_ORGANIZATION`，见 §Deviations 3） |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/rag/service/RagPrefilterService.kt` | 新增（T2/T4；`requestedCoverageKeys`/`shouldRequestCv` I-12 四条件与、`lexicalScore` 100/12/1、`prefilter` I-8 五步；同文件定义 `RagProcessContext`（03 的 D-7 映射目标类型）） |
| 5 | `scripts/dump_rag_parity_fixtures.py` | 新增（T5；读 spike + export 常量，D-3 补丁 + DB 序覆盖键，机器输出 fixtures.json；语料指纹硬门禁 `e62421a42c432cf3`） |
| 6 | `src/test/resources/rag-parity/fixtures.json` | 新增（T5；机器生成；10 条用例：3 real + 7 constructed；corpus 段与 V112 种子行同序同义） |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/rag/RagPrefilterParityTest.kt` | 新增（T5；7 个用例方法：整体平价、D-3 8 行登记、I-9、I-8 场景+白盒、I-12 四条件、I-2/A-5） |
| 8 | `src/test/kotlin/com/weibo/talentintroduction/rag/RagTextNormalizerTest.kt` | 新增（T5 验收；I-7 字面断言 + ASCII-only + paid/unpaid） |

未触碰：`qa_rule`/`qa_category`、既有迁移 V1..V111、c1 已提交 rag 文件（RagFact/RagRetrievalRule/RagFactRepository/RagRetrievalRuleRepository/RagKnowledgeBase/RagProperties/V112/export_rag_kb_sql.py/FlywayMigrationIntegrationTest）、`docs/plans/fast/**`。

## 关键实现

- **快照消费**：四个服务全部只读 `RagKnowledgeBase.snapshot()` 的不可变快照（生产入口方法委托快照重载；确定性核心公开带 `RagCorpusSnapshot` 参数的重载，平价测试在内存重建快照、零 DB 依赖 —— 普通 `mvn test` 不需 Docker/MySQL）。
- **平价闭环**：fixtures.json `corpus` 段与 V112 种子行逐一同义同序（facts 45 / phraseGroups 87 / intentCoverage 21 / mandatoryRules 6 / exclusions 4；指纹 `e62421a42c432cf3`，由 export 指纹门禁 + 测试断言双层钉死）；python 侧期望 = spike `prefilter_facts`/`mandatory_fact_ids`/`requested_coverage_keys` 的 D-3 补丁镜像（`prefilter_facts` 五步逐字保留），Kotlin 侧 10 条用例三项输出全部 == fixture（零差异）。
- **D-3 登记**：`compensationMandatoryIsTheOnlyDeliberateDeviation` 用 fixture 8 行 id（scenario-1..7 + real-mockup-japanese-professor-full）把计划实测基线表的强制列表与 033/036 两列与 ① 计划表常量 ② fixture 机器输出 ③ Kotlin 输出三方互证；A-1（三种措辞必出 033）、A-3（row 7 完全不问钱：强制与候选都无 033）同方法覆盖。
- **I-9**：日本教授完整样例强制列表逐字 `[KB-PROG-002, KB-FUND-033, KB-PROG-003, KB-GOV-004, KB-COMP-007, KB-IP-039, KB-CONF-036]`；033 索引恒为 1（DETAIL(10) 与 COMPENSATION(15) 双命中下首次去重位置不变 —— I-9 直接证据）；requested 16 个（与计划 prose 一致）。
- **I-8**：场景断言（`more details`+`compensation` 候选含 033 不含 034）+ 白盒登记 `prefilterStepOrderIsNotCommutable`（证明 033 是第 ③ 步剔除目标、唯一加回路径是 ④ 强制前置，故 ④ 必须在 ③ 后）。
- **I-12**：四个 false（轮次 1 / CV RECEIVED / 无意愿 / 未问下一步）+ 一个 true + 命中时 requested 末尾追加 `application.required_materials` + `WILLING_TO_CONTINUE` 标签路径。
- **I-10**：`grep -rn 'coverage\[' src/main/kotlin/com/weibo/talentintroduction/rag/` 无输出（exit 1）；确定性层零 LLM 依赖。
- **A-5/I-2**：语料恰 1 条停用事实（KB-APP-017）且在所有输出中一次不出现。

## 命令与结果（JDK 11；最终代码态新鲜执行）

| # | 命令 | 退出码 | 结果 |
|---|---|---|---|
| 1 | `mvn test -Dtest=RagTextNormalizerTest` | 0 | **Tests run: 3, Failures: 0, Errors: 0**, BUILD SUCCESS |
| 2 | `mvn test -Dtest=RagPrefilterParityTest` | 0 | **Tests run: 7, Failures: 0, Errors: 0**, BUILD SUCCESS（10 条 fixture 全部三项平价 + D-3/I-8/I-9/I-12/A-5） |
| 3 | `mvn test -Dtest=RagPrefilterParityTest#compensationMandatoryIsTheOnlyDeliberateDeviation` | 0 | **Tests run: 1, Failures: 0, Errors: 0**, BUILD SUCCESS |
| 4 | `python3 scripts/dump_rag_parity_fixtures.py`（连续重跑） | 0 | `wrote .../fixtures.json: 10 cases (3 real / 7 constructed)`；`cmp` 两次输出逐字节一致；提交后重跑 `git diff --stat` 为空（见命令 7 补充） |
| 5 | `mvn clean package` | 0 | **Tests run: 3073, Failures: 0, Errors: 0, Skipped: 6**, BUILD SUCCESS（c1 基线 3063 → +10 本计划用例） |
| 6 | `mvn test`（全量回归） | 0 | **Tests run: 3073, Failures: 0, Errors: 0, Skipped: 6**, BUILD SUCCESS |
| 7 | `git diff --check` | 0 | 无输出 |
| — | `grep -rn 'coverage\[' src/main/kotlin/com/weibo/talentintroduction/rag/` | 1 | 无输出（I-10 验收门禁） |

计划 ≥28 条真实来信语料的平价断言（A-2 的语料规模部分）因 §Deviations 1 的数据阻塞无法执行 —— 已按控制器裁定以可用语料（10 条）落地并机器断言；其余全部验收（A-1/A-3/A-4 代码侧/A-5/I-7..I-12）绿。

## 偏离（全部经控制器裁定或已登记）

1. **≥20 封真实来信语料：环境/数据阻塞（控制器裁定批准，非实现缺陷）**。历史 `mail_record` INBOUND 正文在本环境不可达 —— 证据：(a) `localhost:3306` 无服务；(b) `mailagg-mysql`（mysql:8.0.36，DB `talent_introduction`，端口 13306）仅 5 行 mail_record、唯一 INBOUND 行 body NULL，且无 qa_rule 表（非真实数据卷）；(c) `qingfei-phase1-mysql` 无 mail 表；(d) brew mysql@5.7 数据目录无 `talent_introduction` 库；(e) scratch 链（c1 机制）只重建 V1..V112 **schema**：mail 正文是运行时用户数据（`AutoMailReplyService` 写入），无任何迁移种子 mail_record 行（grep 证实），故任意 fresh 链 INBOUND 行数 = 0；(f) 仓库测试资源/K-docs/plan 文档无历史真实正文（多为单句构造体）。按 brief 明确指示**不编造**邮件。落地语料 = 全部可用真实/现实来信：spike `SAMPLE_INBOUND_EMAIL`（brief：计入 real）+ mockup `rag-knowledge-base.html` DATA.samples 的样例 A（只问报酬）与样例 B（日本教授问全套细节，已脱敏：去 "Dear WuWei,"→"Dear Colleague,"、去机构名 "Qingfei Tech Talent Team"）→ fixtures.json 10 条（3 real / 7 constructed）。真实语料到位后：把正文（脱敏）追加进 `dump_rag_parity_fixtures.py` 的 `REAL_INBOUND_EMAILS` → 重跑脚本 → fixture 与平价测试自动扩展，无需改 Kotlin。
2. **实测基线 row 8 与 spike `SAMPLE_INBOUND_EMAIL` 文本不符（计划 prose 陈旧，机器为准）**。计划表把 row 8 标注为 `SAMPLE_INBOUND_EMAIL` 并登记 7 强制/16 keys/「预筛候选 18 条」；对 spike 内实际 SAMPLE（植物学教授，计划 identity 同提交）机器运行只得 2 强制/9 keys/8 候选。而 mockup `rag-knowledge-base.html` DATA.samples[1]（计划作者自己的追踪数据）保存了真正的「日本教授问全套细节」完整来信：脱敏后机器运行 **逐字复现计划表** —— 7 强制（含 KB-GOV-004/KB-COMP-007）、requested 16、detail=是、033 命中、036 未命中，故 row 8 采用该真实来信（fixture id `real-mockup-japanese-professor-full`），D-3 测试按裁定逐字断言计划表（含 I-9 精确列表）。「预筛候选 18 条」prose 陈旧：作者自己的 mockup trace 就是 14；且本语料下 16-key/7-mandatory 画像的候选上限 = 14（合格事实仅 14 条，剔除 2 条后 12 + 7 强制去重 = 14），18 不可达 —— 断言以机器值 14 为准（fixture 内 033/036/强制/16 keys 与计划表逐字一致，只有该句 prose 不符）。
3. **V112 种子数据缺陷补偿（消费侧别名，非计划修订；D-3 仍是唯一刻意行为偏离 I-11）**。`rag_mandatory_rule` 规则 30/40 的 match_groups 写作 `GOVERNMENT_ORG`（沿自 plan 01 T2 表与 `export_rag_kb_sql._MANDATORY_ROWS`），而 `rag_phrase_group`/`rag_intent_coverage` 的组代码是 `GOVERNMENT_ORGANIZATION` —— 种子中无 `GOVERNMENT_ORG` 短语组，规则 30（→KB-GOV-004）与规则 40 的 org 支路在纯 DB 语义下永不命中，将违背 02 row 8/I-9 与 03 A-1 的「问政府机构必出 KB-GOV-004」。因 V112/export 不在 c2 授权文件内，在消费侧做单点归一：`RagMandatoryResolver.normalizeGroupCode("GOVERNMENT_ORG") → "GOVERNMENT_ORGANIZATION"`（其余恒等；种子日后改名则自动退化为恒等）；`dump_rag_parity_fixtures.py` 的 `mandatory_ids_d3` 同构镜像。行为与脚本逐字一致（脚本无组代码概念）。测试侧：fixture corpus 仍与 DB 行逐字节同义（不改种子）；sample B（问 org）mandatory=7 验证补偿生效。
4. **requested keys 迭代序 = DB 装载序（01 实现权威），非脚本私有常量序（O-note）**。`rag_intent_coverage` 装载为 `group_code, sort_order` 升序（c1 仓储实现），脚本 `requested_coverage_keys` 遍历其私有 `_INTENT_COVERAGE` 常量序。两者对任一来信输出相同，唯一例外是**同时命中 IP 与 CONFIDENTIALITY 两组**的来信：共享键 `confidentiality.materials` 的首次去重位置不同（键**集合**相同；顺序对下游无影响 —— prefilter 只求交集）。脚本常量序无法由 DB 数据表达，故以 01 装载序为准（同 c1 O-1 机器权威原则）；生成器与 Kotlin 同序计算，fixture 与 Kotlin 恒等（平价全绿）。
5. **fixtures.json 用例数 10（3 real + 7 constructed）而非 28**：直接后果 of Deviation 1；8 行实测基线场景全部以 fixture case 存在（rows 1-7 + row 8 真实来信）；控制器裁定批准后不作计划修订登记（本报告 §Deviations 1 为记录）。
6. **环境副作用**：启动并查询了遗留容器 `mailagg-mysql`、`qingfei-phase1-mysql`（只读探测，数据未改）；两者保持运行状态（原为 exited，启动用于探测；无数据写入）。无仓库文件副作用。

## 新鲜度

- Plan identity 复算: YES（46cc5c4 diff 为空；master 92b0519 未变）
- Worktree identity 复算: YES（branch `fast/2026-09-02-execution-order`；HEAD cd2c363；c1 base acb88c1 为 ancestor）
- 必需命令最终代码态新鲜执行: YES（命令 1-7 + I-10 grep；提交后重跑生成器 `git diff --stat` 为空见提交后复核）
- 平价期望值机器来源: YES（fixtures 全部由脚本函数 + D-3 补丁在最终语料上现算，无手抄）
- 提交不含 fast-p 证据: YES（仅 8 授权文件）
