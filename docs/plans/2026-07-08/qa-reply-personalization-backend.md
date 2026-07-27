# QA 回复链路专家变量渲染（后端）

> 创建日期: 2026-07-08
> 触发原因: QA 自动/人工回复全部为静态文本，所有专家收到逐字节相同回信；`${...}` 占位符写入 qa_rule 正文会原样外发。变量机制已在 INTRODUCTION 首邮链路存在（2026-07-06 template-expert-variables-and-fallback 已实现 fallback 语法），但 QA 出口从未接入。
> 系列: 本计划为 Plan A（后端渲染）；Plan B `qa-variable-config-ui-and-validation.md` 依赖本计划。

## 需求描述

**可观测结果**：运营在 `qa_rule.reply_body` / `reply_snippet.content` 中显式书写的 `${key}` / `${key|fallback}` 占位符，在 QA 自动回复与人工组装台外发时被替换为收件专家的真实档案值（姓氏、研究方向、机构等），`mail_record.body` 持久化渲染后的文本。

**不可变更**：
1. 不含占位符的规则正文渲染前后逐字节相同（渲染是纯显式替换，零隐式注入）。
2. INTRODUCTION 首邮链路两个调用方（`InitialOutreachService` / `ManualInitialOutreachService`）行为不变。(来源: K-introduction-compose-callers)
3. `MailComposeTemplateService.renderText()` 保持唯一替换实现点，行为不变（含 `${key|fallback}` 语义与未知 key 原样保留）。(来源: K-renderText-all-callers)
4. QA 外发的 multipart HTML 机制不变：`plainTextToHtml` + `html=true`，`mail_record.body` 存纯文本。(来源: K-plaintext-reply-client-reflow)
5. `QaReplyComposer` 两条组装链（自动 `compose` / 运营序 `composeInOperatorOrder`）的排序契约不变。(来源: K-composed-reply-order-contract, K-manual-frame-three-consumers)
6. `mail_record_qa_rule` 审计 ordinal 语义不变（渲染发生在 section 正文内部，不动拼接顺序）。

**不在范围**（显式延后）：
- 保存时白名单校验、变量元数据接口、前端编辑器 chip/预览 → Plan B。
- `QaReplyComposer` 硬编码 `GREETING/CLOSING` 常量迁移到 reply_snippet → 单独计划（涉及自动链与人工链共享常量，见 K-manual-frame-three-consumers）。
- `AiReplyDraftService` prompt 注入专家档案、`ManualExpertMailService` 模板路径专家变量、会议邀请路径专家变量。
- `LlmStitchService` 输出渲染：草稿最终经 `PendingMailOperationService` 发送 seam 渲染，展示层渲染由 Plan B 预览端点承担。

**数据现状约束**：线上 `researchFields`/`keyword` 大量为空（采集管道历史未填，OpenAlex enrichment 正在补），可靠有值字段仅 `familyNames`、`institution`、`country`。在 Plan B 校验上线前，运营不应向规则正文写入占位符；本计划上线本身行为中性（正文无占位符时渲染为恒等变换）。

## 关键不变量

### Invariant I-1: 显式渲染，零隐式注入
- Rule: 渲染只替换文本中字面存在的 `${key}` 与 `${key|fallback}`；不自动添加称呼、问候、签名或任何未写在正文里的内容；未知 key（不在白名单）原样保留。
- Applies to: 新 `MailVariableService.renderForContact(...)` 及其所有调用点（`AutoMailReplyService`、`PendingMailOperationService`）。
- Violation consequence: 黑盒行为，运营无法从正文预知外发内容；违背本需求核心诉求。
- 来源: original

### Invariant I-2: 变量白名单单源
- Rule: 专家+发件人变量的构建有且仅有一个实现：`MailVariableService.buildVariables(account, expert)`。`IntroductionMailComposer` 改为委托，不保留第二份变量表；`VARIABLE_LABELS` 一并迁移。
- Applies to: `IntroductionMailComposer.compose()`、QA 渲染路径、（Plan B 的元数据接口）。
- Violation consequence: 首邮与 QA 变量语义漂移，同名占位符两处渲染结果不同。
- 来源: original（依据 K-introduction-compose-callers 审计）

### Invariant I-3: 变量键集恒定，档案缺失降级为空串
- Rule: `buildVariables` 无论 `ExpertProfile` 是否查得（ES 无档案 / 查询异常），返回的 map 必须包含全部固定 key，缺失值为 `""`。从而 `${key}` 渲染为空、`${key|fb}` 必然走兜底，白名单内 key 的 `${` 字面量永不出现在外发正文。
- Applies to: `MailVariableService.buildVariables`、`renderForContact` 的档案解析分支。
- Violation consequence: ES 抖动时专家收到含 `${expertFamilyName}` 字面量的邮件，暴露系统内部实现。
- 来源: original（语义对齐 2026-07-06 计划 I-2）

### Invariant I-4: 渲染仅发生在发送 seam，审计存渲染后文本
- Rule: 每条 QA 出口在 compose 完成之后、`plainTextToHtml` 之前渲染一次；`mail_record.body` 持久化渲染后的纯文本（审计所见 = 实际所发）。渲染幂等（对无占位符文本为恒等），重复渲染不产生二次替换伤害，但代码只在 seam 调一次。
- Applies to: `AutoMailReplyService`（自动 QA 回复 seam，592 行附近）、`PendingMailOperationService.sendQaReply`（83 行）、`PendingMailOperationService.sendManualComposedReply`（303 行）。
- Violation consequence: 漏一个 seam → 该路径外发字面占位符；渲染后不落库 → 审计/前端 `.pre` 展示与实发不一致（K-mail-body-display-sites 全部展示点读 `mail_record.body`）。
- 来源: original（seam 清单来源: K-plaintext-reply-client-reflow）

### Invariant I-5: 档案解析按 contact 索引层级，单点查询
- Rule: 专家档案通过 `contact.orcidId` + `contact.currentIndexLevel` 调 `ExpertSearchService.searchByOrcidId` 获取；APPLICATION 未命中回退 CANDIDATE；每封回信最多 2 次 ES 查询，异常吞掉并按 I-3 降级，绝不因 ES 故障阻断回信发送。
- Applies to: `MailVariableService.renderForContact`。
- Violation consequence: ES 故障导致自动回复管线中断，或档案取错层级致变量值过期。
- 来源: original

## 现状审计

### 变量构建 — `IntroductionMailComposer.buildVariables()`（mail/service）
- 现状: 15 个变量（5 sender + 10 expert），`VARIABLE_LABELS` 中文标签表同文件；`orEmpty()` 保证空值为空串。
- 调用方（写路径，来源: K-introduction-compose-callers，已 re-grep 确认）:
  1. `InitialOutreachService.sendInitialBatch()` — 自动批量首邮
  2. `ManualInitialOutreachService.runScheduledBatch()` — 手动/调度批量首邮
- 读路径: `toTemplateVariableItems()` → `/api/experts/template-variables`（前端模板预览 tab）。

### 渲染引擎 — `MailComposeTemplateService.renderText()`（template/service，private）
- 现状: 377–386 行，`FALLBACK_PLACEHOLDER_REGEX` 处理 `${key|fb}`，fold 替换 `${key}`，未知 key 原样保留。内部 6 处调用（subject / QA_RULE / REPLY_SNIPPET / CUSTOM_TEXT block）。(来源: K-renderText-all-callers，已 re-grep)
- 本计划改动: 增加 public 包装方法供 QA 渲染复用，private 实现不动。

### QA 回复出口（写 `mail_record` 的 OUTBOUND QA 路径）
1. `AutoMailReplyService`（≈592 行）: `match.replyBody`（`QaMatchService.match()` → `QaReplyComposer.compose()`）→ `plainTextToHtml` → send → `mailRecordRepository.save(body = plainBody)`。**无任何变量处理**。单规则分支连 GREETING/CLOSING 都没有（存量行为，本计划不改）。
2. `PendingMailOperationService.sendQaReply()`（83 行）: 单规则人工 QA 回复，同样直接用规则正文（106 行 `plainTextToHtml`）。
3. `PendingMailOperationService.sendManualComposedReply()`（303 行）: 运营序组装（335 行 `composeInOperatorOrder`）+ frame 片段 → 356 行 `plainTextToHtml`。
- 读路径: 前端所有正文展示点读 `mail_record.body`（`.pre` 类，K-mail-body-display-sites）；审计 `mail_record_qa_rule` 按 ordinal 关联。
- 交互点: 渲染必须发生在这 3 个 seam 且落库渲染后文本，否则展示/审计与实发漂移（I-4）。

### 专家档案读取 — `ExpertSearchService.searchByOrcidId()`（expert/service，495 行）
- 现成 term 查询按 level 取单条 `ExpertProfile`；`ExpertContact` 表仅有 `orcidId/expertEmail/expertName/country`，**无** researchFields/institution/familyNames（数据所有权分界，ES 管画像）。

## 实现方案

### Task 1: 抽取 `MailVariableService`（新文件，mail/service）[I-2, I-3, I-5]
- `buildVariables(account: MailSenderAccount?, expert: ExpertProfile?): Map<String, String>` — 从 `IntroductionMailComposer` 平移 15 变量构建逻辑；`expert == null` 时全部 expert key 置 `""`（I-3）。
- `variableMetadata(): List<VariableMeta(key, label, nullable, example)>` — 迁移 `VARIABLE_LABELS`，为 Plan B 元数据接口备好单源（I-2）。
- `renderForContact(text: String, account: MailSenderAccount?, contact: ExpertContact): String` — 按 I-5 解析档案（try/catch 降级 null），调 `MailComposeTemplateService.renderWithVariables(text, vars)`。

### Task 2: `MailComposeTemplateService` 增加 public 包装 [不可变更-3]
- `fun renderWithVariables(text: String, variables: Map<String, String>): String = renderText(text, variables)`。private 实现与 6 个内部调用点零改动。

### Task 3: `IntroductionMailComposer` 委托 [I-2, 不可变更-2]
- `buildVariables` / `VARIABLE_LABELS` / `toTemplateVariableItems` 改为委托 `MailVariableService`，对外方法签名不变，两调用方与 `/api/experts/template-variables` 行为不变。

### Task 4: 三个发送 seam 接渲染 [I-1, I-4]
- `AutoMailReplyService`: `val plainBody = mailVariableService.renderForContact(match.replyBody, account, contact)`（592 行处）。
- `PendingMailOperationService.sendQaReply` / `sendManualComposedReply`: 组装出最终纯文本后、`plainTextToHtml` 前同样渲染。
- 三处均以渲染后文本落 `mail_record.body`。

### Task 5: 测试 [验收标准全部]
- 新 `MailVariableServiceTest`: 键集恒定（profile=null）、空值 `${key}`→空串、`${key|fb}`→fb、未知 key 保留、ES 异常降级不抛。
- `IntroductionMailComposerTest`: mock 迁移后原断言全绿（K-introduction-compose-callers 提示 mock 需同步）。
- `AutoMailReplyServiceTest` / `PendingMailOperationServiceTest`: 各加一条含占位符规则的端到端断言（外发与落库均为渲染值、无 `${` 残留）+ 一条无占位符规则逐字节不变断言。

## 变更文件清单

| # | 文件 | 改动 |
|---|------|------|
| 1 | `mail/service/MailVariableService.kt` | 新增 |
| 2 | `mail/service/IntroductionMailComposer.kt` | 委托变量构建 |
| 3 | `template/service/MailComposeTemplateService.kt` | public 渲染包装 |
| 4 | `mail/service/AutoMailReplyService.kt` | QA seam 渲染 |
| 5 | `mail/service/PendingMailOperationService.kt` | 两个 seam 渲染 |
| 6 | `mail/service/MailVariableServiceTest.kt`（test） | 新增 |
| 7 | `mail/service/IntroductionMailComposerTest.kt`（test） | mock 同步 |
| 8 | `mail/service/AutoMailReplyServiceTest.kt`（test） | 渲染断言 |
| 9 | `mail/service/PendingMailOperationServiceTest.kt`（test） | 渲染断言 |

（9 文件，1 个子系统：mail/template 后端）

## 验收标准

- I-1: 无占位符正文经三 seam 后逐字节不变（单测）；渲染路径中不存在任何非 `${}` 驱动的文本插入（代码评审 + 单测断言输出等于输入）。
- I-2: 全仓 grep 变量 map 构建仅 `MailVariableService` 一处；`IntroductionMailComposerTest` 全绿。
- I-3: profile=null 用例断言 map 含全部 15 key 且 expert 值为 `""`；渲染输出断言 `!contains("${")`（针对白名单 key）。
- I-4: 三个 seam 各有用例断言 `mail_record.body` == 实发纯文本 == 渲染后文本；`html=true` multipart 分支不变。
- I-5: mock ES 抛异常用例断言回信仍发送、变量走空值降级。
- 集成: `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test` 全绿。
