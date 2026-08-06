# Execution Report — p1-expert-profile-absence

## Execution Result: PLAN_CONFLICT

- Plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.worktrees/mail-reliability/docs/plans/2026-08-06/expert-profile-absence-not-error.md`
- Plan SHA-256: `7177699254e35ea798fdde59ed923725655d375eeb3919c0046ff65f257871ba`（执行前/后一致，未变更）
- Execution ID: `.../expert-profile-absence-not-error.md@7177699254e35ea798fdde59ed923725655d375eeb3919c0046ff65f257871ba`
- Execution epoch: NEW
- Executor: `ImplP1`
- Target worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.worktrees/mail-reliability`
- Target branch: `fast/mail-reliability`
- Worktree ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.worktrees/mail-reliability@fast/mail-reliability@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/mail-reliability`
- Pre-execution HEAD: `e704a5853d40d9314c32b27a4eac2303ba194dcc`（= d911bd6 基线 + p4-task0 两提交 + ledger）
- Commit: **未提交**（见 Remaining Blocker）

## Blocker（必须先裁决）

`mvn test` / `mvn test -Dtest=ExpertIndexControllerTest` / `mvn clean package` 三者在 exec-plugin 的
`node --test src/test/js/*.test.js`（pom.xml:199，绑定 test 阶段）处失败，唯一失败用例：

- `src/test/js/mailboxInboundTags.test.js:84` — `refreshExpertTagsFromEs: async () => ["自动晋升"]`

该桩在基线 d911bd6 即存在且当时通过（旧契约 `mutateExpertTag` 直接消费数组）。本计划任务 2.2（必须实现）
将 `mutateExpertTag` 改为 `refreshed.tags` 后，该桩返回裸数组 → `.tags` 为 `undefined` →
`TypeError: Cannot read properties of undefined (reading 'includes')`（`app.js` mutateExpertTag 内）。
计划 Interaction point 3 只审计到 `expertTagBatchFix.test.js:19-25` 一处桩，遗漏了此第二处。

修复是机械且契约确定的（brief 交互点句「既有 JS 测试桩改 `{ found: true, tags: [] }`」）：
`refreshExpertTagsFromEs: async () => ["自动晋升"]` → `async () => ({ found: true, tags: ["自动晋升"] })`，
同文件断言 `Array.from(tags)` 无需改动（`mutateExpertTag` 仍返回标签数组）。

但 `src/test/js/mailboxInboundTags.test.js` **不在本 child 授权 5 文件清单内**（brief「Authorized Files（排他，M-1）」），
master plan M-1 明文「任一执行者若发现需要改动不属于本计划的文件，**必须停止并上报**，由本文件裁决归属后再动。
**禁止**『顺手改一下』」；execute-p 亦规定「If completion requires an unlisted file … stop with PLAN_CONFLICT」。
故按流程停止，等 controller 裁决：授权该文件（本 child 补入第 6 文件）或修订计划。

## 已实现（5 个授权文件全部完成，聚焦验证通过）

| 任务 | 文件 | 状态 | 证据 |
|---|---|---|---|
| 1.1 getExpertProfile 去 404 | `ExpertIndexController.kt` | IMPLEMENTED | 返回 `found = profile != null`，无 `ResponseStatusException`/`try`；`getTemplateVariables` 404 保留 |
| 1.2 `found: Boolean` 字段 | 同上（`:347`） | IMPLEMENTED | 位于 orcidId 后、tags 前，无默认值 |
| 1.3 后端 3 用例 | `ExpertIndexControllerTest.kt` | IMPLEMENTED | 18 run / 0 fail / 0 err（含新 2 用例 + 既有用例补 `found==true`） |
| 2.1 fetchExpertTagsFromEs 契约 | `app.js:4018-4023` | IMPLEMENTED | 返回 `{ found: profile?.found !== false, tags: profile?.tags \|\| [] }`；空 orcidId 早退 `{found:false,tags:[]}`；无 catch |
| 2.2 mutateExpertTag 取 `.tags` | `app.js:4056-4057` | IMPLEMENTED | `refreshed.tags` 后 `.includes`/`.filter` 逐字不变 |
| 2.3 renderExpertTagEditor profileMissing | `app.js:3951+` | IMPLEMENTED | S-1 逐字 DOM（`data-profile-missing="true"`，无 actions 块）；有画像分支 S-2 逐字不变；renderMailboxExpertTagEditor 末位透传 |
| 2.4 5 消费点 | `app.js` 6585/6952/8347/8766/9325 | IMPLEMENTED | 全部取 `.tags` + 传 `found === false`；无 orcid 分支返回 `{found:false,tags:[]}` |
| 2.5 加标签入口拦截 | `app.js:8347` 一带 | IMPLEMENTED | `found === false` → `showStatus("该专家在 ES 中无画像文档，标签功能不可用","warn")` + return，在 openExpertTagAddDialog 之前 |
| 2.6 两监听器 catch | `app.js` 11489/10499 | IMPLEMENTED | 均以 `.catch((error) => showStatus(error.message, "error"))` 收口，与 :11013 逐字一致 |
| 3.1 修既有 JS 测试桩 | `expertTagBatchFix.test.js:19-25` | IMPLEMENTED | `api: async () => ({ found: true, tags: [] })`；断言按对象调整 |
| 3.2 新增 JS 测试 | `expertProfileAbsence.test.js`（新增） | IMPLEMENTED | S-1/S-2 归一化逐字断言、found===undefined 走有画像分支、api 抛错上抛、styles.css class 存在性（K-dom-stub） |

不变量核对（grep）：I-1 ✓（controller 无 404/try；fetch 无 catch）；I-2 ✓（无 CANDIDATE 回退，data-level 仍由入参产出）；I-3 ✓（S-1 无两个 data-action；guard 先 showStatus+return）；I-4 ✓（全文 `=== false`，无 `if (!found)`）；I-5 ✓（两监听器 catch 与 :11013 逐字一致）；S-1/S-2 ✓（测试逐字断言）；styles.css 零改动 ✓；无 migration ✓。

## Commands（cwd = worktree；JAVA_HOME=zulu-11）

| 命令 | 退出码 | 结果 |
|---|---|---|
| `mvn test` | **1** | Kotlin `Tests run: 2161, Failures: 0, Errors: 0`（全绿）；exec 插件 JS 套件 **465 pass / 1 fail**（仅 mailboxInboundTags 桩，见 Blocker） |
| `mvn test -Dtest=ExpertIndexControllerTest` | **1** | `ExpertIndexControllerTest: Tests run: 18, Failures: 0, Errors: 0`；同上 JS 单用例失败 |
| `node --test src/test/js/expertProfileAbsence.test.js` | 0 | tests 7 / pass 7 / fail 0 |
| `node --test src/test/js/expertTagBatchFix.test.js` | 0 | tests 33 / pass 33 / fail 0 |
| `node --check src/main/resources/static/app.js` | 0 | 无输出 |
| `mvn clean package` | **1** | Kotlin `Tests run: 2161, Failures: 0, Errors: 0`；同上 JS 单用例失败 |
| `git diff --check` | 0 | 无输出 |

## Changed Files（工作区，未提交）

- `src/main/kotlin/com/weibo/talentintroduction/expert/controller/ExpertIndexController.kt` — getExpertProfile 去 404 + `found` 字段（+10 −4）
- `src/main/resources/static/app.js` — 契约收敛、S-1 降级、5 消费点、guard、两监听器 catch（+39 −18）
- `src/test/kotlin/com/weibo/talentintroduction/expert/controller/ExpertIndexControllerTest.kt` — 3 用例（+23）
- `src/test/js/expertTagBatchFix.test.js` — 桩改对象 + 断言调整（+4 −3）
- `src/test/js/expertProfileAbsence.test.js` — 新增（7 用例）
- （`docs/plans/fast/mail-reliability/ledger.md` 为 controller 未提交改动，本 child 未触碰）

## Deviations

- 无实现偏差：全部按计划 1/2/3 阶段与 S-1/S-2 逐字实现。
- 未提交、未写 execution 报告至 commit 之外：受 Blocker 影响，等待裁决后按「一个本地提交」规则一次提交（5 文件，或裁决后含 mailboxInboundTags.test.js 的 6 文件）。

## Freshness

- Plan identity rechecked: YES（sha256 一致）
- Worktree identity rechecked: YES
- Required commands run this invocation: YES（全部 7 条，退出码如上）
- Historical evidence used only as baseline: YES（d911bd6 基线仅作对照）

## Remaining Blocker

- 最小缺失授权：允许修改 `src/test/js/mailboxInboundTags.test.js`（第 84 行桩形状），或修订计划文件清单/验收标准以豁免全量门禁。裁决后本 child 可立即完成：改桩 → 重跑 7 条命令 → 提交 → 更新本报告。

## Next Action

- PLAN_CONFLICT → controller 依 M-1 裁决归属；批准后 `ImplP1` 恢复执行（RESUME 同一 Execution ID）。
