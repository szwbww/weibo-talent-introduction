# Child Brief — admission-relaxation（既有准入方案阶段1-2）

- Child ID: `admission-relaxation`；执行 epoch 1。
- Authoritative child plan: `docs/plans/2026-10-13/discovery-admission-relaxation.md`（此 worktree 内已提交于 b06a718；**先完整阅读它，它是唯一权威合同**）。
- Master plan（全局合同）: `docs/plans/2026-10-13/discovery-identity-recovery-master.md`（关键不变量 I-1..I-3、保持项、范围外、实现方案步骤1）。
- child_base_sha: `b06a71898bc7850d2caeb244c9f8478f18a3b2ae`
- Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-13-discovery-identity-recovery-master`
- Branch: `fast/2026-10-13-discovery-identity-recovery-master`
- 要求使用 `execute-p` 技能执行本子计划。

## 范围裁定（master 实现方案步骤1）
- 实施子计划 `## 实现方案` 的**阶段 1 与阶段 2 的全部代码步骤**（其变更文件清单恰为 10 个文件；阶段 3 为后续运营配置，不属代码范围）。
- **不执行**全量迁移 / 生产 apply / 任意 SMTP 或发送动作；不针对生产数据运行 refresh 应用；dryRun 默认语义严格按计划。
- master 明确本阶段"单独验证，不执行全量迁移"；测试一律 JDK 11。

## Authorized files（精确 10 个；其余文件一律不得修改；如必须改第 11 个文件，先停止并返回 PLAN_CONFLICT）

| # | 文件 | 操作 |
|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryAdmissionPolicy.kt` | 修改：I-1 六类缺资料原因移入 hints、硬原因保留、pending-tag-only 显式阻断、`POLICY_VERSION`=`2026-10-13.1` |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryReviewScanService.kt` | 修改：补齐分类输入字段（employment、keyword、worksCount、recentWorkTitles、patentTitles），不重构分页 |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryReviewService.kt` | 修改：recordAutomatic CAS/初始化竞争复读一次；扩展 InitializeRequest 增加 mode/dryRun；REFRESH_DISCOVERY 存量预览与补准入 |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/discovery/controller/DiscoveryReviewController.kt` | 修改：现有 /initialize 接口的 mode/dryRun 校验（400/409），actor 从会话取得 |
| 5 | `src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertRevalidationService.kt` | 修改：发现分支按当前人工结论与有效自动准入先行，软原因不再撤候选，硬原因与邮箱验证失败保留 |
| 6 | `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryAdmissionPolicyTest.kt` | 修改：软硬原因矩阵与版本 |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryReviewServiceTest.kt` | 修改：存量/CAS/投影/不发信 |
| 8 | `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryReviewAllPagesTest.kt` | 修改：完整字段与跨层读取 |
| 9 | `src/test/kotlin/com/weibo/talentintroduction/discovery/controller/DiscoveryReviewControllerTest.kt` | 修改：mode/dryRun/actor/幂等 |
| 10 | `src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertRevalidationServiceTest.kt` | 修改：补全后不重新收紧 |

## Required commands（全部必须真实运行并记录精确计数/退出码；JDK 11 前缀不可省略）
- C1: `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=DiscoveryAdmissionPolicyTest,DiscoveryReviewServiceTest,DiscoveryReviewAllPagesTest,DiscoveryReviewControllerTest,ExpertRevalidationServiceTest test`
- C2: `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=BatchRecipientSelectionServiceTest,ManualInitialOutreachServiceTest,InitialOutreachServiceTest test`
  - C2 三个测试文件为计划 §I-6 要求的"仅执行"回归，不得修改。
- 每次 `mvn test` 还会经 exec-maven-plugin 自动执行 JS 门禁（`node --test src/test/js/*.test.js` 与若干 `node --check`）；其失败数必须不高于基线（baseline.md 记录在案）。
- 不要用不存在的测试类名；先确认类名再执行。

## 必须兑现的合同要点（以子计划正文为准；这里是索引）
- I-1：仅六类（COUNTRY_MISSING、COUNTRY_UNMAPPED、INSTITUTION_MISSING、INSTITUTION_EVIDENCE_MISSING、RND_SCOPE_UNCONFIRMED、RND_EVIDENCE_INSUFFICIENT）从 blockingReasons 移入 hints；RESEARCH_DIRECTION_MISSING 保持提示；原始原因码与事实保留；提示不得标为数据已补齐；不把明确不合格混为未知。
- I-2：自动放宽前提=有效 VERIFIED 姓名—邮箱绑定；缺身份对象、凭证无效/版本不支持/姓名邮箱不一致、SOURCE_ID_CONFLICT、机构凭证 INVALID、RND_OUT_OF_SCOPE、RND_SERVICE_ONLY、非法/一次性邮箱仍阻断；仅"待确认"且 isDiscovery=false 的记录也须检查身份对象存在；不得伪造 discovered 标签、VERIFIED 对象或作者 ID；MANUAL_APPROVED/LEGACY_APPROVED 原授权保护，HOLD/REJECTED 维持；历史缺凭证只登记身份待核验。
- I-3：准入结论唯一来源 expert_discovery_admission；不改 CandidateEligibilityService/研发分类器事实口径；不把 UNKNOWN 改成 ACADEMIC_RND；不为通过改写旧 filterResult；重验先形成新 policy 结论再决定候选投影；不得因 I-1 旧 eligibility 原因删除已准入候选；邮箱独立验证失败仍阻断；人工与历史认可保护分支保持。
- I-4：扫描 RAW→CANDIDATE→APPLICATION，范围取 isDiscoveryOutreach 并集，按真实 _id 去重；跨层身份不一致报告冲突、不任取一层；不反造 RAW；只初始化无记录者、重算自动状态者；用 recordAutomatic 身份哈希与 revision CAS 保护人工/历史认可；旧政策 NEEDS_REVIEW 必须允许刷新（不得沿用 INSERT IGNORE 跳过全部旧自动记录）；CAS 冲突必须复读当前结论，不得用竞争前旧返回值投影候选。
- I-5：准入成功只补缺失候选（projectApprovedCandidate/projectDiscoveryCandidate）；不整行覆盖既有候选；APPLICATION 不反向复制；失败单列可重试；发信数恒为 0；不创建联系人/邮件。
- I-6：不得修改发送 selector 默认值或发送配置源码；UNINITIALIZED 不在发送 selector 临时放行；C2 测试仅执行。
- I-7：完整档案与扫描投影对同一人产出的 blockingReasons/hints 一致；POLICY_VERSION=2026-10-13.1；不新增平行 eligible 布尔；不新增 DB/ES 字段；mode/dryRun 仅为请求 DTO 与既有任务 JSON 选项，不改变持久准入枚举；任务 JSON ≤48KiB。
- 阶段 2：mode 默认 INITIALIZE_MISSING 保持既有契约，新增 REFRESH_DISCOVERY；新模式 dryRun 默认 true，仅显式 false 进入写分支；同一 requestKey 的 mode/dryRun/范围不同返回 409，非法 mode 返回 400；dryRun 对业务表/ES 零写入（允许既有 task_execution 执行日志）；apply 复读当前档案与准入；不制造人工批准、不重写既有候选、不清空失败计数冒充成功；成功数与失败数守恒。
- 回滚预案属计划文本要求（影响清单导出、条件恢复口径）；本轮不执行任何生产操作。

## 下游接口（后续子计划 01/02 依赖，保持稳定）
- `ExpertRevalidationService.revalidateDiscovery`：发现分支先判当前人工结论与有效自动准入；有效准入走共享候选投影；软原因不再撤销候选；硬否决与邮箱验证失败保留；非发现分支不变。
- `DiscoveryReviewService.recordAutomatic`：初始化竞争/CAS 失败时复读当前身份与决策并有限重试一次；仍冲突则显式报告且不投影（01 在补证成功后经此接缝重验）。
- `DiscoveryAdmissionPolicy.POLICY_VERSION` = `2026-10-13.1`；六类软原因仅出现在 hints。
- `InitializeRequest`：`mode`（默认 INITIALIZE_MISSING）与 `dryRun`（新模式下默认 true）；既有幂等键语义不变。

## 报告与提交
- 完整结果写入：`docs/plans/fast/2026-10-13-discovery-identity-recovery-master/children/admission-relaxation/execution.md`（由你撰写；包含逐命令退出码/计数、改动摘要、未决问题）。
- 本地提交仅含 10 个授权文件：`feat(fast-p): implement admission-relaxation`（不要包含 docs/plans/fast 下任何 fast-p 报告/日志）。
- 返回仅限：`READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT`、commit SHA、命令摘要、报告路径。
- 不审查后续子计划、不修复无关行为、不 push/merge/改写历史；遇到计划未覆盖的行为决策或需要新文件：返回 BLOCKED/PLAN_CONFLICT。

## Epoch 2 修订（A9，2026-10-13T23:06+0800 人工批准）

- 授权文件 10 → **11**，新增：`src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertRevalidationServiceBehaviorTest.kt`。
  - 仅把 2 例断言改为 I-1 新语义（软原因只进 hints；`reconcileDiscoveryCandidate` 以空 reasons 调用、filterResult PASSED、候选保留），其余 20 例不动，不改该文件其他行为。
- Required commands（epoch 2 起生效；C1 替换原列表，C2 不变）：
  - C1: `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=DiscoveryAdmissionPolicyTest,DiscoveryReviewServiceTest,DiscoveryReviewAllPagesTest,DiscoveryReviewControllerTest,ExpertRevalidationServiceTest,ExpertRevalidationServiceBehaviorTest test`
  - C2: `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=BatchRecipientSelectionServiceTest,ManualInitialOutreachServiceTest,InitialOutreachServiceTest test`
- 其余合同（I-1..I-7、边界、下游接口、epoch 1 的 10 文件实现）不变；epoch 1 实施与 PLAN_CONFLICT 暂停记录见 execution.md。
- 报告：把 epoch 2 结果**追加**到同一 execution.md 的 `## Epoch 2` 小节（不要改写 epoch 1 记录）。
