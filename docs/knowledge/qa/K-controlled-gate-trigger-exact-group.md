---
id: K-controlled-gate-trigger-exact-group
domain: qa
created: 2026-08-21
last_used: 2026-08-21
hit_count: 0
source: create-p:qa-gate-visibility
severity: P1
---

经验：受控事实门禁（V82 四个原子事实组）的触发条件若写成「覆盖集**含任一**受控键」，
会连带锁死所有"顺带提及承诺"的总览型规则 —— 它们既凑不成合法组，正文也永远不可能等于任何
canonical body，于是**不可保存、不可启用**，且运营在 UI 上无法自救。

实例：`QaCoverageKeyCatalog.validateControlledBody` 用 `parsed.none { it in controlled }` 提前返回，
而 `V76:24` 给 `Program overview`（id=24）回填的 11 个键里含 `fees.policy` + `confidentiality.materials`。
V82 引入约束时未回头清理该行，规则 24 就此砖死。更讽刺的是原计划
（`docs/plans/2026-08-04/trust-reply-atomic-facts-and-duplicate-guard.md:213`）的验收标准白纸黑字写着
「Program overview 等非受控 legacy 规则不被该门禁拒绝」—— 实现与计划自身的验收标准相矛盾。

正确做法：触发条件应是「覆盖集**恰好等于**某受控组」。语义上，
`coverage_keys` 是"这条规则**自称**是该事实的权威出处"的授权声明，不是"正文提到过该话题"的标签。
只有真正自称权威出处的规则才该受正文逐字约束；顺带提及的总览规则、以及组内键勾不全的规则
（如只勾 `contract.party` 不勾 `contract.terms`）都不构成权威出处，应放行。

配套：引入此类"集合相等"约束时，必须同时对**存量违规行**做数据清理迁移，
不能把清理成本留给运营的下一次点击。

关联：[[K-qa-coverage-keys-management-write-boundary]]、[[K-coverage-key-orphan-makes-fact-unreachable]]、
[[K-named-fixture-must-use-real-row]]（这条缺陷本该被测试抓住，却因 fixture 是自造替身而漏网）。
