# Child 01b 执行报告 — ORCID 机构唯一性与任职语义（崩溃重试）

## Execution Result: READY_FOR_VERIFICATION

- Plan（完整合同）: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-28-discovered-institution-repair-00-master/docs/plans/2026-09-28/discovered-institution-repair-01-orcid.md`
- Plan SHA-256: `8301ee87bb823dfd95c7db36b912e580aad4ef479090bd05682d0d6af5a66e22`
- Brief: `children/01b/brief.md`，SHA-256 `10f0423d272260be916a53d46fb98b3b741f6a2a700baaecc802666f3e5c1979`
- Execution ID: `docs/plans/2026-09-28/discovered-institution-repair-01-orcid.md@8301ee87bb823dfd95c7db36b912e580aad4ef479090bd05682d0d6af5a66e22`
- Execution epoch: `RESUME`（同一 EXECUTION_ID 上恢复前任 attempt 1 的未提交产物；前任因 provider error 崩溃，无 commit）
- Approval basis: 本次调用（brief 明确授权「retry after predecessor provider crash；keep what is correct, finish what is missing」）
- Executor: `Implement01bRetry`
- Target worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-28-discovered-institution-repair-00-master`
- Target branch: `fast/2026-09-28-discovered-institution-repair-00-master`
- Worktree ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-28-discovered-institution-repair-00-master@fast/2026-09-28-discovered-institution-repair-00-master@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-2026-09-28-discovered-institution-repair-00-master`
- Pre-execution code SHA (child_base_sha): `b12c971be46a992b275a3e4fb3768047b0af8877`
- Post-execution code SHA（= Evidence HEAD，实现提交）: `68ad011971ac4cbafdd439cfe2d981476ff1332a`（subject 逐字 `feat(fast-p): implement 01b`，单提交，4 文件）
- Implementation boundary: `b12c971be46a992b275a3e4fb3768047b0af8877..68ad011971ac4cbafdd439cfe2d981476ff1332a`（单提交，仅 4 个 Authorized Files；`docs/plans/fast/**` 保持未跟踪）
- 身份工具：`~/.agents/skills/execute-p/scripts/plan_identity.py` / `worktree_identity.py`（本次均可用，输出见上）；执行前/提交前各取一次。

## 前任残留产物审查（崩溃上下文）

`git status` 在本 child 开工前仅有 2 个未提交修改（产品代码零改动）：

| 残留文件 | 增量 | 审查结论 |
|---|---|---|
| `OrcidDataSourceTest.kt` | +63 | **保留**：新增 helper `expandedSearchResponseWithInstitutions`、`onlyRecordWithInstitutions` 与 6 个 I-1 用例，逐条对应计划验收标准「单机构输出原名 / 两个不同机构输出 null / 重复同名只算一家 / 数组为空输出 null」，并额外覆盖 trim 与全空白两种边界，无越界断言、无改动既有用例。 |
| `ExpertDiscoveryServiceTest.kt` | +116/-3 | **保留**：`orcidPageBody` 增加默认参数 `institutionNames = listOf("Test University")`（既有 4 个调用点语义不变）；新增 `orcidTemplate`、`discoverOrcidAndCapture`（用**真实** `OrcidDataSource` 跑完整 ORCID 发现并捕获 RAW/候选两层文档）与 2 个 I-1/I-2 用例，断言落在 ES 文档边界（可观察行为），并含身份回归（主键 / `externalIds.orcid` / 姓名 / 邮箱 / `identityVerification`）。 |

结论：两处残留与计划一致，未发现与计划矛盾的 hunk，无需回退；缺失部分为**全部产品代码**（`OrcidDataSource.parseOrcidRecords` 仍 `firstOrNull()`；`buildOrcidProfile` 仍 `employment=institutionName`、`country=record.country`），本次补齐。仓库无其它未提交/未跟踪残留（`docs/plans/fast/**` 保持未跟踪，按 brief 排除在实现提交之外）。

## Task Status

| Requirement | Status | Files | Evidence |
|---|---|---|---|
| I-1 ORCID 多机构不猜（trim/去空/去重/恰一项，禁 `firstOrNull()`） | IMPLEMENTED | `OrcidDataSource.kt` | `OrcidDataSource.kt:166-177`：`institution-name` → `mapNotNull { it.asText(null)?.trim()?.takeIf { it.isNotEmpty() } }.distinct()` → `institutions.singleOrNull()`；代码中 `firstOrNull()` 已零调用（`grep -n firstOrNull OrcidDataSource.kt` 唯一命中为 `:167` 注释里对旧做法的说明） |
| I-2 任职/国家保守（ORCID 建档 `employment=null`、`country=null`） | IMPLEMENTED | `ExpertDiscoveryService.kt` | `ExpertDiscoveryService.kt:1513-1522`：`country = null`、`employment = null`、`institution = record.institutionName`（唯一机构才非空） |
| 身份路径不变（邮箱/姓名/ORCID ID/`identityVerification`） | IMPLEMENTED（未触碰） | — | `orcidRecordToAuthorEmails` 与 `proofFor(...)` 原样；`buildOrcidProfile` 仅改 employment/country 两字段。测试断言：`ExpertDiscoveryServiceTest.kt:5459-5468`（主键 = `ExpertIdGenerator.generate(null, email)`、`externalIds.orcid`、givenNames/familyNames/email、`IdentityVerification.status=VERIFIED`、`source=ORCID_RECORD_SHA256`、`orcid`、`email`），`:5488-5494`（多机构时身份仍不变） |
| RAW/候选两层同值 | IMPLEMENTED | `ExpertDiscoveryService.kt`（晋升只复制） | `ExpertDiscoveryServiceTest.kt:5474-5477`（`raw["institution"] == candidate["institution"]`，两层 `employment`/`country` 均 null）；`ExpertDiscoveryService.kt:2291-2308` 晋升为 `rawDoc.toMutableMap()` 复制，无二次推导 |
| 不得改变论文 / SBIR 路径行为 | IMPLEMENTED（未触碰） | — | 仅改 `buildOrcidProfile`；论文路径 `ExpertDiscoveryService.kt:2261`（`employment = null, institution = authorEmail.institutionName`）与 SBIR 相关文件零改动（`git diff --stat` 见下） |
| 范围外：历史回填 / 当前职位判定 / ORCID 国籍推断 | 未引入 | — | 无迁移、无回填任务、无职位/国籍推断代码新增 |

## TDD 证据

### 红证据（行为红，改产品代码前，本次调用内 fresh 运行）

命令：`JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -q test -Dtest=OrcidDataSourceTest,ExpertDiscoveryServiceTest`
→ **exit 1，`Tests run: 180, Failures: 6, Errors: 0, Skipped: 0`**（分类：`ExpertDiscoveryServiceTest` 164 跑 / 2 失败；`OrcidDataSourceTest` 16 跑 / 4 失败）

```
[ERROR] Tests run: 164, Failures: 2, Errors: 0, Skipped: 0 … in …ExpertDiscoveryServiceTest
  ORCID discovery writes the single institution and never invents employment or country (I-1 I-2):5456
    机构名不得写成当前任职 ==> expected: <null> but was: <Test University>
  ORCID discovery writes no institution when the record lists two different institutions (I-1 I-2):5485
    两个不同机构时不得任选第一家当主机构 ==> expected: <null> but was: <Seoul National University>
[ERROR] Tests run: 16, Failures: 4, Errors: 0, Skipped: 0 … in …OrcidDataSourceTest
  …keeps the only distinct non-empty institution trimmed (I-1):252  expected: <Seoul National University> but was: <  Seoul National University  >
  …writes null institution when two distinct institutions are listed (I-1):258  expected: <null> but was: <Seoul National University>
  …drops blank institution names before counting (I-1):276  expected: <Korea University> but was: <>
  …writes null institution when every name is blank (I-1):282  expected: <null> but was: <>
```

6 条红全部是**行为红**（旧 `firstOrNull()` 按数组顺序取第一家、truncate 前不 trim、空白当一家；`employment` 被写成机构名），无编译错误、无 setup 报错——即新断言在未改动产品代码上确实因旧行为而失败。另 2 条新用例（重复同名只算一家、空数组输出 null）在改动前即为绿，属回归护栏（旧代码在该输入下恰好也返回 null/原值）。

### 绿证据

- 命令 1（定向两类）：`180 / 0 / 0 / 0`，exit 0（6/6 新用例转绿，其余 174 条无回归）。
- 命令 2（全量）：`4242 / 0 / 0 / 13`，exit 0；Node 套件 `tests 1202 / pass 1202 / fail 0`（与基线一致）。
- 未削弱、未改写任何既有断言；新增用例只在既有测试文件中**追加**（`ExpertDiscoveryServiceTest.orcidPageBody` 以默认参数向后兼容扩展）。

## Commands（fresh，本次调用内运行）

| Command | Result | Evidence |
|---|---|---|
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=OrcidDataSourceTest,ExpertDiscoveryServiceTest` | PASS | exit 0；`Tests run: 180, Failures: 0, Errors: 0, Skipped: 0`（`ExpertDiscoveryServiceTest` 164、`OrcidDataSourceTest` 16；log `/tmp/01b-cmd1.log`） |
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test` | PASS | exit 0；`BUILD SUCCESS`；surefire 聚合 `Tests run: 4242, Failures: 0, Errors: 0, Skipped: 13`；Node 套件 `tests 1202 / pass 1202 / fail 0`（log `/tmp/01b-cmd2.log`） |
| `git diff --check` | PASS | exit 0，无输出 |

### 与基线对比（`children/01/baseline.md`：seed `4220/0/0/13` + Node 1202 pass；child 01 post-state `4234/0/0/13`）

| 指标 | child 01 post-state（child_base_sha `b12c971`） | 本次（01b） | 判定 |
|---|---|---|---|
| surefire 聚合 | 4234 / 0 / 0 / 13 | 4242 / 0 / 0 / 13 | 无新增失败；+8 = 本 child 新增用例数（+6 OrcidDataSourceTest、+2 ExpertDiscoveryServiceTest），与计划验收标准一一对应 |
| OrcidDataSourceTest | 10 / 0 / 0 | 16 / 0 / 0 | +6 |
| ExpertDiscoveryServiceTest | 162 / 0 / 0 | 164 / 0 / 0 | +2 |
| Node 套件 | 1202 pass | 1202 pass / 0 fail | 不变 |
| seed 基线 | 4220 / 0 / 0 / 13 | 4242 / 0 / 0 / 13 | 相对 seed +22（01 的 +14 + 01b 的 +8） |

## Changed Files（4/4，全部在 Authorized Files 白名单内）

```
src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt |  5 +++--
src/main/kotlin/com/weibo/talentintroduction/discovery/service/OrcidDataSource.kt          |  7 ++++++-
src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryServiceTest.kt | +116/-3（前任残留，审查后保留）
src/test/kotlin/com/weibo/talentintroduction/discovery/service/OrcidDataSourceTest.kt        | +63（前任残留，审查后保留）
```

- `OrcidDataSource.kt:166-177` — 唯一机构提取：`trim` / 去空 / `distinct` / `singleOrNull()`；`firstOrNull()` 已移除。
- `ExpertDiscoveryService.kt:1513-1522` — `buildOrcidProfile`：新增 I-2 注释、`country = null`、`employment = null`（`institution` 语义保持在「唯一机构」）。
- `OrcidDataSourceTest.kt:57-83,250-283` — 2 个 helper + 6 个 I-1 用例。
- `ExpertDiscoveryServiceTest.kt:455-474,476-531,5450-5499` — helper 扩展 + 2 个 I-1/I-2 用例（RAW/候选同值 + 身份回归）。

## 下游接口（供 child 02）

- ORCID 发现的专家档案：`institution` **仅在** `expanded-result.institution-name` 去空去重后**恰好一家**时等于该 trimmed 原名，否则为 `null`；`employment` 恒 `null`；`country` 恒 `null`。
- `institutionSource` 不参与 ORCID 路径（无 `INSTITUTION_SOURCE_ORCID`；ORCID 机构走 `OrcidRecord.institutionName` → 档案 `institution`），故 02 的 `institutionEvidence` `ORCID:` 签发只能基于「唯一机构」这一判定结果。
- 未新增/未修改 ES 字段、mapping、迁移、前端；`OrcidRecord.country` 字段保留（构造签名不变，`OrcidDataSource.kt:35`），ORCID 路径不再读取它。

## Deviations

- 无范围偏差：只改 4 个 Authorized Files；未新建白名单外文件；未改 `docs/plans/**`；未 push/merge/rebase/squash/amend/reset；未跑格式化/lint。
- 两处需要记录的判断（均在计划文本之内）：
  1. `buildOrcidProfile` 写 `country = null` 而非保留 `record.country`：解析器本来就恒写 `country = null`，两种写法行为等价；计划「country 继续 null」要求显式化，故按计划写 `null`。`OrcidRecord.country` 字段保留不删——删除会改变构造签名，波及白名单外的 `ExpertDiscoveryServiceTest` / `DiscoveryPipelineServiceTest` 调用点，属越界。
  2. 机构名以 **trim 后** 原值写入（既满足 I-1「先 trim」，也满足验收「单机构输出原名」——测试断言 `"  Seoul National University  "` → `"Seoul National University"`）。
- 前任残留的 `OrcidDataSourceTest`/`ExpertDiscoveryServiceTest` 编辑经逐 hunk 审查后全部保留（无与计划矛盾的 hunk）。

## Freshness

- Plan identity rechecked: YES（执行前 / 提交前两次 `plan_identity.py` 均为 `8301ee87…6e22`）
- Worktree identity rechecked: YES（`worktree_identity.py --expect-root/--expect-branch` 通过；全程指向本 worktree 与 `fast/2026-09-28-discovered-institution-repair-00-master`）
- Reported commits reachable from target branch: YES（见 commit SHA；即该分支 HEAD）
- Required commands run this invocation: YES（红证据 + 命令 1/2/3 均在本次调用内 fresh 运行）
- Historical evidence used only as baseline: YES（`children/01/baseline.md`、child 01 post-state 计数仅用于对比）

## Remaining Blocker

- None.

## Next Action

- READY_FOR_VERIFICATION → run `verify-p`。
- 人工验收（master plan A-1/A-2）需真实 ORCID 发现 + 详情页目视，属 whole-system 人工门禁，不在本 child 执行范围。
