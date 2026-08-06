# Fast-P Child Brief — p4-task0-rfc8058

- Master plan (global authority): `docs/plans/2026-08-06/00-main-plan-mail-reliability.md` — 执行顺序 ①「P4-任务 0 单独先行」。
- Child plan (唯一权威文本): `docs/plans/2026-08-06/material-reminder-02-headers-personalization.md`。
- 本 child 只执行该计划 **阶段 0 任务 0（J-7）** 与 **阶段 D 任务 8 中 J-7 对应的单测用例**。
  该计划其余阶段（阶段 A/B/C/D 其余任务 1-9）由 master 计划执行顺序 ⑤ **明确推迟**（"不要提前动"），
  **不在本 child 范围**。严禁借机改动退订头抑制、From 显示名、称呼个性化、V84 迁移等任何推迟内容。
- child_base_sha: `92a678b`（fast/mail-reliability HEAD）。
- 工作区：`/Users/lukai/IdeaProjects/weibo-talent-introduction/.worktrees/mail-reliability`（分支 `fast/mail-reliability`）。

## Authorized Files（排他，只允许改这两处）

1. `src/main/kotlin/com/weibo/talentintroduction/mail/service/SmtpMailDeliveryService.kt`
   —— **仅一行**（任务 0，计划 `:54`）：
   - 改前：`message.addHeader("List-Unsubscribe-Post", "List=One-Click")`
   - 改后：`message.addHeader("List-Unsubscribe-Post", "List-Unsubscribe=One-Click")`
2. `src/test/kotlin/com/weibo/talentintroduction/mail/service/SmtpMailDeliveryServiceTest.kt`
   —— **仅新增一个用例**（任务 8 的 J-7 用例）：
   - 断言 `message.getHeader("List-Unsubscribe-Post").single() == "List-Unsubscribe=One-Click"`（**相等断言，不得用 `contains`**）；
   - 同一用例内断言 `List-Unsubscribe` 头**逐字未变**（https 与 mailto 两段及其顺序）。

其余 6 个含 `ComposedMail(` 的文件、`ManualExpertMailService.kt`、任何 migration、任何前端文件、任何知识库文件**一律不得触碰**。

## Key Invariants

- **J-7**：`List-Unsubscribe-Post` 值必须逐字为 `"List-Unsubscribe=One-Click"`（RFC 8058 §5 ABNF 是相等不是包含）；`List-Unsubscribe` 头（`:53`）逐字不变；`:49-52` 四行（URL 构造、mailto 构造）逐字不变。
- **M-1（文件所有权）**：`SmtpMailDeliveryService.kt` 归 P4 独有；P1/P2/P3 已声明零改动。本 child 也不得改动任何其他计划的文件。
- **M-3**：本 child 不得新增任何 Flyway migration。
- **M-6**：**不得**把「Gmail 界面出现退订按钮」写入判据 —— DKIM `h=` 未覆盖 List-\* 头（腾讯企业邮 `s=card2607`，`h=Date:From:To:Message-ID:Subject:MIME-Version`），代码侧无解（J-7 第二个 ⚠️）。
- 其余邮件类型的退订头行为逐字不变；不引入任何 mailType 判断；`unsubscribeTokenService.enabled()` 门控不动。

## Required Commands（必须全部运行，并记录退出码与输出要点）

```bash
# 本计划相关测试类
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=SmtpMailDeliveryServiceTest

# 全量测试（回归门禁，P4 验收标准「回归」条）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test

# 空白/换行卫生
git diff --check
```

通过判据：退出码 0 且输出含 `Tests run: N, Failures: 0, Errors: 0`；`git diff --check` 无输出。
既有 16 条 `SmtpMailDeliveryServiceTest` 用例语义不改，全部保持绿色。

## Downstream Interfaces

- 本 child 无下游依赖（P1/P2/P3 均声明 `SmtpMailDeliveryService.kt` 零改动，见 M-1）。
- 留给 P4 其余阶段（被推迟）的接口：投递层退订头写入块保持任务 0 修改后的唯一差异形态。

## Deliverables

1. 实现提交：`feat(fast-p): implement p4-task0-rfc8058`（**只含上述 2 个文件**；fast-p 报告/日志不得进入该提交）。
2. 执行报告：`docs/plans/fast/mail-reliability/children/p4-task0-rfc8058/execution.md`（**不提交**，由 controller 单独提交证据）。
3. 返回：`READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT`，commit SHA，命令摘要，报告路径。
