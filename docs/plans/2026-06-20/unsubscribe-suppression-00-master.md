# 主计划：退订抑制名单 + List-Unsubscribe 一键退订

> 用 create-p skill 编写。本主计划只定义共享上下文与不变量；具体任务在子计划中。
>
> 子计划（按顺序执行，后者依赖前者）：
> 1. `2026-06-20-unsubscribe-suppression-01-suppression-core.md` — 抑制名单存储 + 外发过滤 + 入站退订捕获
> 2. `2026-06-20-unsubscribe-suppression-02-list-unsubscribe-oneclick.md` — List-Unsubscribe 头 + RFC 8058 一键退订端点

## 背景与目标

合规与送达需要：① 收件人能一键退订；② 退订/明确拒收的地址进入"不再联系"名单；③ 后续批量外发自动跳过这些地址。Gmail/Yahoo 批量发件人规则已强制 `List-Unsubscribe` 与一键退订（RFC 8058）。

- 可观察结果：
  - 退订过的邮箱不再收到任何**自动批量外发**邮件。
  - 收件人回复含退订意图，或点击邮件客户端的"取消订阅"按钮后，其邮箱被加入抑制名单。
  - 外发邮件头部带 `List-Unsubscribe` 与 `List-Unsubscribe-Post: List=One-Click`。
- 必须不变：
  - 现有发送/接收/退信/会话状态机流程不变。
  - 现有 `AutoMailReplyService` 对各意图的处理结果不变（退订捕获是**附加**动作，不替换既有 MANUAL_HANDOFF 流转）。
  - `ConversationStatus` / `InboundIntentCode` 枚举不新增值（避免触碰状态机）。
- 不做（明确推迟）：
  - text+HTML 多部分邮件（独立计划）。
  - 按收件服务商节流、发件预热、外部信誉监控（各自独立计划）。
  - 退订的前端管理页（查看/手动增删抑制名单）——本期仅后端 + 一键端点。
  - 对**会话内回复**（QA 自动回复、会议邀请、人工发送）的发送拦截——这些是已建立会话的应答，退订人通常已转人工；本期抑制只作用于**主动批量外发**入口。

## 共享关键不变量（被两个子计划引用）

### Invariant G-1：邮箱归一化唯一
- 规则：抑制名单的键是归一化邮箱 = `email.trim().lowercase(Locale.ROOT)`。写入与查询都必须用同一归一化函数（`EmailSuppressionService.normalize`）。
- 适用于：所有写入 `email_suppression` 的路径、所有 `isSuppressed` 查询、外发过滤、一键端点。
- 违反后果：大小写/空格差异导致漏判，退订人仍被联系（合规事故）。

### Invariant G-2：抑制是"加入即永久生效"，幂等
- 规则：同一归一化邮箱在 `email_suppression` 中最多一行（唯一约束）；重复退订是幂等 upsert，不报错、不产生多行。
- 适用于：`EmailSuppressionService.suppress`、入站捕获、一键端点。
- 违反后果：唯一约束冲突异常中断入站/退订流程。

### Invariant G-3：外发主动批量必须先查抑制名单
- 规则：`InitialOutreachService` 与 `ManualInitialOutreachService` 在为某专家创建联系记录/发送前，必须调用 `isSuppressed`，命中则跳过（计入 skipped），**不创建** `ExpertContact`、**不发送**。
- 适用于：两个外发入口的目标遍历循环。
- 违反后果：退订人收到外发邮件。

### Invariant G-4：一键退订端点免鉴权且仅能新增抑制
- 规则：一键退订 HTTP 端点路径不在 `/api/**` 之下（如 `/u/unsubscribe`），因此不经过 `AuthInterceptor`；该端点只能把邮箱加入抑制名单，不暴露任何读取/删除/枚举能力；邮箱来自带签名的 token（`UnsubscribeTokenService` HMAC 校验），不接受明文邮箱参数。
- 适用于：`UnsubscribeController`。
- 违反后果：未鉴权端点被滥用枚举/退订他人，或被任意注入。

## 共享现状审计

### 数据库
- 最新迁移 `V29__create_bounce_record.sql`。本特性新迁移从 **V30** 起。Flyway 启动执行，禁止改既有迁移。

### 发送链路（写路径）
- 统一出口：`MailDeliveryService.send(account, mail)`，实现类 `SmtpMailDeliveryService`。DTO：`ComposedMail(to, subject, body, html=false, messageId=null)`；返回 `DeliveredMail(messageId, status, errorCategory, smtpResponseCode, errorDetail)`。
- 调用方（`grep "mailDeliveryService.send"`）：
  1. `campaign/service/InitialOutreachService.kt:51` — 自动批量首次外发（**抑制过滤目标点**，G-3）。
  2. `campaign/service/ManualInitialOutreachService.kt:286` — 人工批量外发（**抑制过滤目标点**，G-3）。
  3. `mail/service/AutoMailReplyService.kt:384,664` — QA 自动回复 / 会议邀请（会话内应答，本期不拦截）。
  4. `mail/service/ManualExpertMailService.kt:72` — 人工单发（会话内，本期不拦截）。
  5. `campaign/service/MeetingScheduleService.kt:129` — 会议邀请（会话内，本期不拦截）。
  6. `mail/service/PendingMailOperationService.kt:99,181` — 队列化发送（同上不拦截）。

### 邮件头注入点（写路径）
- `SmtpMailDeliveryService.send` 直接操作 `javax.mail.internet.MimeMessage`，已自定义 `Message-ID`、设 From/To/Subject/Content。`List-Unsubscribe*` 头在此追加（子计划 02）。可用变量：`mail.to`、`account.senderEmail`。

### 入站退订识别（读路径）
- `InboundIntentClassifier`：`unsubscribe` 当前归入 `notInterestedKeywords` → `InboundIntentCode.NOT_INTERESTED`，最终走 MANUAL_HANDOFF。**没有**任何"加入抑制名单"动作。
- `AutoMailReplyService`：按 `intent.autoAction` 路由；退订捕获将作为**附加**步骤插入入站处理（子计划 01），不改既有路由结果。

### 鉴权
- `auth/config/AuthWebConfig.kt`：`AuthInterceptor` 拦截 `/api/**`，排除 `/api/auth/login`、`/api/auth/me`。**非 `/api` 前缀的路径不被拦截** → 一键端点用 `/u/**`，无需改鉴权配置（G-4）。需确认 `FrontendController` 的 SPA 兜底不会吞掉 `/u/**`（子计划 02 验收项）。

## 子计划拆分与依赖

| 子计划 | 交付价值 | 依赖 | 文件数 |
|---|---|---|---|
| 01 抑制核心 | 退订被记录、外发自动跳过 | 无 | ≤ 9 |
| 02 一键退订 | 收件人可一键退订，头部合规 | 01（复用 `EmailSuppressionService`） | ≤ 7 |

每个子计划独立可部署、独立验证。02 不得反向修改 01 的不变量。
