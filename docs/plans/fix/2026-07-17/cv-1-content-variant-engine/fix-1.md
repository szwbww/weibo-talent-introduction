# fix-1：CV-1 content_variant 引擎复验

## 原计划 / 子计划引用

- 原计划：`docs/plans/2026-07-09/cv-1-content-variant-engine.md`
- 复验对象：CV-1（变体机制二次重构 1/6）

## 约束摘录

- I-1 变体池语义：`ContentVariantService.resolveBody` 是唯一解析实现；pool = 主体 + enabled content_variant，按 `Math.floorMod(seed + ownerId, pool.size)` 选择。
- I-2 模板主题恒定：render/renderByCode/preview/previewDraft 的 subject 一律来自 `template.subject`/request subject；旧主题池函数删除；`subjectVariants` 自本计划起为死字段，保存忽略，main 源码中仅保留 DTO 字段声明与 toDetail 透传。
- I-3 owner_type 白名单：仅 `QA_RULE`/`REPLY_SNIPPET`；未知 ownerType 或 ownerId=null 返回主体。
- I-4 previewDraft 预览语义：`variantIndex ?: 0` 作为 seed；响应新增 `variantPoolSize`，取所有块池大小最大值；preview 与 render 共用 `resolveBody`。
- I-5 无变体回归：无 content_variant 记录时 `resolveBody` 返回主体原文，不 trim、不改写。
- K-positive-hash-index：下标映射必须用 `Math.floorMod`。
- K-renderText-all-callers：不得改变 `renderText` 占位符行为和 5 个外部变量注入入口。
- K-composed-reply-order-contract：`qaRuleIds` 顺序按块顺序保持。

## 修正记录表

| ID | 严重级别 | 触发频率 | 问题 |
|---|---|---|---|
| P1-1 | P1 | 中等：运营仍使用旧模板编辑器保存含主题变体的模板时触发；每次 create/update 都会保留旧值 | 计划要求 `subjectVariants` 自 CV-1 起保存忽略、仅 DTO 字段声明与 toDetail 透传，但实现仍在 create/update 中把 `command.subjectVariants` 写入 `mail_compose_template.subject_variants`，旧前端字段不会静默失效。 |

## 修复规格

### P1-1：忽略旧 subjectVariants 保存输入

- 文件：`src/main/kotlin/com/weibo/talentintroduction/template/service/MailComposeTemplateService.kt`
- 修改：
  - create 保存 `MailComposeTemplate` 时不要使用 `command.subjectVariants`，新建模板应写 `subjectVariants = null` 或不显式赋值。
  - update 保存已有模板时不要使用 `command.subjectVariants` 更新旧列；为满足“旧字段直接丢弃/保存忽略”，推荐写 `subjectVariants = null`，确保下一次保存会清掉旧主题变体。
  - 保留 `MailComposeTemplateCommand.subjectVariants`、`ComposeTemplatePreviewDraftRequest.subjectVariants`、`MailComposeTemplateDetail.subjectVariants`、controller request 字段，避免旧前端/DTO 断裂。
- 测试：
  - 在 `MailComposeTemplateServiceTest.kt` 增加 create/update 断言：即使 command 带 `subjectVariants = """["A"]"""`，repository.save 收到的实体 `subjectVariants == null`。
  - 现有 render/preview subject 恒定用例保持通过。
- 预期行为：旧前端继续发送 subjectVariants 不报错，但后端不保存、不解析、不参与渲染。

## 当前状态

- 编译/测试：PASS
- 命令：`JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test`
- 结果：BUILD SUCCESS；JUnit Tests run: 1299, Failures: 0, Errors: 0, Skipped: 3；Node tests 198 pass。
- `git diff --check`：PASS
- `git diff --cached --check`：PASS

## 合规审计

- I-1 变体池语义：✅ Evidence: `src/main/kotlin/com/weibo/talentintroduction/variant/service/ContentVariantService.kt:19-24` 统一 buildPool 后用 `Math.floorMod(seed + ownerId!!, pool.size)`；`:42-47` 读取 enabled 变体并拼 `[mainBody] + variants.content`。
- I-2 模板主题恒定：❌ Evidence: `src/main/kotlin/com/weibo/talentintroduction/template/service/MailComposeTemplateService.kt:138` render subject 用 `template.subject`；`:168` previewDraft 用 request subject；但 `:59`、`:82` 仍保存 `command.subjectVariants`，违反“保存忽略 / 仅 DTO+toDetail 透传”。
- I-3 owner_type 白名单：✅ Evidence: `src/main/kotlin/com/weibo/talentintroduction/variant/domain/ContentVariant.kt:20-26` 仅声明 `QA_RULE`/`REPLY_SNIPPET`；`src/main/kotlin/com/weibo/talentintroduction/variant/service/ContentVariantService.kt:39-40` 未知 ownerType 或 ownerId=null 返回主体池。
- I-4 previewDraft 语义：✅ Evidence: `src/main/kotlin/com/weibo/talentintroduction/template/service/MailComposeTemplateService.kt:157-167` variantIndex 作为 seed 传 resolveBlocks；`:403-411` QA 块共用 resolveBody 并累计 poolSize；`:449-457` 片段块同源；`:220-228` 返回 variantPoolSize。
- I-5 无变体回归：✅ Evidence: `src/main/kotlin/com/weibo/talentintroduction/variant/service/ContentVariantService.kt:20-21` 池大小 <=1 返回 mainBody；`:44-45` 变体为空返回主体池；`src/test/kotlin/com/weibo/talentintroduction/variant/service/ContentVariantServiceTest.kt:75-83` 断言空池原文保留空白。
- 删除旧主题/片段选择实现：✅ Evidence: grep `selectSubjectVariant|buildSubjectPool|validateSubjectVariants|selectDraftSubject|parseSubjectVariants|resolveSnippetVariant|findByVariantGroupAndSnippetTypeAndEnabledTrueOrderByDisplayOrderAsc` 在 `src/main/kotlin` 零命中。
- No extras：❌ Evidence: `src/main/kotlin/com/weibo/talentintroduction/template/service/MailComposeTemplateService.kt:59,82` 是计划不允许的旧字段保存消费。

## 语义完整性检查

- Accumulation check：✅ no time-window counters。
- State machine check：✅ no state machine。
- Cross-plan check：✅ contracts consistent for CV-1/CV-5 preview boundary；`variantPoolSize` 已由 `ComposeTemplatePreviewDraftResult` 暴露。
