# Fix-1：移除采用 AI 草稿后的编号发送闸门

## 原计划引用

- 原计划：`docs/plans/2026-07-16/ai-adopt-direct-manual-send.md`
- 复验轮次：1/3
- 复验对象：采用 AI 草稿后直接人工发送的已实现改动。

## 约束摘录

- I-2：采用只复制所选草稿到人工编辑器，不发审核请求、不写审核事件。
- I-3：人工富文本发送只执行主题、正文、规则、账号和最终变量渲染等既有邮件校验；不得因 AI 草稿状态或审核信息拒绝。
- I-4：审核 UI、审核事件与审核写入路径不可达。
- Task 2.2：删除仅用于 review 的非 READY 编号发送 gate。
- Task 2.5：manual-rich payload 组装完成后直接调用 `submitManualRichReply(...)`。
- 范围限制：仅可修改原计划列出的 `app.js` 与两个 JS 测试；不得改 CSS、后端、数据库或新增工作流状态。

## 修正记录表

| P1 | 触发频率 | 现状 | 修复规格 |
|---|---|---|---|
| P1-1：采用多项 AI 草稿仍受编号闸门阻断 | 任何 `requestCount >= 2` 且正文没有恰好 `1..N` 标题的采用草稿；常见于模型未用编号、运营删改标题或手写补充 | `src/main/resources/static/app.js:9515-9547` 保留 `validateSectionNumbering(...)`；`app.js:9588-9592` 在 manual-rich API 前 return，`aiReplyReviewConfirmation.test.js:60-63` 还把该行为当作预期 | 删除 `validateSectionNumbering(...)` 整个 helper 与 `send-manual-rich-reply` 中的调用/return。主题和正文非空、变量渲染、QA 选择及外发链保持不变。将 JS 断言改为：采用 `BLOCKED` 或多项草稿后只发起一次 manual-rich 请求，不出现编号错误，也不出现审核 UI。 |

## 当前状态

- Build：PASS。`mvn -Dtest=AiReplyReviewAuditServiceTest,PendingMailOperationServiceTest,UnmatchedInboundAiReplyTurnKnowledgeTest,QaRuleAuditServiceTest test`：61 tests，0 failures，0 errors。
- JS：PASS。`node --check src/main/resources/static/app.js`；`node --test src/test/js/aiReplyReviewConfirmation.test.js src/test/js/aiReplyLoadingFeedback.test.js`：39 tests，0 failures。
- 失败性质：设计合规 P1，不是编译或测试失败；现有测试把残留闸门固化为预期。

## 合规审计

| 约束 | 结论 | 证据 |
|---|---|---|
| I-1 初稿日志只观测 | ✅ | `AiReplyReviewAuditService.kt:21-49` 捕获日志异常；`UnmatchedInboundMailController.kt:316-363` 始终 preview 并返回本次草稿。 |
| I-2 显式、逐草稿采用 | ✅ | `app.js:9455-9495` 仅 `ai-adopt-draft` 写 editor/baseline，未发 API 或审核事件。 |
| I-3 人工发送不读 AI 审核信息 | ❌ P1-1 | `app.js:9588-9592` 仍按 adopted draft 的 `requestCount` 拦截，未直接进入 `submitManualRichReply(...)`。 |
| I-4 审核路径不可达 | ✅ | `index.html:1872-1875` 已无审核 modal；`UnmatchedInboundMailController.kt:205-225,282-364` 无 review-event；全局搜索无 `aiReviewConfirmation`、`replySource`、`draftIdentity`。 |
| I-5 质量面板仅初稿指标 | ✅ | `app.js:1693-1716` 仅四个初稿质量卡，无“直发拦截”或“人工确认”。 |
| S-1 无替代 UI/CSS | ✅ | `git diff -- src/main/resources/static/styles.css` 为空；`app.js:8863-8868` 保留既有采用按钮结构。 |
| 已删除审核代码 | ❌ | 审核 modal、review-event、identity/confirmation 已删除；但 `validateSectionNumbering` 是原计划明确要求删除的残留审核发送闸门。 |
| No extras | ✅ | 代码变更仅在原计划 10 个文件中；无 migration、CSS、新类、enum 或状态。 |

## 语义完整性检查

- Accumulation check：✅ N/A；无时间窗口计数器。
- State machine check：✅ N/A；本计划删除审核状态机，未引入新状态或恢复路径。
- Cross-plan check：✅ N/A；该计划为独立用户流程改造，无依赖子计划写入的新共享字段。

## 修复后复验要求

```bash
node --check src/main/resources/static/app.js
node --test src/test/js/aiReplyReviewConfirmation.test.js src/test/js/aiReplyLoadingFeedback.test.js
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=AiReplyReviewAuditServiceTest,PendingMailOperationServiceTest,UnmatchedInboundAiReplyTurnKnowledgeTest,QaRuleAuditServiceTest test
```

复验时仅核查本 Fix-1 修改的 `app.js` 与两个 JS 测试，并确认 P1-1 已消失；不得在此轮扩展到原计划范围外的清理。
