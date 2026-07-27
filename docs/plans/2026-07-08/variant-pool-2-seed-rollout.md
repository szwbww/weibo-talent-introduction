# 计划二：变体轮换全路径接种子（variant-pool-2-seed-rollout）

> 系列：变体池完善 2/3。**依赖计划一（variant-pool-1-engine）已合入**：使用其 `variantSeedFor` helper 与新池语义。
> 决策记录：方案 A——手工发送/自动回复/会议邀请等全部路径启用变体轮换，按专家身份派生确定性 seed。

## 需求描述

可观测结果：手工发送模板邮件、自动回复流水线的会议邀请、会议确认信、会议邀请 composer、自动回复预览这 5 个路径，渲染时按专家身份派生 seed，不同专家分散命中主题/片段变体，同一专家恒定命中同一变体。

不得改变：
- 无变体模板在全部路径的输出（subject/body 不变）。
- `IntroductionMailComposer` 现有 seed 行为（`orcidId.hashCode()`，与 helper 对 orcidId 非空场景输出一致，不改动）。
- 预览与实发一致性：`AutoReplyPreviewService` 展示的会议邀请必须与 `AutoMailReplyService.sendMeetingInvitation` 实发同变体。(来源: K-preview-mirrors-pipeline, P1)
- 各路径现有 variables 注入内容。(来源: K-renderText-all-callers)

超出范围（明确不做）：
- `IntroductionMailComposer` 改用 helper（行为等价，无收益，不动）。
- mail record 记录选中变体（已决策本期不做）。
- 前端 UI（计划三）。
- `MeetingScheduleService` 等路径的其他重构。

## 关键不变量

### Invariant I-1: 统一 seed 派生（引用计划一 I-5）
- Rule: 5 个调用点的 seed 一律 `MailComposeTemplateService.variantSeedFor(orcidId, email)`，禁止各自手写 `hashCode()`。逐点取值：
  1. `ManualExpertMailService.composeComposeTemplate` → `variantSeedFor(contact.orcidId, contact.expertEmail)`
  2. `AutoMailReplyService.sendMeetingInvitation` → 调用方 (:470) 作用域内的 `effectiveContact`：签名增加 `contact: ExpertContact` 参数（或传 `orcidId`/`email` 两参），seed = `variantSeedFor(contact.orcidId, contact.expertEmail)`
  3. `MeetingInvitationMailComposer.compose` → `variantSeedFor(expert.orcidId, expert.email)`
  4. `MeetingScheduleService` 确认信 → `variantSeedFor(contact.orcidId, contact.expertEmail)`
  5. `AutoReplyPreviewService` → 与 #2 完全同源：`contactId` 非空时加载同一 `ExpertContact` 并同式派生；`contactId` 为空（无实发对应物）时 seed = 0，且此偏差在代码注释中显式标注
- Applies to: 上述 5 文件的 render/renderByCode 调用。
- Violation consequence: 同一专家跨路径变体不一致；预览与实发漂移（运营据错预览调策略）。
- 来源: K-preview-mirrors-pipeline + original

### Invariant I-2: 同专家确定性
- Rule: 同一专家（同 orcidId 或同 email）在同一路径重复触发，渲染结果恒等。
- Applies to: 全部 5 个调用点。
- Violation consequence: 重发内容抖动，触发反垃圾。
- 来源: original

### Invariant I-3: 测试 mock 显式匹配 seed
- Rule: 更新 stub 时 `renderByCode`/`render` 的 `variantSeed` 参数用具名精确值或 `any()` 显式声明；禁止为省事把整个 service mock 改成 relaxed 从而丢失断言。至少各路径有一条用例断言"传入的 seed == variantSeedFor(期望标识)"。
- Applies to: 5 个测试文件。
- Violation consequence: seed 接错（如误传 contactId.hashCode）测试仍绿。
- 来源: original（对齐 K-template-feature-coverage 的"测试通过≠落到实现点"教训）

## 现状审计

### 渲染调用点（本计划改动面）
1. `ManualExpertMailService.composeComposeTemplate` (:155-165) — 参数已有 `contact: ExpertContact`（orcidId/expertEmail 均非空字段，见 ExpertContact.kt:8-14），`render(templateId, mailTemplateVariables(account))` 缺 seed。
2. `AutoMailReplyService.sendMeetingInvitation` (:972) — 私有方法，参数 `(account, contactId, received, sourceInboundId)`；唯一调用点 :470，作用域内有 `effectiveContact`（ExpertContact）。`renderByCode("MEETING_INVITATION", mailTemplateVariables(account))` 缺 seed。
3. `MeetingInvitationMailComposer.compose` (:12-26) — 参数已有 `expert: ExpertProfile`（orcidId 非空、email 可空），`renderByCode("MEETING_INVITATION", ...)` 缺 seed。
4. `MeetingScheduleService` (:109) — 作用域内有 `contact`（`contact.expertEmail` :125 可见），`renderByCode("MEETING_CONFIRMATION", ...)` 缺 seed。
5. `AutoReplyPreviewService` (:86) — `renderByCode("MEETING_INVITATION", mailTemplateVariables(account))` 缺 seed；作用域内有 `contactId`（:91 `hasMeetingInvitation(contactId)` 可见）。是否已注入 ExpertContact 仓库需执行时确认；若无则新增构造器依赖 `ExpertContactRepository`（只读，符合该服务纯只读约束，来源: K-preview-mirrors-pipeline 第 4 条）。
6. `IntroductionMailComposer` (:18-22) — 已传 seed，**不动**。(来源: K-introduction-compose-callers：其 2 个调用方自动继承，无需改)

### 测试基线（mock 形态）
grep `renderByCode|mailComposeTemplateService.render` 命中的测试文件：`AutoReplyPreviewServiceTest`、`ManualExpertMailServiceTest`、`MeetingInvitationMailComposerTest`、`MeetingScheduleServiceTest`、`AutoMailReplyServiceTest`（存在，mock 形态执行时确认）、`MailComposeTemplateServiceTest`（计划一）、`IntroductionMailComposerTest`（不动）。stub 若按旧双参记录，新调用带 seed 后不匹配 → 必须更新（这正是 I-3 要求显式化的地方）。

### Interaction points
- 本计划是"写入新参数"，计划一是"消费该参数的语义"——两者接缝在 `render`/`renderByCode` 签名（已存在 `variantSeed: Int = 0` 缺省参，**无签名变更**，纯调用方传参）。
- `sendMeetingInvitation` 签名变更（+contact）只有 1 个调用点（:470），无其他消费方。
- 预览镜像：#5 与 #2 的派生代码必须逐字同式（review 时并排比对）。

## 实现方案

### T1 — ManualExpertMailService（I-1.1, I-2）
`render(templateId, mailTemplateVariables(account), MailComposeTemplateService.variantSeedFor(contact.orcidId, contact.expertEmail))`

### T2 — AutoMailReplyService（I-1.2）
`sendMeetingInvitation` 增加 `contact: ExpertContact` 参数，:470 调用处传 `effectiveContact`；方法内派生 seed 传入 renderByCode。

### T3 — MeetingInvitationMailComposer（I-1.3）
`renderByCode("MEETING_INVITATION", variables, variantSeed = MailComposeTemplateService.variantSeedFor(expert.orcidId, expert.email))`

### T4 — MeetingScheduleService（I-1.4）
确认信 renderByCode 传 `variantSeedFor(contact.orcidId, contact.expertEmail)`。

### T5 — AutoReplyPreviewService（I-1.5，镜像）
contactId 非空 → 加载 ExpertContact（如需新增只读仓库依赖）→ 与 T2 同式派生；contactId 空 → seed 0 + 注释标注偏差。不加 @Transactional、不引入任何写操作。

### T6 — 测试（I-1, I-2, I-3）
5 个测试文件：更新 stub 匹配 seed 参数；每路径至少 1 条用例用 slot/verify 断言实际传入 seed 等于 `variantSeedFor(期望 orcidId, 期望 email)`；补"同专家两次调用结果恒等"确定性用例（Manual 路径为代表即可）；保留并通过全部既有用例。

## 变更文件清单

| # | 文件 | 变更 |
|---|------|------|
| 1 | src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualExpertMailService.kt | T1 |
| 2 | src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyService.kt | T2（签名 + 调用点 :470） |
| 3 | src/main/kotlin/com/weibo/talentintroduction/mail/service/MeetingInvitationMailComposer.kt | T3 |
| 4 | src/main/kotlin/com/weibo/talentintroduction/campaign/service/MeetingScheduleService.kt | T4 |
| 5 | src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoReplyPreviewService.kt | T5 |
| 6 | src/test/kotlin/com/weibo/talentintroduction/mail/service/ManualExpertMailServiceTest.kt | T6 |
| 7 | src/test/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyServiceTest.kt | T6 |
| 8 | src/test/kotlin/com/weibo/talentintroduction/mail/service/MeetingInvitationMailComposerTest.kt | T6 |
| 9 | src/test/kotlin/com/weibo/talentintroduction/campaign/service/MeetingScheduleServiceTest.kt | T6 |
| 10 | src/test/kotlin/com/weibo/talentintroduction/mail/service/AutoReplyPreviewServiceTest.kt | T6 |

文件数 10 ≤ 10；子系统 2（mail、campaign）≤ 2；新增共享存储字段 0。

## 验收标准

- I-1: 逐路径 verify/slot 断言 seed 值；grep 确认 5 个调用点均引用 `variantSeedFor`，无手写 `hashCode()` 派生。
- I-1.5 镜像: 并排 diff AutoReplyPreviewService 与 AutoMailReplyService 的派生表达式逐字一致；预览服务 grep 无 `save(`、无 `@Transactional`。
- I-2: 同专家重复调用输出恒等用例通过。
- I-3: grep 测试文件无新增 `relaxed = true`（原有的不算）；每个 stub 的 variantSeed 位显式声明。
- 回归: 无变体模板用例在 5 路径输出与改动前一致；`mvn test` 全绿；IntroductionMailComposer 及其 2 个调用方零改动（git diff 确认）。
