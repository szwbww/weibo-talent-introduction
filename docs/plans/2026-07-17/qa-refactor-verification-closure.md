# QA 重构复验收口修复计划

## 需求描述

完成六个 QA 重构子方案的复验收口：发布记录保存 V78 在目标环境的真实 pre/post 门禁证据；过时的 materializer 测试改为验证“自然段、无固定标题”的现行契约。两项都完成后，全量构建应恢复为零失败。

必须不变：

- 不修改 V78、发布门禁脚本、`AiReplyGroundedDraftMaterializer`、`AiReplyPointByPointComposer` 或任何生产代码。
- 不直接手工重跑、回滚或改写已由 Flyway 管理的 V78；数据库变更只能来自已授权的正常应用部署。
- 不伪造、补写或倒推 pre/post 输出；凭据、密码、完整 JDBC URL 不得进入仓库。
- Grounded 回复继续保留答案顺序、事实边界、服务端结构校验和自然段表达。

Out of scope：新增 QA 功能；调整 prompt/事实选择/风险校验；修改项目介绍邮件内容；重构其他测试；决定具体发布时间；处理与本次全量测试无关的既有日志噪声。

## 关键不变量

### Invariant I-1：门禁证据必须来自真实执行
- Rule：发布记录中的环境、UTC 时间、部署制品标识、pre/post 原始计数和 PASS/FAIL 必须来自目标环境实际执行；示例、待填写文本或人工推算均不能关闭 P1。记录不得包含密码、token 或完整 JDBC 凭据。
- Applies to：`qa-refactor-01-template-boundary-release-gate-record.md` 的 pre/post 执行记录。
- Violation consequence：无法证明 V78 没有跳过悬空引用或错误统一 QA 变体。
- 来源：K-release-gate-evidence-not-example。

### Invariant I-2：V78 门禁按状态顺序 fail closed
- Rule：执行人先只读查询 `flyway_schema_history`。若 V78 未应用，必须先取得 pre=`0/1/0`，再通过正常部署应用 V78，最后取得 post `qa_rule_blocks=0`；任一步失败即停止。若 V78 已应用且没有历史 pre 输出，不得补造 pre，计划状态保持阻塞并交由人工决定是否接受缺失证据。
- Applies to：门禁脚本 pre/post、V78 部署窗口、执行记录。
- Violation consequence：迁移已发生后无法还原当时是否存在变体或悬空引用，P1 被错误关闭。
- 来源：原计划 I-2；K-qa-fact-refactor-template-boundary；K-release-gate-evidence-not-example。

### Invariant I-3：测试修正不得改变生产语义
- Rule：本计划只修改过时测试断言；不得为迁就测试恢复固定编号、章节标题或 cross-reference，不得改变 materializer JSON 校验。
- Applies to：`AiReplyGroundedDraftMaterializerTest.kt`。
- Violation consequence：回复重新出现明显模板感，违反减少 AI 感的产品目标。
- 来源：原计划 04 I-7；K-grounded-natural-structure-server-gate。

### Invariant I-4：自然结构测试同时验证正向内容与负向结构
- Rule：合法 per-intent JSON 必须 `valid=true`，两个答案均存在且顺序与 request index 一致；输出不得包含 `1. Financial arrangements`、`2. Deliverables`、JSON 字段、intent key、状态标签或 rule ID。
- Applies to：materializer 的合法 JSON 测试。
- Violation consequence：测试可能只删除旧断言，却没有保护当前自然结构契约。
- 来源：原计划 04 I-7；K-grounded-natural-structure-server-gate。

### Invariant I-5：代码完成与发布证据分别关闭
- Rule：测试全绿只关闭测试契约项；只有真实 pre/post 证据完整且数值满足门禁，才能关闭发布项。最终复验必须同时满足两项。
- Applies to：执行状态、修复交付、后续 fix-v。
- Violation consequence：再次把“脚本已开发”误报为“门禁已执行”。
- 来源：K-release-gate-evidence-not-example。

## 现状审计

### `mail_compose_template_block` / `content_variant` / `qa_rule`（本计划仅门禁读取）

- Schema/mapping：
  - `mail_compose_template_block` 由 V61 创建，`block_type VARCHAR(30)`、`ref_id BIGINT NULL`、`custom_text TEXT NULL`；`ref_id` 无数据库外键。
  - `content_variant` 由 V67 创建，门禁只统计 `owner_type='QA_RULE'`。
  - `qa_rule.reply_body` 是 V78 的快照来源；本计划不修改 QA 数据。
- Write paths：
  1. `V62__unify_mail_templates.sql` — 插入系统模板块。
  2. `V71__update_material_reminder_template.sql` — 更新/插入材料提醒模板块。
  3. `MailComposeTemplateService.saveBlocks()` 与 `MailComposeTemplateRepository.deleteBlocksByTemplateId()` — 运行时保存、删除模板块。
  4. `V78__decouple_compose_templates_from_qa_rule.sql:4-10` — 将有效 `QA_RULE` 块原位更新为 `CUSTOM_TEXT`。
  5. `ContentVariantService`/repository — 运行时维护 `content_variant`；门禁前要求 QA_RULE owner 数为 0。
  6. `QaRuleManagementService` 与历史 Flyway migration — 维护 `qa_rule`；V78 只读取 `reply_body`。
- Read paths：
  1. `qa-refactor-01-template-boundary-release-gate.sh:49-51` — 读取 QA 变体数、QA 块数和悬空引用数。
  2. `V78__decouple_compose_templates_from_qa_rule.sql:4-10` — join 读取规则正文并写入模板块。
  3. `MailComposeTemplateService.resolveBlocks()` — 部署前后读取块并渲染模板。
- Interaction points：pre 门禁读取三表 → 正常 Flyway 部署写模板块 → post 门禁读取结果 → 执行记录供 fix-v 审计。

### 发布门禁记录文件

- Write path：发布执行人将脚本真实 stdout、环境标识、制品标识、执行人和 UTC 时间写入 `qa-refactor-01-template-boundary-release-gate-record.md`。
- Read path：fix-v 读取该文件判断原 fix-1 P1 是否关闭。
- 当前缺口：文件 `:43-65` 仍为“发布窗口填写”和“示例（须替换）”，没有真实执行证据。
- Interaction point：脚本 exit code/输出必须原样对应记录中的 PASS/FAIL，禁止只摘录期望值。

### Grounded materializer 测试契约

- Production producer：
  1. `AiReplyGroundedDraftMaterializer.kt:30-34` 严格解析 JSON 后调用 composer。
  2. `AiReplyPointByPointComposer.kt:16-35` 按 request 顺序收集答案。
  3. `AiReplyPointByPointComposer.kt:82-105` 组装称呼、一次致谢、自然段和结束语，不生成固定标题。
- Test reader：`AiReplyGroundedDraftMaterializerTest.kt:59-69` 仍要求 `1. Financial arrangements` 与 `2. Deliverables`，与当前生产契约相反。
- 交叉验证：`AiReplyPointByPointComposerTest.kt:69,200` 已明确断言固定标题不存在；失败测试是遗漏的旧契约，而非生产回归。
- Interaction point：合法 JSON → materializer → natural composer → 测试应验证答案顺序和无内部结构泄漏。

## 实现方案

### T0：执行前状态快照

- 文件：无修改。
- 记录当前工作树状态和本计划两个目标文件的 hash，避免覆盖六个方案已有改动。
- 只读查询目标环境 `flyway_schema_history` 中 version `78` 的 `version/description/success/installed_on`。
- 若 V78 已成功应用且没有历史 pre 原始输出，立即按 I-2 停止发布证据任务；不得在当前库运行伪 pre，也不得回滚数据制造 pre 状态。
- 遵守 I-1、I-2、I-5。

### T1：修正过时的自然结构测试

- 文件：`src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyGroundedDraftMaterializerTest.kt`。
- 将测试名改为 `valid per-intent json materializes natural paragraphs without fixed section titles`。
- 保留 `result.valid`、两个答案正文、无 JSON/STATUS 的断言。
- 将两个固定标题断言改为 `assertFalse`。
- 新增顺序断言：`Salary is competitive.` 的 index 小于 `Deliverables depend on project.`。
- 新增负向断言：输出不含 `finance.arrangements`、`role.deliverables`、`sourceRuleIds`、`RULE 1`。
- 不修改 fixture、production composer/materializer 或其他测试。
- 遵守 I-3、I-4。

### T2：执行并记录 V78 pre 门禁

- 文件：`docs/plans/2026-07-17/qa-refactor-01-template-boundary-release-gate-record.md`。
- 前置：T0 证明 V78 尚未应用；发布操作已获人工授权。
- 执行现有脚本 `pre`，保存完整 stdout 与 exit code；允许值严格为：
  - `qa_rule_variants=0`
  - `qa_rule_blocks=1`
  - `dangling_qa_rule_refs=0`
  - `PASS: pre-deploy gate`
- 任何值不符或脚本非 0：停止，不部署 V78；记录 FAIL，不把任务标为完成。
- PASS 时，将环境简称、UTC 时间、目标 DB 名、待部署 commit/artifact、执行人和完整非敏感输出写入记录；删除示例占位符。
- 遵守 I-1、I-2、I-5。

### T3：正常部署后执行并记录 post 门禁

- 文件：同 T2。
- 由已授权发布流程启动包含 V78 的应用，交给 Flyway 一次性应用迁移；不得直接用 mysql 重放 V78。
- 只读确认 `flyway_schema_history.version='78' AND success=1`，再执行现有脚本 `post`。
- `qa_rule_blocks` 必须为 `0`，脚本必须输出 `PASS: post-deploy gate` 且 exit 0；否则按原发布方案停止后续阶段并回滚应用版本。
- 将 post 的环境、UTC 时间、实际制品、执行人和完整非敏感输出写入记录；表格状态只能在真实 PASS 后改为完成。
- 遵守 I-1、I-2、I-5。

### T4：复验

- 文件：无新增修改。
- 依次运行：

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home \
  mvn -q -Dtest=AiReplyGroundedDraftMaterializerTest test

JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home \
  mvn -q -Dtest=QaFactSelectionServiceTest,AiReplyDraftServiceTest,AiReplyGroundedDraftMaterializerTest,AiReplyPointByPointComposerTest,AiReplyHighRiskClaimValidatorTest,AiTrainingSimulateTest,UnmatchedInboundAiReplyTurnKnowledgeTest test

JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean test
node --test src/test/js/*.test.js
git diff --check
```

- 对比 T0 状态快照：本计划执行产生的文件差异只能是变更清单中的两个文件；已有六方案改动必须原样保留。
- 后续重新调用 fix-v；只有 I-1 至 I-5 全部满足才允许签发整体通过。

## 变更文件清单

| # | 文件 | 变更 |
|---|---|---|
| 1 | `docs/plans/2026-07-17/qa-refactor-01-template-boundary-release-gate-record.md` | 用真实 pre/post 输出替换占位符，记录执行元数据 |
| 2 | `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyGroundedDraftMaterializerTest.kt` | 将固定标题旧断言更新为自然结构契约 |

共 2 个文件、2 个独立子系统（发布证据 + LLM 测试）。不允许修改清单外文件。

## 验收标准

- I-1：发布记录中不存在 `_发布窗口填写_`、`示例（须替换）`；pre/post 均包含真实 UTC、环境、制品、执行人、完整计数和 PASS；无密码/token/JDBC 凭据。
- I-2：V78 未应用时存在严格先 pre、后 Flyway、再 post 的时间序列；pre=`0/1/0`，post `qa_rule_blocks=0`。V78 已应用但缺失 pre 时，结果必须保持 BLOCKED，不能宣称关闭。
- I-3：T0 前后 production composer/materializer/V78/门禁脚本的内容 hash 不变；本计划没有生产代码 diff。
- I-4：`AiReplyGroundedDraftMaterializerTest` 通过，并同时断言答案存在、有序、无固定标题、无 JSON/internal labels。
- I-5：全量 Maven 为 0 failure/0 error，JS 为 329/329 通过，且真实门禁记录完整；缺少任一项均不得签发整体通过。
- Interaction：pre 输出与记录逐行一致；Flyway V78 success 与 post 输出属于同一目标环境和制品；合法 per-intent JSON 经 materializer/composer 后保持答案顺序且无标题。

## 人工验收清单

### A-1：目标环境 pre 门禁
- 前置条件：只读确认目标环境尚无成功的 Flyway V78；准备发布制品标识和非明文数据库凭据。
- 操作步骤：1. 执行门禁脚本 `pre`；2. 检查退出码；3. 打开发布记录核对原始输出。
- 预期结果：退出码 `0`；记录中依次出现 `qa_rule_variants=0`、`qa_rule_blocks=1`、`dangling_qa_rule_refs=0`、`PASS: pre-deploy gate`，且 UTC/环境/制品/执行人非空。
- 覆盖：I-1、I-2、发布脚本→记录 interaction point。

### A-2：V78 post 门禁
- 前置条件：A-1 已通过；已授权部署包含 V78 的同一制品；Flyway history 显示 version `78`、success `1`。
- 操作步骤：1. 执行门禁脚本 `post`；2. 检查退出码；3. 核对发布记录 post 区域。
- 预期结果：退出码 `0`；`qa_rule_blocks=0`；出现 `PASS: post-deploy gate`；post 环境与 A-1 相同。
- 覆盖：I-1、I-2、Flyway→post→记录 interaction point。

### A-3：介绍信快照回归
- 前置条件：保存 V78 前同一专家、同一账号的 INTRODUCTION preview subject/text/html；A-2 已通过。
- 操作步骤：用同一专家、账号再次生成 preview，逐字段比较。
- 预期结果：subject、text、html 逐字一致；模板块为 `CUSTOM_TEXT`；QA 后台正文修改不会改变该 preview。
- 覆盖：必须不变第 1、2 项；I-2。

### A-4：Grounded 自然结构回归
- 前置条件：准备同时询问薪酬与职责、且两项均有事实依据的测试来信。
- 操作步骤：生成一次 AI 草稿并查看正文。
- 预期结果：薪酬答案先于职责答案；正文不出现 `1. Financial arrangements`、`2. Deliverables`、`finance.arrangements`、`sourceRuleIds`、`STATUS:`；事实内容仍完整。
- 覆盖：I-3、I-4、materializer→composer interaction point。

### A-5：门禁失败必须停止
- 前置条件：使用隔离测试库构造 `qa_rule_blocks=0` 的 pre 场景；不得修改生产库。
- 操作步骤：执行门禁脚本 `pre`。
- 预期结果：退出码 `1`；输出 `FAIL: pre-deploy gate not satisfied`；没有后续部署动作，发布记录不得标为 PASS。
- 覆盖：I-2、I-5。

