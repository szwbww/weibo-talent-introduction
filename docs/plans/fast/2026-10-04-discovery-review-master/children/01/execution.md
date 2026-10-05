# Execution Report — child 01（基础校验解释器）

## Execution Result: READY_FOR_VERIFICATION

Plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-04-discovery-review-master/docs/plans/2026-10-04/discovery-review-01-admission.md`
Plan SHA-256: `07e1d27c024336944b8c55e28315daca274cd172b0c3b0e948a05d610ca0f954`
Execution ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-04-discovery-review-master/docs/plans/2026-10-04/discovery-review-01-admission.md@07e1d27c024336944b8c55e28315daca274cd172b0c3b0e948a05d610ca0f954`
Execution epoch: NEW
Approval basis: fast-p child 01 brief（`docs/plans/fast/2026-10-04-discovery-review-master/children/01/brief.md`）+ 批准计划原文
Executor: ImplDiscoveryReview01
Target worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-04-discovery-review-master`
Target branch: `fast/2026-10-04-discovery-review-master`
Worktree ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-04-discovery-review-master@fast/2026-10-04-discovery-review-master@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-2026-10-04-discovery-review-master`
Pre-execution code SHA: `07beaafc111a1b14ed3c48d514db527c8a13fc31`（child base = master base `e28e53fd898edd62905a0d45a6bf90396b18b1fc` + plans seed）；pre-execution HEAD = `e018d930a0eec0ef1ee992fa5a070d7819ff94e2`（docs seed）
Post-execution code SHA: `209315103a89c5ea7807e4707bb87967d8579525`
Evidence HEAD: N/A（本 child 不要求单独证据提交；本报告为 untracked fast-p 报告，未进入产品提交）
Implementation boundary: `07beaaf..2093151`（5 files changed, 882 insertions(+), 0 deletions(-)）

## Task Status

| Requirement | Status | Files | Evidence |
|---|---|---|---|
| 实现方案 1（I-1/I-3 模型） | IMPLEMENTED | `discovery/domain/DiscoveryAdmissionModels.kt`（新增） | `AdmissionReason`（code/label/field/observed/expected/sourceLocation，未知用 `null` + `UNKNOWN`）、`AutomaticAdmissionResult`（status/blockingReasons/hints/policyVersion/checkedAt/configSnapshot）、`AdmissionConfigSnapshot`、`DiscoveryAdmissionStatus`、`AdmissionReasonCodes`；无 `REVIEW_REQUIRED` 布尔，`eligible` 由 status 派生 |
| 实现方案 2（I-1/I-2/I-3 policy + 解释函数） | IMPLEMENTED | `discovery/service/DiscoveryAdmissionPolicy.kt`（新增）、`expert/domain/DiscoveryIdentity.kt`（仅新增解释函数） | `evaluate(profile)`（实时重算候选资格）与 `evaluate(profile, eligibility)`；`DiscoveryIdentity.explainIdentity/explainInstitutionEvidence`；`allowed`/`validInstitutionEvidence`/签发算法逐字未改 |
| 实现方案 3（I-3 原因词表） | IMPLEMENTED | models + identity + policy | 12 个 01 自有码（计划 11 码 + `IDENTITY_STATUS_UNVERIFIED`）+ 资料提示码 `RESEARCH_DIRECTION_MISSING` + 现有 13 个 candidate 原码（`AdmissionReasonCodes.CANDIDATE_RULE_CODES`）；未知码原样透传（测试 `unknown candidate rule code is preserved verbatim`） |
| 实现方案 4（I-1/I-2 历史认可） | IMPLEMENTED | policy | 有效 `LEGACY_APPROVED` 单独返回、`blockingReasons` 为空、机构/国家/凭证/资格事实进 `hints`；`DiscoveryIdentity.allowed` 仍 false；改邮箱后旧认可不生效（两条测试） |
| 实现方案 5（测试与回归） | IMPLEMENTED | 两个测试文件 | 合成样本覆盖全过/缺机构/缺国家/不可映射国家/无 token/token 失效/缺来源 ID/来源 ID 冲突/legacy/非发现；`LegacyDiscoveryApprovalTest` 全量回归未改动 |
| I-1 自动判定只搬现有规则 | IMPLEMENTED | policy | `toggling one candidate rule changes only that rule's reason`（逐个开关只改对应原因）、`no new thresholds - all existing filters off still auto passes a sparse profile`、`blank and unmapped country produce their own codes`、`research direction missing is only a hint and never blocks`；`AdmissionConfigSnapshot` 只回显 `CandidateFilterProperties`/`AcademicFilterProperties` 现有字段 |
| I-2 人工认可不升级为学术证明 | IMPLEMENTED | policy + identity | `valid legacy approval is admitted alone with facts shown as hints`、`legacy receipt is never upgraded to a source verified identity`；policy 无签发/写入代码（构造依赖仅 `CandidateEligibilityService`/`EligibilityFilterService`/`Clock`，无 repository/ES） |
| I-3 解释与布尔判定同源 | IMPLEMENTED | identity + policy + tests | 真值表 `explainIdentity(p).isEmpty() == allowed(p)`、`explainInstitutionEvidence(p).isEmpty() == validInstitutionEvidence(p)`；`status is derived from the blocking reasons across the sample matrix` |
| 人工验收 A-1/A-2 前置数据 | IMPLEMENTED（代码面） | 测试夹具 | 5 份结构化合成样本（全过/缺机构/缺国家/有机构无 token/token 不匹配）与 1 份有效历史回执 + 改邮箱副本均可在测试内生成；未使用真实专家姓名或属性，未改线上数据 |
| 必需命令 1、2 | IMPLEMENTED | — | 见下方 Commands（exit 0） |

## Commands

| Command | Result | Evidence |
|---|---|---|
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -DskipTests test-compile`（worktree 根，final 实现态） | PASS | exit 0；`[INFO] BUILD SUCCESS`，`Total time: 01:58 min`；日志 `/tmp/fastp-dr-01-testcompile.log`；产物 `target/classes/.../DiscoveryAdmissionPolicy.class`、`.../domain/DiscoveryAdmissionModels`（4 个类）、`target/test-classes/.../DiscoveryAdmissionPolicyTest.class` 均在 |
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=DiscoveryAdmissionPolicyTest,DiscoveryIdentityTest,LegacyDiscoveryApprovalTest test`（worktree 根，final 实现态） | PASS | exit 0；`Tests run: 35, Failures: 0, Errors: 0, Skipped: 0`（`DiscoveryAdmissionPolicyTest` 17 / `DiscoveryIdentityTest` 13 / `LegacyDiscoveryApprovalTest` 5）；`BUILD SUCCESS`；同一 test 阶段 `exec-maven-plugin` 的 `node --test`：`tests 1434 / pass 1434 / fail 0`；日志 `/tmp/fastp-dr-01-unit.log` |
| `python3 .../execute-p/scripts/plan_identity.py docs/plans/2026-10-04/discovery-review-01-admission.md` | PASS | exit 0；sha256 = `07e1d27c...0f954`（执行后复核，未变化） |
| `python3 .../execute-p/scripts/worktree_identity.py <plan> --expect-root ... --expect-branch ... --expect-git-dir ...` | PASS | exit 0；worktree_id/HEAD 与 preflight 一致（`/tmp/fastp-dr-01-worktree.json`） |

命令顺序说明：先 `test-compile`，后定向测试；两条命令都在最终实现态（最后一次测试断言修正之后）**重新完整执行**。修正前的一次失败运行（`DiscoveryIdentityTest.legacy receipt...` 断言过严）不作为证据，仅用于定位。

## Changed Files

- `src/main/kotlin/com/weibo/talentintroduction/discovery/domain/DiscoveryAdmissionModels.kt` — 新增：准入原因、配置快照、准入结果、状态、原因词表。
- `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryAdmissionPolicy.kt` — 新增：只读准入解释器（`evaluate` 两个重载），逐条搬用现有身份/机构/国家/凭证/候选规则。
- `src/main/kotlin/com/weibo/talentintroduction/expert/domain/DiscoveryIdentity.kt` — 仅新增 `explainIdentity`/`explainInstitutionEvidence` 与两个私有辅助；`allowed`/`validInstitutionEvidence`/`institutionEvidence`/`legacyOutreachApproved` 及全部签发算法逐字未改（diff 为纯新增 133 行）。
- `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryAdmissionPolicyTest.kt` — 新增 17 例，全部结构化合成样本。
- `src/test/kotlin/com/weibo/talentintroduction/expert/domain/DiscoveryIdentityTest.kt` — 新增 6 例（真值表 + 各原因码 + 零/多机构不可归因）；既有 7 例未改动。

`git show --stat HEAD` 回执：5 files changed, 882 insertions(+), 0 deletions(-)；提交 `209315103a89c5ea7807e4707bb87967d8579525` 主题 `feat(fast-p): implement 01`，是 `fast/2026-10-04-discovery-review-master` 的 HEAD。提交内容不含 `docs/plans/**`。

## Deviations

1. **相对路径落点事故（已完全还原，不影响交付内容）**：实施初期 `write/edit` 使用相对路径，被解析到主 worktree `/Users/lukai/IdeaProjects/weibo-talent-introduction`（main）。已按字节还原主 worktree 的 2 个被改文件（改前与改后 diff 均为 0 删除，故还原即原状；`git status` 复核主 worktree 无任何 `src/**` 改动），删除误建的 3 个新文件，并把 5 个授权文件改放到目标 worktree（md5 与备份一致）。此后所有读写/命令/提交均在目标 worktree 绝对路径下执行。此期间以主 worktree 内容编译的两次 `mvn` 结果未作为证据。
2. **范围谓词**：解释器判定范围取 `RecipientScope.isDiscoveryOutreach`（`isDiscovery` ∪ `待确认` 标签），与现有首发门禁同定义；非新发现档案返回 `AUTO_PASSED` 且不产生原因（保留原入口）。理由是计划 I-1 所列规则针对「发现专家」，而仓库对「新发现/待确认」的唯一定义就是该函数（`RecipientScope` 未在本 child 授权清单内，故未改动它）。
3. **新增解释码**：计划词表为「至少包括」，因此除 11 个计划码外新增 `IDENTITY_STATUS_UNVERIFIED`（凭证存在但非 VERIFIED，避免把它误报为 `IDENTITY_MISSING`）与资料提示码 `RESEARCH_DIRECTION_MISSING`；其余计划码逐字保留。
4. **解释函数的范围无关性**：`explainInstitutionEvidence` 不按发现范围裁剪，保持「空 ⟺ 凭证验签通过」的严格等价；是否需要凭证由 policy 决定。凭证缺失时只列「当前档案没有该键」与可核实的来源 ID 冲突，`SOURCE_ID_MISSING` 仅在凭证来源种类已知时列出（不猜测当时来源），符合「不声称还原历史故障」。
5. **LEGACY_APPROVED 的 hints**：除机构/国家/凭证外，同时把候选规则失败与身份非来源验证作为事实提示展示（不阻断），以便运营看到真实缺项。
6. **未做**：未新增/修改任何阈值或配置项；未改来源识别/签发/`allowed` 语义；未写任何存储（无 repository、无 ES、无迁移、无 `pom.xml` 改动）；未新增依赖；`LegacyDiscoveryApprovalTest`、`CandidateEligibilityService`、`RecipientScope` 等未改动。

## Freshness

- Plan identity rechecked: YES（`07e1d27c024336944b8c55e28315daca274cd172b0c3b0e948a05d610ca0f954`，执行前后一致）
- Worktree identity rechecked: YES（提交前后 `--expect-root/--expect-branch/--expect-git-dir` 均通过）
- Reported commits reachable from target branch: YES（`2093151` = 分支 HEAD；父提交 `e018d93`）
- Required commands run this invocation: YES（最终实现态，exit 0/0）
- Historical evidence used only as baseline: YES（`baseline.md` 仅作对照，未复用其结论；两处 `-Dtest` 类名之外的历史结果未使用）

## Remaining Blocker

- None

## Next Action

- READY_FOR_VERIFICATION → run `verify-p`
