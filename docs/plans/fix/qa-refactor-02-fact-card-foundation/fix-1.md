# QA 重构 02：事实卡数据与管理基础 — 修复计划 1

## 原计划 / 子计划引用

- 总计划：`docs/plans/2026-07-17/qa-fact-card-trust-reply-master-plan.md` Phase 2。
- 子计划：`docs/plans/2026-07-17/qa-refactor-02-fact-card-foundation.md`。
- 本轮无此前修复计划。

## 约束摘录

| 约束 | 要求 |
|---|---|
| I-1 | V79 仅回填 `answer_body=reply_body`，不得改旧正文、开关、ID 或 `updated_at`；新建必须以 `answerBody` 写两列并安全转人工。 |
| I-2 | `answerBody` 必填、事实化；禁止邮件签名框架。 |
| I-3 | 旧字段只读冻结；新 UI 不维护 coverage；总计划全量 JS 测试必须可运行。 |
| I-4/I-5 | 删除仍清 QA 变体，历史 `mail_record_qa_rule` 关联保持 RESTRICT。 |

## 修正记录表

| ID | 优先级 | 发现 | 触发频率 |
|---|---|---|---|
| P1-1 | P1 | `qa_rule.updated_at` 带 `ON UPDATE CURRENT_TIMESTAMP`；V79 的回填 UPDATE 未显式保持旧值，会把全部存量规则标成迁移时更新，违反 I-1。 | 一次发布，影响全部存量 QA 规则。 |
| P1-2 | P1 | POST 的 `answerBody` 可空，`resolvedAnswerBody()` 会以 deprecated `replyBody` 回退，未落实“answerBody 必填”；旧客户端可绕过新字段边界。 | 每次旧客户端/脚本仍只提交 `replyBody` 时。 |
| P1-3 | P1 | 签名正则只匹配无标点的结尾；`Best regards,`、`Kind regards,`、`Sincerely,` 会通过，允许邮件框架进入事实正文。 | 常见人工邮件签名粘贴。 |
| P1-4 | P1 | `qaCoverageKeyEditor.test.js` 仍断言被 T5 删除的 coverage UI；全量 JS 18 项中 7 项失败，违反总计划测试门。 | 每次 CI/全量 JS 验证。 |

## 修复规格

### P1-1：保持迁移时间戳

- 文件：`src/main/resources/db/migration/V79__add_qa_answer_body.sql`、`QaRuleManagementServiceTest.kt`。
- 在回填 UPDATE 中显式保留 `updated_at=updated_at`；增加断言，防止未来删掉该赋值。
- V79 若已在任一目标库执行，停止修改该迁移并人工处理审计时间戳；不得伪造旧时间。

### P1-2：POST 强制 answerBody

- 文件：`QaRuleManagementController.kt`、`QaRuleManagementService.kt`、对应 controller/service 测试。
- create request/command 的 `answerBody` 改为必填；删除从 `replyBody` 推导事实正文的分支。保留 nullable `replyBody` 仅供响应/兼容读取，不得作为写入源。
- 缺少或空白 `answerBody` 返回 400，且不保存规则。

### P1-3：拒绝带标点的签名

- 文件：`QaFactBodyPolicy.kt`、`QaRuleManagementServiceTest.kt`。
- 结尾签名匹配 `Best regards`、`Kind regards`、`Sincerely` 后的英文/中文逗号和尾随空白；正文内普通事实不受影响。

### P1-4：同步移除旧测试契约

- 文件：`src/test/js/qaCoverageKeyEditor.test.js`（已由子计划修正记录纳入范围）。
- 删除或替换所有要求 coverage UI、coverage endpoint、coverage 表格列的断言；保留与本阶段仍有效的通用转义断言仅在其不依赖已删除 UI 时。
- `node --test src/test/js/*.test.js` 必须无失败。

## 当前状态（修复前）

- 编译 / Phase 2 Kotlin：PASS — 84 passed, 0 failed（33 + 3 + 35 + 13）。
- Phase 2 JS：PASS — 12 passed, 0 failed。
- 全量 JS：FAIL — `qaCoverageKeyEditor.test.js` 18 项中 7 项失败，均断言已删除的 coverage UI。
- `git diff --check`：PASS；`styles.css` 无 diff。

## 合规审计

| 约束 | 结论 | 证据 |
|---|---|---|
| I-1 双正文隔离 | ❌ P1-1/P1-2 | `V79__add_qa_answer_body.sql:4-6` 触发 `updated_at` 自动更新；`QaRuleManagementService.kt:166-179` 允许 `replyBody` 回退。更新路径本身在 `:95-104` 保留旧正文。 |
| I-2 事实正文 | ❌ P1-3 | `QaFactBodyPolicy.kt:11` 仅匹配无逗号签名；其余空白/长度/变量/HTML/问候校验见 `:18-37`。 |
| I-3 旧字段冻结/UI | ❌ P1-4 | `app.js:1638-1647,2505-2524` 已移除 coverage 请求/写入；但 `qaCoverageKeyEditor.test.js:107-176` 仍要求旧 UI。 |
| I-4 | ✅ | `QaRuleManagementService.kt:109-114` 仍先调用 `deleteForOwner(QA_RULE,id)`。 |
| I-5 | ✅ | `QaRuleManagementService.kt:109-114` 未改 ID/外键策略；V79 仅加列、回填。 |
| S-1/S-2 | ✅ | `index.html:1561-1589` 为事实卡表单；`app.js:1771-1796` 为 9 列、转义 120 字预览；`styles.css` 无 diff。 |
| 删除/兼容 | ❌ P1-4 | 运行时代码已删除 QA coverage 写路径，但旧测试仍把删除项当契约。 |

### 语义完整性审计

- Accumulation check：✅ N/A；无时间窗口计数器。
- State machine check：✅ N/A；无状态机。
- Cross-plan check：❌ P1-1。Phase 1→2 的模板已与 `reply_body` 解耦，但 V79 改写 `updated_at` 会把事实卡迁移误呈为运营正文更新；Phase 2→4 的 `answerBody` 写入边界亦被 `replyBody` 回退绕过（P1-2）。
