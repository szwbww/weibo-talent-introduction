# QA 重构 01：模板与 QA 事实源解耦 — 修复计划 1

## 原计划 / 子计划引用

- 总计划：`docs/plans/2026-07-17/qa-fact-card-trust-reply-master-plan.md` Phase 1。
- 子计划：`docs/plans/2026-07-17/qa-refactor-01-template-boundary.md`。
- 本轮无此前修复计划。

## 约束摘录

| 约束 | 要求 |
|---|---|
| I-1 | V78 将有效 `QA_RULE` 块的主 `qa_rule.reply_body` 原样写入 `custom_text`，改为 `CUSTOM_TEXT` 并清空 `ref_id`；不提前渲染或重排。 |
| I-2 | V78 前必须断言 QA 变体数为 0、`QA_RULE` 块数为 1、悬空 QA 引用数为 0；任一失败即停止发布；迁移后 `QA_RULE` 块数必须为 0。 |
| I-3 | create/update/previewDraft 均拒绝 `QA_RULE`；编辑器不得提供该 option，新增默认 `CUSTOM_TEXT`。 |
| I-4 | `resolveBlocks()` 保留 legacy `QA_RULE` 主正文只读渲染，且不得读取 QA 变体。 |
| S-1 | 不改 `styles.css`，块行仅移除 QA 分支，既有 DOM/class 不变。 |

## 修正记录表

| ID | 优先级 | 发现 | 触发频率 |
|---|---|---|---|
| P1-1 | P1 | V78 仅执行 `INNER JOIN` 更新；仓库内没有 T1 三项发布前断言及迁移后 `QA_RULE=0` 断言的可追溯执行记录。若上线期间出现悬空 QA 引用，更新会跳过该块，遗留块仍在运行时读取 `qa_rule.reply_body`，违反解耦。 | 低频：仅发布窗口内基线漂移、手工数据异常或门禁未执行时；一旦发生，后续 QA 正文修改会影响介绍信。 |

## 修复规格

### P1-1：完成可追溯发布门禁

- 修改范围：发布单/部署执行记录；不改业务代码，不扩展本子计划的五个产品文件。
- V78 执行前，记录以下三个 SQL 的实际计数，且仅允许 `0 / 1 / 0`：

```sql
SELECT COUNT(*) FROM content_variant WHERE owner_type='QA_RULE';
SELECT COUNT(*) FROM mail_compose_template_block WHERE block_type='QA_RULE';
SELECT COUNT(*)
FROM mail_compose_template_block b
LEFT JOIN qa_rule q ON q.id=b.ref_id
WHERE b.block_type='QA_RULE' AND q.id IS NULL;
```

- 任一值不符即停止；特别是不得在有 QA 变体时任意选择变体快照。
- V78 后记录 `SELECT COUNT(*) FROM mail_compose_template_block WHERE block_type='QA_RULE';`，必须为 `0`；否则停止发布并回滚应用版本，不能继续进行事实正文改造。
- 修复后复验需提供上述四项实际输出，并重跑本子计划两条测试命令。

## 当前状态（修复前）

- 编译：PASS — `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -q -Dtest=MailComposeTemplateServiceTest,IntroductionMailComposerTest test`。
- Kotlin 测试：PASS — 38 passed, 0 failed, 0 skipped（`MailComposeTemplateServiceTest` 33；`IntroductionMailComposerTest` 5）。
- JS 测试：PASS — `node --test src/test/js/composeTemplatePreview.test.js`，7 passed, 0 failed, 0 skipped。
- `git diff --check`：PASS；`src/main/resources/static/styles.css` 无 diff。

## 合规审计

| 约束 | 结论 | 证据 |
|---|---|---|
| I-1 | ✅ | `V78__decouple_compose_templates_from_qa_rule.sql:4-10` 原位 join update，写入 `q.reply_body`、设 `CUSTOM_TEXT`、清空 `ref_id`；未调用变量渲染。 |
| I-2 | ❌ P1-1 | `V78__decouple_compose_templates_from_qa_rule.sql:4-10` 只更新 join 命中的块；没有 T1 三项前置计数或迁移后零 QA 块的执行记录。 |
| I-3 | ✅ | `MailComposeTemplateService.kt:157-177` 预览先校验；`:372-380` 拒绝 `QA_RULE`；`app.js:7371-7409` 仅有 `REPLY_SNIPPET`/`CUSTOM_TEXT` 且默认后者。 |
| I-4 | ✅ | `MailComposeTemplateService.kt:397-434` 直接使用 `rule.replyBody`；变体调用仅出现在 `REPLY_SNIPPET` 分支 `:452-460`。 |
| S-1 | ✅ | `app.js:7379-7394` 保留既有块行骨架和 class；`styles.css` 无 diff。 |
| 删除/兼容 | ✅ | `ComposeBlockType.QA_RULE` 的读取分支仍在 `MailComposeTemplateService.kt:397-434`；新写入口均阻止。 |
| 范围 | ✅ | 产品改动仅为子计划列出的 5 个文件；知识库命中计数为 fix-v 例行写入。 |

### 语义完整性审计

- Accumulation check：✅ N/A；没有时间窗口计数器。
- State machine check：✅ N/A；没有状态机。
- Cross-plan check：❌ P1-1。Phase 1→Phase 2 的边界要求模板在 QA 正文语义变化前完全脱钩；悬空块被 V78 跳过时，该边界不成立。正常快照路径、失败停止路径与重启后 Flyway 已应用路径均依赖上述门禁记录。
