# QA 重构 03：单一回复策略 — 修复计划 1

## 原计划 / 子计划引用

- 总计划：`docs/plans/2026-07-17/qa-fact-card-trust-reply-master-plan.md` Phase 3。
- 子计划：`docs/plans/2026-07-17/qa-refactor-03-reply-policy.md`。
- 本轮无此前修复计划。

## 约束摘录

| 约束 | 要求 |
|---|---|
| I-1 | `reply_policy` 是唯一权威；runtime 写入必须由 policy 派生旧布尔影子，不得由旧布尔反推。 |
| I-2 | policy 的 domain 默认值为 `REVIEW`；固定影子映射为 AUTO=`1/0`，REVIEW/NEVER=`0/1`。 |
| T3 | `replyPolicy` 是 create/update 的必填请求；旧布尔 request 字段保留为 nullable 兼容字段且必须忽略。 |
| I-3/I-4/I-5 | NEVER 在匹配前过滤；聚合取最严格策略；enabled 与 policy 正交。 |

## 修正记录表

| ID | 优先级 | 发现 | 触发频率 |
|---|---|---|---|
| P1-1 | P1 | `QaRule.replyPolicy` 的 Kotlin 默认值为 `AUTO`，与计划和 V80 的 `REVIEW` 默认值冲突。任何未显式赋 policy 的 domain 构造、fixture 或未来写路径会得到自动发送资格，违背 fail-safe 默认。 | 低频：新增未复用管理 service 的构造路径、测试/脚本漏传 policy 时；触发后可能把应审核规则当 AUTO。 |
| P1-2 | P1 | create/update request 的 `autoReplyEnabled`、`handoffRequired` 仍为非空 `Boolean`。旧客户端显式传 `null` 时会在 JSON 绑定阶段 400，未满足“nullable 兼容且忽略”的 API 契约。 | 低频：旧客户端/脚本序列化旧可选字段为 `null` 时；会阻断本应由 `replyPolicy` 决定的保存。 |

## 修复规格

### P1-1：统一 fail-safe 默认策略

- 文件：`src/main/kotlin/com/weibo/talentintroduction/qa/domain/QaRule.kt`。
- 将 `QaRule.replyPolicy` 默认值从 `QaReplyPolicy.AUTO.name` 改为 `QaReplyPolicy.REVIEW.name`；不改 V80 回填优先级、enum 集合或影子映射。
- 更新受影响测试 fixture：需要 AUTO 行为的 fixture 必须显式传 `QaReplyPolicy.AUTO.name`，不能依赖 domain 默认值；新增断言覆盖默认 REVIEW 与 `0/1` 影子语义。
- 预期：未指定 policy 永不获得 AUTO；管理 service 仍以 `withReplyPolicy()` 写入请求指定值。

### P1-2：保留 nullable 旧布尔兼容字段

- 文件：`src/main/kotlin/com/weibo/talentintroduction/qa/controller/QaRuleManagementController.kt`。
- 将 `QaRuleCreateRequest` 和 `QaRuleUpdateRequest` 的 `autoReplyEnabled`、`handoffRequired` 改为 `Boolean? = null`；保持 `toCommand()` 不读取、不传递这两个字段。
- 在 `QaRuleManagementControllerTest.kt` 增加 create/update JSON 含两个旧字段 `null` 的回归：请求由 `replyPolicy` 正常决定 command，旧字段不影响保存。
- 预期：缺失、布尔值或显式 null 均不反推/覆盖 policy；缺失 `replyPolicy` 仍返回 400。

## 当前状态（修复前）

- 编译/测试：PASS — `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -q -Dtest=QaRuleManagementServiceTest,QaMatchServiceTest,AutoMailReplyServiceTest,AutoReplyPreviewServiceTest test`。
- Kotlin：PASS — 135 passed, 0 failed, 0 skipped（40 + 36 + 39 + 20）。
- API 回归：PASS — `QaRuleManagementControllerTest`，6 passed。
- JS：PASS — `node --test src/test/js/qaFactCardEditor.test.js`，7 passed。
- `git diff --check`：PASS；`styles.css` 无 diff。

## 合规审计

| 约束 | 结论 | 证据 |
|---|---|---|
| I-1：policy 权威/影子派生 | ❌ P1-1 | `QaRule.kt:52` 默认 AUTO；`QaRule.kt:65-69` 的显式映射正确，但默认值与 V80/计划不一致。 |
| I-2：V80 回填与固定映射 | ✅ | `V80__add_qa_reply_policy.sql:4-14` 按旧布尔回填 AUTO/REVIEW，并同步 `1/0`、`0/1` 影子。 |
| T3：nullable 旧 request 兼容 | ❌ P1-2 | `QaRuleManagementController.kt:256-257,288-289` 为非空 `Boolean`；`toCommand()` 虽忽略旧字段，但显式 JSON null 在绑定前失败。 |
| I-3：NEVER 候选过滤 | ✅ | `QaMatchService.kt:100-101` 统一过滤；三个入口均从 `matchableRules()` 读取（`:20,46,64,71`）。 |
| I-4：最严格聚合与旧响应 | ✅ | `QaMatchService.kt:86-95` 聚合 policy 后派生 legacy 字段；`QaRule.kt:24-35` 只有全 AUTO 才返回 AUTO。 |
| I-5：enabled/policy 正交 | ✅ | repository 的 `findAllEnabledOrdered()` 后再按 policy 过滤，见 `QaMatchService.kt:100-101`；管理列表不走 match filter，见 `QaRuleManagementService.kt:47-64`。 |
| S-1/S-2 | ✅ | `index.html:1572-1592` 仅一个三值 select；`app.js:1753-1762,1787-1788` 仅渲染单一 policy badge 与相邻 enabled badge；`styles.css` 无 diff。 |
| 范围 | ✅ | 发现均在子计划列出的 domain/controller/test 文件内。 |

### 语义完整性审计

- Accumulation check：✅ N/A；无时间窗口聚合。
- State machine check：✅ N/A；无状态机。
- Cross-plan check：❌ P1-1。Phase 2 的事实卡写路径与 Phase 3 的 policy 默认值共同构成新规则的安全默认；domain 默认 AUTO 会让绕过管理 service 的后续写路径偏离 V80 的 REVIEW 默认。
