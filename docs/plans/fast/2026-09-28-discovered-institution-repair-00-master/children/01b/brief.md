# Fast-P Child Brief — 01b（ORCID 机构唯一性与任职语义）

## 身份与边界

- Master plan（批准版，字节冻结，只读）：`docs/plans/2026-09-28/discovered-institution-repair-00-master.md`，identity `commit:2e9639df7947bc5f3057ca08e1445b155aba7cd7`。
- 本 child 批准计划（**完整合同，必须先通读**）：`docs/plans/2026-09-28/discovered-institution-repair-01-orcid.md`，identity `commit:2e9639df7947bc5f3057ca08e1445b155aba7cd7`。
- Worktree：`/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-28-discovered-institution-repair-00-master`；Branch：`fast/2026-09-28-discovered-institution-repair-00-master`。
- `child_base_sha`：`b12c971be46a992b275a3e4fb3768047b0af8877`（child 01 的 Code head，含 01 的产物）。
- 依赖：01。上游产物（不得回退）：内部 `institutionSource` 语义与唯一机构判定、`employment=null`、类型不跨源覆盖、`AuthorEmail`/`PaperAuthor` 新字段。
- 下游：02 依赖本 child 的「ORCID 唯一机构」语义（`institutionEvidence` 的 `ORCID:` 签发只能基于唯一机构）。

## 全局约束

1. JDK 11 固定：所有 Maven 命令必须 `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home`。
2. 只允许修改下面「Authorized Files」表内 4 个文件；不得新建白名单外文件；不得修改 `docs/plans/**`。
3. 不得修改主工作区；不得 push、merge、rebase、squash、amend、reset。
4. 产品代码提交格式：`feat(fast-p): implement 01b`，单个提交；把 `docs/plans/fast/**` 报告/日志排除在该提交之外。
5. 计划与代码冲突、需要白名单外文件、需要新行为、需要修订计划：返回 `PLAN_CONFLICT` / `BLOCKED`。
6. 只读参考（不得复制、不得提交）：主工作区下 `docs/audits/2026-09-28-discovered-institution-comprehensive/README.md`。

## 必须保持不变（计划「需求描述」「关键不变量」「验收标准」）

- 不得改变 ORCID 邮箱/姓名/主键绑定与 `identityVerification` 生成路径；不得改变论文、SBIR 路径行为。
- 范围外：历史回填、当前职位判定、ORCID 国籍推断。
- I-1：`OrcidDataSource.parseOrcidRecords` 对 `expanded-result.institution-name` 先 trim、去空、去重；**恰一项**时取该项，零项或多项写 null。禁止 `firstOrNull()` 代表主机构。
- I-2：ORCID `institution-name` 只表达来源关联，不证明当前雇主/职位；`ExpertDiscoveryService.buildOrcidProfile` 把 `employment=null`、`country=null`；多机构时机构为空，不复制原文。
- 计划要求 TDD：先写失败断言（记录改前红证据），再实现到通过；不得改旧期望变绿。

## Authorized Files（4）

| # | 文件 | 变更 |
|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/discovery/service/OrcidDataSource.kt` | 唯一机构提取（trim/去空/去重/恰一项） |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt` | ORCID 建档 `employment=null`、`country=null` |
| 3 | `src/test/kotlin/com/weibo/talentintroduction/discovery/service/OrcidDataSourceTest.kt` | 单/多/空/重复同名机构 |
| 4 | `src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryServiceTest.kt` | RAW/候选字段同值与身份回归 |

## 已知基线事实

- 基线命令与逐类计数见 `children/01/baseline.md`（控制方在 seed 提交上 fresh 运行）。测试若在基线上已红，按基线对比归类。

## 必需命令（fresh 运行，逐条记录 exit code 与计数）

```sh
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=OrcidDataSourceTest,ExpertDiscoveryServiceTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test
git diff --check
```

## 交付

- 执行报告写入 `children/01b/execution.md`（报告本身不进实现提交）：逐子任务的文件:行证据、改前红证据、命令与 exit code/计数、与基线对比、偏差。
- 只返回：`READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT`、commit SHA、命令摘要、报告路径。
- 不得修复白名单外问题、不得重构相邻代码。
