# 04a 轻量验证报告（verify-p / four-gate）

## Light Verification: LIGHT_PASS_WITH_NOTES
Child: 04a — docs/plans/fast/batch-send-rhythm-and-filter/children/04a/brief.md
Boundary: 4004c387920eaa6a99997ca833d038da5b281729..f3738e89a286764e3fb8a5c93dd178b89ffa0a42
Verifier: Verifier04a

### Four Gates

| Gate | Result | Evidence |
|---|---|---|
| 1. Authorized scope | PASS | 04a 实现提交 `f3738e8`（vs 父 `1ea4367`）`git diff --name-only` 恰为 7 个授权文件：TaskExecutionRepository.kt / TaskExecutionService.kt / BatchSendTaskConfig.kt / BatchSendTaskConfigService.kt / BatchSendConfigController.kt / BatchSendTaskConfigServiceTest.kt / BatchSendConfigControllerTest.kt。禁改文件（BatchSendScheduler.kt、TaskExecution.kt、迁移、app.js、index.html）在整段范围零改动；`pom.xml` 无 diff（无第三方 cron 库）。无 spike 测试提交：`git ls-files | grep -i spike` 为空，`git status --porcelain src/` 干净。 |
| 2. Plan and invariants | PASS | I-1: `CronExpression.parse` 在 BatchSendTaskConfigService.kt 命中 3 处（既有 :244 + computeNextFireTime :400 + previewCron :407），computeNextFireTime 与调度器同用 Spring 6 段实现；用例断言 nextFireTime 晚于当前时刻。I-2: `if (!autoEnabled) return null`；用例通过。I-3: computeNextFireTime 用 `runCatching{}.getOrNull()` 降级 null，previewCron 用 `runCatching{}.getOrElse{→valid=false+message}`，控制器恒 `ResponseEntity.ok`（200）；toView 脏 cron 用例与 previewCron("bogus") 用例通过，控制器用例断言非法表达式 200。I-4: `list()` 单次 `lastExecutedAtByBatchConfigIds(ids)` 聚合 + 空集合判空（service 层 `isEmpty()→emptyMap()`，不触发 IN ()）；`toView` 方法体 grep 0 处 repository/service 调用；3 配置用例 Mockito `verify(times(1))` + captor 断言恰好 1 次且实参 = [1,2,3]；0 配置用例通过。I-5: 新查询 SQL diff 内 grep `trigger_type` 为 0（文件内唯一命中 :37 属既有 countActiveSince 查询），无 trigger_type 过滤；spike 实测 `batch_config_id IS NULL` 行被 IN 子句天然排除。View 字段 nextFireTime/lastExecutedAt（LocalDateTime? = null）置于 updatedAt 之后；toView 可选参 lastExecutedAt 已接入 list/get/update/setEnabled，create() 保持默认 null；previewCron trim、count.coerceIn(1,20)、严格递增（cursor=next）、空 times→valid=false；POST /api/mail/batch-send/cron/preview（@RequestMapping 基路径 + @PostMapping）置于 DELETE /configs/{id} 之后、execute 之前；CronPreviewRequest(cron, count: Int?=null)。构造依赖 TaskExecutionService 的全部 2 处手工实例化点（两个测试文件，均在授权清单内）已更新。A-1 框架假设已消解：DTO 投影 spike 完成（@DataJdbcTest + docker CLI 起的真实 MySQL 8.0.36 + Flyway target=73，`Tests run: 1, Failures: 0, Errors: 0`），结论与命令记录于 execution.md；spike 源码提交前已删、未提交。 |
| 3. Required commands (fresh, zulu-11) | PASS | ① `mvn test` → exit 0，2373/0/0/5，BUILD SUCCESS（多出的 1 个 skip 为 target/ 中遗留的 spike 编译产物，见 RECORD_ONLY）。② `mvn test -Dtest=BatchSendTaskConfigServiceTest,BatchSendConfigControllerTest` → exit 0，37+8=45/0/0/0。③ `mvn clean package` → exit 0，2372/0/0/4（clean 后恢复基线 skip=4，恰好 +10 新用例 = 2362+10），WAR `target/weibo-talent-introduction-1.0.0-SNAPSHOT.war` 产出。④ `git diff --check 4004c38..f3738e8` → exit 2，唯一命中在 `docs/.../children/03/brief.md:397`（new blank line at EOF），属中间 docs 提交 `1ea4367`（03 验证记录）的文件，非 04a 实现；`git diff --check 1ea4367..f3738e8`（04a 提交自身）exit 0 无输出。 |
| 4. Downstream interfaces | PASS | 04b brief 消费关系确认：X-3 消费 `GET /configs` 的 `nextFireTime`/`lastExecutedAt`（JSON 键名一致，控制器测试断言两键存在于响应 JSON，null 时前端 `—` 兜底）；04b I-3 要求「测试」按钮 `POST /api/mail/batch-send/cron/preview` 并渲染 `nextFireTimes`——端点路径与响应形态 {valid, message, nextFireTimes} 一致，`count ?: 5` 默认 5 次。既有端点/响应形态除追加字段外未变（diff 全为增量）。 |

### AUTO_FIX
无。四门全部通过；未发现可证明的计划违例。

### RECORD_ONLY
1. `git diff --check 4004c38..f3738e8` 在范围边界上 exit 2：`docs/plans/fast/batch-send-rhythm-and-filter/children/03/brief.md:397` 「new blank line at EOF」。该文件由中间提交 `1ea4367`（docs(fast-p): record 03 light verification）引入，非 04a 实现产物，亦不在 04a 授权文件清单内（且 brief 禁止实现者改动 docs/plans/fast 工件）。04a 实现提交自身 `git diff --check 1ea4367..f3738e8` 干净（exit 0）。建议 03 流程后续顺手修复该文件尾随空行。
2. `target/test-classes/` 与 `target/surefire-reports/` 中存在实现者 spike 阶段的遗留编译产物 `TaskExecutionRepositoryProjectionSpikeTest`（源码已删、未提交、`git status` 干净），使未经 clean 的 `mvn test` 计数为 2373/0/0/5（多 1 个 skipped）。`mvn clean package` 后产物消失，恢复 2372/0/0/4（基线 2362 + 10 个新用例，skip=4 与 03 基线一致）。属工作树卫生问题，非提交缺陷，clean 构建自愈。
3. 实现提交信息 `feat(fast-p): implement 04a` 无正文（brief A-1 曾要求「在提交信息中说明」spike 结论）；实际结论已完整记录于 `children/04a/execution.md`（DTO 投影成功、未降级），风险已消解，仅记录。
### Required Action
- COMPLETE_CHILD
