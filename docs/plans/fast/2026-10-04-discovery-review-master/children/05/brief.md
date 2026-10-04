# Fast-P Child Brief — 05（批量目标同源与模板开关真实生效）

## 身份与边界

- Master plan（批准版，字节冻结）：`docs/plans/2026-10-04/discovery-review-master.md`，identity `commit:07beaafc111a1b14ed3c48d514db527c8a13fc31`。
- 本 child 批准计划（完整合同，必须先通读）：`docs/plans/2026-10-04/discovery-review-05-explicit-send.md`，identity `commit:07beaafc111a1b14ed3c48d514db527c8a13fc31`。全部章节逐条生效；本 brief 摘要与计划原文冲突时以计划原文为准。
- Worktree：`/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-04-discovery-review-master`；branch `fast/2026-10-04-discovery-review-master`；`child_base_sha = <见派发消息>`。
- 依赖：01–04。下游：06（前端显示准入计数与模板开关文案；显式条件与预估口径）。
- 关键范围事实（master 计划表与自检）：**D1（历史发送政策）未定案。本子计划可开发统一计算能力；不得启用发送切换，不得声称"所有过滤已清零"。** 现行发送分支中的退订、历史已发、账号绑定、永久失败、材料提醒无 contact、旧 cron 无页面快照等 D1 项：在 D1 定案前保留为待决冲突，不得删除、不得改名成"技术限制"、不得静默扩权（计划 5.6）。
- 若计划内部条款（"不得启用发送切换"与实现方案 3 "删除各发送点对 matchesDiscoveryOutreach 的调用"）在本次执行中无法唯一确定行为，按 execute-p 返回 `PLAN_CONFLICT` 并说明冲突点，不要静默选择。
- 取证材料（worktree 内只读）：`docs/plans/2026-10-04/discovery-review-audit.md`（E3/E4/E5）、`docs/plans/2026-10-04/discovery-review-evidence/`（batch-gates.txt、shared-send-writers.txt）。
- 基线命令结果：`docs/plans/fast/2026-10-04-discovery-review-master/baseline.md`。

## 全局约束

1. 只允许修改「Authorized Files」表内 10 个文件；不得新建白名单外文件。其余 Kotlin/SQL/迁移/前端/文档全部只读。
2. 不得修改 `docs/plans/**` 内的计划与其他证据；本 child 唯一可写非产品文件是执行报告 `docs/plans/fast/2026-10-04-discovery-review-master/children/05/execution.md`。fast-p 报告不进入产品提交（控制方单独提交）。
3. 不得 push、merge、rebase、squash、amend、reset；不得改写已有提交。产品代码只提交一次：`feat(fast-p): implement 05`。
4. 计划与代码冲突、需要白名单外文件、需要新行为或需要修订计划：返回 `PLAN_CONFLICT` / `BLOCKED`，不得自行扩范围或改计划。
5. 禁止联网抓取、连线上 MySQL/ES、发信、部署；不得新增依赖；不得改 `pom.xml`。
6. 测试库必须是本机容器 `ti-mysql-it`（localhost:3306，root/root，库 `talent_introduction`，带 `allowPublicKeyRetrieval=true`）；禁止线上/日常库。Docker = OrbStack。
7. 不新增迁移、不改数据库/ES 字段；`ManualOutreachTxHelper`、`SmtpMailDeliveryService`、选号 `SenderAccountAssignmentService`、任务节奏/取消/配额语义均不在授权清单，不得改动。
8. 保持：人工单发/自动回复/会议/材料提醒共享组件与 SMTP 现有策略不被改坏；I-5 用参数默认值保持既有调用行为（`enforcePersonalizationGate` 默认 true；`ManualExpertMailService` 内部策略参数默认保持人工单发）。
9. 发送账号/节奏是执行配置；`senderAccountCodes`/`roundSize` 等不得冒充专家学术不合格原因。
10. 运行期结果里每个排除必须对应准入结论、现有显式条件或用户明确认可的 D1 政策；剩余 D1 分支保留并在报告中逐条列出（对应计划 5.6 与主计划 I-7）。

## Authorized Files（10）

| # | 精确路径 | 改动 |
|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/campaign/domain/BatchExecutionModels.kt` | 移出隐式学术门禁、解释显式条件 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchRecipientSelectionService.kt` | 新增统一选择/解释（逐人准入结果、filterKeys、原因去重） |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt` | 预估/两循环/重试接入统一 selector |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/campaign/service/InitialOutreachService.kt` | 按计划接入共用准入（如与"不得启用发送切换"冲突则返回 PLAN_CONFLICT，不得静默） |
| 5 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/IntroductionMailComposer.kt` | `evaluateForBatch` 共用判定 + 显式参数 `enforcePersonalizationGate`（默认 true） |
| 6 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualExpertMailService.kt` | 材料批量调用显式传开关；默认保持人工行为 |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchRecipientSelectionServiceTest.kt` | 新增真值表 |
| 8 | `src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt` | preview/new/retry/material |
| 9 | `src/test/kotlin/com/weibo/talentintroduction/campaign/service/InitialOutreachServiceTest.kt` | 旧入口回归/准入 |
| 10 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/BatchTemplateGateParityTest.kt` | 新增跨两 composer/非批量回归 |

## 关键不变量（计划 I-1～I-5；逐字以计划为准）

- I-1：发送只消费 04 的持久准入结果；AUTO_PASSED/MANUAL_APPROVED/LEGACY_APPROVED 同等准入；同身份批准后不再调用基础资格/机构/国家/凭证门禁；未初始化/身份变化/人工拒绝是明确展示的准入状态，不混入"发送失败"；非发现专家保留原入口。
- I-2：过滤只来自计划列出的显式控件；状态空集合不限；类型空集合仍零人；模板门禁关闭不得再产生 PERSONALIZATION_INCOMPLETE；候选首发空状态不得偷偷切 NOT_CONTACTED 基座。
- I-3：统一 selector 返回逐人准入与全部不匹配 filterKeys；同一记录多原因重复计"原因命中"、总排除按人去重；preview 无写入；执行/重试同 selector；实时验证未运行只显示"待执行验证"。
- I-4：模板门禁开启时用同一模板 ID/版本/seed/实际选中变体与 `MailVariableService` 实际值做完整判定；关闭时不检查个性化缺项，`${key|fallback}` 正常生效，裸变量缺值按 renderText 变空串不伪造；语法错误/残留占位符是明确模板错误。
- I-5：共享组件不改变其他发送路径；不得改全局 PersonalizationGateService 语义、不得一律关闭 SMTP 抑制检查；D1 业务判断仍列待定。

## 必需命令（fresh 运行，逐条记录 exit code 与计数；与 baseline.md 对照）

```sh
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -DskipTests test-compile
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=BatchRecipientSelectionServiceTest,ManualInitialOutreachServiceTest,InitialOutreachServiceTest,BatchTemplateGateParityTest test
```

## 下游接口（06 依赖）

- 预估/执行同一 selector 的名单与计数；准入计数（准入通过/待审核/显式条件排除/本次目标）与模板门禁文案数据来源；`Issue`/原因 key 供前端显示。

## 上游产出（01–04，由控制方在派发消息中补全）

- 见派发消息中的 01–04 code head 与 execution.md 摘要。

## 交付物

- 产品提交：`feat(fast-p): implement 05`（只含授权产品/测试文件）。
- 执行报告：`docs/plans/fast/2026-10-04-discovery-review-master/children/05/execution.md`，包含 execute-p 规定字段；必须逐条列出保留的 D1 分支与本次实际生效范围。
- 返回：`READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT` + commit SHA + 命令摘要 + 报告路径。不得声明验证通过。
