# 06 · P-F：批量任务收件人预估

> 主计划：`docs/plans/2026-08-13/README.md`（计划集索引）
> 本计划：`docs/plans/2026-08-13/06-recipient-count-preview.md`（commit:37ebb355894783cbf4f380484359bf6218d62949）——**该文件即完整契约，必须全文阅读后执行**
> 依赖：**05（P-E，已落地）** ｜ 子系统：2（后端 + 前端）｜ 6 文件

## 授权文件（Authorized Files）

| # | 文件 | 类型 |
|---|---|---|
| 1 | `src/main/kotlin/…/campaign/service/ManualInitialOutreachService.kt` | 改 |
| 2 | `src/main/kotlin/…/mail/controller/BatchSendConfigController.kt` | 改 |
| 3 | `src/main/resources/static/index.html` | 改 |
| 4 | `src/main/resources/static/app.js` | 改 |
| 5 | `src/test/kotlin/…/campaign/service/ManualInitialOutreachServiceTest.kt` | 改 |
| 6 | `docs/knowledge/campaign/K-recipient-count-preview-parity.md` | 新增 |

**禁止**：`styles.css`（零 diff）；`GET /types/{sendType}/pending-count` 端点行为改动；`batchConfigEditorVolumeHint` 提示改动；其他文件；`docs/plans/fast/*`。

## 关键不变量（详见计划文件）

- **I-1** 预估与执行同源：`countBySnapshot(snapshot)` = `RecipientScope.fromSnapshot` + `buildRetryableTargets` + `countEsTargets`，复用执行路径同一套代码，不得另写查询逻辑；MATERIAL_REMINDER 分支复用 `buildMaterialReminderSnapshot(...).targets.size`。
- **I-2** 入参即 `BatchExecutionSnapshot` 本身，不另设 DTO。
- **I-3** 预估无副作用：不创建 task_execution、不写 expert_contact、不建 campaign（campaign 用只读 `findByCampaignCode("MANUAL_OUTREACH")` 判空，**不得**调 `getOrCreateManualCampaign()`）。
- **I-4** 端点用 POST（照抄 `/cron/preview` 既有决策）：`POST /api/mail/batch-send/recipients/preview`，返回 `PendingOutreachSummary`。
- T-1 核心断言：`countBySnapshot(s).totalSendable` == 同 snapshot 下执行路径 `totalEstimate`。
- T-3 前端：两面板各加提示行（复用 `.batch-config-editor-hint`，styles.css 零改动）；防抖 500ms；加载中"计算中…"；失败显示"预估不可用"（不弹报错）；请求序号丢弃过期响应。

## 必跑命令（Required Commands，zulu-11，全部 fresh 运行）

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=ManualInitialOutreachServiceTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package
git diff --check
```

通过判据：退出码 0，`Tests run: N, Failures: 0, Errors: 0`。

## 基线（Baseline）

`mvn clean package` exit 0：surefire 2378+ / 0 / 0 / 4 skipped（01/03/05 落地后以 fresh 实际为准）；JS 496 pass。

## 下游接口（Downstream Interfaces）

- 无下游子计划。验收标准：端点 `@PostMapping` 且入参类型 `BatchExecutionSnapshot`；单测 `verifyNoInteractions(taskExecutionService)` + campaign 不存在时 retryable=0；`git diff styles.css` 空。

## 执行契约

1. 先读 `skill://execute-p` 并严格遵循。
2. 只改上述 6 个授权文件；只提交授权文件；不得提交 `docs/plans/fast/*`。
3. 最终实现状态上 fresh 运行全部必跑命令。
4. 实现提交：`feat(fast-p): implement 06`。
5. 完整执行结果追加写入 `<worktree>/docs/plans/fast/batch-send-status-consistency/children/06/execution.md`。
6. 返回：`READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT` + commit SHA + 命令摘要 + 报告路径。
7. 不做：review 后续计划、修复无关问题、push、merge、amend、squash、rewrite history。
