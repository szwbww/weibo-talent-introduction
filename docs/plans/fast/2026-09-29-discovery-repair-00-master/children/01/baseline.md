# Child 01 — Baseline Command Evidence

- Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-29-discovery-repair-00-master`
- Branch: `fast/2026-09-29-discovery-repair-00-master`
- Baseline revision: `b9ec45b008f5c4f965679b99575db0ea8fe50731` (plan-seed + ledger init; product code byte-identical to master base `1cd59e31164d11e962203e31c9f61310f2bc5912`)
- Runner: controller (fast-p setup), `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home`
- Run at: 2026-09-29 ~12:45–12:51 (+08:00). Raw log: `/tmp/fastp-baseline-a.log` (controller host, not committed)

## CMD — child-01 required classes (union run shared with child 02)

```
mvn -Dtest=SourceAuthorEmailResolverTest,ExpertDiscoveryServiceTest,DiscoveryPipelineServiceTest,DiscoveryIdentityTest,OpenAlexDataSourceTest,ExpertAcademicEnrichmentJobServiceTest test
```

exit 0, BUILD SUCCESS. Per-class results at baseline (`target/surefire-reports/*.txt`):

| Class | Tests | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|
| SourceAuthorEmailResolverTest | 19 | 0 | 0 | 0 |
| ExpertDiscoveryServiceTest | 172 | 0 | 0 | 0 |
| DiscoveryPipelineServiceTest | 46 | 0 | 0 | 0 |
| DiscoveryIdentityTest | 7 | 0 | 0 | 0 |
| OpenAlexDataSourceTest | 79 | 0 | 0 | 0 |
| ExpertAcademicEnrichmentJobServiceTest | 4 | 0 | 0 | 0 |
| **Total** | **327** | **0** | **0** | **0** |

## Notes

- Baseline is fully green; any post-implementation failure in these classes is a regression, not a pre-existing red.
- Child-01 required command also names `DiscoveryPipelineServiceTest` and `DiscoveryIdentityTest`; both were executed above.
- No `mvn clean package` was run here; the controller runs it once after the last child.
