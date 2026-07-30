# 修复计划：AI 回复第 7 步最终发送完整性（fix-2）

## 原计划 / 子计划引用

- 原计划：`docs/plans/2026-07-20/ai-reply-07-final-send-integrity-plan.md`
- 上轮修复计划：`docs/plans/fix/ai-reply-07-final-send-integrity-plan/fix-1.md`
- 复验轮次：2/3。

## 约束摘录

- I-2：subject、text、HTML 完成变量渲染后，必须以最终可发送内容复验；检查对象不得截断，须覆盖 HTML 的全部 href。
- I-4：当前确定性风险（无来源数字/URL/承诺等）必须在 SMTP 前阻断。
- I-5：最终 canonical payload 使用同一 rendered subject 作为身份字段。
- I-13：输入边界失败必须在 SMTP 前返回稳定结果。

## 修正记录

| ID | P1 | 触发频率 |
|---|---|---|
| P1-1 | `renderedSubject` 未进入 `finalValidationText`，且 href 正则只匹配带单/双引号的属性。风险数字、URL 或承诺可仅放在 subject，或置于合法的无引号 `href`，绕过 SMTP 前复验。 | 每次 subject 含变量带入风险文本，或 HTML 使用无引号 href；人工编辑/API 请求均可触发。 |

## 修复规格

### P1-1：最终复验覆盖 rendered subject 与所有 href 语法

- 文件：`src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt`、`src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTrustWorkbenchTest.kt`。
- `buildFinalValidationText` 的输入加入 `renderedSubject`，并只使用这一份 rendered subject 进行复验、指纹和 SMTP；不得回读 raw subject。
- 将 href 提取改为可枚举 HTML `href` 属性的解析方式，覆盖单引号、双引号和无引号值；保留原始值加入最终复验，不限 http(s)。
- 增加回归：subject 变量渲染后含无来源数字/URL/高风险承诺，以及无引号 `href=mailto:...` / 非 http(s) href，均在 `prepareAndClaim` 与 SMTP 前返回 422；安全 subject/链接不改变 multipart、账号或指纹语义。

## 当前状态

- 编译：未完成。`mvn clean test -Dtest=...` 在 Kotlin compile 后由本地执行器提前结束，未产出本轮 surefire 报告。
- 目标测试：未完成。上次可用报告为 12 tests、3 errors；根因是测试中的 Mockito matcher 残留。复验中已作仅测试 matcher 的机械修正，仍需在可完成 Maven 进程的环境重跑。
- `git diff --check`：PASS。

## 合规审计

- I-1：✅ `PendingMailOperationService.kt:128-162` 每次读取 inbound/contact/account，并按当前 inbound/research canonicalize；未读取 draftHash/readiness。
- I-2：❌ `PendingMailOperationService.kt:164-189` 已渲染 subject/text/HTML，但 `539-546` 只拼 text/html/href，漏掉 rendered subject；`541` 的 regex 只覆盖引号包裹 href。
- I-3：✅ `PendingMailOperationService.kt:142-160, 579-583` 非空候选重新 canonicalize，全部失效阻断；空候选仍走当前态选择。
- I-4：❌ `PendingMailOperationService.kt:199-211, 557-573` 已做当前风险校验，但因 I-2 漏掉 subject/无引号 href，确定性风险仍可到达 claim/SMTP。
- I-5：✅ `ManualReplySendAttemptService.kt:74-92` 长度前缀包含 inboundProcessingId 且保留 canonical ID 顺序；`PendingMailOperationService.kt:214-225` 使用 rendered subject 构建 payload。
- I-6：✅ `ManualReplySendAttemptService.kt:95-151` `REQUIRES_NEW` reservation/CAS 在 delivery 前完成；`PendingMailOperationService.kt:236-253` 仅 claim winner 调 SMTP。
- I-7：✅ `ManualReplySendAttemptService.kt:154-197` SENT/IN_PROGRESS/UNKNOWN/FAILED_SAFE_TO_RETRY/FAILED 均按 fail-closed 分支处理。
- I-8：✅ `PendingMailOperationService.kt:252-353, 634-669` unchecked、无确认结果、finalize 异常均尽力收敛 UNKNOWN；收敛失败保留 IN_PROGRESS 并返回 409。
- I-9：✅ `ManualReplySendAttemptService.kt:213-269, 291-336` 通过 attempt FK 查找并更新同一 mail record；非 SENT 写 FAILED，sentAt 仅在成功时写。
- I-10：✅ `ManualReplySendAttemptService.kt:250-267, 339-410` 成功事务内按 ordinal 写关联，提交后 best-effort 审计；dedup/failure/unknown 不进入审计调用。
- I-11：✅ `PendingMailOperationService.kt:162, 241-248` 仍经人工账号选择并继续 multipart text/html、稳定 Message-ID。
- I-12：✅ `MailSendAttemptStatus.kt:3-9` 仅新增状态；`ManualReplySendAttemptService.kt:95-197` 人工 key 与 INTRODUCTION key 隔离。
- I-13：✅ `PendingMailOperationService.kt:135-138, 164-167, 191-197, 228-234` subject、final validation text、inReplyTo 在 claim/SMTP 前限制；schema 约束 `V1__create_business_tables.sql:102-105`、`V23__create_mail_send_attempt_and_add_mail_record_error.sql:3-8` 覆盖持久化列。
- Deleted code：✅ 无计划删除项。
- No extras：⚠️ 观察：`AiReplyHighRiskClaimValidator.kt` 改为 public，不在原 8 文件范围且对当前模块调用非必要；未见生产影响，不作为本轮 P1。

## 语义完整性审计

- Accumulation check：✅ 无时间窗聚合。
- State machine check：✅ PREPARED → DELIVERY_IN_PROGRESS → SENT / FAILED_SAFE_TO_RETRY / FAILED / DELIVERY_UNKNOWN；UNKNOWN 与 IN_PROGRESS 均 fail closed。
- Cross-plan check：✅ 第 6 步仅作写作辅助；当前 payload 使用 inbound identity 和 canonical 顺序，INTRODUCTION 路径不进入人工 key。
