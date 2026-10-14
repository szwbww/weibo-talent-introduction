# Execution Log — 03-ui

（由实施者写入；append-only）

---

## 2026-10-14 · Impl03Ui · 实施提交

- Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-14-batch-filter-main`
- Branch: `fast/2026-10-14-batch-filter-main`
- child_base_sha: `9c9457cadda70e95dc605f720d652d3e4617b803`（02-diagnostics 终止 Code head）
- 实施提交：`50dc450b9e3d68b4cc44ad3a660d8a00936bb662` — `feat(fast-p): implement 03-ui`（仅 5 个授权文件）
- 结果：**READY_FOR_VERIFICATION**（无 PLAN_CONFLICT、无 BLOCKED）

### 1. 变更文件（提交内唯一路径集合，5 个）

| 文件 | insertions | deletions |
|---|---:|---:|
| `src/main/resources/static/app.js` | 79 | 14 |
| `src/main/resources/static/index.html` | 18 | 18 |
| `src/test/js/recipientPreviewDiagnostics.test.js`（新） | 319 | 0 |
| `src/test/js/batchEmailVerification.test.js` | 39 | 0 |
| `src/test/js/gateTemplateFilter.test.js` | 6 | 3 |

合计 460 insertions / 35 deletions。`styles.css` 差异为 0；无新增/修改后端代码、无迁移、无新增 class 或 inline style。
`docs/plans/fast/**` 下的 fast-p 证据文件未进入该提交（工作树里 `ledger.md` 的既有改动保持未提交）。

### 2. 实现摘要

**I-1 / S-1（诊断事实与推测分开，两表单同一骨架）**

- `refreshRecipientPreview` 内联自包含辅助函数（`hasDiagnostics`/`numberOf`/`asOfText`/`diagnosticsHtml`/`gateHtml`/`hintHtml`），因为 `batchSendTaskConsoleInteraction.test.js` 与 `batchEmailVerification.test.js` 按函数名单独抽取 `refreshRecipientPreview`，新顶层函数会变成未定义。
- 带新字段（`baseline != null`）的响应渲染 S-1 骨架：`统计时间：<span>…</span>`、`范围内 N 人；排除 <span class="batch-gate-excluded">N</span> 人；符合任务条件 N 人；待执行验证 N 人`、`原因人数可重叠…` warnline、`<details><summary>查看排除原因与样本</summary>`。
- 每个 `reasonHitsDetail` 原因渲染独立 `原因：<key> N 人；仅显示前 M 条`（`truncated` 追加「样本已截断」），样本逐条 `样本：docId（orcidId）`；原因命中**绝不求和**冒充排除人数。
- `partial` 追加 warnline「统计未完成，结果可能不完整。」；`asOf` 取 `YYYY-MM-DD HH:mm:ss`；无缓存目标仍显示「待执行验证 N 人」，从不显示「可投递」/供应商 unknown。
- 动态内容（reasonKey、docId、orcidId、asOf）全部经既有 `escapeHtml`。
- 缺新字段的旧响应沿用现有 `baseHintHtml` 摘要并追加 `<span>详细解释不可用</span>`，**不补 0**。

**I-2 / S-2（模板提示与开关一致）**

- `index.html` 两个 `batch-gate-hint` 与 `refreshBatchGateState` 的 `DEFAULT_HINTS` 同步改为 S-2 文案（`关闭时不因个性化缺项排除`），二者逐字相等。
- 动态 gate 说明：开启显示「因模板缺项排除 N 人（门禁过滤已排除）」；关闭显示「其中 N 位缺少该模板必填字段，当前未开启门禁不会因此排除。」；**不再出现「计入失败」「发送时会被门禁拦下」**。

**I-3（交互安全与兼容）**

- 保留 500ms 防抖、seq 陈旧响应丢弃、加载态 `当前条件命中 <strong>计算中…</strong>`、只读（只打 `/api/mail/batch-send/recipients/preview`，零发送/验证请求）。
- HTTP/API 失败显示「预估不可用」且不弹窗。
- `index.html` 16 个版本化引用统一为单一新 key `20261014-batch-filter`（styles.css / app.js / trust-reply-workbench.js 同值，符合 triad 规则）。

### 3. 命令与结果（fresh run，提交后）

| 命令 | 结果 |
|---|---|
| `node --test src/test/js/recipientPreviewDiagnostics.test.js` | exit 0；tests 13 / pass 13 / fail 0 |
| `node --test src/test/js/batchEmailVerification.test.js` | exit 0；tests 42 / pass 42 / fail 0 |
| `node --test src/test/js/gateTemplateFilter.test.js` | exit 0；tests 8 / pass 8 / fail 0 |
| `node --test src/test/js/*.test.js` | exit 0；tests 1671 / pass 1671 / fail 0（基线 1657 + 新增 14） |
| `node --check src/main/resources/static/app.js` | exit 0 |

MAIN G-2 共同 fixture（新测试断言）：`范围内 10 / 排除 4 / 符合任务条件 6 / 待执行验证 3`，原因命中 2+2+1+1=6 次且不改变排除 4；截断与 partial 显式；两表单骨架逐字相同。

### 4. 偏差（受既有未授权契约测试约束，未扩大范围）

1. **错误文案按 HTTP 状态区分**：计划 I-3 要求「API失败为预估不可用」。实现为「带 `status` 的 HTTP/API 错误 → `预估不可用`；无状态的非 API 错误（如前端校验异常）保留 `预估失败：<message>`」。原因：`src/test/js/batchSendTaskConsoleInteraction.test.js`（**不在 5 个授权文件内**）在 :1183/:1191/:2117 逐字断言 `预估失败：…`；若对所有错误统一改为「预估不可用」会使其变红，违反「只改 5 文件 + 全量 JS 绿」。计划 A-3 本身也以「模拟接口 500」为判据，`预估不可用` 对 HTTP 失败逐字成立。
2. **gate 开启动态文案保留旧短语**：计划 I-2 要求开启显示「因模板缺项排除 N 人」。实现同时保留旧短语「门禁过滤已排除」（`batchSendTaskConsoleInteraction.test.js` G9 断言其存在）。I-2 只禁止「计入失败」，故未违规。
3. **旧响应「详细解释不可用」用无 class 的 `<span>`**：计划建议 partial/不可用放 `batch-gate-warnline`。若旧响应标注也使用该 class，`batchSendTaskConsoleInteraction.test.js` G7 断言「无 warnline」会变红，故旧响应标注仅用纯文本 span，诊断骨架的 partial 才用 warnline。

以上均为「保持 5 文件边界 + 全量 JS 绿」的兼容选择，未削弱任何断言，也未改动未授权文件。

### 5. 残留风险

- 反作弊提示：`batchSendTaskConsoleInteraction.test.js` 仍固化旧错误文案与旧 gate 文案片段；未来若计划正式扩展变更清单包含该文件，应把这些断言迁到新语义。
- 真实浏览器浅色/暗色目测、展开交互、网络慢请求（A-1～A-4 人工项）未执行，属验证/人工阶段。
- `verificationPhase` 未在页面上单独展示（S-1 骨架无该元素）；「待执行验证 N 人」已表达待验证语义。

