# Fast-P Child Brief — 02-diagnostics

- 唯一契约（子计划）: `docs/plans/2026-10-14/batch-filter-02-diagnostics.md`
- 全局约束（主计划）: `docs/plans/2026-10-14/batch-filter-main.md`
- Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-14-batch-filter-main`
- Branch: `fast/2026-10-14-batch-filter-main`
- Base SHA: `__CHILD1_CODE_HEAD__`（= 01-region 的终止 Code head，由控制器在派发前替换）
- 依赖: 01-region（已 LIGHT_PASS；其地区语义是本阶段母集与 matcher 的输入）
- 实施提交信息（必须逐字）: `feat(fast-p): implement 02-diagnostics`

## 交付范围

只允许修改/新增以下 10 个文件（子计划「变更文件清单」逐字）：

|序号|精确路径|变更|
|---|---|---|
|1|src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt|共用预检查接入|
|2|src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchRecipientSelectionService.kt|事实与条件分离|
|3|src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchEmailVerificationService.kt|只读历史解释|
|4|src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchRecipientPreflightService.kt|新增服务/DTO|
|5|src/main/kotlin/com/weibo/talentintroduction/campaign/repository/BatchRecipientReadRepository.kt|新增只读批查|
|6|src/main/kotlin/com/weibo/talentintroduction/mail/controller/BatchSendConfigController.kt|诊断响应/入口|
|7|src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchRecipientPreflightServiceTest.kt|新增单测|
|8|src/test/kotlin/com/weibo/talentintroduction/campaign/repository/BatchRecipientReadRepositoryIT.kt|新增真实 SQL 测试|
|9|src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt|路径集成回归|
|10|src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchRecipientSelectionServiceTest.kt|准入与去重回归|

不得新增 Flyway 迁移、不得新增/修改持久字段、不得改动 ES mapping、不得改动其他文件。

## 必须保持的不变量

- **I-1（母集与原因计数可核对）**：母集只应用所选漏斗层级与标签，保留原真实 docId/ORCID 去重；不得先应用地区/状态/研究方向/类型/模板再自称全量解释。总排除按人去重；`reasonHits` 可重叠、不得相加冒充总排除。准入已通过但地区失败不得标 `NOT_DISCOVERY`。
- **I-2（共用预检查与快照）**：预估与执行目标选择调用同一 preflight、同一快照；既有发送/抑制/绑定/永久失败保护不放宽；发送前实时保护保留。
- **I-3（邮箱历史与实时验证开关分离）**：两开关 OFF 时不读邮箱历史、不调用供应商；历史过滤 ON 沿用有效最新 undeliverable 排除；实时验证 ON 时按当前 `allowedStates` 解释可复用结果，无结果标 `AWAITING_VERIFICATION`。有效期、`request_count>0`、原始非 error 记录、`checkedAt/id` 排序沿用现有仓储，禁止发明第二套缓存有效性。
- **I-4（预估零业务副作用）**：预估只 SELECT/ES 读取；不得创建 campaign/contact/task/Pending 验证/发送尝试/准入；不得调用 `verify()`/SMTP/供应商。
- **I-5（规模、隐私、失败显式）**：500 条一批；每原因最多 20 样本并标 `truncated`；按 docId 诊断每请求最多 200；响应含 `asOf`、母集数、去重排除数、通过数、待执行验证数、`reasonHits` 与样本；失败/超时返回不可用或明确 `partial`；日志只存聚合与限量样本。
- **I-6（模板开关按实际内容解释）**：开启时复用 `effectiveRequiredKeys` 与现有 `batchTemplateGate`；关闭时个性化缺项不排除；缺模板/模板不可用按任务配置错误。
- MAIN M-2/M-3：共享字段唯一含义（baseline / excludedUnique / eligibleBeforeLiveVerification / awaitingVerification / reasonHits / asOf / partial / verificationPhase），`baseline = excludedUnique + eligibleBeforeLiveVerification`，`awaitingVerification` 是 `eligibleBeforeLiveVerification` 的子集；旧响应字段含义兼容。
- MAIN M-5：验证只用本地 fixture，真实 SQL IT 必须实际执行（Docker testcontainers，`-DmysqlIt=true`）；跳过 IT 不能通过本阶段。

## 必须运行的命令（JDK11）

```bash
cd /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-14-batch-filter-main
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest='BatchRecipientPreflightServiceTest,BatchRecipientSelectionServiceTest,ManualInitialOutreachServiceTest,BatchEmailVerificationServiceTest,CountryContinentMappingTest,ExpertSearchServiceTest' -DfailIfNoTests=false
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest='BatchRecipientReadRepositoryIT' -DmysqlIt=true -DfailIfNoTests=false
```

- 第二条为真实 MySQL 集成测试（testcontainers，参考 `src/test/kotlin/com/weibo/talentintroduction/campaign/repository/BatchEmailVerificationRepositoryIT.kt` 的既有 gating 与迁移建表约定）。必须实际运行并给出结果；不得静默跳过。
- 第二条命令不得依赖生产库；表结构由真实迁移建立。

## 下游接口（03 直接消费，必须逐字固定）

- 预估/诊断响应字段名与语义：`baseline`、`excludedUnique`、`eligibleBeforeLiveVerification`、`awaitingVerification`、`reasonHits`（每原因含命中数与 ≤20 样本、超限 `truncated`）、`asOf`、`partial`、`verificationPhase`（`DISABLED`/`CACHED_ALLOWED`/`CACHED_REJECTED`/`AWAITING_VERIFICATION`）。
- 旧响应字段保留原含义；新增字段为兼容扩展；旧 UI 仍能消费原字段。
- `POST /api/mail/batch-send/recipient-diagnostics` 请求形状：现有 snapshot 形状 + `docIds`（1～200）；母集外 `OUTSIDE_BASELINE`、找不到 `NOT_FOUND`。
- 共同 fixture（MAIN G-2）：10 人母集 → baseline=10、excludedUnique=4、eligibleBeforeLiveVerification=6、awaitingVerification=3、原因命中合计 6。

## 报告与返回

- 完整执行报告写入：`docs/plans/fast/2026-10-14-batch-filter-main/children/02-diagnostics/execution.md`。
- 只提交产品代码为 `feat(fast-p): implement 02-diagnostics`；**不得**把 `docs/plans/fast/` 下任何 fast-p 证据文件纳入该提交。
- 不 push、不 merge、不 rebase、不 amend、不 squash、不改写历史。

返回：`READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT` + commit SHA + 命令摘要 + 报告路径。
