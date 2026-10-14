# Fast-P Child Brief — 01-region

- 唯一契约（子计划）: `docs/plans/2026-10-14/batch-filter-01-region.md`
- 全局约束（主计划）: `docs/plans/2026-10-14/batch-filter-main.md`
- Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-14-batch-filter-main`
- Branch: `fast/2026-10-14-batch-filter-main`
- Base SHA: `b02eeb6405b27d5d4f30a32d17019e19c855483d`
- 依赖: none（本子计划可独立交付；02 不得成为 01 的运行依赖）
- 实施提交信息（必须逐字）: `feat(fast-p): implement 01-region`

## 交付范围

只允许修改以下 4 个文件（子计划「变更文件清单」逐字）：

|序号|精确路径|变更|
|---|---|---|
|1|src/main/kotlin/com/weibo/talentintroduction/campaign/domain/BatchExecutionModels.kt|地区精确匹配|
|2|src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt|ES 粗筛|
|3|src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchRecipientSelectionServiceTest.kt|地区矩阵|
|4|src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt|跨入口回归|

不得新增产品文件、不得新增持久字段、不得改动其他文件（含 `ExpertSearchService`、`CountryContinentMapping`、ES mapping）。

## 必须保持的不变量

- **I-1（Other 是未知国家的地区桶）**：对含 discovered 标签的专家，国家 null、空白、`Other`、未识别值均按 `REGION_OTHER` 判断；`regions` 非空时仅在包含 Other 时通过。已识别国家不能因选择 Other 而放行。`regions` 为空维持不限地区。
- **I-2（ES 粗筛不能提前丢人）**：Other 被选中时，批量入口的既有地区查询增加 `tags=discovered AND country 不存在` 的 should 分支（`minimum_should_match=1`）；最终仍由同一个 `RecipientScope` 判定；非 discovered 分支、country/nationality 原有语义不变。
- **I-3（不改准入与发送事实）**：不写国家/国籍/标签/准入，不变更身份哈希、人工决定、退订、绑定账号、已发送、永久失败、模板与邮箱开关；执行仍用开始时的配置快照；保持真实 docId/ORCID 去重。
- MAIN M-1：本子计划独立门禁；共享文件 `ManualInitialOutreachService.kt` / `ManualInitialOutreachServiceTest.kt` 的 01 语义必须保留给 02，不得复制旧函数覆盖。
- MAIN M-4：本阶段只读语义，不得引入任何写入或绕过保护。
- MAIN M-5：只用本地 fixture/mock 验证，不调用 SMTP、不调用生产环境、不做线上数据写入。

## 必须运行的命令（JDK11）

```bash
cd /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-14-batch-filter-main
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest='BatchRecipientSelectionServiceTest,ManualInitialOutreachServiceTest,CountryContinentMappingTest,ExpertSearchServiceTest' -DfailIfNoTests=false
```

- 四个测试类都必须通过；不得以跳过/禁用方式规避。
- 若测试需要新增用例，只能写入上述已授权测试文件。

## 下游接口（02 必须继承）

- `RecipientScope.matchesRegion` 对 discovered 未知国家（null/空白/Other/未识别）的判定必须唯一且可复用：02 的诊断母集、预估与执行共用同一 matcher，不得再复制旧函数。
- `ManualInitialOutreachService.buildEsFiltersForLevel`（及 `countEsTargets`、实际 ES 目标遍历、NEW 重试）的 Other should 分支结构必须可被 02 复用为「母集查询」；不得在 02 之前把该分支写成会提前丢弃非地区原因目标的形式。

## 报告与返回

- 完整执行报告写入：`docs/plans/fast/2026-10-14-batch-filter-main/children/01-region/execution.md`（含：命令、退出码/计数、改动摘要、未决风险）。
- 只提交产品代码为 `feat(fast-p): implement 01-region`；**不得**把 `docs/plans/fast/` 下任何 fast-p 证据文件纳入该提交。
- 不 push、不 merge、不 rebase、不 amend、不 squash、不改写历史。

返回：`READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT` + commit SHA + 命令摘要 + 报告路径。
