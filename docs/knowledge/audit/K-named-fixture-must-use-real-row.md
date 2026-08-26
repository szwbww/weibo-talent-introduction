---
id: K-named-fixture-must-use-real-row
domain: audit
created: 2026-08-21
last_used: 2026-08-21
hit_count: 0
source: create-p:qa-gate-visibility
severity: P1
---

经验：验收标准或测试用例名里**点名了某条真实数据**（"Program overview 不被该门禁拒绝"、
"id=24 保持可保存"）时，fixture 必须取该行的**真实值**。自造一个同名替身，会让测试名与
所测内容脱钩 —— 测试全绿，线上砖死，而且事后翻测试列表会误以为该场景已覆盖。

实例：`QaRuleManagementServiceTest.kt:931` 用例名
`legacy non controlled coverage is unaffected by the body gate`（注释指向 Program overview），
fixture 却是自造的 `coverageKeys = listOf("programme.purpose", "programme.structure")` +
`answerBody = "Program overview body."` —— 刻意避开了受控键。
而真实的 id=24 覆盖串（`V76:24` 回填）含 `fees.policy` 与 `confidentiality.materials` 两个受控键，
恰恰是会被门禁拒绝的那一类。结果：原计划
`trust-reply-atomic-facts-and-duplicate-guard.md:213` 的验收标准从未真正被验证过。

正确做法：
- 用例名点名真实行 → fixture 逐字取该行现值（从迁移 SQL 或线上导出），并在用例里注明取值出处。
- 想验证"某类通用形态"时，用例名就写通用形态，不要挂真实行的名字。
- 计划的验收标准里凡写"某条现存数据不受影响"，必须在任务里指定 fixture 来源，
  否则执行 agent 默认会造一个最省事的替身。

关联：[[K-plan-quantified-claims-need-grep-receipts]]（计数与全称判断要 grep 回执；
这条讲的是**测试数据**也要回执）、[[K-controlled-gate-trigger-exact-group]]。
