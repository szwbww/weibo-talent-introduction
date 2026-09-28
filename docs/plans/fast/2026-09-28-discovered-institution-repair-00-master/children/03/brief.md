# Fast-P Child Brief — 03（无机构证据的新发现首发拦截）

## 身份与边界

- Master plan（批准版，字节冻结，只读）：`docs/plans/2026-09-28/discovered-institution-repair-00-master.md`，identity `commit:2e9639df7947bc5f3057ca08e1445b155aba7cd7`。
- 本 child 批准计划（**完整合同，必须先通读**）：`docs/plans/2026-09-28/discovered-institution-repair-03-outreach.md`，identity `commit:2e9639df7947bc5f3057ca08e1445b155aba7cd7`。
- Worktree：`/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-28-discovered-institution-repair-00-master`；Branch：`fast/2026-09-28-discovered-institution-repair-00-master`。
- `child_base_sha`：`c30c954761b199467c7d904a50b177808f54ddce`（child 02 的 Code head）。
- 依赖：02。上游产物（不得回退）：`institutionEvidence` 格式与统一验签函数、`ExpertProfile.institutionEvidence/filterResult` 投影。
- 下游：无（本 child 是最后一个）。

## 全局约束

1. JDK 11 固定：所有 Maven 命令必须 `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home`。
2. 只允许修改下面「Authorized Files」表内 10 个文件；不得新建白名单外文件；不得修改 `docs/plans/**`。
3. 不得修改主工作区；不得 push、merge、rebase、squash、amend、reset。
4. 产品代码提交格式：`feat(fast-p): implement 03`，单个提交；把 `docs/plans/fast/**` 报告/日志排除在该提交之外。
5. 计划与代码冲突、需要白名单外文件、需要新行为、需要修订计划：返回 `PLAN_CONFLICT` / `BLOCKED`。
6. 只读参考（不得复制、不得提交）：主工作区下 `docs/audits/2026-09-28-discovered-institution-comprehensive/README.md`。

## 必须保持不变（计划「需求描述」「关键不变量」「验收标准」）

- 不得改变非新发现专家的类型白名单及发送语义、材料催办、邮件模板正文。范围外：历史机构重提取和任务配置 UI。
- I-1 新发现首发判定：`DiscoveryIdentity.isDiscovery(profile)` 或档案标签含 `待确认` 时，发送前必须 `DiscoveryIdentity.allowed(profile)`、`institution` 非空、`country` 可由 `CountryContinentMapping` 映射、`institutionEvidence` 经 02 的统一验签通过（含来源 ID 一致性）、`filterResult == PASSED`；否则不建联系人、不占发件名额、不发邮件。`待确认` 缺身份对象也因无机构证据被阻断。其他非发现档案不加这组门禁；不改全局 `DiscoveryIdentity.isDiscovery` 既有语义。
- I-2 地区：新发现/待确认按已证实机构所在地 `country` 判地区；空国家不属于 Other；未映射/无法证实的国家即使任务未指定地区也不能进入首发；不得用 `nationality` 或论文国家推断本人国籍。ES 预筛和内存重试最终判定相同。
- I-3 预估—执行共用最终筛选：预估与实际发送必须对 ES 候选页及 NEW 重试联系人应用同一个 I-1/I-2 最终谓词；ES 查询可粗筛，但不能把粗筛数量当可发送数量；旧首发即使先取到无证据者也要继续分页找足指定数量；页 offset 针对粗筛持续推进，过滤一页不能当数据耗尽；实际发送前再检查。
- `CandidateEligibilityService` 对新发现只读明确 `nationality`（旧非发现 `nationality ?: country` 原样保留）。
- `OperatorStatusWriteSeamGuardTest` 因 `ExpertSearchService.kt` 行号偏移而红时，只允许按语义更新排除清单中的行号，不得放宽断言。
- 计划要求 TDD：先写失败断言（记录改前红证据），再实现到通过；不得改旧期望变绿。

## Authorized Files（12，含 A2 追加的第 11–12 行）

| # | 文件 | 变更 |
|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/campaign/domain/BatchExecutionModels.kt` | `RecipientScope` 唯一最终谓词、重试地区 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt` | 预估/ES/重试/发前门禁 |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/campaign/service/InitialOutreachService.kt` | 旧首发分页与发前门禁 |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchService.kt` | 旧查询可分页、必要的 ES 粗筛 |
| 5 | `src/main/kotlin/com/weibo/talentintroduction/expert/service/CandidateEligibilityService.kt` | 新发现不以所在地代国籍 |
| 6 | `src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt` | 预估/新目标/重试/Other |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/campaign/service/InitialOutreachServiceTest.kt` | 旧首发继续分页、不建错联系人 |
| 8 | `src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchServiceTest.kt` | 分页及 ES 地区粗筛 |
| 9 | `src/test/kotlin/com/weibo/talentintroduction/expert/service/CandidateEligibilityServiceTest.kt` | 国籍语义回归 |
| 10 | `src/test/kotlin/com/weibo/talentintroduction/campaign/OperatorStatusWriteSeamGuardTest.kt` | 若搜索文件移行，仅改排除清单行号 |
| 11 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/MailOpenTrackingPersistenceTest.kt` | A2 修正（human 2026-09-28T18:40:47+08:00 批准，计划 identity 随之变为 `commit:c01c86cdce3cb5747457d364241f83c535c3961d`）：仅补 `scrollExpertsFiltered` stub，断言不变 |
| 12 | `src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskRuntimeIntegrationTest.kt` | A2 修正：反射目标改为新签名 `countEsTargets(RecipientScope, LocalDateTime, () -> Boolean)`，断言不变 |

## 已知基线事实

- 基线命令与逐类计数见 `children/01/baseline.md`（控制方在 seed 提交上 fresh 运行）。测试若在基线上已红，按基线对比归类。

## 必需命令（fresh 运行，逐条记录 exit code 与计数）

```sh
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=ManualInitialOutreachServiceTest,InitialOutreachServiceTest,ExpertSearchServiceTest,CandidateEligibilityServiceTest,OperatorStatusWriteSeamGuardTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test
git diff --check
```

## 交付

- 执行报告写入 `children/03/execution.md`（报告本身不进实现提交）：逐子任务的文件:行证据、改前红证据、命令与 exit code/计数、与基线对比、偏差。
- 只返回：`READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT`、commit SHA、命令摘要、报告路径。
- 不得修复白名单外问题、不得重构相邻代码。
