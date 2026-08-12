# 03 执行报告 — 收件范围新增「地区」多选（后端）

- 计划: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.worktrees/fast/batch-send-rhythm-and-filter/docs/plans/fast/batch-send-rhythm-and-filter/children/03/brief.md`
- Plan SHA-256: `718c22062df23c5acac6f8eafb829685c5e229b6472dce02ed748f729947f9c7`
- Execution ID: `<canonical path>@718c22062df23c5acac6f8eafb829685c5e229b6472dce02ed748f729947f9c7`
- Execution epoch: NEW
- 执行者: Impl03 (fast-p child 03 implementer)
- 目标 worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.worktrees/fast/batch-send-rhythm-and-filter`
- 目标分支: `fast/batch-send-rhythm-and-filter`
- Worktree ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.worktrees/fast/batch-send-rhythm-and-filter@fast/batch-send-rhythm-and-filter@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/batch-send-rhythm-and-filter`
- Pre-execution code SHA (product base): `919a0d66a2d938983534375c54903b688d6de943`（child 02b 代码头；worktree HEAD 为 135ee62，docs-only evidence commit）
- Post-execution code SHA: 见下方提交 SHA（`feat(fast-p): implement 03`）
- Implementation boundary: `919a0d6..<实现提交>`

## 变更文件（10/10，均在授权清单内）

| # | 文件 | 改动 |
|---|---|---|
| 1 | `src/main/resources/db/migration/V93__add_regions_to_batch_send_task_config.sql`（新增） | `ADD COLUMN regions_json TEXT NOT NULL AFTER funnel_level` + 兜底 `UPDATE ... SET regions_json='[]' WHERE regions_json=''`；无 `${...}` |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/campaign/domain/BatchSendTaskConfig.kt` | 实体 `regionsJson: String = "[]"`；View / CreateCommand / UpdateCommand `regions: List<String> = emptyList()`（A-3） |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/campaign/domain/BatchExecutionModels.kt` | Snapshot `regions`；RecipientScope `regions`；`fromSnapshot` 透传；`matchesExpert` 加 `toRegion` 归一判定（I-4）；`toExecutionSnapshot` 解析 `regionsJson`（A-5） |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskConfigService.kt` | `normalizeRegions`（trim/去空/去重/校验 allRegions/按 allRegions 序排序）+ `parseRegions`；`ConfigFields.regions` / `NormalizedConfig.regionsJson`；create/update 实体写入；toView 解析；updateLegacyConfig 保留实体现值；三个 `toFields()` 接入（A-4） |
| 5 | `src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendControlService.kt` | `validateSnapshotFields` 加 `allRegions()` 校验（I-1）；`toLegacySnapshot` 加 `regions = emptyList()`（A-6） |
| 6 | `src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt` | `buildEsFiltersForLevel` 在 if/else 与 tags 之后单次 `add` regionsFilter（I-2/I-3）；legacy `buildRetryableTargets` 与 `buildMaterialReminderSnapshot(config)` 两处手工 `RecipientScope` 补 `regions = emptyList()`（后者附 X-2 注释）（A-7） |
| 7 | `src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchService.kt` | `regionFilter` 移入 companion 并公开（方法体逐字不变，I-5）；新增 `regionsFilter`（I-2）（A-2） |
| 8 | `src/test/kotlin/.../campaign/service/BatchSendTaskConfigServiceTest.kt` | +5 用例（合法多选持久化+回读、非法值 422 消息、去重规范化、空列表、updateLegacyConfig 保留） |
| 9 | `src/test/kotlin/.../expert/service/ExpertSearchServiceTest.kt` | +5 用例（空列表 null、单选与 regionFilter 逐字相等、多选单 should 子句、非法值抛异常、`Other` 回归 I-5） |
| 10 | `src/test/kotlin/.../campaign/service/ManualInitialOutreachServiceTest.kt` | +4 用例（分支 A / 分支 B 地区子句入 ES filters、重试路径 Germany 保留/过滤、null country 归 Other 保留） |

未触碰（禁止清单）：`app.js`、`index.html`、`ExpertIndexController.kt`、`CountryContinentMapping.kt`、`BatchSendSettingService.kt`、已应用迁移 V72/V91/V92、`buildMaterialReminderEsFilters`（grep 实证已不存在于当前代码，零改动）。

## 验证命令与退出码

| 命令 | 结果 | 证据 |
|---|---|---|
| `JAVA_HOME=.../zulu-11.jdk/Contents/Home mvn test` | PASS（exit 0） | `Tests run: 2362, Failures: 0, Errors: 0, Skipped: 4`；`BUILD SUCCESS` |
| `JAVA_HOME=... mvn test -Dtest=BatchSendTaskConfigServiceTest,ExpertSearchServiceTest,ManualInitialOutreachServiceTest` | PASS（exit 0） | BatchSendTaskConfigServiceTest 30/0/0；ExpertSearchServiceTest 43/0/0；ManualInitialOutreachServiceTest 53/0/0 |
| `JAVA_HOME=... mvn test -Dtest='ExpertSearchServiceTest#regionsFilter empty list returns null meaning unrestricted'`（单方法示例） | PASS（exit 0） | `Tests run: 1, Failures: 0, Errors: 0`；`BUILD SUCCESS` |
| `JAVA_HOME=... DOCKER_HOST=~/.orbstack/run/docker.sock mvn test -Dtest=FlywayMigrationIntegrationTest -DmigrationIt=true -Dapi.version=1.40` | FAIL（exit 1，已知环境基线） | `Tests run: 9, Failures: 0, Errors: 8`；全部 8 个错误同一根因：`Migration V82__split_trust_reply_atomic_facts.sql failed — V82 baseline drift: audited legacy QA rules changed; stop deployment and merge manually`（V82 在 V93 之前执行，与本次改动无关）。**已在 base 919a0d6 上复现完全一致**（临时 worktree `/tmp/base-v82-check` 同样 `Tests run: 9, Errors: 8`，V82 drift 门禁），确认为既有缺陷，非本子计划缺陷，按任务说明记录 |
| `JAVA_HOME=... mvn clean package` | PASS（exit 0） | `Tests run: 2362, Failures: 0, Errors: 0, Skipped: 4`；`BUILD SUCCESS` |
| `git diff --check` | PASS（无输出） | 空白/换行无问题 |

注：首个单方法尝试（`#regionsFilter empty list returns null`，方法名不完整）因 surefire 找不到方法报 `No tests were executed!`；用完整方法名 `#regionsFilter empty list returns null meaning unrestricted` 后通过。属选择器名称输入问题，非实现缺陷。

## 验收标准自查

- **I-1** ✅：非法值用例（`["中国"]` → `IllegalArgumentException`，消息含 `region must be one of`）通过；grep `CountryContinentMapping.allRegions()` 在 BatchSendTaskConfigService.kt（:344/:348/:357）与 BatchSendControlService.kt（:430）各命中 ≥1 处。
- **I-2** ✅：三个 `regionsFilter` 用例通过；`ManualInitialOutreachService.kt:1223` 调用形态为 `ExpertSearchService.regionsFilter(scope.regions)?.let { filters.add(it) }`（单次 add），无 `regions.forEach { filters.add(` 写法。
- **I-3** ✅：4 个新增用例（分支 A/B + 重试保留/过滤）通过；`buildEsFiltersForLevel` 中地区行位于 if/else 块与 tags 块之后（:1223），两分支同时生效；`buildMaterialReminderEsFilters` 零改动（当前代码中已不存在该方法）。
- **I-4** ✅：`matchesExpert` 方法体含 `CountryContinentMapping.toRegion(profile.country)`（BatchExecutionModels.kt:66-67）；`country = null` 归 `Other` 用例通过。
- **I-5** ✅：git diff 确认 `regionFilter` 方法体与 base 919a0d6 逐字相同（归一化缩进后唯一差异为可见性 `private` → `fun`）；`region = "Other"` 回归用例通过。
- **G-1/G-4** ✅：API/DB/ES 值均为英文领域常量；`regions` 经 `BatchExecutionSnapshot` 传入执行循环（`toExecutionSnapshot`/`fromSnapshot`），运行中不重读配置表。
- **迁移约束** ✅：V93 无 `${...}`（grep -c = 0）；V72/V91/V92 未修改；版本号 V93 未被占用（落地顺序与原计划一致）。
- **G-3 已知偏差（X-2）** ✅：`countPending(MATERIAL_REMINDER)` 统计路径 `buildMaterialReminderSnapshot(config)` 手工构造 scope 不携带地区（KV 层 `BatchSendConfig` 无地区维度），已按计划在代码处加注释；发送路径经 `fromSnapshot` 携带地区。

## Deviations

无实现偏差。唯一非 PASS 命令为 FlywayMigrationIntegrationTest，属 V82 baseline drift 既有环境基线（已在 base 上复现），非本子计划引入，未做任何修复（修复需改已应用迁移，超出授权范围）。

## Freshness

- Plan identity rechecked: YES（前后 SHA-256 一致 `718c22...`）
- Worktree identity rechecked: YES（branch `fast/batch-send-rhythm-and-filter`，git-dir 一致）
- 提交可达性：提交后验证实现提交为 worktree HEAD 且为分支祖先
- Required commands run this invocation: YES（全部命令本次运行；base 复现用临时 worktree 已清理）
- Historical evidence used only as baseline: YES

## 剩余阻塞

无。
