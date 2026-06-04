# 专家自动回复全流程测试页面 设计方案

## 1. 目标

提供一个内置在 admin 静态站点里的"模拟器"页面，用来在不真正通过 IMAP/SMTP 走邮件的前提下：

- 选择/新建一个测试联系人（test campaign 下的 `ExpertContact`）；
- 模拟该专家给我们发来的各种回复（自由文本 + preset 模板 + 附件）；
- 触发后端真实的入站处理链路（`AutoMailReplyService` 的单条消息处理逻辑），让真实的 `InboundIntentClassifier` / `QaMatchService` / `ConversationStateService` / `ManualHandoff` / `MeetingScheduleService` 全部参与；
- 在页面上实时观察：当前会话状态、状态历史、邮件记录、入站意图、handoff 单、应用层 ES 状态、推荐下一步动作；
- 对照"期望状态"做断言，用于验证状态流转是否正确。

不要做的事：

- 不真的发出站邮件 —— 用一个 `NoopMailDeliveryService` 在测试模式下接管；
- 不动真实 campaign / 真实 ES 数据 —— 限定 campaignId = `simulator_campaign`，并隔离 orcidId 前缀；
- 不上 IMAP —— 模拟入站直接喂 `ReceivedMail` 给单条处理函数，绕开 fetch/markSeen。

## 2. 整体架构

```
┌──────────────────────────────────────────────┐
│  static/simulator.html  +  simulator.js      │   浏览器
│   ── 联系人面板 ── 操作面板 ── 时间线 ── 断言 │
└──────────────┬───────────────────────────────┘
               │  REST /api/simulator/*
               ▼
┌──────────────────────────────────────────────┐
│  SimulatorController                         │
│   - seedContact / resetContact               │
│   - simulateInboundMail                      │
│   - listScenarios / runScenario              │
│   - snapshot (state + mails + history)       │
└──────────────┬───────────────────────────────┘
               │
               ▼
┌──────────────────────────────────────────────┐
│  AutoMailReplyService                        │
│   - processSingle(account, ReceivedMail) ★新 │  抽出来供模拟器直接调用
│                                              │
│  ConversationStateService（不动）             │
│  InboundIntentClassifier（不动）              │
│  QaMatchService（不动）                       │
│  MeetingScheduleService（不动）               │
│                                              │
│  MailDeliveryService                         │
│   ↳ NoopMailDeliveryService ★Profile=test   │  拦截真实发件
│  MailReceiveService                          │
│   ↳ 不调用（模拟入站绕过 IMAP）              │
└──────────────────────────────────────────────┘
```

## 3. 后端改造

### 3.1 抽出单条处理方法（核心改造，最小）

`AutoMailReplyService.receiveAndAutoReply` 当前是 `fetchUnread → forEach → 一大段逻辑`。把 forEach 内部那一段抽成：

```kotlin
@Transactional
fun processSingle(
    account: MailSenderAccount,
    received: ReceivedMail,
    skipImapAck: Boolean = false        // 模拟器传 true
): SinglePipelineResult
```

`SinglePipelineResult` 返回这一次执行所产生的：mailRecordId、intentCode、autoAction、newStatus、manualHandoffId、meetingScheduleId、replyDelivery 状态。这样模拟器可以直接报告这一步发生了什么，不用从库里反查。

原 `receiveAndAutoReply` 改为薄壳：`fetchUnread → for each → processSingle(skipImapAck=false)`，行为完全不变（保证回归安全）。

### 3.2 `SimulatorController` (`/api/simulator/**`)

只在 `simulator` 这个 Spring profile（或 `talent-introduction.simulator.enabled=true`）下注册 Bean，正式环境不暴露。

| Method | Path | 作用 |
|---|---|---|
| POST | `/contacts` | 在测试 campaign 下创建一个联系人，可指定起始状态（默认 `INTRO_SENT`，并自动写一条 OUTBOUND `INTRODUCTION` mail record，让 `hasIntroductionInquiry` 通过） |
| POST | `/contacts/{id}/reset` | 清掉该联系人的 mail records / inbound processing / status history / handoffs / meeting schedules，回到指定起始状态 |
| GET  | `/contacts/{id}/snapshot` | 一次返回：contact、status history、mail records、inbound intents、latest handoff、meeting schedules、推荐下一步、ES application index 状态 |
| POST | `/contacts/{id}/inbound` | 喂一条模拟入站邮件，调用 `processSingle`，返回 `SinglePipelineResult` |
| GET  | `/scenarios` | 列出预置场景 |
| POST | `/scenarios/{key}/run` | 在新建/重置的联系人上按顺序执行一组 inbound + 断言，返回逐步结果 |
| GET  | `/presets` | 列出 preset 入站文本（与 `InboundIntentClassifier` 关键词一一对应） |

`/contacts/{id}/inbound` 请求体：

```json
{
  "subject": "Re: Introduction",
  "body": "I am interested, happy to discuss.",
  "attachments": [
    { "fileName": "cv.pdf", "contentType": "application/pdf", "contentBase64": "..." }
  ],
  "expectedIntent": "INTERESTED",        // 可选，做断言用
  "expectedAutoAction": "SEND_MEETING_INVITATION",
  "expectedNewStatus": "MEETING_SCHEDULING"
}
```

服务端构造 `ReceivedMail`，`imapUid` 用自增伪值（`SIM_<contactId>_<seq>`），`messageId` 用 `simulator-<uuid>@local`，再把账号取 contact 当前绑定的 sender account，最后调 `processSingle(account, received, skipImapAck=true)`。

### 3.3 出站邮件拦截

新增 `NoopMailDeliveryService implements MailDeliveryService`，在 `simulator` profile 下 `@Primary` 覆盖 SMTP 实现：

- 不连接 SMTP，只生成 `messageId = noop-<uuid>` 并标记 `sendStatus=SIMULATED`；
- 把 outbound 内容存到一个内存 ring buffer，供页面"出站邮件预览"查看；
- 真实 `MailRecord` 仍按正常流程写库（这样 `hasMeetingInvitation` / `hasIntroductionInquiry` 的判定保持一致）。

`SmtpMailDeliveryService` 不动。

### 3.4 数据隔离

- 新建 V`n`__simulator_campaign.sql migration，插入一行 `campaign` 主键固定为 `9000`、code = `SIMULATOR`；
- 所有模拟器接口强校验 `contact.campaignId == 9000`；非该 campaign 一律 403；
- 联系人 `orcidId` 强制前缀 `SIM-`，`expertEmail` 前缀 `sim+`；
- 模拟器创建的联系人 `applicationIndexed` 默认 false、`expertIndexWriterService` 在 noop profile 下也短路（或者只写一个虚拟 ES index `simulator_application`，别污染真实索引）。

## 4. 前端测试页面

新建 `static/simulator.html`，`FrontendController` 加路由 `/simulator` → 转发到该文件；`simulator.js` 单独 bundle，复用现有 `styles.css` 调色。

### 4.1 布局（三栏）

```
┌────────────┬──────────────────────────┬──────────────────┐
│ 联系人      │ 状态机图 + 当前状态高亮   │ 时间线           │
│  - 选择     │  ──────────────────────  │  - 状态变更      │
│  - 新建     │ 操作面板                  │  - 邮件 inbound  │
│  - 重置     │  - preset 选择            │  - 邮件 outbound │
│             │  - 自定义 subject/body    │  - handoff       │
│             │  - 附件上传               │  - meeting       │
│             │  - 期望断言下拉           │                  │
│             │  [模拟收到此回复]         │                  │
│             │                          │ 断言面板         │
│             │ 场景跑批                  │  - 通过 / 失败   │
│             │  - 选择场景 → Run         │                  │
└────────────┴──────────────────────────┴──────────────────┘
```

### 4.2 状态机可视化

把 `ConversationStatus` 关键节点画成有向图（SVG，硬编码节点位置；不接 mermaid 也能 inline）。高亮三类节点：当前状态（蓝）、推荐下一步（虚线橙）、历史路径（灰边）。当一次模拟入站完成后，把新状态、状态转移边动画闪一下。

仅画核心节点即可：
`NEW → INTRO_SENT → WAITING_REPLY → { QA_AUTO_REPLIED | MEETING_SCHEDULING → MEETING_SCHEDULED → MEETING_DONE | MANUAL_HANDOFF } → ... → SUBMITTED`，其他企业内材料/视频/承诺节点折叠成"... 后续节点（点开展开）"。

### 4.3 操作面板

Preset 下拉对应 `InboundIntentClassifier` 中每一组关键词，至少覆盖：

| Preset | Body 模板 | 期望意图 | 期望 autoAction | 期望下一状态 |
|---|---|---|---|---|
| Interested | "I am interested, happy to discuss." | INTERESTED | SEND_MEETING_INVITATION | MEETING_SCHEDULING |
| Ask More Info | "Could you tell me more details about the program?" | ASK_MORE_INFO | QA | QA_AUTO_REPLIED |
| Ask Process | "What is the application timeline?" | ASK_PROCESS | QA | QA_AUTO_REPLIED |
| Ask Remote | "Can this be remote / part-time?" | ASK_REMOTE_PART_TIME | QA | QA_AUTO_REPLIED |
| Ask Funding | "Who is funding the salary?" | ASK_FUNDING | MANUAL_REVIEW | MANUAL_HANDOFF |
| Ask Confidentiality | "My employer has an IP conflict clause..." | ASK_CONFIDENTIALITY | MANUAL_REVIEW | MANUAL_HANDOFF |
| Meeting Time | "I am available on Tuesday 10am via Zoom." | MEETING_TIME_PROVIDED | MANUAL_REVIEW | MANUAL_HANDOFF (并创建 meeting schedule) |
| Meeting Request | "Could we set up a meeting next week?" | MEETING_REQUESTED | MANUAL_REVIEW | MANUAL_HANDOFF |
| CV Attached | body 短句 + 上传一个伪 PDF | CV_ATTACHED | MANUAL_REVIEW | MANUAL_HANDOFF |
| Docs Attached | body + 任意附件 | DOCS_ATTACHED | MANUAL_REVIEW | MANUAL_HANDOFF |
| Not Interested | "Please remove me, not interested." | NOT_INTERESTED | CLOSE | MANUAL_HANDOFF（closedReason=NOT_INTERESTED） |
| QA No Match | "Random unrelated text about weather." | UNKNOWN | QA → 落 QA_NO_MATCH | MANUAL_HANDOFF |
| Empty Body | "" | UNKNOWN | MANUAL_REVIEW | MANUAL_HANDOFF |

每条点击后右侧填充 subject/body，可二次编辑再发送。

附加 toggle：

- "联系人 auto-reply 已关闭" —— 先调 `pauseAutoReply` 再发 inbound，验证回到 MANUAL_HANDOFF；
- "联系人当前已经在 MANUAL_HANDOFF" —— 验证再次入站不会重复转移；
- "INTRODUCTION 尚未发送" —— 直接清掉 outbound INTRODUCTION record，验证 `INTRODUCTION_NOT_SENT` 分支；
- "联系人未匹配" —— 用一个未注册的 from 地址发，验证 `CONTACT_NOT_FOUND` 分支。

### 4.4 时间线

按 `createdAt` 升序合并展示：状态变更（来自 `ExpertContactStatusHistory`）、入站邮件（带意图标签与关键词高亮）、出站邮件（INTRODUCTION / QA_REPLY / MEETING_INVITATION，点击展开渲染 body）、handoff 单（PENDING/ASSIGNED/COMPLETED）、meeting schedule 变更。每行带 ⏱ icon + 来源（AUTO_REPLY / MANUAL / SIMULATOR）。

### 4.5 断言面板

操作面板里设了 `expectedIntent / expectedAutoAction / expectedNewStatus`，发送后比对 `SinglePipelineResult`：

- 三项全对：绿色 ✅；
- 任一不对：红色 ❌，列出 expected vs actual；
- 没填期望：黄色 ⚪️（仅观察）。

底部统计：本次会话累计 N 步，✅ a / ❌ b / ⚪️ c。

### 4.6 场景跑批

预置端到端剧本，按顺序执行多条 inbound + 断言：

| 场景 | 步骤 |
|---|---|
| happy-path 兴趣 → 会议 | INTRO_SENT → Interested(✅ MEETING_SCHEDULING) → MeetingTime(✅ 仍 MEETING_SCHEDULING 且生成 meeting schedule) → 操作员 confirmMeetingAndEmail → MEETING_SCHEDULED |
| QA 闭环 | INTRO_SENT → AskProcess(✅ QA_AUTO_REPLIED) → AskRemote(✅ 仍 QA_AUTO_REPLIED, 再回一条) → AskFunding(✅ MANUAL_HANDOFF) |
| 拒绝 | INTRO_SENT → NotInterested(✅ MANUAL_HANDOFF, closedReason=NOT_INTERESTED) |
| 附件触发人工 | INTRO_SENT → CVAttached(✅ MANUAL_HANDOFF, 创建 document + handoff) |
| 自动→人工→自动 | INTRO_SENT → AskProcess(✅ QA_AUTO_REPLIED) → 操作员 switchToManual(✅ MANUAL_HANDOFF) → AskProcess(✅ 仍 MANUAL_HANDOFF, 不自动回复) → 操作员 switchToAuto(✅ WAITING_REPLY) → AskProcess(✅ QA_AUTO_REPLIED) |
| 联系人未匹配 | 用陌生邮箱发 → ✅ UNMATCHED_CONTACT 落 inbound_mail_processing，不产生 contact 变更 |
| INTRODUCTION 未发 | reset 后清掉 INTRODUCTION → 发 Interested → ✅ INTRODUCTION_NOT_SENT，转 MANUAL_HANDOFF |
| 重复入站 | 同一 imapUid 发两次 → ✅ 第二次直接跳过，不写第二条 mail record |

页面 Run 一个场景：右侧滚动播放每一步的请求/响应/断言。全场景跑完显示总通过率。

## 5. 隔离与安全

- profile 隔离：默认 `application.yml` 不启用，`application-simulator.yml` 才注册 `SimulatorController` + `NoopMailDeliveryService`。
- 二级开关：`talent-introduction.simulator.enabled=true` 才放行 `/api/simulator/**`，并加 `@ConditionalOnProperty`。
- 鉴权：与现有 admin UI 一致；接口里再做一层 `assertSimulatorCampaign(contactId)`。
- 出站护栏：`NoopMailDeliveryService` 始终断言 `to` 以 `sim+` 开头，否则抛错（防止误把模拟器指向真实联系人）。
- 调度隔离：`MailAutomationScheduler` 自身不跑 simulator campaign（按 campaignId 过滤），避免定时任务把模拟数据再拉一遍。

## 6. 实施分期

阶段一（最小可用）
- `AutoMailReplyService.processSingle` 抽方法（含单测，保证 `receiveAndAutoReply` 行为不变）。
- `NoopMailDeliveryService` + simulator profile。
- 新建 simulator campaign migration + `SimulatorController.seedContact / inbound / snapshot`。
- 静态页面只做：左侧联系人选择/新建，中间手写 subject+body+预期，右侧时间线 + 断言。

阶段二（preset + 状态机图）
- preset 文本与状态机 SVG。
- 联系人状态/handoff/会议日程统一从 snapshot 渲染。
- 切手动/切自动按钮直接复用 `/api/expert-contacts/{id}/switch-to-*`。

阶段三（场景跑批）
- `/api/simulator/scenarios` + 跑批 runner，按步骤执行并断言，前端按时间轴播放。
- 输出一份 JSON 报告，可下载，作为回归基线。

阶段四（可选增强）
- 入站附件 multipart 支持（上传任意文件用作 CV/材料场景）。
- 跑批 CI 化：写一个 `@SpringBootTest` 直接调 `SimulatorController` 的 service，跑完所有场景，断言全绿，挂在 `mvn test`。
- 把 inbound 文本与意图关键词的覆盖率统计出来，便于发现"哪类关键词没有任何 preset 覆盖"。

## 7. 不变更的事

- `ConversationStateService.transition` 仍是状态转移唯一入口，模拟器不旁路。
- `InboundIntentClassifier`、`QaMatchService`、`MailBodyCleaner`、`MailAttachmentService`、`MeetingScheduleService` 全部走真实实现，不 mock，否则就失去验证意义。
- 数据库 schema 走 Flyway 新增 migration，不改已应用的 V1..V10。

## 8. 验收标准

- 启用 simulator profile 后，`/simulator` 页面可加载；非 simulator profile 下访问返回 404。
- 单条模拟入站：能在 1s 内看到状态、邮件、handoff、meeting 同步更新。
- 所有 preset 单步执行的实际意图/动作/状态与表格中"期望"列一致。
- 7 个预置场景全绿，可作为 PR 必跑回归。
- 真实邮箱账户、真实 ES 索引（candidate / application）在跑模拟器期间无任何写入。
