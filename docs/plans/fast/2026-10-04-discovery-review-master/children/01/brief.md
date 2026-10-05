# Fast-P Child Brief — 01（基础校验解释器）

## 身份与边界

- Master plan（批准版，字节冻结）：`docs/plans/2026-10-04/discovery-review-master.md`，identity `commit:07beaafc111a1b14ed3c48d514db527c8a13fc31`。
- 本 child 批准计划（完整合同，必须先通读）：`docs/plans/2026-10-04/discovery-review-01-admission.md`，identity `commit:07beaafc111a1b14ed3c48d514db527c8a13fc31`。「需求描述」「关键不变量」I-1～I-3、「实现方案」1～5、「变更文件清单」「验收标准」逐条生效；本 brief 摘要与计划原文冲突时以计划原文为准。
- Worktree：`/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-04-discovery-review-master`；branch `fast/2026-10-04-discovery-review-master`；`child_base_sha = 07beaafc111a1b14ed3c48d514db527c8a13fc31`。
- 依赖：none。下游：02（审核 API 消费本片解释结果）、04（自动收录/重验调用本片 policy；`recordAutomatic` 由 02 提供）、05（发送准入解释）。本片不得接业务写入。
- 取证材料（worktree 内只读）：`docs/plans/2026-10-04/discovery-review-audit.md`（E1/E2/E5）、`docs/plans/2026-10-04/discovery-review-evidence/`（source-excerpts.md、es-read-paths.txt、es-write-paths.txt 等）。
- 基线命令结果（控制方已记录，实施前先读）：`docs/plans/fast/2026-10-04-discovery-review-master/baseline.md`。

## 全局约束

1. 只允许修改「Authorized Files」表内 5 个文件；不得新建白名单外文件（含 fixture、工具脚本、静态资源）。其余 Kotlin/SQL/迁移/前端/文档全部只读。
2. 不得修改 `docs/plans/**` 内的计划与其他证据；本 child 唯一可写非产品文件是你的执行报告 `docs/plans/fast/2026-10-04-discovery-review-master/children/01/execution.md`。fast-p 报告不进入产品提交（控制方单独提交）。
3. 不得 push、merge、rebase、squash、amend、reset；不得改写已有提交。产品代码只提交一次：`feat(fast-p): implement 01`。
4. 计划与代码冲突、需要白名单外文件、需要新行为或需要修订计划：返回 `PLAN_CONFLICT` / `BLOCKED`，不得自行扩范围或改计划。
5. 禁止联网抓取、连线上 MySQL/ES、发信、部署；不得新增依赖；不得改 `pom.xml`。
6. 测试库（若用到）必须是本机容器 `ti-mysql-it`（localhost:3306，root/root，库 `talent_introduction`，连接串需带 `allowPublicKeyRetrieval=true`）。禁止指向线上/日常数据库。
7. 不得修改已应用迁移；本片不应新增迁移。
8. 保持发现采集与补全可运行、既有发送行为不变、来源证据不伪造；I-1 明确不得新增阈值/标准，I-2 不得把人工认可升级为学术证明。

## Authorized Files（5）

| # | 精确路径 | 改动 |
|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/discovery/domain/DiscoveryAdmissionModels.kt` | 新增：`AdmissionReason`、`AutomaticAdmissionResult`（状态/阻断原因/提示/policyVersion/checkedAt/配置快照摘要；无 REVIEW_REQUIRED 冗余布尔） |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryAdmissionPolicy.kt` | 新增：输入完整 profile 与现有 EligibilityResult，调用现有签名函数；AUTO_PASSED iff 阻断原因空；LEGACY_APPROVED 单独返回 |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/expert/domain/DiscoveryIdentity.kt` | 新增 `explainIdentity/explainInstitutionEvidence` 返回原因；allowed/validInstitutionEvidence 仍以原函数为权威，不改来源识别/签发算法 |
| 4 | `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryAdmissionPolicyTest.kt` | 新增：结构化合成样本覆盖分支（不得冒用真实专家姓名） |
| 5 | `src/test/kotlin/com/weibo/talentintroduction/expert/domain/DiscoveryIdentityTest.kt` | 解释/布尔一致性测试 |

## 关键不变量（计划 I-1～I-3；逐字以计划为准）

- I-1 自动判定只搬现有规则：CandidateEligibilityService 当前启用规则 + DiscoveryIdentity 身份 + 机构非空 + 国家可映射 + 机构凭证；`filterResult` 是已有资格结果；实时重算候选资格产生新结论；研究方向缺失只作资料提示，不产生资格原因。不得引入新阈值。
- I-2 人工认可不升级为学术证明：有效 LEGACY_APPROVED 单独返回（及资料提示），不修改 `DiscoveryIdentity.allowed` 的 source-verified 语义；纯 policy 不签发人工批准。
- I-3 解释与布尔判定同源：未命中有效历史认可时，AUTO_PASSED iff 阻断原因数组为空，NEEDS_REVIEW iff 非空；每项返回 code/label/field/observed/expected/sourceLocation；未知用 null+UNKNOWN，禁止虚构；原因词表至少覆盖计划列出的 11 个码 + 现有 candidate rejectReasons 原码。

## 必需命令（fresh 运行，逐条记录 exit code 与计数；与 baseline.md 对照）

```sh
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -DskipTests test-compile
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=DiscoveryAdmissionPolicyTest,DiscoveryIdentityTest,LegacyDiscoveryApprovalTest test
```

## 交付物

- 产品提交：`feat(fast-p): implement 01`（只含授权产品/测试文件）。
- 执行报告：`docs/plans/fast/2026-10-04-discovery-review-master/children/01/execution.md`，包含 execute-p 规定的字段（Plan SHA-256、worktree ID、pre/post code SHA、Task Status、Commands、Changed Files、Deviations、Freshness、Next Action）。
- 返回：`READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT` + commit SHA + 命令摘要 + 报告路径。不得声明验证通过。
