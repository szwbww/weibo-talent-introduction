# Verification Report — 05 P-E：RecipientScope 接入专家状态过滤

## Light Verification: LIGHT_PASS

Child: 05 — `docs/plans/2026-08-13/05-recipient-scope-status-filter.md`（含 Amendments A2 + A5）
Boundary: `9df711aa2f0017450dfb531a3aa03376c94c4f5d..b3ae95ac31ad4e24c3a4670d66e65850ab80d8cf`
Verifier: Verifier05
Date: 2026-08-13

### Four Gates

| Gate | Result | Evidence |
|---|---|---|
| Authorized scope | PASS | b3ae95a（implement 05）恰好 11 个文件：10 个授权文件 + A5 守卫测试。`git diff 9df711aa b3ae95a -- src/main/resources/static/styles.css` = 0 行（零 diff）。守卫测试 diff 仅两个 hunk 且均在 `EXCLUDED_NOISE_SITES` 内：① `ExpertSearchService.kt` 钉死点 :332→:345，context `"operatorStatus = source.nullableText"` 未变（新增 operatorStatusFilter 13 行使 :332 偏移至 :345，文件实际 :345 命中）；② +10 处配置映射排除项（BatchExecutionModels.kt:107/:243；BatchSendTaskConfigService.kt:74/:107/:187/:292/:399/:527/:545/:563），逐项与源文件实际行号+context 精确核对一致；`ALLOWED_WRITE_SITES`（:28-33，仅 ExpertOperatorStatusService.kt + ManualInitialOutreachService.kt）与闭包断言（:155 `assertEquals(ALLOWED_WRITE_SITES, hitFiles)`、:162 stale-exclusion assert）未动。边界内另有 fc9cd75（child-04 验证记录，docs/plans/fast/* + ledger）与 e590785（A5 计划修订文档）两个记录/计划提交，非实现提交；b3ae95a 本身不含 docs/plans/fast/* 文件。 |
| Plan and invariants | PASS | I-1 三条旁路：① `buildEsFiltersForLevel`（ManualInitialOutreachService.kt:1218）CANDIDATE 分支 :1221-1230（留空/NOT_CONTACTED→notContactedWithEmailFilters，显式非 NOT_CONTACTED→状态无关基座+operatorStatusFilter :1229）与 else 分支 :1237 均应用过滤；② `RecipientScope.matchesExpert`（BatchExecutionModels.kt:60-68）NOT_CONTACTED→`profile.operatorStatus.isNullOrBlank()`、其余→相等；③ `buildMaterialReminderEsFilters` :1091 追加 operatorStatusFilter。I-2：else（APPLICATION）分支 :1237 + 测试 `ES APPLICATION branch applies operatorStatus term filter (I-2)`。I-3：`operatorStatusFilter`（ExpertSearchService.kt:144-149）NOT_CONTACTED→复用 `notContactedWithEmailFilters(null)`（must_not exists），其余→`term`；测试断言 must_not exists 存在且无 term。I-4：`updateLegacyConfig` :187 `operatorStatus = existing.operatorStatus,`（显式保留）+ `updateLegacyConfig preserves operatorStatus when only cron changes (I-4)` captor 断言 NOT_CONTACTED。I-5：`toView()` :399、`ConfigFields`、三个 `toFields()` :527/:545/:563 均带字段；`toLegacyConfig` :217-231 与 updateLegacyConfig 返回 BatchSendConfig 构造 :196-207 均不带。校验白名单 `ALLOWED_OPERATOR_STATUSES = OperatorStatus.entries.map{it.name}.toSet()`（:580-582），枚举 6 项与 `operatorStatusOptions`（app.js:618-624）一致。前端两面板：index.html 配置编辑器 select `batchConfigEditorOperatorStatus`（:1217-1221）与手动面板 `batchManualOperatorStatus`（:1373-1379），复用 `.batch-config-field` + `.bsc-input/.bsc-select`；`fillBatchOperatorStatusSelectOptions`（app.js:14585-14597，`operatorStatusOptions` + 「全部状态」空值）在 `bindBatchSendTaskEvents` :14638 调用；保存/读取/差异显示路径 :13665/:13883/:13898/:13923/:13947/:13992/:14022/:14099 全链路接线。V95 迁移：`ADD COLUMN operator_status VARCHAR(32) NULL AFTER discipline`（可空默认 NULL=不限，无 CHECK）。T-7：+6 测试（ES CANDIDATE term 替换 / NOT_CONTACTED must_not 无 term / APPLICATION 生效 / 重试 REPLIED 排除（A-3 形态）/ 留空行为不变 / I-4 保留）。 |
| Required commands | PASS | 全部 zulu-11 fresh 运行：① `mvn test` exit 0 → Tests run: 2410, Failures: 0, Errors: 0, Skipped: 4；JS 496 pass（基线 child-04 后 2404，+6 新测试）；② `mvn test -Dtest=ManualInitialOutreachServiceTest` exit 0 → 63/0/0/0；③ `mvn clean package` exit 0 → 2410/0/0/4，JS 496，BUILD SUCCESS；④ `git diff --check` exit 0 无 whitespace 错误；⑤ `mvn test -Dtest=OperatorStatusWriteSeamGuardTest`（A5 范围检查）exit 0 → 1/0/0/0。FlywayMigrationIntegrationTest 未运行（Amendments A2 豁免）。与 execution.md 记录基线完全一致。 |
| Downstream interfaces | PASS | `RecipientScope` 带 `operatorStatus`（BatchExecutionModels.kt:56，默认 null 保持兼容）；`BatchExecutionSnapshot` 带 `operatorStatus`（:20）；child 06 复用构件签名未变：`countEsTargets` :1184、`buildRetryableTargets` :926/:964、`PendingOutreachSummary` :1371；唯一签名变更 `buildMaterialReminderEsFilters`（私有，新增可选参默认 null，向后兼容，属 T-5 计划内）。 |

### AUTO_FIX
- N/A

### RECORD_ONLY
- N/A

### Required Action
- COMPLETE_CHILD
