# 00 · 执行顺序（2026-08-26）

基线：`main` @ `f293507`（`git log --oneline -1` → `f293507 feat: add resolved action to inbound detail`）。

## 背景（问题现场，实测复现）

专家来信 4 条 bullet，一键预判产出四段里三段是同一套话术、两处 CTA 撞车、带裸占位符。
逐条追代码后确认：**事实库不缺答案，是关键词召回取不到。**

复现脚本按 `QaFactKeywordMatcher.normalize`（`QaFactSelectionService.kt:589-594`）+
`matchesRule`（`:602-611`，纯 `contains`）逐条比对：

| 专家问的 | 库里已有事实（迁移证据） | 关键词命中 | 判定 |
|---|---|---|---|
| 企业类型与研发缺口 | `Partner company information`（`V38__restructure_qa_categories_and_seed_new_rules.sql`，正文 "…introduce the matched partner company with a profile, website, address…"） | **零** | UNSUPPORTED |
| IP 归属 | `Pre-contract IP boundary`（`V82__split_trust_reply_atomic_facts.sql:132-137`，正文 "Until a contract is signed, nothing you share with us transfers any rights…"） | **零**（库里写 `who owns the`，来信写 "Who owns **IP** arising"） | UNSUPPORTED |
| 报酬结构 | `Funding support`（`V3__seed_qa_rules.sql` id=8 + `V106`） | `compensation` | GROUNDED |
| 官网/顾问名单/周期 | `Programme name and public visibility`（`V105__add_programme_identity_facts.sql:13-24`，正文 "not publicly listed and has no public project website"）+ `Application process`（id=9） | 只中 `timeline` | PARTIAL |

## 三份计划与顺序

| 顺序 | 计划 | 交付的可观察结果 | 依赖 |
|---|---|---|---|
| 1 | `01-llm-fact-retrieval.md` | 上表 3 条零命中项各自绑上正确事实，正文引用库里已有口径 | 无 |
| 2 | `02-unrecognized-asks-and-orphan-keys.md` | 匹配器读不懂的诉求不再静默消失，且不再显示"依据充分" | 建议在 01 之后（复用 01 的 status 口径） |
| 3 | `03-orchestration-and-preview.md` | 四段不再复读、CTA 只有一处、预览不再出现裸占位符 | **必须**在 01 之后（编排表的事实分配依赖 01 的检索结果） |

**01 与 03 不可对调。** 03 的「事实分配表」以 `RequestFactItem.factRuleIds` 为输入；
在 01 落地前，三条无据项的 `factRuleIds` 恒空（证据见 01 的 `## 现状审计`），编排表会是空表。

02 与 03 之间无强依赖，可并行，但 02 会改 `RequestGroundingStatus` 的产生条件，
若与 03 并行必须共用同一份 `QaFactSelectionService`，建议仍按 1→2→3 串行。

## 三份计划共享的硬约束（每份都重复声明为不变量，此处只做索引）

1. **`requestKey` 的哈希输入含 `intentKeys`** — `TrustReplyWorkbenchService.kt:2323` 起的
   `requestKey(...)`，`intents` 读点见 `:2077`。三份计划**都不得**增删 `intentCoverages` 条目、
   改 `intentKey` 字面量或改条目顺序，否则 `trust_reply_workbench_state`（V83）里的
   历史锁定项全部失配、bootstrap 直接 422。（来源: K-request-key-includes-intent-keys）
2. **矩阵路径是运营最终权威，任何计划都不得让机器结果回写它** —
   `QaFactSelectionService.resolveMatrixSelection`（`:195`）在 `:251-255` 把
   `factRuleIds`/`boundRuleIds` 直接设为 `explicitIds`。（来源: K-workbench-matrix-path-is-operator-scoped、
   K-explicit-fact-selection-must-match-request 的 2026-08-24 修订）
3. **`ANSWER_FACTS_VERBATIM` 是逃生舱，三份计划都不得触碰** —
   `allowedHandlings()`（`TrustReplyWorkbenchService.kt:2274-2275`）恒返回 7 种全量；
   `composeVerbatimFactAnswer`（`AiReplyDraftService.kt:1060-1069`）逐字取 `answerBody`，
   `usedLlm=false`、claims 恒空、不调 LLM。
4. **LLM 失败必须可分类、不得静默** —（来源: K-llm-timeout-fallback）

## 验证命令（三份计划共用；各计划的 `## 验证命令` 节引用本表）

> 本项目必须用 **JDK 11（zulu-11）**，裸 `mvn` 会构建失败。来源：`CLAUDE.md:7`「**JDK 11 (zulu-11) is required** — newer JDKs will fail the build」。

```bash
# 全量测试（Kotlin + JS；JS 通过 exec-maven-plugin 的 node-test execution 绑定在 test 阶段，pom.xml:203-217）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test

# 构建
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package

# JS 全量（不经 Maven）
node --test src/test/js/*.test.js

# 空白/换行卫生
git diff --check
```

通过判据：`mvn test` 退出码 0，输出含 `Tests run: N, Failures: 0, Errors: 0, Skipped: *`；
`node --test` 退出码 0，输出末尾 `# fail 0`。
来源：`CLAUDE.md:7-27`（Commands 章节）、`pom.xml:185-250`（exec-maven-plugin）、`verify.sh:1-23`。
