# 修复计划：AI 回复第 7 步最终发送完整性（fix-1）

## 原计划 / 子计划引用

- 原计划：`docs/plans/2026-07-20/ai-reply-07-final-send-integrity-plan.md`
- 复验轮次：1/3
- 既往 fix plan：无。

## 约束摘录

- I-1：只读取当前服务端 inbound/contact/account/QA，历史草稿状态不得成为发送 gate。
- I-2：subject、text、HTML 完成变量渲染后，复验最终 text、HTML 可见文本和**全部** href；不得截断。
- I-3/I-4：当前 canonical QA 与确定性风险是唯一阻断依据；纯人工不因空 facts 阻断，但无依据的数字、URL、高风险承诺仍须阻断。
- I-5：长度前缀 SHA-256 必含 `inboundProcessingId` 与**有序** canonical QA IDs；Message-ID 仅首次 reservation 生成并持久化。
- I-6/I-7：独立事务 reservation/CAS；IN_PROGRESS/UNKNOWN fail closed，只有 SAFE_TO_RETRY 可单 winner 重试。
- I-8/I-9：任何无法确认投递、unchecked delivery error 或成功后 finalize error 必须为 DELIVERY_UNKNOWN；每 attempt 至多一条 mail_record。
- I-10/I-11/I-12：首次成功才写 QA/audit；人工账号、multipart、INTRODUCTION 路径不变。
- I-13：所有 SMTP 后会写库的有限列在 SMTP 前校验；稳定 HTTP，不泄露正文/堆栈/凭据。

## 修正记录

| ID | P1 | 触发频率 |
|---|---|---|
| P1-1 | 最终 subject 未渲染/校验，href 仅收集 http(s)，最终复验对象不完整。 | 每次 subject 使用变量，或邮件含 `mailto:`、相对 URL、非 http(s) 链接时。 |
| P1-2 | 纯人工（空 QA）跳过无依据 claim 校验，数字/URL/高风险承诺可直达 SMTP。 | 任意纯人工编辑含此类内容时。 |
| P1-3 | 指纹遗漏 inboundProcessingId，且排序 canonical IDs，违背精确 payload identity。 | 同一联系人多封 inbound 或 canonical 顺序变化时。 |
| P1-4 | delivery unchecked exception、成功后 finalize failure 未转 DELIVERY_UNKNOWN，遗留无结果的 IN_PROGRESS。 | SMTP/runtime/数据库短暂故障；低频但会导致人工无法安全判断是否重发。 |
| P1-5 | `inReplyTo` 等后续持久化字段未在 SMTP 前按列界限验证。 | 异常长/损坏 inbound Message-ID 等边界输入时。 |

## 修复规格

### P1-1：最终检查覆盖 subject 与全部 href

- 文件：`PendingMailOperationService.kt`、`PendingMailOperationServiceTrustWorkbenchTest.kt`。
- subject 与 text/HTML 一样先做 placeholder 校验、用当前 account/contact 渲染；后续 `ComposedMail`、fingerprint、mail_record 与 response 使用同一 rendered subject。
- 用能枚举 HTML 全部 `href` 属性的现有/轻量解析方式，保留其原值进入 final validation input；不可只匹配 `https?://`。
- 验收：subject 变量带入高风险数字或未解析 placeholder、HTML-only `mailto:`/非 http(s) href 均在 SMTP 前阻断；安全 subject 保持 multipart/账号语义。

### P1-2：空 QA 不能放行无依据高风险内容

- 文件：`PendingMailOperationService.kt`、`PendingMailOperationServiceTrustWorkbenchTest.kt`。
- final check 对空 canonical facts 增加确定性的 source-free high-risk 检查：无事实来源的数字、URL、承诺不得当作“无 QA 证据”而放行；仍不得仅因空 facts、PARTIAL、历史 readiness 或 preflight 不可用阻断。
- 验收：纯人工安全正文发送；纯人工最终 text/HTML/href 的无依据 URL、数字、高风险承诺 422 且零 reservation/SMTP/mail record/audit。

### P1-3：完整且有序的 payload identity

- 文件：`ManualReplySendAttemptService.kt`、`PendingMailOperationService.kt`、`ManualReplySendAttemptServiceTest.kt`。
- `SendPayload` 增加 `inboundProcessingId`，以长度前缀写入 digest；canonical IDs 保持传入 canonical 顺序，禁止 `sorted()`。
- 重复命中除完整 hash 外，比较 account/recipient/subject 等 stored metadata；短 key 碰撞保持 fail closed。
- 验收：仅 inboundProcessingId 改变或仅 QA 顺序改变均产生不同 hash；相同 payload 复用持久化 Message-ID，且并发仅一个 claim。

### P1-4：投递与 finalize 的 UNKNOWN 收敛

- 文件：`ManualReplySendAttemptService.kt`、`MailSendAttemptRepository.kt`、`PendingMailOperationService.kt`、对应两份 service tests。
- delivery 调用及 success/failure finalize 用保守异常边界包裹；所有 unchecked delivery error、无确认结果、成功后 finalize error 通过独立事务尽力 CAS/更新为 `DELIVERY_UNKNOWN` 并写有界稳定前缀。
- 若 UNKNOWN 标记自身失败，仍只返回 409“发送状态未知，请勿重复发送”，保留 IN_PROGRESS 供核查；绝不再次 SMTP。
- 验收：每种异常后重复请求零 SMTP；可读到 UNKNOWN 或明确 IN_PROGRESS 占位；已成功 SMTP 后绝不降级为 SAFE_RETRY/FAILED。

### P1-5：SMTP 前持久化边界

- 文件：`PendingMailOperationService.kt`、`ManualReplySendAttemptService.kt`、相关 tests。
- 在 claim 前拒绝会写入有限列却超界的 final values，至少覆盖 `inReplyTo`（mail_record `VARCHAR(255)`）、recipient/account/message ID 对应 attempt 列；hash/最终正文不得截断后再校验或指纹。
- `errorSummary` 继续有界；错误 response 仅给稳定中文/阻断码。
- 验收：每个 255/256 边界均在 SMTP 前得到稳定 422，零 attempt/mail_record/audit。

## 当前状态（修复前）

- 编译：PASS（主代码 Kotlin 编译通过）。
- 目标测试：FAIL。`PendingMailOperationServiceTrustWorkbenchTest` 12 个中 4 个 error；根因是新增 Mockito Kotlin matcher（`any/eq`）传入非空参数时抛 `NullPointerException`，后续 matcher 被污染。其余目标报告：ManualReply 13/13、controller 11/11、ManualInitialOutreach 39/39、ManualOutreachTxHelper 3/3。
- `git diff --check`：PASS。
- Phase 3 已用尽 3 轮机械测试修正；不再直接改码。

## 合规审计

- I-1：✅ `PendingMailOperationService.kt:128-153` 重新读 inbound/contact/current selection；`209-220` payload 不含历史 draft/readiness。
- I-2：❌ `134-136` 只 trim subject、未渲染/placeholder 校验；`479-486` 仅 regex 收集 http(s) href。
- I-3：✅ `140-160` 当前 inbound/research canonicalize，候选全失效 422；`415-421` 使用 selection IDs。
- I-4：❌ `497-504` 仅 `carriesQa` 才作 source claim 校验，空 QA 直接跳过。
- I-5：❌ `ManualReplySendAttemptService.kt:68-80` 未编码 inboundProcessingId，且 `79` 对 canonical IDs 排序。
- I-6：✅ `ManualReplySendAttemptService.kt:83` 独立 `REQUIRES_NEW`；`PendingMailOperationService.kt:222-235` claim 返回后才 SMTP。
- I-7：✅ `ManualReplySendAttemptService.kt:104-174` 各状态 fail closed / CAS；`101-113` content/full hash/recipient 碰撞阻断。
- I-8：❌ `PendingMailOperationService.kt:235-245` 无 unchecked/finalize exception 边界，异常会留下 IN_PROGRESS；`565-600` 仅处理已返回的 DeliveredMail。
- I-9：❌ `ManualReplySendAttemptService.kt:198-283` 正常 finalize 一 attempt 一 record，但 P1-4 异常路径无结果记录/状态收敛。
- I-10：✅ `198-253` success transaction 写 association，`recordSendAudit` 在 success 后 best effort；DEDUP 分支 `296-305` 不审计。
- I-11：✅ `464-469` 仍经 `getManualSendAccount`；`227-234` 保持 multipart text/html 与稳定 Message-ID。
- I-12：✅ `MailSendAttemptStatus.kt:3-9` additive；目标 INTRODUCTION 回归报告通过。
- I-13：❌ `209-220`/`241-275` 在 SMTP 后才会触发 `inReplyTo` 等有限列写入，缺少 claim 前边界拒绝。
- Deleted code：✅ 无计划要求删除项遗漏。
- No extras：✅ 仅计划列出的 8 个实现/测试文件变更。

## 语义完整性审计

- Accumulation check：✅ 无时间窗聚合计数。
- State machine check：❌ `DELIVERY_IN_PROGRESS` 在 `PendingMailOperationService.kt:235-245` 的 runtime/finalize exception 后无转移至 UNKNOWN，重复请求只获 409，不能形成计划要求的持久化 unknown 结果。
- Cross-plan check：❌ AI 回复分步链；最终发送 payload 的 I-5 身份字段与本子计划声明不一致，导致跨同一 contact 的 inbound identity 被折叠。其余 INTRODUCTION/additive contract 回归未见破坏。
