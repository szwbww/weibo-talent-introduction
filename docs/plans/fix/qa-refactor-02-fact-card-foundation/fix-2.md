# QA 重构 02：事实卡数据与管理基础 — 修复计划 2

## 原计划 / 子计划引用

- 总计划：`docs/plans/2026-07-17/qa-fact-card-trust-reply-master-plan.md` Phase 2。
- 子计划：`docs/plans/2026-07-17/qa-refactor-02-fact-card-foundation.md`。
- 前轮：`docs/plans/fix/qa-refactor-02-fact-card-foundation/fix-1.md`。

## 约束摘录

| 约束 | 要求 |
|---|---|
| I-1 | `answerBody` 是新权威字段；update 只能写 `answer_body`，不得改 `reply_body`。 |
| I-2 | 新建/更新的 `answerBody` 必须非空且最多 4000 字符，并通过事实正文策略校验。 |
| I-3 | 旧字段仅兼容读取；新 UI 不维护 coverage/QA variants。 |
| I-4/I-5 | 删除清理 QA variants；历史 `mail_record_qa_rule` 外键保持 RESTRICT。 |

## 修正记录表

| ID | 优先级 | 发现 | 触发频率 |
|---|---|---|---|
| P1-1 | P1 | `QaRuleManagementService.updateRule()` 用 `QaFactBodyPolicy` 校验 `trim()` 后长度，却把未 trim 的原始 `command.answerBody` 写入 `answer_body`。有效正文刚好 4000 字、外加空白时可保存超过 4000 字符，违反 I-2。 | 低频：直接 API 或粘贴带前后空白的长事实正文；一旦触发，数据库中的事实正文超出运行时约束。 |

## 修复规格

### P1-1：规范化 update 的事实正文后再保存

- 文件：
  - `src/main/kotlin/com/weibo/talentintroduction/qa/service/QaRuleManagementService.kt`
  - `src/test/kotlin/com/weibo/talentintroduction/qa/service/QaRuleManagementServiceTest.kt`
- 在 `updateRule()` 中先生成 `command.answerBody.trim()`，以该值校验并写入 `existing.copy(answerBody = ...)`。
- 不改 `replyBody`、reply subject、旧路由开关、coverageKeys 或 variants。
- 新增回归：4000 个有效字符加首/尾空白后，更新保存值恰为 4000 个字符；超过 4000 个有效字符仍返回 400。

## 当前状态（修复前）

- Kotlin：PASS — `QaRuleManagementServiceTest` 35、`QaRuleManagementControllerTest` 5、`QaMatchServiceTest` 35，均 0 failed；同一 Phase 2 Maven 命令完成。
- Phase 2 JS：PASS — 12 passed, 0 failed。
- 全量 JS：PASS — `node --test --test-reporter=dot src/test/js/*.test.js` exit 0。
- `git diff --check`：PASS；`styles.css` 无 diff。

## 合规审计

| 约束 | 结论 | 证据 |
|---|---|---|
| I-1 | ✅ | `V79__add_qa_answer_body.sql:4-7` 仅回填 answer_body 并保持 updated_at；`QaRuleManagementService.kt:95-104` 的 update copy 不赋 replyBody；`:73-80` 新建双写与安全旧路由。 |
| I-2 | ❌ P1-1 | `QaFactBodyPolicy.kt:20-23` 以 trimmed 值校验；`QaRuleManagementService.kt:92,102` 校验原值后仍保存未 trim 的 command.answerBody。邮件框架校验本身见 `QaFactBodyPolicy.kt:25-39`。 |
| I-3 | ✅ | `QaRuleManagementService.kt:91,123-126` 拒绝非空 variants，`:95-104` 保留 existing 旧字段；`app.js:2505-2524` 仅提交事实卡字段；`index.html:1557-1589` 无旧复杂控件。 |
| I-4 | ✅ | `QaRuleManagementService.kt:109-114` 在删除前调用 `deleteForOwner(QA_RULE, ruleId)`。 |
| I-5 | ✅ | `QaRuleManagementService.kt:109-114` 不改规则 ID 或外键策略；`V79__add_qa_answer_body.sql:1-10` 仅增加并回填列。 |
| S-1/S-2 | ✅ | `index.html:1557-1590` 使用既有表单骨架；`app.js:1771-1795` 渲染 9 列并对事实正文 escape + 截断 120 字；styles.css 无 diff。 |
| 前轮 P1-1/P1-2/P1-3/P1-4 | ✅ | V79 `updated_at=updated_at` 见 `V79:4-7`；请求 answerBody 非空类型见 `QaRuleManagementController.kt:246-308`；签名逗号匹配见 `QaFactBodyPolicy.kt:11-14`；全量 JS exit 0。 |

### 语义完整性审计

- Accumulation check：✅ N/A；无时间窗口计数器。
- State machine check：✅ N/A；无状态机。
- Cross-plan check：✅ Phase 1 保持 `reply_body` 兼容读取，Phase 2 update 不写该字段；Phase 4 的 future reader 将消费 `answer_body`。P1-1 仅是写入长度边界，修复后不改变接口语义。

## 观察（不建修复任务）

- `app.js:1741-1751` 仍有未调用的 legacy coverage label helper；运行时 UI/写路径已移除，属非阻塞清理项。
