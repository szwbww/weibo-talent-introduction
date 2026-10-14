# Execution Log — 02-diagnostics

（由实施者写入；append-only）

---

## 2026-10-14 · Impl02Diagnostics · 实施提交

- Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-14-batch-filter-main`
- Branch: `fast/2026-10-14-batch-filter-main`
- child_base_sha: `bba581fe011730b52ad05d35e6248c9599372c2d`（01-region 终止 Code head）
- 实施提交：`8b0736389c92e5d6f4c5766cdaf968720e6d2d03` — `feat(fast-p): implement 02-diagnostics`（仅 10 个授权文件）
- 结果：**READY_FOR_VERIFICATION**（无 PLAN_CONFLICT、无 BLOCKED）

### 1. 变更文件（提交内唯一路径集合，10 个）

| 文件 | insertions | deletions |
|---|---:|---:|
| `src/main/kotlin/com/weibo/talentintroduction/campaign/repository/BatchRecipientReadRepository.kt`（新） | 151 | 0 |
| `src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchRecipientPreflightService.kt`（新） | 396 | 0 |
| `src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchEmailVerificationService.kt` | 26 | 0 |
| `src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchRecipientSelectionService.kt` | 43 | 33 |
| `src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt` | 262 | 0 |
| `src/main/kotlin/com/weibo/talentintroduction/mail/controller/BatchSendConfigController.kt` | 23 | 0 |
| `src/test/kotlin/com/weibo/talentintroduction/campaign/repository/BatchRecipientReadRepositoryIT.kt`（新） | 189 | 0 |
| `src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchRecipientPreflightServiceTest.kt`（新） | 402 | 0 |
| `src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchRecipientSelectionServiceTest.kt` | 17 | 0 |
| `src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt` | 102 | 0 |

合计 1,578 insertions / 33 deletions。无 Flyway 迁移、无新增/修改持久字段、无 ES mapping 或写路径改动、无其他文件改动。
`docs/plans/fast/**` 下的 fast-p 证据文件未进入该提交（工作树里 `ledger.md` 的既有改动保持未提交）。

### 2. 实现摘要

**I-1（完整母集 + 独立原因）**

- 新增 `BatchRecipientPreflightService`：输入母集（仅所选漏斗层级 + 标签）与本次显式条件分开；对母集**逐人**输出 `admissionState`、`conditionReasonKeys`、`protectionReasonKeys`、`templateMissingKeys`、`verificationPhase`。
- `ManualInitialOutreachService.scanBaseCandidates`：母集扫描的 ES 过滤器只含 `exists email` +（可选）标签（`buildBaseEsFilters`），**不**追加地区/状态/方向/类型/模板门禁，也绝不追加 INTRODUCTION 空类型集合的 `MATCH_NONE_FILTER`；再并入同层级 NEW 重试候选，按真实 docId 去重。母集超限（20,000）返回 `partial=true`。
- `BatchRecipientSelectionService.select`：改为对**全部**发现/待确认档案读取持久准入（不再跳过条件未通过者），条件不匹配只写 `filterKeys` 且不再把已准入目标标成 `NOT_DISCOVERY`；`reasonKeysOf` 口径逐字不变（条件优先、否则准入状态；通过不计）。

**I-2（共用预检查与快照）**

- 预估（`countBySnapshot`）、按 docId 诊断（`diagnoseRecipients`）与执行期模板门禁共用同一个 `BatchRecipientPreflightService`；模板逐人判定（`templateGateExcludes`/`expertMissingTemplateKeys`）是唯一实现，`ManualInitialOutreachService.batchGateExcludes` 委托它。
- 执行路径原有的发送前实时保护（抑制、已绑定、历史永久失败、类型）逐字保留；新增抑制在发送前仍然拦截（见测试）。

**I-3（邮箱历史与实时验证开关分离）**

- `BatchEmailVerificationService.readReusableProviderStates`：只读历史解释入口，复用 `findReusableByEmails` 的有效期/`request_count>0`/非复用原始行/无错误码与最新排序，不做任何写入、不调供应商。
- 两开关 OFF：预检查**完全不触碰**邮箱验证明细（零调用）；历史过滤 ON：只按有效最新 undeliverable 排除；实时验证 ON：按当前 `allowedStates` 解释可复用结论，无结论标 `AWAITING_VERIFICATION`。

**I-4（零业务副作用）**

- 预检查只读：`BatchRecipientReadRepository`（只 SELECT，只读事务）、`BatchRecipientSelectionService.select`、邮箱历史只读入口、模板评估；不创建 campaign/contact/task/Pending 验证/发送尝试/准入，不调用 `verify()`/SMTP/供应商。

**I-5（规模、隐私、失败显式）**

- 只读事实按 500 一批参数化 IN（空入参不发 SQL）；每原因样本 ≤20 并标 `truncated`（`count` 始终为完整命中数）；按 docId 诊断 1..200，非法数量由 `require` → `IllegalArgumentException` → 400。
- 响应含 `baseline`/`excludedUnique`/`eligibleBeforeLiveVerification`/`awaitingVerification`/`reasonHits`（含样本与 truncated）/`asOf`/`partial`/`verificationPhase`。

**I-6（模板按实际内容解释）**

- 门禁开启时复用 `IntroductionMailComposer.evaluateForBatch` 与 `effectiveRequiredKeys`，只把**专家变量**缺项算原因（账号变量缺项仍是启动前配置错误）；门禁关闭时个性化缺项不排除；模板不可读时由 composer 原样抛出（任务配置错误）。

**接口（03 直接消费）**

- `POST /api/mail/batch-send/recipient-diagnostics`，请求 = 现有 snapshot 形状 + `docIds`（1..200）；响应类型 `BatchRecipientDiagnosticsResponse`（定义在 `BatchRecipientPreflightService.kt`）。
- `PendingOutreachSummary` 新增字段 `baseline`/`excludedUnique`/`eligibleBeforeLiveVerification`/`awaitingVerification`/`verificationPhase`/`partial`/`asOf`/`reasonHitsDetail`；旧字段（`pending`/`retryable`/`totalSendable`/`excludedVerifiedUnavailable`/`admission`/`reasonHits` map/`excludedRecipients`）含义与类型逐字保留，新字段为兼容扩展，未计算时为 `null`（禁止补 0）。

### 3. 命令与结果（JDK11，fresh run）

命令 1（必需）：

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test \
  -Dtest='BatchRecipientPreflightServiceTest,BatchRecipientSelectionServiceTest,ManualInitialOutreachServiceTest,BatchEmailVerificationServiceTest,CountryContinentMappingTest,ExpertSearchServiceTest' \
  -DfailIfNoTests=false
```

- 结果：**BUILD SUCCESS**，`Tests run: 338, Failures: 0, Errors: 0, Skipped: 0`
  - `BatchRecipientPreflightServiceTest` 11
  - `ManualInitialOutreachServiceTest` 189
  - `BatchRecipientSelectionServiceTest` 14
  - `BatchEmailVerificationServiceTest` 40
  - `ExpertSearchServiceTest` 75
  - `CountryContinentMappingTest` 9
- 同一次运行中 `exec-maven-plugin` 的前端门禁：`node --test` 1657 pass / 0 fail，`node --check` 通过。

命令 2（必需，真实 MySQL IT）：

```bash
DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock \
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test \
  -Dtest='BatchRecipientReadRepositoryIT' -DmysqlIt=true -DargLine='-Dapi.version=1.44' -DfailIfNoTests=false
```

- 结果：**BUILD SUCCESS**，`Tests run: 4, Failures: 0, Errors: 0, Skipped: 0`（`BatchRecipientReadRepositoryIT`）
- 容器真实启动：`tc.mysql:8.0.36` started（JDBC `jdbc:mysql://localhost:32928/talent_introduction`），Flyway 在容器库上执行真实迁移（`Database: ... (MySQL 8.0)`）；**非跳过**。

补充回归（非必需，`BatchRecipientSelectionService` / `PendingOutreachSummary` 共享类影响面）：

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test \
  -Dtest='BatchSendTaskRuntimeIntegrationTest,MailOpenTrackingPersistenceTest,BatchSendControlServiceTest,BatchSendLiveExecutionViewTest,BatchSendTaskConfigServiceTest,BatchRecipientSelectionServiceTest' \
  -DfailIfNoTests=false
```

- 结果：**BUILD SUCCESS**，`Tests run: 207, Failures: 0, Errors: 0, Skipped: 0`

### 4. MAIN G-2 fixture 断言（`BatchRecipientPreflightServiceTest`）

10 名 discovered + MANUAL_APPROVED 目标，全部通过任务条件/模板/保护检查，除下表特例：

| 目标 | 唯一特例 | 断言 |
|---|---|---|
| D1、D2 | 地区（Japan vs Europe）与状态（null vs CONTACTED）同时不匹配 | 各贡献 REGION 1 + OPERATOR_STATUS 1 |
| D3 | 模板门禁开启且缺专家必填变量 | TEMPLATE_MISSING 1 |
| D4 | 有效 risky 缓存、当前仅允许 deliverable/unknown | EMAIL_VERIFICATION_REJECTED 1 |
| D5～D7 | 有效 deliverable 缓存 | CACHED_ALLOWED，计入通过 |
| D8～D10 | 无有效缓存 | AWAITING_VERIFICATION，计入通过且计入待验证 |

断言结果：`baseline=10`、`excludedUnique=4`、`eligibleBeforeLiveVerification=6`、`awaitingVerification=3`、原因命中合计 **6**（REGION 2 + OPERATOR_STATUS 2 + TEMPLATE_MISSING 1 + EMAIL_VERIFICATION_REJECTED 1，可重叠、不相加冒充 4 人排除），并断言 `baseline = excludedUnique + eligibleBeforeLiveVerification`、`awaitingVerification ⊆ eligibleBeforeLiveVerification`、D1/D4 准入仍为 `ADMITTED`。
另有用例覆盖：25 人同因 → `count=25`、样本 20、`truncated=true`；两开关/放行集合四态矩阵；保护原因（绑定/抑制/已发送/永久失败/EMAIL_INVALID）；模板门禁开关；去重与 `partial` 透传；空母集。

### 5. 残留风险与说明

1. **预估新增一次只读母集 ES 扫描**：`countBySnapshot` 现在对 INTRODUCTION 额外做一次「层级+标签」scroll（上限 20,000，超限 `partial=true`），以给出完整母集口径；这会让预估的 ES 读取量上升（只读、无写入）。既有预估字段（`pending`/`retryable`/`totalSendable` 等）计算方式未变。
2. **`BatchRecipientPreflightService` 在 `ManualInitialOutreachService` 中是可选依赖（默认 `null`）**：Spring 环境恒注入；仅历史手工构造该服务、未传该依赖的两个既有测试夹具（`BatchSendTaskRuntimeIntegrationTest`、`MailOpenTrackingPersistenceTest`，均不在本阶段授权文件清单内）走逐字相同的模板门禁回退分支，不产生第二套判定语义。`diagnoseRecipients` 在缺依赖时显式失败（`requireNotNull`）。
3. **保护原因不含「无联系人」**：INTRODUCTION 的 ES 目标本就无联系人行，故不把「无联系人」当作排除原因；保护原因只覆盖有持久事实的绑定/抑制/已发送/永久失败/EMAIL_INVALID。
4. **幂等/并发**：诊断与预估全程只读，无写入竞争面；实时验证 ON 时的相位解释基于「当前可复用历史」的只读快照，执行期真实 `verify()` 结论仍以执行时为准（I-2 允许预估后事实变化产生带原因的运行时跳过）。
5. **`snapshot` 参数语义**：`recipient-diagnostics` 复用与 `/recipients/preview` 相同的 `BatchExecutionSnapshot` 形状与认证，不新增权限面；`docIds` 为必填（1..200）。
