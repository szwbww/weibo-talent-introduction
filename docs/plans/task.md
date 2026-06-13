# Task List: 2026-06-13-manual-bulk-outreach-twelfth-verification-fix-plan

| Task ID | Task Description | Status | Evidence |
|---|---|---|---|
| Task-01 | P0: 修复 nullable attempt 编译错误 | not_started | |
| Task-02 | P0: 从可 claim 状态中安全移除 PREPARED | not_started | |
| Task-03 | P0: 修复 confirmSent，统一走 RECONCILING 成功事务入口 | not_started | |
| Task-04 | P0: 异常路径增加 markPostSendPersistenceUncertain 并避免旧对象 save 覆盖 | not_started | |
| Task-05 | P1: 在 recordFailure 中执行来源状态白名单并返回 outcome | not_started | |
| Task-06 | P1: 真实观察 PREPARED 状态并重构准备与推进路径 | not_started | |
| Task-07 | P1: 新增真实 MySQL 竞争与并发测试（并发 claim, confirmSent, 竞争对账） | not_started | |
| Task-08 | P1: 新增真实 task_execution 状态集成测试 | not_started | |
| Task-09 | P2: 修正 docs/plans/task.md 验收证据并跑通非迁移全部门禁 | not_started | |
