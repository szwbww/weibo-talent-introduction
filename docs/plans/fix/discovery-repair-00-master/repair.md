# Repair Plan: discovery-repair-00-master

Status: DRAFT — HUMAN APPROVAL REQUIRED
Baseline plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-26-discovery-repair-00-master/docs/plans/2026-09-26/discovery-repair-00-master.md`
Baseline identity: commit `152028fb4f6adf467a5254ed3627bf84c561f6bc`；SHA256 `0a40f221660ecc0008394126ae029e869c6078e725f2585cd2d55ba938be67fd`，A1/A2已批准。
Verification report: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-26-discovery-repair-00-master/docs/plans/review/2026-09-26-discovery-repair-00-master/machine-verification.md`，Epoch 1，FAIL / INITIAL。
Implementation boundary: `64c0394a940bd79c2ecc04e5c497650f045faa75..4ad9e79b034798ee78f12c3285faf5882991b3bc`
Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-26-discovery-repair-00-master`
Branch: `fast/2026-09-26-discovery-repair-00-master`
Current evidence HEAD at review: `075425401b1f5d401a7ec2bf8549a2d53033c703`；执行时允许其后只含本评审/修复草案的docs提交，须确认产品基线不变。

## Objective

恢复 discovery 自动复评的既有完整邮箱验证；让合法历史画像使用真实 ES `_id` 完成资格写回、晋升或条件撤候选，保持学术身份和并发保护。

## Findings in Scope

| Finding | Severity | Requirement | Root Cause | Disposition |
|---|---|---|---|---|
| V-1 | P1 | master M2；09d保持项/I-1 | 新discovery分支跳过EmailValidationService.validate，仅格式/一次性域名检查 | REPAIRABLE |
| V-2 | P1 | 09d真实_id保持项/I-2/I-3/I-4；master M2/X5 | 两处将源orcidId强等于真实docId，额外约束合法历史键 | REPAIRABLE |

两项共享复评路径，维持一个修复计划，不拆轮次。不存在prior aggregate repair lineage；INITIAL允许规划，不授权实施。

## Findings Excluded

| Finding | Reason |
|---|---|
| O-1旧ORCID ACTIVE stream | 批准09a明确保留；历史流策略不属本修复。 |
| O-2版本onlyPending发布影响 | 发布前只读清点事项，不修改调度/执行回填。 |
| O-3普通资格update noop | 未认定必修缺陷；禁止顺带改写。 |
| DD-08/DD-10 | 原文存储与上游身份一致性明确延期。 |
| fast-p格式/READY门槛 | 用户仅本轮豁免流程前置；不改全局规则/历史台账。 |

## Unchanged Contract

- 六目标专业、clinical优先、未知留RAW、现算分类、promotionGateEnabled不能绕专业规则保持。
- 姓名/邮箱/学术作者ID须同源且唯一；不改证据VERSION/EXTRACTION_VERSION，不猜作者ID，不改历史orcidId或ES主键。
- 复评先于补全Success；失败走既有重试；RAW/候选snapshot及seq_no/primary_term条件、候选仅DELETE、404幂等、409/500可重试保持。
- 不覆盖已有候选运营字段；不删RAW/APPLICATION，不改MySQL联系人/邮件或发送可见配置；不新增隐藏发送门禁。
- 非discovery与人工override原行为保持；解析/缓存/ORCID编码/前端不改；不改批准主/子计划。
- 禁止生产访问/数据清洗/部署/新增自动化/推送/合并。

## Authorized Files

全部相对所列保留工作树，穷尽清单为4文件，单一专家资格/写入子系统及测试：

| File | Purpose |
|---|---|
| `src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertRevalidationService.kt` | V-1完整邮箱验证与原因；V-2移除错误业务键等同要求 |
| `src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertIndexWriterService.kt` | V-2按真实_id维护并发/身份核验，不要求业务orcidId==docId |
| `src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertRevalidationServiceBehaviorTest.kt` | 两反例及直接/自动入口真实复评回归 |
| `src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertIndexWriterServiceTest.kt` | 真实_id不同且身份稳定的create/delete/CAS请求回归 |

禁止修改清单外产品/测试。若必要机械守卫变更超出这4文件，先提交精确范围变更供人批准，不静默扩项。

## Repair Tasks

### R-1: 恢复 discovery 完整邮箱验证

- Resolves: V-1。
- Root cause: ExpertRevalidationService339–347 bypasses validate，而evaluateEligibility27–31仅格式/一次性域名。
- Files: ExpertRevalidationService.kt；ExpertRevalidationServiceBehaviorTest.kt（上表完整路径）。
- Change: requireValidEmail启用时，discovery复评遵循既有完整EmailValidationService.validate结果；失败作为明确邮箱拒绝原因写RAW并阻止新候选，已有候选按既有条件删除语义处理。验证服务异常不能报允许/Success，沿既有失败重试语义；开关关闭不强行增加原本关闭的网络验证。专业资格仍现算、与完整邮箱验证共同决定结果。
- Regression test: 使用真实CandidateEligibilityService及真实ExpertRevalidationService，结构化工程ID+足够科研分+合法身份/格式，validate返回NO_MX_RECORD；断言validate确实调用、候选不create且不能Promoted/AlreadyPresent掩盖拒绝，RAW资格原因反映邮箱失败。覆盖RAW晋升/候选复评的公共discovery入口至少一条真实调用，不mock掉revalidateDiscovery；成功邮箱允许原专业通过路径，验证异常不成功。
- Existing verification: 下述定向命令及全量clean package。
- Must not change: 不修改EmailValidationService实现、过滤配置、分类分数/专业目录和发送入口；邮箱验证关闭保持原语义。
- Prohibited: 不为修测试stub整体资格pass，也不删原非discovery邮箱校验回归。

### R-2: 真实 ES 文档定位与历史业务键解耦

- Resolves: V-2。
- Root cause: ExpertRevalidationService334与Writer751错误要求source.orcidId==docId。
- Files: 上表4文件。
- Change: 从真实_id取得的RAW快照按同一真实_id执行资格局部更新/候选create或条件删除；身份一致性检查使用批准的学术身份/证据及RAW/CANDIDATE稳定快照。源orcidId保留原值，允许与ES_id不同；仍要求RAW完整可读、稳定身份、候选同一真实_id/身份、seq/term条件。不得把取消/身份改变/CAS失败变成成功。
- Regression test: 使用已有OLD-DOC/历史ORCID不同的合法discovery画像思想，实际revalidation+writer方法经隔离HTTP替身：专业/邮箱通过可按OLD-DOC create，拒绝可按OLD-DOC CAS删除且RAW保留；保存源orcidId、姓名、邮箱、proof、运营字段不变。再令身份变化或CAS409，断言不误删/不报成功。该回归不得把revalidateDiscovery mock成固定成功来证明闭环。
- Existing verification: ExpertRevalidationServiceBehaviorTest/ExpertIndexWriterServiceTest；全量中ExpertDiscoveryServiceTest、enrichment job/worker测试确认返回值与终态映射不回退。
- Must not change: 保留_snapshot比较、_seq_no/_primary_term、缺RAW拒绝、APPLICATION保留、候选create语义。
- Prohibited: 不改历史键、不放宽DiscoveryIdentity.allowed、不用delete-by-query、不整文档覆盖已有候选。

## Verification Commands

全部在精确保留工作树执行，JDK11；不访问生产。

1. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=ExpertRevalidationServiceBehaviorTest,ExpertIndexWriterServiceTest,ExpertRevalidationServiceTest,CandidateEligibilityServiceTest,CandidateEligibilityServiceEnhancedTest,ExpertDiscoveryServiceTest,ExpertAcademicEnrichmentJobServiceTest,ExpertAcademicEnrichmentWorkerTest,OperatorStatusWriteSeamGuardTest,ExpertClassificationVersionGateGuardTest`
2. `git diff --check`，核对实际产品/测试差异仅上表4文件。
3. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package`

完成记录必须写实际测试数/失败/跳过、命令退出码与代码SHA，不能沿用评审的4141/1194。全量已有Maven-bound Node测试/语法门禁，无需重复等价测试制造证据。

## Completion Criteria

- V-1：完整邮箱验证拒绝时，专业合格discovery不晋升/不保留错误候选；成功/异常/关闭语义符合原配置。
- V-2：合法身份且真实_id不同于源orcidId可完成真实_id复评；稳定身份不改键，冲突/错误仍可重试且不误删。
- 原文身份、未知先RAW、三层事实、缓存、ORCID来源key、发送配置不变。
- 所有required命令通过；范围不超4文件；没有生产操作；独立aggregate复验后才能机器PASS。
- 人工A1–A6维持PENDING，修复实现或机器PASS不能替代人工ACCEPT。

## Human Approval

Execution is prohibited until the human explicitly approves this plan.
After approval, run execute-p with this exact file:
`/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-26-discovery-repair-00-master/docs/plans/fix/discovery-repair-00-master/repair.md`。

## Review-Fast-P Execution Handoff

An explicit human-originated `$execute-p /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-26-discovery-repair-00-master/docs/plans/fix/discovery-repair-00-master/repair.md` invocation authorizes:

1. Only the Authorized Files and required verification commands in this plan.
2. After all repair tasks and required commands pass, exactly one local product commit before emitting `READY_FOR_VERIFICATION`, staging only the four Authorized Files. Product commit subject: `fix(discovery): preserve email validation and historical document IDs`.
3. Appending `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-26-discovery-repair-00-master/docs/plans/review/2026-09-26-discovery-repair-00-master/repair-execution.md` with the exact approval source, repair identity/hash, pre/post code SHAs, changed files, commands, deviations, executor identity when exposed, and clean-state evidence. This evidence path is authorized only as this handoff, not as product scope.
4. Exactly one docs-only evidence commit containing only that execution handoff. Evidence commit subject: `docs(review-fast-p): record discovery admission repair execution`.
5. Returning to the already authorized review-fast-p aggregate re-review in the same task when the user's invocation requests it, against `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-26-discovery-repair-00-master/docs/plans/2026-09-26/discovery-repair-00-master.md` with machine evidence at `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-26-discovery-repair-00-master/docs/plans/review/2026-09-26-discovery-repair-00-master/machine-verification.md`.

This authorizes no extra files, amend, history rewrite, push, merge, deployment, or product repair beyond this plan.
