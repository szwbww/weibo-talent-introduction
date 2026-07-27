# fix-1：真实邮件 intent fixture 与匹配归一化复验

## 原计划 / 子计划引用

- 子计划：`docs/plans/2026-07-16/ai-reply-intent-fixture-normalization.md`
- 实现提交：`933d3ba6 fix: normalize AI reply intent matching for real fixtures`

## 约束摘录

- I-1：URL/query 只在匹配副本中屏蔽；原 request 文本不得改写或形成噪声 intent。
- I-2：Pracheta Janmeda 原始七组问题必须精确得到 14 个 intent；第 4 组只能是 `researcher.selection`、`enterprise.matching`。
- I-3：catalog 是 intent/title/coverage 的唯一 authority，完整 intent set 决定固定 group title。
- I-4：intent 命中不等于有依据；coverage/readiness 与研究双证据语义不变。
- I-5：普通 `selected` 命中，`preselected` 与 URL query 不命中；既有 alias 语义不回归。

## 修正记录表

| ID | 严重度 | 触发频率 | 问题 | 证据 |
|---|---|---|---|---|
| P1-1 | P1 | 每次处理原始问法 `How researchers are selected and matched with enterprise projects` | `enterprise projects` 的通用 alias 与 `selected`、`matched` 同时命中，产生 3 个 intent；固定组标题也退化为 `Enterprise project types`。端到端 fixture 被改写为 `enterprise partners`，因此 353 JS / 1751 JVM 全绿仍未覆盖真实输入。 | `AiReplyIntentCatalog.kt:95-100,132-143`；`AiReplyIntentCatalog.kt:77-84`；`AiReplyDraftServiceTest.kt:3246-3264` |

## 根因

匹配器按整句独立扫描全部 alias，但没有区分：

1. 第 1 组的“询问团队管理哪些 enterprise projects”，其语义是项目类型；
2. 第 4 组的“researchers are selected and matched with enterprise projects”，其中 `enterprise projects` 是 matching 的宾语，不是另一个项目类型问题。

测试又主动把原始第 4 问改成 `enterprise partners`，绕开冲突，违反“完整原始专家邮件 fixture”验收前提。

## 修复规格

### P1-1：对 selection + matching 复合句做窄范围语境消歧

文件：

- `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyIntentCatalog.kt`
- `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyIntentCatalogTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftServiceTest.kt`

要求：

1. 保留 `enterprise.project_types` 与现有 aliases，确保第 1 组及独立“types of enterprise projects”仍命中。
2. 初步匹配同时包含 `researcher.selection` 与 `enterprise.matching`，且 `enterprise.project_types` 只由泛化 `enterprise project(s)` 命中时，将其视为 matching 宾语并移除。
3. 若句子含明确项目类型问法，如 `types of projects`、`types of enterprise`、`project type(s)`，不得移除 `enterprise.project_types`。
4. definitions 顺序、coverage keys、group title 表与 request 原文保持不变；不得修改 `QaRequestExtractor`、prompt、QA 数据或 migration。
5. 不允许针对专家姓名或整封邮件做硬编码；消歧必须只依赖当前 request group 的 canonical 文本与已命中 intent。

## 测试要求

### Catalog

- 精确句 `How researchers are selected and matched with enterprise projects` 只返回：
  `researcher.selection`, `enterprise.matching`。
- 上述 intent set 的标题必须为 `Selection and enterprise matching`。
- `What types of enterprise projects do you manage?` 仍返回 `enterprise.project_types`。
- 含明确项目类型问法且同时提及 selection/matching 的混合句，应保留 3 个 intent，防止过度消歧。

### DraftService 端到端

- 把 `janmedaMail` 替换为原计划所述原始专家邮件，不再用 `enterprise partners` 规避冲突。
- 断言 extractor 得到 7 个原文 groups、总计 14 个 intent；第 4 组精确为 2 个 intent，并返回固定标题。
- 保留 programme、hyphenated IP、URL 屏蔽、单组 coverage 降级及研究双证据断言。

## 当前状态（修前）

- JVM：PASS — `mvn -q test`，1751 tests，0 failures，0 errors，4 skipped。
- JS：PASS — 353 tests，0 failures。
- 语法：PASS — `node --check src/main/resources/static/app.js`。
- 功能验收：FAIL — 原始第 4 问得到 3 个 intent，不满足 I-2/I-3。

## 合规审计

| Constraint | Verdict | Evidence |
|---|---|---|
| I-1 URL/原文边界 | ✅ | catalog canonicalization 屏蔽 URL；DraftService extractor fixture 未产生 URL group。 |
| I-2 精确 7 组 / 14 intents | ❌ P1-1 | 原始第 4 问额外命中 `enterprise.project_types`；测试 fixture 被改写。 |
| I-3 catalog 与固定标题 | ❌ P1-1 | 三 key 集合不属于任何 group title，回退为 definitions 中首个 `Enterprise project types`。 |
| I-4 coverage/readiness | ✅ | nested request→intent→rule 取证及缺证降级测试通过；未改 QA 数据。 |
| I-5 边界误命中 | ✅ | URL `selected=true`、`preselected`、普通 `selected` 测试通过。 |
| Deleted code | ✅ | 无删除要求。 |
| No extras | ✅ | 实现提交只涉及原计划 3 个文件。 |

### 语义完整性检查

- Accumulation check：✅ 无时间窗口或累计状态。
- State machine check：✅ N/A。
- Cross-plan check：❌ 真实 fixture 与 catalog alias 的语境冲突被测试改写掩盖；修复必须同时覆盖训练模拟和收发邮件共用的 DraftService 路径。
