# Child 01 — Controller Baseline Command Evidence

- Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-28-discovered-institution-repair-00-master`
- Branch: `fast/2026-09-28-discovered-institution-repair-00-master`
- Baseline revision: `2e9639df7947bc5f3057ca08e1445b155aba7cd7`（seed 提交；产品代码与 master base `d90084841d400e75eb0f2b6c4c6726e54307260a` 相同，只多 `docs/plans/**`）
- Runner: controller（fast-p setup），JDK `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home`
- Run at: 2026-09-28 14:55–14:59 (+08:00)

## CMD — `mvn clean package`

exit 0，`BUILD SUCCESS`，Total time 04:44 min。

- surefire 聚合（`target/surefire-reports/*.txt`，269 个类）：**4220 tests, 0 failures, 0 errors, 13 skipped**。
- test 阶段绑定 Node 套件：**1202 tests / 1202 pass / 0 fail**。

各 child 定向类在基线（供每 child 对比，均 0 failures / 0 errors）：

| Class | Tests | Failures | Errors | Skipped |
|---|---|---:|---:|---:|
| JatsXmlEmailParserTest | 35 | 0 | 0 | 0 |
| OpenAlexDataSourceTest | 73 | 0 | 0 | 0 |
| SourceAuthorEmailResolverTest | 18 | 0 | 0 | 0 |
| OrcidDataSourceTest | 10 | 0 | 0 | 0 |
| ExpertDiscoveryServiceTest | 160 | 0 | 0 | 0 |
| ExpertSearchServiceTest | 70 | 0 | 0 | 0 |
| OperatorStatusWriteSeamGuardTest | 1 | 0 | 0 | 0 |
| ManualInitialOutreachServiceTest | 182 | 0 | 0 | 0 |
| InitialOutreachServiceTest | 18 | 0 | 0 | 0 |
| CandidateEligibilityServiceTest | 7 | 0 | 0 | 0 |

## Notes

- 基线全绿；任何实现后的失败都是回归，不是预置红。
- 本计划不改数据库迁移、不改前端资源；`-DmigrationIt` / `-DmysqlIt` Docker IT 不属于本计划必需命令（默认跳过）。
