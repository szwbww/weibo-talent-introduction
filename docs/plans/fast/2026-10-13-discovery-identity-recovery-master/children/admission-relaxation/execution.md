# Execution — admission-relaxation（fast-p 子计划，epoch 1）

- Result: **PLAN_CONFLICT**（10 个授权文件的实现与两个 Required commands 全部绿；但需要**第 11 个文件**才能让仓库整体保持绿，见 §未决问题）
- Plan: `docs/plans/2026-10-13/discovery-admission-relaxation.md`
- Plan SHA-256: `8233781609b6b8488321bf0589e31a6536f26df96c3580f8b70862fe7e9c97aa`（执行前后各算一次，未变化）
- Execution ID: `…/docs/plans/2026-10-13/discovery-admission-relaxation.md@82337816…c97aa`
- Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-13-discovery-identity-recovery-master`
- Branch: `fast/2026-10-13-discovery-identity-recovery-master`
- Worktree git dir: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-2026-10-13-discovery-identity-recovery-master`
- Pre-execution code SHA: `0d958a94c9158042fcfa0435412fa7ea52952d5b`（= brief 指定的 controller prepare commit，起始时 `git status` 干净）
- Post-execution code SHA / Evidence HEAD: **`cf08a69529c74aa0521f1cadc34e61591b0a2427`**（`feat(fast-p): implement admission-relaxation`，恰好 10 个授权文件，`docs/plans/fast/**` 未提交；`git merge-base --is-ancestor HEAD <branch>` = OK）
  - 提交前 controller 已把 HEAD 从 `0d958a9` 推到 `36075ec`（`docs(plans): A1-A8 human update…`，纯 `docs/plans/2026-10-13/` 文件，未触碰本子计划的 10 个文件，也未改 `discovery-admission-relaxation.md`）；我的提交基于该 HEAD 叠加。
  - Plan identity 在提交前后各重算一次，SHA-256 均为 `82337816…c97aa`（未变）。
- JDK: `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home`（每条命令均带该前缀）

## Required commands（本次全新执行）

| # | 命令 | 结果 | 退出码 | 计数 |
|---|---|---|---|---|
| C1 | `mvn -Dtest=DiscoveryAdmissionPolicyTest,DiscoveryReviewServiceTest,DiscoveryReviewAllPagesTest,DiscoveryReviewControllerTest,ExpertRevalidationServiceTest test` | PASS | **0** | 127 tests / 0F / 0E / 0 skipped（逐类：Policy 22、ReviewService 35、AllPages 18、Controller 17、Revalidation 35） |
| C2 | `mvn -Dtest=BatchRecipientSelectionServiceTest,ManualInitialOutreachServiceTest,InitialOutreachServiceTest test` | PASS | **0** | 211 tests / 0F / 0E / 0 skipped（8 + 183 + 20，与基线逐类一致） |

两次 `mvn test` 随附的 JS 门禁（exec-maven-plugin `node --test src/test/js/*.test.js` + `node --check app.js` / `task-modal-runtime.js`）：

- C1：`tests 1657 / pass 1657 / fail 0 / skipped 0`
- C2：`tests 1657 / pass 1657 / fail 0 / skipped 0`

补充证据（非 Required，用于确认仓库整体不被本改动弄红；基线 16 类命令原文）：

| 命令 | 结果 | 退出码 | 计数 |
|---|---|---|---|
| `mvn -Dtest=<baseline 16 类> test`（baseline.md 原命令） | PASS | **0** | **791 tests / 0F / 0E**（基线 766 + 本次新增 25）；JS 1657/1657/0 |

## 变更摘要（严格 10 个授权文件）

| # | 文件 | 改动 |
|---|---|---|
| 1 | `discovery/service/DiscoveryAdmissionPolicy.kt` | **I-1**：`SOFT_REASON_CODES`（六类：COUNTRY_MISSING、COUNTRY_UNMAPPED、INSTITUTION_MISSING、INSTITUTION_EVIDENCE_MISSING、RND_SCOPE_UNCONFIRMED、RND_EVIDENCE_INSUFFICIENT）从 `blockingReasons` 移入 `hints`，原码/事实/`field/observed/expected` 逐字保留；`RESEARCH_DIRECTION_MISSING` 仍为独立提示。**I-2**：仅带 `待确认` 标签、无 `identityVerification` 的档案显式阻断（`IDENTITY_MISSING`），不再因 `allowed()` 对非发现档案恒真而放行；身份/SOURCE_ID_CONFLICT/SOURCE_ID_MISSING/INSTITUTION_EVIDENCE_INVALID/RND_OUT_OF_SCOPE/RND_SERVICE_ONLY/学历/年龄/国籍/指标/邮箱仍阻断。`POLICY_VERSION = "2026-10-13.1"` |
| 2 | `discovery/service/DiscoveryReviewScanService.kt` | **I-7**：`_source` 请求补齐 `employment`、`keyword`、`worksCount`、`recentWorkTitles`、`patentTitles`（分类器/policy 输入），分页逻辑未动 |
| 3 | `discovery/service/DiscoveryReviewService.kt` | **I-4**：`recordAutomatic` 初始化竞争后复读真实结论、CAS 未命中复读 + 最多重试一次、仍冲突则显式报告复读结果（身份变化 ⇒ `admitted=false`）且不投影旧批准；新增 `recordAutomaticConclusion`（worker 端已有 policy 结论时复用同一写入路径）。**阶段 2**：`InitializeRequest` 增加 `mode`/`dryRun`；`INITIALIZE_MISSING` 契约与 payload 完全不变；`REFRESH_DISCOVERY` 两遍遍历三层（先判 `isDiscoveryOutreach`、按真实 `_id` 去重、跨层身份冲突单列不写；apply 再复读当前档案/准入后写入缺记录初始化 + 自动结论刷新 + 人工/历史认可跳过 + 准入者走共享投影）；`dryRun` 默认 true 且业务表/ES 零写入；计数含准入/硬阻断/缺身份/人工保留/身份变化/冲突/重复层/记录/刷新/候选投影/失败与 ≤100 条样本，成功+失败=总人数；`initializeStatus` 暴露 mode/dryRun 与全部计数 |
| 4 | `discovery/controller/DiscoveryReviewController.kt` | 现有 `/initialize` 在进服务前校验 `mode`/`dryRun`（非法 mode、旧模式带 `dryRun` → 400）；同 `requestKey` 不同 payload → 409（服务层 payload 比较）；actor 仍只取会话 |
| 5 | `expert/service/ExpertRevalidationService.kt` | **I-1/I-3**：发现分支顺序改为「人工结论（含历史认可）→ 硬否决/独立邮箱验证 → 有效自动准入走共享投影 → 其余 reconcile+recordAutomatic」；六类软原因不再进入候选投影阻断原因（不再撤候选）；硬原因与 `EMAIL:` 失败保留原词汇；非发现分支不变 |
| 6 | `discovery/service/DiscoveryAdmissionPolicyTest.kt` | 软硬矩阵（六类单独/组合/与硬并存）、软原因只进 hints 且事实保留、`待确认` 仅标签缺身份阻断、`POLICY_VERSION`/`SOFT_REASON_CODES` 断言、分类器输入字段影响事实的回归锚 |
| 7 | `discovery/service/DiscoveryReviewServiceTest.kt` | `recordAutomatic` 初始化竞争复读、CAS 重试一次、CAS 持续失败时不再报告旧批准；`refresh` dryRun 零写入、跨层去重/冲突、apply 初始化+刷新+投影+守恒、apply 复读跳过、summary ≤48 KiB 且样本封顶 |
| 8 | `discovery/service/DiscoveryReviewAllPagesTest.kt` | `_source` 字段完整性断言、profile 带分类器输入、真实 scroll 下三层刷新预览的去重/冲突/每批一次 admission 读取/零业务写入 |
| 9 | `discovery/controller/DiscoveryReviewControllerTest.kt` | `/initialize` 的 mode/dryRun 透传与会话 actor、非法 mode 400、旧模式带 dryRun 400、同 requestKey 不同选项 409、状态读取 |
| 10 | `expert/service/ExpertRevalidationServiceTest.kt` | 有效自动准入走共享投影（不 reconcile 不重签）、软原因不撤候选、硬原因 `RND_OUT_OF_SCOPE` 与 `EMAIL:…` 仍阻断 |

不变量落实要点：

- **I-1**：六类原因只出现在 `hints`，原码与事实未改写，提示文案只陈述缺失事实。
- **I-2**：身份/来源冲突/凭证 INVALID/明确不符/无效邮箱仍阻断；未伪造 discovered 标签、VERIFIED 对象或作者 ID；人工/历史认可保护分支不变。
- **I-3**：准入结论唯一来源 `expert_discovery_admission`（未改 `CandidateEligibilityService`/分类器事实口径）；重验先形成新 policy 结论再决定候选投影；软原因不再删候选；邮箱独立验证仍阻断。
- **I-4**：三层扫描 + 真实 `_id` 去重 + 跨层身份冲突单列不写；CAS 冲突复读；旧 `NEEDS_REVIEW` 可刷新（不再 INSERT IGNORE 跳过全部旧自动记录）；人工 HOLD/REJECTED/MANUAL_APPROVED/LEGACY_APPROVED 不被覆写。
- **I-5**：准入成功后只补缺失候选（复用 `projectApprovedCandidate`），不整行覆盖候选、APPLICATION 不反向复制；dryRun 业务写 0；全流程无发信、无联系人/邮件写入（测试断言 0）。
- **I-6**：未改发送 selector/发送配置源码；C2 三个测试文件仅执行未修改。
- **I-7**：扫描与完整档案共用同一份事实字段；`POLICY_VERSION=2026-10-13.1` 走既有写入点落库；未新增 DB/ES 字段、无迁移；`mode`/`dryRun` 仅请求 DTO 与既有任务 JSON 选项，任务 JSON 样本 ≤100 条（测试断言 ≤48 KiB）。

## 未决问题

### 1. PLAN_CONFLICT — 需要第 11 个文件（测试断言与本计划的 I-1 直接冲突）

`src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertRevalidationServiceBehaviorTest.kt`（**不在**授权清单、**不在** C1/C2，也不在 baseline.md 的 16 类中）把「`RND_SCOPE_UNCONFIRMED` 必须作为候选投影的阻断原因」写死为期望：

- `discovery revalidation admits only after current RAW has target research evidence`：期望 `Rejected(listOf("RND_SCOPE_UNCONFIRMED"))` 且 `reconcileDiscoveryCandidate(..., match(listOf("RND_SCOPE_UNCONFIRMED")), ...)`；
- `application discovery still updates RAW qualification without touching candidate`：stub `reconcileDiscoveryCandidate(..., match(listOf("RND_SCOPE_UNCONFIRMED")), match(true))`。

实测（`mvn -Dtest=ExpertRevalidationServiceBehaviorTest test`，**退出码 1**，22 tests / **2 failures** / 0E）：

- `…target research evidence:451 expected: <Rejected(reasons=[RND_SCOPE_UNCONFIRMED])> but was: <Promoted>` —— 正是 I-1 要求的新语义；
- `…without touching candidate:486 expected: <AlreadyPresent> but was: <WriteFailed>` —— stub 未命中导致 mock 返回 false（同一根因：reasons 已不含软原因）。

按 brief「如必须改第 11 个文件，先停止并返回 PLAN_CONFLICT」，本轮**未修改**该文件。所需授权/修订（最小）：把该测试类加入受影响测试清单并更新上述 2 处为新语义（`RND_SCOPE_UNCONFIRMED` 只出现在 hints；`reconcileDiscoveryCandidate` 的 reasons 为空、`filterResult=PASSED`、候选保留）。除此之外该类 20 个用例保持通过。

### 2. 明确记录的执行决策（计划未逐字规定，均为 fail-closed）

1. `dryRun` 与旧模式 `INITIALIZE_MISSING` 组合 → **400**（不静默忽略「不写入」开关）；`dryRun` 缺省在 `REFRESH_DISCOVERY` 下为 `true`。
2. `DiscoveryReviewAdmissionOutcome.decision` 在既有 DTO 中是非空 `String`，故 CAS 冲突回显返回**复读到的行现值**并置 `admitted=false`/`identityChanged=true`（不能返回 null）；竞争前的批准绝不被当作当前结论投影。
3. `REFRESH_DISCOVERY` 的 apply 会遍历三层**两遍**（第一遍只读判定并在写任何东西之前发现跨层身份冲突；第二遍复读后写入）。dryRun 只遍历一遍。理由是「禁止任取一层覆盖」只有在读完全部层后才能保证。
4. 阶段 2 第 6 条「回滚预案」是运维/文本口径（影响清单导出、按新版本与未被人工改动的 CAS 条件恢复），本轮不涉及代码，也未执行任何生产动作。
5. 新增 DTO 字段一律**追加 + 带默认值**（`DiscoveryReviewInitializeRequest`/`…Status`/`…Outcome`），保持既有构造与读取兼容；新增 `DiscoveryAdmissionPolicy.SOFT_REASON_CODES` 与 `DiscoveryReviewService` 的 `MODE_*`/`normalizeMode`/`normalizeDryRun`/`recordAutomaticConclusion` 为追加式接口。
6. 未执行全量迁移、生产 apply、SMTP/发送、refresh 对生产数据运行；`docs/plans/fast/**`（含本报告）未提交。

### 3. 工作树内的非我方改动

`docs/plans/fast/2026-10-13-discovery-identity-recovery-master/ledger.md` 在执行期间被 controller 改为 `IMPLEMENTING`（非本子计划改动），已保持原样、**未提交**。
