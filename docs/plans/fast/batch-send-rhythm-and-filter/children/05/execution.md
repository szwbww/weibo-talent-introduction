# 05 执行报告（Impl05）

- 计划：`docs/plans/fast/batch-send-rhythm-and-filter/children/05/brief.md`
- 计划 SHA-256：`7ee045c090e549256621d173982632e8053add583233b83c4df2322c3767f689`
- Execution ID：`/Users/lukai/IdeaProjects/weibo-talent-introduction/.worktrees/fast/batch-send-rhythm-and-filter/docs/plans/fast/batch-send-rhythm-and-filter/children/05/brief.md@7ee045c…`
- Execution epoch：NEW
- Worktree ID：`/Users/lukai/IdeaProjects/weibo-talent-introduction/.worktrees/fast/batch-send-rhythm-and-filter@fast/batch-send-rhythm-and-filter@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/batch-send-rhythm-and-filter`
- Pre-execution code SHA（产品基座）：`72ccad590f93e8d2aadccccbf2be51627ae59960`（child 04b 代码头；worktree HEAD 曾为 docs-only 的 `da18bb6`）
- Post-execution code SHA：`4aa1d4789d4e92bde16d52cf682eae2436e861bd`（实现提交，已为 worktree HEAD）
- Evidence HEAD：N/A（本 child 无独立 evidence 提交；实现提交即产品提交）
- 结果：**READY_FOR_VERIFICATION**

## 验证命令与退出码（全部在最终代码状态上新鲜执行）

| 命令 | 退出码 | 结果 |
|---|---|---|
| `node --test src/test/js/batchSendTaskConsoleInteraction.test.js src/test/js/loadContactsFilter.test.js`（最终态复跑） | 0 | tests 28 / pass 28 / **fail 0** |
| `node --check src/main/resources/static/app.js` | 0 | 无输出 |
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=ManualInitialOutreachServiceTest,BatchSendTaskConfigServiceTest,ExpertSearchServiceTest -q` | 0 | 139 用例，0 失败 0 错误（首轮 2 个新用例断言 NPE、1 个缺 campaign stub，已修后复跑通过） |
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -q`（全量回归） | 0 | 全绿 |
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package -q` | 0 | 产出 `target/weibo-talent-introduction-1.0.0-SNAPSHOT.war` |
| `git diff --check` | 0 | 无输出 |

## 变更文件（实现提交 `4aa1d47`，恰好 10 个授权文件，321 insertions / 21 deletions）

1. `src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchService.kt` — `ALLOWED_DISCIPLINES` 与 `disciplineFilter` 去 `private`；**方法体零改动**（diff 仅可见性修饰符两行）。
2. `src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt` — 两条 term 旁路改调 `ExpertSearchService.disciplineFilter()`（`:1081` 死代码防复活 + `:1214` 活跃 else 分支，I-3）。
3. `src/main/kotlin/com/weibo/talentintroduction/campaign/domain/BatchExecutionModels.kt` — `RecipientScope.matchesExpert()` 按 brief A-3 逐字改为 UNCLASSIFIED=「字段为空」语义（I-4）。
4. `src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskConfigService.kt` — 白名单引用权威来源（I-5，见 A-4 决策）。
5. `src/main/resources/static/index.html` — 恰好 +2 行 `<option value="UNCLASSIFIED">未分类</option>`（`:1214` 编辑器、`:1361` 手动 tab；`git diff` 实证仅此 2 行，S-4）。
6. `src/main/resources/static/app.js` — 新增 `REGION_LABELS`（9 key 与 `REGION_ORDER` 英文常量逐字一致）+ `regionLabel()`（未知值原样回退）；三处地区展示接入（`loadRegions` option 文案、监控地区分布表单元格、`BATCH_REGION_OPTIONS` 9 项 label 引用 `REGION_LABELS`）；`renderBatchConfigRow()` 地区摘要改 `c.regions.map(regionLabel).join("、")`；两处学科文案补 `UNCLASSIFIED → 未分类`（I-1/I-2/S-1/S-2/S-3）。
7. `src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt` — +4 用例（else 分支 must_not exists、INTRODUCTION+CANDIDATE 分支回归、重试路径保留 null、重试路径过滤 STEM）。
8. `src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskConfigServiceTest.kt` — +2 用例（`UNCLASSIFIED` 创建成功且 View/实体同值；`OTHER_STUFF` 仍被拒且 `never().save`）。
9. `src/test/js/batchSendTaskConsoleInteraction.test.js` — +3 用例（REGION_LABELS 9 key 逐字等于英文常量、`regionLabel("Mars")` 原样回退、保存 payload 的 regions 为英文常量）。
10. `src/test/js/loadContactsFilter.test.js` — +2 用例（`loadRegions` 生成的 option value 英文 / textContent 中文开头；选中中文显示选项后 `/api/experts?region=` 为英文常量）。

**未修改（验收实证）**：`styles.css`（`git diff --stat` 为空）、`CountryContinentMapping.kt`（diff 为空）、`BatchSendSettingService.kt`（diff 为空，A-7 限制保留）、`docs/plans/fast/` 工件（未入提交）。

## A-4 白名单决策：引用权威来源（非第二份字面量）

- **选择**：`BatchSendTaskConfigService.ALLOWED_DISCIPLINES = ExpertSearchService.ALLOWED_DISCIPLINES`（直接引用）。
- **理由**：brief A-4 首选直接引用，字面量只是「循环依赖/分层顾虑」时的退路。已核实 `ExpertSearchService`（expert/service 包）不依赖 campaign 包，无循环依赖；引用后全仓 `ALLOWED_DISCIPLINES` 取值唯一（grep 实证：ExpertSearchService.kt:53 为唯一定义，BatchSendTaskConfigService 引用之，BatchSendSettingService.kt:236 按计划保留原样）。已加注释指向单一权威（I-5）。
- **影响**：错误消息 `discipline must be one of [STEM, HUMANITIES, UNCLASSIFIED] or ALL/empty` 自动随权威集合变化。

## `parts.push` 摘要检查结果

- **结论：地区维度出现在收件范围摘要中，且已接入 `regionLabel()`**。
- 实证：`renderBatchConfigRow()` 的摘要由 `scopeParts.push` 组成，其中 `app.js:13107` 存在地区维度 push（`"地区: " + c.regions.join(", ")`）。该处本就列于 brief A-6 接入清单（现状审计第 6 项），本实现已按 brief 逐字改为 `c.regions.map(regionLabel).join("、")`。其余 `parts.push`（AI 回复横幅等）均与地区/学科筛选摘要无关。

## 验收 grep 实证

- **I-1**：`app.js:3908` `opt.value = d.region` 仍在且未被 `regionLabel` 包裹；全仓 `.js` 无 `params.set("region", regionLabel` 写法；`loadContactsFilter.test.js` 的英文常量断言通过。
- **I-2**：`grep -c 'var REGION_LABELS' app.js` = **1**（单一权威）；`regionLabel("Mars")` 用例通过；`BATCH_REGION_OPTIONS` label 全部引用 `REGION_LABELS`，无第二份中文字面量表。
- **I-3**：`grep -rn 'disciplineCategory" to' --include=*.kt src/main/` 仅剩 `ExpertSearchService.kt:63` 一处（disciplineFilter 的 else 分支）；死代码 `buildMaterialReminderEsFilters` 的旁路已一并修正（未再多命中）。
- **I-4**：`BatchExecutionModels.kt:56-58` 含 `isNullOrBlank()` 分支；两个重试路径用例通过。
- **I-5**：`BatchSendTaskConfigService.kt:561` 引用 `ExpertSearchService.ALLOWED_DISCIPLINES`；两个配置用例通过。
- **S-1/S-2/S-3/S-4**：`styles.css` 零 diff；`index.html` diff 仅 2 行新增 option，无 `style=`、无新 class、无其他 DOM 变更。
- **不变项**：`CountryContinentMapping.kt` 与 `BatchSendSettingService.kt` 零 diff；`disciplineFilter()` 方法体零改动（diff 仅可见性修饰符）。

## 偏差

- **无契约偏差**。说明性记录（均在授权范围内，已透明化）：
  1. 既有测试 `BATCH_REGION_OPTIONS values are the 9 English region constants verbatim (G-1)` 内含「label equals value until child 05 localizes the display text」占位断言——本 child 正是该占位指向的本地化提交，故按计划意图将该断言翻转为「label == `REGION_LABELS[value]`」，并在同一 sandbox 先注入 `REGION_LABELS`（`BATCH_REGION_OPTIONS` 定义时即引用之）。
  2. brief 中多处行号（`index.html:1201/:1338`、`app.js:13110/:13737` 等）与实测有漂移（brief 已预告「只能当存在性提示，必须 grep 复核」），全部按 grep 实证的相邻行落点实现。
  3. 新增 Kotlin 用例初版有两处自伤错误（`as Map<*,*>` 强转对无 `bool` key 的 map NPE；CANDIDATE 分支用例漏 stub campaign/联系人），均属测试脚手架问题，修复后复跑通过，未改实现代码。

## Freshness

- Plan identity rechecked：YES（SHA-256 与执行前一致 `7ee045c…`）
- Worktree identity rechecked：YES（root/branch/git-dir 与执行前一致）
- Reported commit reachable from target branch：YES（`4aa1d47` 为 worktree HEAD，位于 `fast/batch-send-rhythm-and-filter`）
- Required commands run this invocation：YES（全部 6 条，均在最终代码状态新鲜执行）
- Historical evidence used only as baseline：YES
