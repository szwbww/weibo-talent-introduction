# 05-ingestion 执行报告（epoch 1）

## Execution Result: READY_FOR_VERIFICATION

- Plan: `docs/plans/2026-10-13/discovery-person-05-ingestion.md`
- Plan SHA-256: `924e929a9228d4ba302a8ce0df6017337e430cf986c70716ef2d50fc667d92ff`
- Execution ID: `docs/plans/2026-10-13/discovery-person-05-ingestion.md@924e929a…d92ff`
- Execution epoch: NEW
- Executor: Impl05-2 (execute-p)
- Target worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-13-discovery-identity-recovery-master`
- Target branch: `fast/2026-10-13-discovery-identity-recovery-master`
- Pre-execution code SHA (start HEAD): `b1c8c23f0c1dd423a27688b9f413b0e7de99fee4`
- Post-execution code SHA: 见提交（`feat(fast-p): implement 05-ingestion`）
- Authorized files: 精确 8 个（见下）。未新增第 9 个文件；未新建任何类文件（统一登记接缝 `DiscoveryPersonRegistrationService` 落在授权文件 `DiscoveryIdentityRepairService.kt` 内）。
- 边界：无 Flyway 迁移、无新 DB/ES 字段、无生产动作、未触碰其他 tag/运营/学术写入路径、未改 `backfillRawEmailsAndPromote`、未纳入 `docs/plans/fast/**` 到提交。
- 起始工作树说明：`docs/plans/fast/2026-10-13-discovery-identity-recovery-master/ledger.md` 在开始时已被 controller 记账改动（非本任务产物），全程未触碰、未纳入提交。

## 实现摘要（I-1/I-2/I-3）

### 统一登记接缝（新增于授权文件 `DiscoveryIdentityRepairService.kt`）
- `DiscoveryPersonRegistrationService.refresh(source, docId)`：从**当前持久化 ES 档案**（RAW/CANDIDATE/APPLICATION 逐层 `readDiscoveryDocument`）重读事实，由 04 的 `DiscoveryPersonIdentity` 单源签发指纹与作者键，构建 `DiscoveryPersonSnapshot` 后调用已验证的 `DiscoveryPersonService.register`。
  - 只对**实时 discovered 成员**生效：非 discovered/无档案 → `SKIPPED`（非失败）；已注册则**整组现存成员一并复验**（指纹变化 → `STALE`，不借旧关系通过；跨根 → `NEEDS_REVIEW`）。
  - 结论 `DiscoveryPersonRefreshResult.status ∈ {CREATED, ATTACHED, UNCHANGED, NEEDS_REVIEW, SCOPE_CONFLICT, STALE, FAILED, SKIPPED}`；`retryable = STALE || FAILED`。
  - 不猜身份（不从无凭证 externalIds 构造）、不据同名归组、不写姓名/邮箱/作者编号/学术/运营字段。

### ExpertDiscoveryService（I-1/I-2/I-3）
- 接入 04 登记到全部成功出口：
  - `consumeOutcomeInternal` 新建出口：`indexToRaw` 成功 + 准入后 → `registerPerson(...)`。
  - 重复出口 `recordIdentityDuplicate`：补证成功（REPAIRED/ALREADY_VERIFIED）复用补证内刷新结果 `repair.personRegistration`；无补证可走时对现有 `docId` 直接登记；重复分支不再提前 return 而跳过归人。
  - `consumeQueuedItem`：经由 `consumeOutcomeInternal` 覆盖；新增字段透传。
  - 同步 ORCID 循环 `discoverFromOrcid`：新建出口与重复出口均登记（与论文队列共用同一 `registerPerson`）。
- 计数单位清楚（I-3）：`ConsumeOutcomeResult`/`QueuedItemConsumption` 新增 `personCreated`（新增人物）、`personAttached`（追加邮箱）、`personNeedsReview`（需复核）、`personScopeConflict`（范围冲突）、`registrationFailed`（可重试登记失败）；原邮箱事实条数（`indexedExperts`/`duplicateExperts`）语义不变，不当作人数。
- 传播（I-2/I-3）：可重试登记失败（STALE/FAILED）→ `registrationFailed=true`，`succeeded=false`，页不推进（新增停止原因 `PERSON_REGISTRATION_INCOMPLETE`，纸本页与 ORCID 页两条循环均覆盖）；语义 `NEEDS_REVIEW`/`SCOPE_CONFLICT` → 仅记 `failureReasons`（`PERSON_NEEDS_REVIEW`/`PERSON_SCOPE_CONFLICT`），不无限重试。
- 预算不变：`maxAuthorsPerRun`、队列批大小、下载预算均未改。

### DiscoveryIdentityRepairService / DiscoveryIdentityRecoveryService（I-1/I-2）
- `repairDuplicateIdentity`：REPAIRED/ALREADY_VERIFIED 后刷新人物登记，结果随 `IdentityRepairResult.personRegistration` 返回（默认 null，向后兼容 02）。仅补证成功触发，缺名/冲突/部分/失败不抢先建立或改写关系。
- `DiscoveryIdentityRecoveryService.apply`：APPLIED 后刷新人物登记（只读结论，失败不改恢复结论）；PARTIAL 不刷新。

### DiscoveryPipelineService（I-2/I-3）
- 新增原因映射：`consumption.registrationFailed → "PERSON_REGISTRATION_FAILED"`，按既有 `handleRetryableFailure` 退避重试，超限落 FAILED（不无限重试）。

## 变更文件清单（8 个授权文件）

| 文件 | 动作 |
|---|---|
| `src/main/kotlin/.../discovery/service/ExpertDiscoveryService.kt` | 修改 |
| `src/main/kotlin/.../discovery/service/DiscoveryPipelineService.kt` | 修改 |
| `src/main/kotlin/.../discovery/service/DiscoveryIdentityRepairService.kt` | 修改（含新增统一登记接缝） |
| `src/main/kotlin/.../discovery/service/DiscoveryIdentityRecoveryService.kt` | 修改 |
| `src/test/kotlin/.../discovery/service/ExpertDiscoveryServiceTest.kt` | 修改 |
| `src/test/kotlin/.../discovery/service/DiscoveryPipelineServiceTest.kt` | 修改 |
| `src/test/kotlin/.../discovery/service/DiscoveryIdentityRepairServiceTest.kt` | 修改 |
| `src/test/kotlin/.../discovery/service/DiscoveryIdentityRecoveryServiceTest.kt` | 修改 |

## 回归场景覆盖（计划实现方案 4）

- 原多邮箱 HTML 夹具：`ExpertDiscoveryServiceTest.published HTML through parser consumer and writer admits two owned emails ...` 扩展断言 —— 同作者两邮箱登记为 `created=1 / attached=1`，ES 保留 4 条技术记录（两邮箱各 RAW+CANDIDATE，第二邮箱不被删除）。
- same-ORCID 两邮箱：`DiscoveryIdentityRepairServiceTest.two emails of the same orcid become one person with two email evidences (I-1)` —— 真实 `DiscoveryPersonService` + 内存仓储：`CREATED` 后 `ATTACHED`，`persons=1`、`members=2`、ES 仍 2 条原始邮箱事实。
- 中断/重复/来源冲突/预算：
  - 登记失败可重试且不推进页：`ExpertDiscoveryServiceTest.a retryable registration failure keeps the consumption incomplete (I-2 I-3)`、`...synchronous page with an incomplete registration stops...`、`DiscoveryPipelineServiceTest.a registration failure stays retryable and terminates only after the fifth attempt`。
  - 重复任务幂等/归人：`a second email of the same author attaches to the existing person`；补证出口 `a repaired duplicate surfaces the person registration result`。
  - 语义需复核不重试：`a needs-review registration is recorded without failing the item (I-3)`。
  - 非 discovered/无档案跳过：`a non-discovery document is skipped and never creates a person`。
  - 暂停/预算断言沿用既有 `DiscoveryPipelineServiceTest` 未改动用例（人工暂停不登记、超限 FAILED），本次未改暂停/预算逻辑。

## Commands（fresh，本次调用内执行）

| Command | Result | Evidence |
|---|---|---|
| C1: `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=ExpertDiscoveryServiceTest,DiscoveryPipelineServiceTest,DiscoveryIdentityRepairServiceTest,DiscoveryIdentityRecoveryServiceTest test` | PASS (exit 0) | BUILD SUCCESS；Surefire：ExpertDiscoveryServiceTest 190/0/0/0，DiscoveryPipelineServiceTest 49/0/0/0，DiscoveryIdentityRepairServiceTest 16/0/0/0，DiscoveryIdentityRecoveryServiceTest 17/0/0/0；JS 门禁 1657 tests / 0 fail |
| C2: `DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dapi.version=1.41 '-DargLine=-Dapi.version=1.41' -DmysqlIt=true -Dtest=DiscoveryPersonServiceTest,DiscoveryPersonRepositoryTest test` | PASS (exit 0) | BUILD SUCCESS；Surefire：DiscoveryPersonServiceTest 17/0/0/0，DiscoveryPersonRepositoryTest 6/0/0/0；JS 门禁 1657 tests / 0 fail |

> C1/C2 均在本实现最终态之后**本次调用内**重新执行；JS 门禁基线 1657/1657/0 保持不变。

## Freshness

- Plan identity rechecked: YES（SHA-256 未变）
- Worktree identity rechecked: YES
- Reported commits reachable from target branch: YES
- Required commands run this invocation: YES
- Historical evidence used only as baseline: YES

## Remaining / 未决

- 无阻断性未决项。设计说明：
  - 「复验完整组」由 05 侧在 `buildMembers` 带上目标成员所在人物的全部现存成员指纹，再由 04 服务端逐条复验落实（指纹变化 → `STALE`，跨根 → `NEEDS_REVIEW`），05 不自行吞并根。
  - `STALE` 归类为可重试（读档案与登记之间事实/指纹竞争），由 pipeline 既有 5 次退避上限兜底，不无限重试。
