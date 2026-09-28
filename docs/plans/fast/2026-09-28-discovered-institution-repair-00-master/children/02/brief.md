# Fast-P Child Brief — 02（机构来源证据落库）

## 身份与边界

- Master plan（批准版，字节冻结，只读）：`docs/plans/2026-09-28/discovered-institution-repair-00-master.md`，identity `commit:2e9639df7947bc5f3057ca08e1445b155aba7cd7`。
- 本 child 批准计划（**完整合同，必须先通读**）：`docs/plans/2026-09-28/discovered-institution-repair-02-evidence.md`，identity `commit:2e9639df7947bc5f3057ca08e1445b155aba7cd7`。
- Worktree：`/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-28-discovered-institution-repair-00-master`；Branch：`fast/2026-09-28-discovered-institution-repair-00-master`。
- `child_base_sha`：`68ad011971ac4cbafdd439cfe2d981476ff1332a`（child 01b 的 Code head）。
- 依赖：01b。上游产物（不得回退）：01 的内部来源字段与唯一机构判定、01b 的 ORCID 唯一机构与 `employment=null`。
- 下游：03 依赖本 child 的 `institutionEvidence` 格式与统一验签函数。

## 全局约束

1. JDK 11 固定：所有 Maven 命令必须 `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home`。
2. 只允许修改下面「Authorized Files」表内 10 个文件；不得新建白名单外文件；不得修改 `docs/plans/**`；**不得执行线上 `PUT _mapping` 或任何存量数据回填**（只改仓库配置，部署验收由人工执行）。
3. 不得修改主工作区；不得 push、merge、rebase、squash、amend、reset。
4. 产品代码提交格式：`feat(fast-p): implement 02`，单个提交；把 `docs/plans/fast/**` 报告/日志排除在该提交之外。
5. 计划与代码冲突、需要白名单外文件、需要新行为、需要修订计划：返回 `PLAN_CONFLICT` / `BLOCKED`。
6. 只读参考（不得复制、不得提交）：主工作区下 `docs/audits/2026-09-28-discovered-institution-comprehensive/README.md`。

## 必须保持不变（计划「需求描述」「关键不变量」「验收标准」）

- 不得改变已有主键、身份校验结构、旧非发现档案、历史邮件；本步不启用发信拦截（03 才做），不回填历史。
- I-1 单个证据字段：三层 ES 新增且仅新增 `institutionEvidence` keyword；值为 `JATS:<64位小写SHA256>`、`OPENALEX:<...>` 或 `ORCID:<...>`。签发只依据 01 的内部 `institutionSource`（ORCID 用 01b 唯一机构），不根据 `dataSource` 或旧 `institution` 补签。统一函数以 NUL 分隔固定顺序计算 `来源种类、已存 identityVerification.source/evidenceHash、规范邮箱、姓名、来源作者 ID、已存 externalIds 中论文 ID 或 ORCID ID、机构、国家、机构类型` 的 SHA256；验签只读 ES 已存字段并重算。机构为空或任一必需来源/身份 ID 缺失时不写此键，不写 `false`/`UNVERIFIED`。
- I-2 原子同步：仅在 `DiscoveryIdentity.allowed`、`identityVerification` 中非空的 ORCID/OpenAlex 作者 ID 与 `externalIds` 对应 ID 无冲突、真实 ORCID 主键（非 `EMAIL-*`）与其 ORCID 值无冲突、且 01/01b 的同一作者单机构有结构证据时签发；JATS 必须有 `externalIds.pmcId`；OPENALEX 必须有同作者 `externalIds.openAlexAuthorId` 及 `doi`/`pmcId` 至少一项；ORCID 必须有 `externalIds.orcid` 与主键一致。机构/国家/类型任一变更必须重算或清除证明。`buildProfile` 在原始 `AuthorEmail` 尚在场时签发，`toIndexMap` 写入；不得扫描历史文档按字段外观补签。
- I-3 读取全路径：`ExpertProfile` 加可空 `institutionEvidence`、`filterResult`；`ExpertSearchService` 的 `_source` 投影与转换显式读取；旧文档无值为 null，不默认合格。`ExpertIndexWriterService.discoveryProfile` 仍从原 `_source` 读取机构值，不参与发送门禁。
- 晋升 `_source` 全量透传沿用现状，不另写第二套复制逻辑；`updateExpertAcademicFields` 不得触碰机构/证据。
- `OperatorStatusWriteSeamGuardTest` 因 `ExpertSearchService.kt` 行号偏移而红时，只允许按语义更新排除清单中的行号，不得放宽断言。
- 计划要求 TDD：先写失败断言（记录改前红证据），再实现到通过；不得改旧期望变绿。

## Authorized Files（11，含 A1 追加的第 11 行）

| # | 文件 | 变更 |
|---|---|---|
| 1 | `src/main/resources/es/orcid_info_raw.json` | mapping：`institutionEvidence` keyword |
| 2 | `src/main/resources/es/orcid_info_candidate.json` | mapping 同上 |
| 3 | `src/main/resources/es/orcid_info_application.json` | mapping 同上 |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/expert/domain/ExpertProfile.kt` | 可空 `institutionEvidence`/`filterResult` |
| 5 | `src/main/kotlin/com/weibo/talentintroduction/expert/domain/DiscoveryIdentity.kt` | token 唯一生成/验证函数 |
| 6 | `src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt` | 证据签发/写入 |
| 7 | `src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchService.kt` | `_source` 读取投影 |
| 8 | `src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryServiceTest.kt` | 签发/拒签 |
| 9 | `src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchServiceTest.kt` | 缺失/存在读取 |
| 10 | `src/test/kotlin/com/weibo/talentintroduction/campaign/OperatorStatusWriteSeamGuardTest.kt` | 若搜索文件移行，仅改排除清单行号 |
| 11 | `src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertIndexServiceTest.kt` | A1 修正（human 2026-09-28T17:19+08:00 批准，计划 identity 随之变为 `commit:a90f59d8d57dea33d83b571bbb62c5f389d387d7`）：仅更新 RAW 顶层 mapping 属性计数期望 36→37 与紧邻注释，断言与语义不变 |

## 已知基线事实

- 基线命令与逐类计数见 `children/01/baseline.md`（控制方在 seed 提交上 fresh 运行）。测试若在基线上已红，按基线对比归类。
- 线上反例（计划现状审计）：论文来源档案有 1,256 条非 EMAIL 主键与 `externalIds.orcid` 冲突；`DiscoveryIdentity.allowed` 当前只核姓名/邮箱/evidenceHash，未核该 ID 一致性——签发前必须核。

## 必需命令（fresh 运行，逐条记录 exit code 与计数）

```sh
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=ExpertDiscoveryServiceTest,ExpertSearchServiceTest,OperatorStatusWriteSeamGuardTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test
git diff --check
```

## 交付

- 执行报告写入 `children/02/execution.md`（报告本身不进实现提交）：逐子任务的文件:行证据、改前红证据、命令与 exit code/计数、与基线对比、偏差。
- 只返回：`READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT`、commit SHA、命令摘要、报告路径。
- 不得修复白名单外问题、不得重构相邻代码。
