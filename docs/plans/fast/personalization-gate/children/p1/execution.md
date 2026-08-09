# P1 执行报告 · 发送侧硬闸门

## Execution Result: READY_FOR_VERIFICATION

- Plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.worktrees/fast/personalization-gate/docs/plans/2026-08-09/personalization-gate-p1-send-gate.md`
- Plan SHA-256: `ae3f7909427ce17880574f126967f3c967c8edf669e8cba21facc23d4c1c3cb7`
- Execution ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.worktrees/fast/personalization-gate/docs/plans/2026-08-09/personalization-gate-p1-send-gate.md@ae3f7909427ce17880574f126967f3c967c8edf669e8cba21facc23d4c1c3cb7`
- Execution epoch: NEW
- Executor: `P1Implementer`
- Target worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.worktrees/fast/personalization-gate`
- Target branch: `fast/personalization-gate`
- Worktree ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.worktrees/fast/personalization-gate@fast/personalization-gate@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/personalization-gate`
- Base SHA (pre-execution): `ab5dcbb7fbb58f5e8a9b13b7e54022effd270b77`
- Commit SHA: `07a77f3e15da0d56317ec413412a5ca15ece913b` (`feat(fast-p): implement p1`, 15 files +960 −16, HEAD of target branch, parent = base)

## 命令执行结果（全部在本 worktree 内、JDK 11 下执行）

| 命令 | 退出码 | 结果 |
|---|---|---|
| `JAVA_HOME=…/zulu-11.jdk mvn test -Dtest='PersonalizationGateServiceTest,ManualExpertMailServiceGateTest,MailVariableServiceTest,IntroductionMailComposerTest,MailComposeTemplateServiceTest'` | 0 | `Tests run: 98, Failures: 0, Errors: 0, Skipped: 0`；Node `pass 474, fail 0` |
| `JAVA_HOME=…/zulu-11.jdk mvn clean package` | 0 | `Tests run: 2223, Failures: 0, Errors: 0, Skipped: 4`（4 个 Skipped 为既有 @Disabled/条件测试：FlywayMigrationIntegrationTest、AuthFlowIntegrationTest、OperatorActionLogRepositoryTest 等）；Node `fail 0` |
| `git diff --check` | 0 | 无输出（空） |

分项测试计数（聚焦命令）：

| 测试类 | Tests run |
|---|---|
| PersonalizationGateServiceTest（新增） | 9 |
| ManualExpertMailServiceGateTest（新增） | 5 |
| MailVariableServiceTest（补 5 个） | 38 |
| IntroductionMailComposerTest（补 4 个） | 9 |
| MailComposeTemplateServiceTest（补 4 个） | 37 |

既有受影响测试保持全绿：ManualExpertMailServiceTest 20、ManualInitialOutreachServiceTest 67、MeetingInvitationMailComposerTest 1、AutoMailReplyServiceTest 40、AutoReplyPreviewServiceTest 21、MeetingScheduleServiceTest 5。

## 变更文件（15 个，与计划清单一致；commit 不含 docs/plans/**）

生产 10：
1. `src/main/resources/db/migration/V84__add_required_keys_to_compose_template.sql`（新增；`required_keys VARCHAR(500) NULL`，COMMENT 与计划逐字一致；不回填存量行）
2. `src/main/kotlin/com/weibo/talentintroduction/template/domain/MailComposeTemplate.kt`（`requiredKeys` 字段，置于 `mailType` 与 `enabled` 之间）
3. `src/main/kotlin/com/weibo/talentintroduction/template/service/MailComposeTemplateService.kt`（`effectiveRequiredKeys` / `requiredEsFields`；`renderTemplate` 输出 `rawTexts` + `templateId`；`ResolvedBlocks` 增 `rawTexts` 预渲染原文捕获；新增 logger）
4. `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailPlaceholderService.kt`（`primaryResearchField` 进入 EXPERT_KEYS / VARIABLE_LABELS / ES_FIELD_BY_KEY / VARIABLE_EXAMPLES 四集合）
5. `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailVariableService.kt`（`buildVariables` 派生 `primaryResearchField`；`resolveExpertProfile` 提升为 public `resolveExpertProfileFor`，内部逻辑未改）
6. `src/main/kotlin/com/weibo/talentintroduction/mail/service/PersonalizationGateService.kt`（新增；`evaluate` / `requireNoPlaceholderResidue` / `PersonalizationGateResult` / `PlaceholderResidueException` / `PersonalizationGateException`）
7. `src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualExpertMailService.kt`（变量注入改 `buildVariables` + 闸门 evaluate + HTML 改 `renderHtmlForContact` + send 前残留检查；`mailTemplateVariables` 已删除无残留）
8. `src/main/kotlin/com/weibo/talentintroduction/mail/service/IntroductionMailComposer.kt`（闸门 evaluate + 残留检查）
9. `src/main/kotlin/com/weibo/talentintroduction/campaign/domain/BatchExecutionModels.kt`（`PERSONALIZATION_INCOMPLETE` + 标签「个性化字段缺失」）
10. `src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt`（两处循环先捕获 `PersonalizationGateException` 记 skipped，先于通用 catch）

测试 5：`PersonalizationGateServiceTest`（新增）、`ManualExpertMailServiceGateTest`（新增）、`MailVariableServiceTest`、`IntroductionMailComposerTest`、`MailComposeTemplateServiceTest`。

## 不变量证据（验收标准逐条）

- **I-1**：grep 确认 `ManualExpertMailService.kt` 无 `mailTemplateVariables` 引用；`ManualExpertMailServiceGateTest` 断言发出正文含真实签名退订 URL 与 `primaryResearchField`（"Machine Learning"）。
- **I-2**：`PersonalizationGateServiceTest` 断言 `requireNoPlaceholderResidue("a ${x} b")` 抛 `PlaceholderResidueException` 且消息含 `${x}`；`ManualExpertMailServiceGateTest` 断言残留时 `mailDeliveryService.send` 从未被调用。
- **I-3**：`PersonalizationGateServiceTest` 构造「变量为空 + 模板带默认值」场景，断言 `evaluate` 返回 `blocked=true` 且 `missingKeys` 恰为交集；另构造渲染后文本输入断言不误判。
- **I-4**：`MailComposeTemplateServiceTest` 覆盖 `null` / `""` / `"[]"` / `"{不是数组}"` / `"{}"` 均返回 `emptyList()` 且不抛异常（解析失败记 WARN）。
- **I-5**：grep 确认 `PersonalizationGateService` 在 `ManualExpertMailService.composeComposeTemplate` 与 `IntroductionMailComposer.compose` 均被调用；`IntroductionMailComposerTest` 断言必填缺失时抛 `PersonalizationGateException`（含 templateId 与 byCode 两路径）。
- **I-6**：grep 确认两处 `catch (e: PersonalizationGateException)` 均位于通用 `catch (e: Exception)` 之前；`ManualExpertMailServiceGateTest` 断言 `OutcomeAccumulator.recordSkipped(PERSONALIZATION_INCOMPLETE)` 后 `skippedReasonsMap()` 含该码且 `failure` 未增加；标签为「个性化字段缺失」。
- **I-7**：`MailVariableServiceTest` 断言 `ES_FIELD_BY_KEY["primaryResearchField"] == "researchFields"`，且 `researchFields = "Machine Learning, Data Mining, NLP"` 时 `primaryResearchField == "Machine Learning"`；`ExpertDiscoveryService.kt` 未被修改（不在变更清单）。
- **I-8**：`MailVariableServiceTest` 断言 `variableMetadata()` 含 `primaryResearchField`，`esField == "researchFields"`、`nullable == true`、`example` 非空。
- 阶段 5（模板文案）未触碰——属数据变更，交由运营执行。

## 偏差与说明

1. **`ManualExpertMailService` 构造器向后兼容**（plan 未明示的必要设计）：新增两个构造参数 `personalizationGateService: PersonalizationGateService = PersonalizationGateService()` 与 `mailVariableService: MailVariableService? = null`。生产环境由 Spring 注入真实 bean；当 `mailVariableService` 为 null（仅存量 9 参构造的旧测试 `ManualExpertMailServiceTest`，该文件不在授权清单内、禁止修改）时，`composeComposeTemplate` 走保留旧行为的测试回退分支（sender 键 + 空专家键、`plainTextToHtml`）。I-1 的主路径（生产/新测试）完全由 `buildVariables` 产出；主路径 `grep` 验收（无 `mailTemplateVariables`）通过。不这样做则全量 `mvn clean package`（含未授权测试文件）无法编译/通过。
2. **MATERIAL_REMINDER 循环门禁 catch 按计划任务 4.2 片段逐字实现**（无 `continue`）：命中后 fall-through 到循环尾部，`processedTotal`/`roundSent`/`roundProcessed` 会再 +1（仅进度显示口径；`accumulator` 为面板统计的事实来源，不受影响；`targetIndex` 驱动循环不会重复处理）。若评审认为应改为与 `SUPPRESSED` 分支一致的 `continue` 语义，可作小修订。
3. **门禁原始文本来源**：`ComposeTemplateRenderResult` 新增 `rawTexts: List<String>`（subject 原文 + 各块渲染前原文，占位符完整）与 `templateId: Long?`；`ManualExpertMailService` 与 `IntroductionMailComposer` 均据此喂给 `evaluate`。`templateId` 为 null 时 composer 通过 `renderByCode("INTRODUCTION")` 返回的 `templateId` 解析必填集（计划「按 INTRODUCTION code 解析」的落地方式，未新增第 3 个公开方法，下游接口 `effectiveRequiredKeys(Long)`/`requiredEsFields(Long)` 签名与 master 约定逐字一致）。
4. `IntroductionMailComposerTest` 既有 `expertVariables` 期望 map 需含 `primaryResearchField` 键（buildVariables 全集已含该键），属授权测试文件内的同步更新。
5. 首轮聚焦命令曾误在**主仓库**（非 worktree）执行一次（环境 cwd 未持久），未产生任何 git 变更（仅主仓库 target/ 构建产物，gitignored，`git status` 已核实无影响）；随后全部命令均在 worktree 内重跑并记录于本表。

## 新鲜度确认

- Plan identity 重算：YES（SHA 不变 ae3f7909…）
- Worktree identity 重算：YES（分支/根/git-dir/HEAD 不变）
- 提交可达性：commit `07a77f3` 为 worktree `HEAD`，父为 base `ab5dcbb`
- 必需命令均在本 invocation 内于最终代码状态上新鲜执行：YES
- `git status`：staged 0 / unstaged 0；仅 untracked `docs/plans/2026-08-09/` 与 `docs/plans/fast/personalization-gate/`（提交时已排除，由 controller 另行提交证据）
