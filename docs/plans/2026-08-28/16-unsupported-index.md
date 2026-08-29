# 16 无依据回答索引：入库放宽、topic 检索与两个回流通道

顺序权威：`10-reply-orchestration-order.md`。依赖 `13-letter-orchestrator.md`（通道 A 的安全性完全建立在 13 的第 1 道校验「来源封闭」上）与 `15-workbench-three-step.md`（运营事实 `op*` 是本计划新的归档主体）。

## 需求描述

**Observable outcome**
1. 运营在工作台确认过的缺口回答，在训练评估通过或线上真实发送后能进入索引——包括**运营改过正文**的那些（现在恰好被排除）。
2. 「无依据回答索引」页可按主题检索，而不是只能倒序翻页。
3. 生成时按主题注入历史回答作为**措辞样例**（通道 A）；模型无法借样例引入新命题，因为 13 的第 1 道校验要求每个 `factId` 都在事实集内。
4. 同主题累计命中 ≥N 次的历史回答进入「待转事实」队列，运营一键生成 QA 事实草稿（通道 B）。

**What must NOT change**
- 索引写入仍只发生在两个既有触发点：训练评估（`AiTrainingEvaluationService.kt:103`）与线上发送后（`PendingMailOperationService.kt:683`）。**不新增写入入口。**
- 归档失败不得阻断主流程——两处调用都在 try/catch 中，失败只记 warn 并返回 `FAILED`/`PARTIAL` 状态（`AiTrainingEvaluationService.kt:114-118`）。
- `documentId = sha256("<sourceType>|<sourceId>|<requestKey>|<versionId>")`（`UnsupportedAnswerIndexService.kt:254-255`）的构成不变——它是幂等键，改了会让存量文档全部变成"新文档"。
- ES 不可用时列表接口返回 `503 UNSUPPORTED_ANSWER_INDEX_UNAVAILABLE`（`UnsupportedAnswerIndexController.kt:31-35`）的降级行为不变。
- 索引内容**永远不作为事实来源**进入生成——只做措辞样例（通道 A）或人工转化的原料（通道 B）。

**Out of scope**
- 把索引直接接进 grounded 链路作为内容来源（这是本计划显式拒绝的方向，理由见 I-1）。
- 向量检索 / 语义相似度。本计划只做 `topic` 的 keyword 精确过滤 + 时间倒序。
- 自动把历史回答转成 QA 事实（通道 B 必须人工点确认）。

## 关键不变量

### Invariant I-1: 索引供措辞，不供事实
- Rule: 索引内容进入生成链路时，只能作为**风格样例**出现在提示词里，且提示词必须明确「只学句式与语气，不得引用其中任何事实」。索引条目**不得**被包装成 `PlanFact` 进入 13 的事实集，因此它们没有合法 `factId`。
- 保障机制（不靠模型自觉）：13 的第 1 道校验「来源封闭」要求 `paragraphs[].factIds` 的每个 id 都在输入 `paragraphPlan` 的并集内。样例里的命题无 id 可挂 → 整段被打回重试。
- 三条理由（写进代码注释与提示词，供后人不要"优化"掉）：
  1. 破坏可追溯——审计链是 claim → sourceIds → `qa_rule`，历史回复没有 rule id。
  2. 复活旧口径——发出时正确的信可能含已废弃表述（如 `labor contract`、"约 10% 成功率"、"PhD team"），索引不带时效。
  3. 自我强化漂移——AI 生成 → 被采纳 → 入索引 → 被引用 → 再入索引，无外部真值锚定。
- Violation consequence: 一封信里出现一句谁也追不到出处的对外承诺。对"对方正在判断是不是诈骗"的场景，这是最危险的失效模式。
- 来源: original

### Invariant I-2: ES mapping 是 `dynamic: strict`，加字段必须先改 mapping
- Rule: `src/main/resources/es/trust_reply_unsupported_answer_v1.json` 的 mappings 是 `"dynamic": "strict"`。**在 `documentNode` 里新增一个字段而不先更新线上 mapping，写入会被 ES 直接拒绝**，归档从"能用"变成"全失败"。
- 且 `bootstrapIndex()`（`UnsupportedAnswerIndexService.kt:101-121`）只在 HEAD 返回 404 时创建索引（`:108-110`），**对已存在的索引从不更新 mapping**——改 JSON 文件对存量索引零效果。
- 因此本计划新增 `topic` / `finalParagraphText` / `editedByOperator` 三个字段时，必须二选一并在实现说明中写明所选方案：
  - **方案 A（推荐）**：新增 `bootstrapIndex()` 的 mapping 补丁步骤——HEAD 命中时额外 `PUT <index>/_mapping` 提交新 properties（向 strict mapping 追加 properties 是允许的），失败只记 warn 不阻断启动。
  - **方案 B**：切 `v2` 索引 + reindex，`ElasticsearchProperties.unsupportedAnswerIndexName`（`:15`，默认 `"trust_reply_unsupported_answer_v1"`）改默认值。
- Violation consequence: 上线后所有归档静默失败（只在 warn 日志里），索引永远是空的——与现状相比毫无改善且更难发现。
- 来源: original（`es/trust_reply_unsupported_answer_v1.json` 与 `UnsupportedAnswerIndexService.kt:101-121` 实读）

### Invariant I-3: `requestText` / `answerText` 在 v1 mapping 里是 `index: false`，不可检索
- Rule: 现有 mapping 中 `requestText`、`operatorInstruction`、`answerText` 三个 text 字段都带 `"index": false`——**它们不进倒排索引，永远搜不了**。本计划的「按主题检索」只能靠新增的 `topic`（`keyword` 类型）实现，不得声称支持按正文检索。
- 若将来要按正文检索，必须走方案 B 的 v2 + reindex（改既有字段的 `index` 设置不能就地生效）。
- 来源: original（mapping 文件实读）

### Invariant I-4: 入库门槛只放宽，不放弃「已被人认可」这个前提
- Rule: 两个触发点保持不变——训练侧仍要求 `rating == MEETS_EXPECTATION`（`AiTrainingEvaluationService.kt:88`），线上侧仍要求真实发送成功。放宽的只有**样本形态**：
  - `validate()`（`UnsupportedAnswerIndexService.kt:411-413`）的 `handling != "ANSWER_FROM_OPERATOR_INPUT" || generationKind != "AI_GENERATED"` 硬拒改为允许集合 `{ANSWER_FROM_OPERATOR_INPUT, ANSWER_EVIDENCE_WITH_OPERATOR_INPUT, ANSWER_SUPPORTED_PART, ACKNOWLEDGE_PENDING}` × `{AI_GENERATED, SAFE_TEMPLATE}`。
  - `operatorInstruction` 从必填降为可选：`bounded()`（`:424`）要求 `isNotBlank()`，而 `operatorInstruction` 是必检项（`:418`）；改为「非空时校验长度，空时放行」，同时 `AiTrainingEvaluationService.kt:92` 的 `item.operatorInstruction.isNotBlank()` 过滤条件一并去掉。
  - 线上侧去掉「正文一字未改才归档」：`PendingMailOperationService.kt:675-677` 的 `if (assembled.rawDraftText != rawTemplate || assembled.renderedDraftText != finalTextBody) return failedArchive(...)` 改为**照常归档并置 `editedByOperator = true`**。
- Violation consequence（保持不变的那一半）：若连"被人认可"也放弃，索引会灌满未经审核的模型输出，通道 B 的转化质量崩掉。
- 来源: original（三处实读）

### Invariant I-5: `CANDIDATE` / `ACTIVE` 改为按「是否已转化」区分
- Rule: 现状是按来源分——训练写 `CANDIDATE`、线上写 `ACTIVE`（`:346-347` 与 `:365-366`），两者之间**没有任何晋升路径**，语义为零。改为：
  - `CANDIDATE` = 尚未转化为 QA 事实，**只能用于通道 A 的措辞样例**；
  - `ACTIVE` = 已由运营转化为 QA 事实（通道 B 完成），此后**不再作为措辞样例注入**（它的内容已由正规事实承载）。
  - 来源仍由 `sourceMode`（TRAINING / LIVE）表达，不再用 status 兼任。
- `validate()`（`:428-435`）中把 status 与 sourceMode 绑死的两个组合判定必须相应放开。
- 来源: original

### Invariant I-6: 列表侧的空值丢弃是潜在缺陷，修它但不要把它当主因
- Rule: `parseListItem`（`:294-300`）对 `LIST_SOURCE_FIELDS`（status / sourceMode / requestText / operatorInstruction / answerText / model / createdAt）逐个查 `isBlank` 即整条 `return null`，只记 warn；而 `total` 取自 ES 的 `track_total_hits`（`:238`）→ 会出现「共 N 条」但表格 0 行。
- **但写入侧 `validate()` 已保证这 7 个字段非空**，所以此路只对 validate 上线前写入或旁路直写 ES 的脏文档生效。本计划把 `operatorInstruction` 降为可选后，**它会变成真正的主因**——必须同时把该字段从空值丢弃判定中移除，改为渲染为 `—`。
- 排查顺序（写进实现说明）：先看 `total` 是否为 0（门槛问题），`total > 0` 且 items 为空才是这条。
- 来源: original

## 现状审计

### ES 索引 `trust_reply_unsupported_answer_v1`
- **Mapping**（`src/main/resources/es/trust_reply_unsupported_answer_v1.json`）：`dynamic: strict`；23 个 properties；`requestText` / `operatorInstruction` / `answerText` 为 `{"type":"text","index":false}`；其余为 keyword / long / integer / date。
- 索引名来自 `ElasticsearchProperties.unsupportedAnswerIndexName`（`:15`，默认 `trust_reply_unsupported_answer_v1`）。
- **Write paths**（全仓仅 2 处）：
  1. `AiTrainingEvaluationService.kt:103` `archiveCanonicalVersions(...)` —— 前置过滤 `:88-95`：`rating == MEETS_EXPECTATION` 且 `handling == ANSWER_FROM_OPERATOR_INPUT` 且 `generationKind == AI_GENERATED` 且 `requestText/operatorInstruction/answerText` 三者非空。
  2. `PendingMailOperationService.kt:683` `archiveLiveCanonicalVersions(...)` —— 前置 `:675-677` 要求发送正文与 assembly 产物**逐字相等**，否则 `failedArchive`。
  两者最终都进 `create()`（`:124-156`）→ `PUT <index>/_create/<id>`，409 视为 `ALREADY_EXISTS` 并计入成功。
- **Read paths**（全仓仅 1 处）：`UnsupportedAnswerIndexController.list`（`:19-36`）→ `service.list`（`:219-247`）→ `listQuery`（`:262-278`）：`match_all`（或 `term` on `sourceMode`）+ `sort` by `createdAt desc, versionId asc` + `_source` 限定为 7 个 `LIST_SOURCE_FIELDS`。
  **生成链路对该服务的引用为 0**——`grep -rn "UnsupportedAnswerIndexService" src/main/kotlin` 只命中 controller、两个写入方与自身。
- **Interaction points**：
  - **IP-1**：write × mapping（I-2）——新增字段必须先改 mapping，否则 strict 拒绝。
  - **IP-2**：write 的 `validate()` × read 的 `parseListItem()`（I-6）——两处对同一批字段各有一套非空要求，放宽写入侧必须同步放宽读取侧。
  - **IP-3**：本计划新增的通道 A（提示词注入）× 13 的第 1 道校验（I-1）——安全性完全依赖后者，13 未上线时**不得启用通道 A**。

### 前端
「无依据回答索引」页状态在 `app.js:104-112`（`unsupportedAnswers*` 八个字段）；渲染 `:3426-3451`（空态文案「暂无已确认的无依据回答」）；分页 `:3411-3418`；加载 `:3457-3466`。本计划新增 topic 过滤器需在此处落位。

### 前端样式盘点
- 可复用 class：该页现有表格与分页复用全局 `.list-pager` / `.muted-cell` 等；topic 过滤器复用既有筛选下拉的 class（与 `sourceMode` 过滤器同款，位置见 `app.js:3466` 附近的 `unsupportedAnswersSourceMode` 用法）。
- **本计划不新增任何 class**：topic 过滤器与 `sourceMode` 过滤器并排，复用其 DOM 骨架与样式。
- 改动前基线：空态单元格文案为 `暂无已确认的无依据回答`，`colspan="6"`。

## 实现方案

### T-1：mapping 演进（I-2 / I-3）
按 I-2 的方案 A 实现：`bootstrapIndex()` 在 HEAD 成功分支中追加一次 `PUT <index>/_mapping`，提交三个新 properties：
```json
{"properties":{"topic":{"type":"keyword"},"finalParagraphText":{"type":"text","index":false},"editedByOperator":{"type":"boolean"}}}
```
同步更新 `es/trust_reply_unsupported_answer_v1.json`（供新环境首建）。失败只记 warn，不阻断启动。**实现说明中必须写明选择了方案 A 及理由。**

### T-2：入库门槛放宽（I-4 / I-5）
- `validate()`（`:410-435`）：handling / generationKind 改为允许集合；`operatorInstruction` 的非空要求改为「非空时校验长度」；status × sourceMode 的绑死组合按 I-5 放开。
- `AiTrainingEvaluationService.kt:88-95`：去掉 `handling == ANSWER_FROM_OPERATOR_INPUT` 与 `operatorInstruction.isNotBlank()` 两个过滤条件，保留 `rating == MEETS_EXPECTATION` 与 `answerText.isNotBlank()`。
- `PendingMailOperationService.kt:675-677`：不再 `failedArchive`，改为置 `editedByOperator = true` 继续归档。
- `UnsupportedAnswerIndexDocument` 新增 `topic: String`、`finalParagraphText: String`、`editedByOperator: Boolean`；`documentNode`（`:311-336`）同步写出。**`documentId` 的四项输入不动**（What must NOT change）。

### T-3：列表侧修复与 topic 过滤（I-3 / I-6）
- `LIST_SOURCE_FIELDS` 增加 `topic`、`editedByOperator`；`parseListItem` 的空值丢弃判定**排除** `operatorInstruction`（空时渲染 `—`）。
- `listQuery` 支持可选 `topic` 的 `term` 过滤，与 `sourceMode` 组合为 `bool.filter`。
- Controller 增加 `topic` 查询参数，非法值按既有 `invalidRequest()` 返回 400。
- 前端在 `sourceMode` 过滤器旁增加 topic 下拉（复用其 DOM 与样式）。

### T-4：通道 A —— 措辞样例注入（I-1 / IP-3）
在 13 的提示词构造处，按当前信的 `topicOrder` 各取最近 N 条（建议 N=2）`status = CANDIDATE` 的 `finalParagraphText`，作为 few-shot 样例注入，并附逐字提示：「以下段落仅供参考句式、语气与过渡方式，**不得引用其中任何事实或数字**；每个段落的 factIds 必须来自本次提供的事实清单。」
**开关**：以配置项控制，默认关闭；13 未上线的环境必须保持关闭（IP-3）。

### T-5：通道 B —— 待转事实队列（I-5）
新增列表视图「待转事实」：按 `topic` 聚合 `status = CANDIDATE` 的条目，命中次数 ≥ 阈值（建议 3）的主题置顶。运营点「生成 QA 事实草稿」→ 打开 QA 规则新建表单并预填 `answer_body`（取该主题命中次数最多的 `answerText`）与建议 coverage key。运营保存后，把这些条目的 `status` 更新为 `ACTIVE`。
**不自动创建规则**——必须经运营保存（Out of scope 已声明）。

### T-6：测试
1. `validate()` 的允许集合边界：四种 handling × 两种 generationKind 全部通过；集合外的 `OMIT` / `ANSWER_WITH_EVIDENCE` 仍拒绝。
2. `operatorInstruction` 为空时 `validate()` 通过；超长时仍拒绝。
3. `parseListItem` 在 `operatorInstruction` 为空时**返回条目**（而非 null），其余字段为空时仍返回 null。
4. `documentId` 在新增三个字段前后对同一条输入产出**相同哈希**（幂等键未变）。
5. 线上侧：正文被编辑时仍归档且 `editedByOperator == true`。
6. mapping 补丁：`bootstrapIndex()` 在 HEAD 成功时发出一次 `PUT _mapping`（以 RestTemplate 桩断言）。

## 变更文件清单

| # | 文件 | 动作 |
|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/UnsupportedAnswerIndexService.kt` | 修改（T-1、T-2、T-3） |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/llm/controller/UnsupportedAnswerIndexController.kt` | 修改（T-3 的 topic 参数、T-5 的队列端点） |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiTrainingEvaluationService.kt` | 修改（T-2 过滤条件） |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt` | 修改（T-2 去掉逐字相等门槛） |
| 5 | `src/main/resources/es/trust_reply_unsupported_answer_v1.json` | 修改（T-1 新增三个 properties） |
| 6 | `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyLetterOrchestrator.kt` | 修改（T-4 样例注入；A3：原清单误标 AiReplyDraftService.kt，编排提示词构造点为 c4 实现的 `buildPrompt`，见修订说明） |
| 7 | `src/main/resources/static/app.js` | 修改（T-3 topic 过滤器、T-5 待转事实视图） |
| 8 | `src/test/kotlin/com/weibo/talentintroduction/llm/service/UnsupportedAnswerIndexServiceTest.kt` | 新增或修改（T-6.1～T-6.4、T-6.6） |
| 9 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTest.kt` | 修改（T-6.5） |
| 10 | `src/test/js/aiTrainingUnsupportedAnswers.test.js` | 修改（topic 过滤器与空 `operatorInstruction` 渲染为 `—`） |
| 11 | `src/test/kotlin/com/weibo/talentintroduction/llm/controller/UnsupportedAnswerIndexApiTest.kt` | 修改（A3：`mapping is strict with only V1 fields and non-searchable bodies` 的字段集断言从 23 更新为 26） |

合计 11 个文件（**超过原上限，A3 修订后豁免**），2 个子系统（索引服务链路 / 静态前端）。

> **修订 A3（2026-08-28 需求方批准）**：
> 1. **T-4 落点修正**：通道 A 的样例注入点是 13 计划的编排提示词构造 `AiReplyLetterOrchestrator.buildPrompt`（c4 实现，`:457`，private），不是 `AiReplyDraftService.kt`（它只构造逐条提示词，且计划 13 明令不得因编排改动它）。清单 #6 相应替换。若接线需要（编排器新增样例源与默认关闭开关的构造参数），允许同步改动 `AiReplyLetterCloser.kt`——仅限构造器传参与调用点，不得改变兜底行为。
> 2. **mapping 测试授权**：T-1 要求 `es/trust_reply_unsupported_answer_v1.json` 新增三个 properties，会打破既有测试 `UnsupportedAnswerIndexApiTest.kt:94`（`mapping is strict with only V1 fields and non-searchable bodies`，断言恰 23 个 V1 字段）。该测试字段集断言须更新为 26 个字段，属纯测试授权。

测试落点的依据（实测）：`src/test/js/aiTrainingUnsupportedAnswers.test.js`（197 行，2 处 `unsupported-answers`）是索引页的现有归属地。

> **规模预警**：T-4（通道 A）与 T-5（通道 B）都依赖前面的 T-1～T-3 落地。若执行中发现 9 个文件放不下，**优先把 T-4 与 T-5 拆成 `17-index-recirculation.md`**，先交付「入库放宽 + topic 检索」这半份——它本身就能让索引从空变为可用。

## 验证命令

> 本项目是 Kotlin + Spring Boot 2.7 (Java 11) Maven 工程，**必须用 JDK 11 (zulu-11)**，裸 `mvn` 会构建失败。

```bash
# 全量测试（回归门禁）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test

# 本计划相关测试类
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=UnsupportedAnswerIndexServiceTest,PendingMailOperationServiceTest

# 构建
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package

# 前端 JS 用例（node:test）
node --test src/test/js/aiTrainingUnsupportedAnswers.test.js

# 前端 JS 全量回归
node --test src/test/js/*.test.js

# 空白/换行卫生
git diff --check
```

> 线上核对（需求方执行，非 CI 门禁）：
> ```bash
> curl -s 'http://<服务地址>/api/ai-training/unsupported-answers?page=0&size=20'
> ```
> 上线前记录 `total` 与 `items.length`；上线后重跑，用于 A-1 的对比基线。


> **JS 门禁与 `mvn test` 的关系**：`pom.xml:190-201` 的 exec-maven-plugin 把
> `bash -lc 'node --test src/test/js/*.test.js'` 绑在 `test` phase，因此 `mvn test`
> 名义上覆盖 JS 用例；但该 execution 带 `<skip>${skipNodeTests}</skip>` 而
> `skipNodeTests` 在 `pom.xml:19-25` 的 `<properties>` 中**未定义**（K-js-test-invocation-surface
> 记为推断）。**本计划的权威门禁是上面的 `node --test <file>` 单跑命令**，`mvn test`
> 作为全量回归另列。首次执行须确认 `mvn test` 输出里出现 `node --test` 记录。
> `verify.sh` 只跑 `normalizeDiscoveryResultSummary.test.js` 一个文件，**不可用作前端门禁**。

通过判据：`mvn test` 退出码 0，输出 `Tests run: N, Failures: 0, Errors: 0`。
来源：Maven 命令取自项目根 `CLAUDE.md` 的 `## Commands`（逐字照抄）；JS 命令取自 `pom.xml:190-201` 的 exec 绑定与 `docs/knowledge/frontend/K-js-test-invocation-surface.md`，目标文件名经 `ls src/test/js/` 实测确认。

## 验收标准

- **I-1**：`grep -rn "UnsupportedAnswerIndexService" src/main/kotlin/.../llm/service/AiReplyGroundedContentPlanner.kt` 无结果（索引不进事实集）；T-4 注入的样例在提示词中位于独立小节且带禁止引用事实的逐字提示。
- **I-2**：T-6.6 通过；`es/trust_reply_unsupported_answer_v1.json` 的 `dynamic` 仍为 `strict` 且新增三个 properties；实现说明中写明选了方案 A。
- **I-3**：mapping 中 `topic` 为 `keyword`；`listQuery` 只对 `topic` / `sourceMode` 做 term 过滤，不含任何对 `requestText` / `answerText` 的查询子句。
- **I-4**：T-6.1、T-6.2、T-6.5 通过；`AiTrainingEvaluationService.kt` 中 `rating == MEETS_EXPECTATION` 仍在。
- **I-5**：`validate()` 中不再存在 status 与 sourceMode 的绑死组合；测试断言 TRAINING + ACTIVE 与 LIVE + CANDIDATE 均合法。
- **I-6**：T-6.3 通过。
- **What must NOT change（documentId）**：T-6.4 通过。
- **回归**：执行「验证命令」节的全量测试命令通过。

## 人工验收清单

### A-1: 索引从空变为有内容
- 前置条件：上线前先跑一次 `curl .../unsupported-answers?page=0&size=20` 并记录 `total`。
- 操作步骤：
  1. 上线后，在 AI 训练里对一封含缺口的来信走完整流程：选「按回答说明生成」→ 生成 → 评估为「符合预期」
  2. 打开「无依据回答索引」页
- 预期结果：页面出现刚才那条记录；`total` 比上线前基线大 1。若上线前基线是 0 且现在仍是 0，说明入库门槛仍有未放开的条件，属缺陷。
- 覆盖：observable outcome 1；I-4

### A-2: 运营改过正文的样本也能入库
- 前置条件：一封已整合、可发送的来信。
- 操作步骤：
  1. 在发送前手工编辑正文（改几个词）
  2. 正常发送
  3. 打开「无依据回答索引」页
- 预期结果：该条进入索引，且标记为「运营已编辑」。**这是本计划最关键的一条**——改造前这类样本恰好被排除。
- 覆盖：observable outcome 1；I-4

### A-3: 「回答说明」为空也能入库并正常显示
- 前置条件：一条 handling 为「回答有依据部分」（不要求回答说明）的摘要。
- 操作步骤：走完评估流程后打开索引页。
- 预期结果：该条出现在列表中，「回答说明」列显示 `—` 而非整行消失；分页显示的「共 N 条」与实际渲染行数一致。
- 覆盖：observable outcome 1；I-6；IP-2

### A-4: 按主题检索
- 前置条件：索引中至少有两个不同 topic 的条目。
- 操作步骤：在索引页的 topic 下拉里选一个主题。
- 预期结果：只显示该主题的条目；「共 N 条」随之变化；清空下拉恢复全部。
- 覆盖：observable outcome 2；I-3

### A-5: 措辞样例不带出事实
- 前置条件：13 已上线；通道 A 开关打开；某主题下已有历史回答，其中含一个具体数字（如金额或月份）。
- 操作步骤：对一封新来信点「一键预判」，通读生成正文。
- 预期结果：正文的句式与语气可能与历史回答相似，但**不出现**历史回答里那个数字（除非该数字同时也在本次的事实集里）。若出现，说明 13 的第 1 道校验没兜住，属 P1。
- 覆盖：observable outcome 3；I-1；IP-3

### A-6: 待转事实队列可用
- 前置条件：某主题下 `CANDIDATE` 条目 ≥ 阈值。
- 操作步骤：打开「待转事实」视图，点该主题的「生成 QA 事实草稿」。
- 预期结果：打开 QA 规则新建表单且已预填正文与建议 coverage key；**保存前不产生任何 QA 规则**；保存后该主题的相关条目变为 `ACTIVE` 且不再出现在待转队列。
- 覆盖：observable outcome 4；I-5

### A-7（回归）: 归档失败不阻断主流程
- 前置条件：临时把 ES 地址配错或停掉 ES。
- 操作步骤：走一次训练评估、走一次线上发送。
- 预期结果：评估正常保存、邮件正常发出；只在日志里出现归档 warn，界面上归档状态显示为失败/部分成功，**不抛错、不回滚**。
- 覆盖：What must NOT change 第 2 项

### A-8（回归）: ES 不可用时列表降级
- 前置条件：ES 不可达。
- 操作步骤：打开「无依据回答索引」页。
- 预期结果：显示「无依据回答索引暂不可用」，接口返回 503 与 `UNSUPPORTED_ANSWER_INDEX_UNAVAILABLE`；页面其余部分不崩。
- 覆盖：What must NOT change 第 4 项

### A-9（回归）: 同一条重复归档不产生第二份文档
- 前置条件：一条已归档的记录。
- 操作步骤：对同一次评估重复触发一次归档（重复点评估保存，或重发同一封信）。
- 预期结果：索引里仍只有一条（`create` 的 409 被当作 `ALREADY_EXISTS` 计入成功）；`total` 不增加。
- 覆盖：What must NOT change 第 3 项；T-6.4
