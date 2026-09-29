# Child 03 — Baseline Command Evidence

- Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-29-discovery-repair-00-master`
- Branch: `fast/2026-09-29-discovery-repair-00-master`
- Baseline revision: `b9ec45b008f5c4f965679b99575db0ea8fe50731` (plan-seed + ledger init; product code byte-identical to master base `1cd59e31164d11e962203e31c9f61310f2bc5912`)
- Runner: controller (fast-p setup), `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home`
- Run at: 2026-09-29 ~12:52–12:55 (+08:00). Raw log: `/tmp/fastp-baseline-b.log` (controller host, not committed)

## CMD — child-03 required command at master-base product code

```
mvn -DmigrationIt=true -Dtest=ExpertDiscoverySchedulerTest,DiscoveryScheduleSettingServiceTest,DiscoveryScheduleControllerTest,DiscoveryScheduleSettingRepositoryIT test
```

exit 0, BUILD SUCCESS.

| Class | Tests | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|
| ExpertDiscoverySchedulerTest | 15 | 0 | 0 | 0 |
| DiscoveryScheduleSettingServiceTest | absent at baseline (new file authorized by the plan) | — | — | — |
| DiscoveryScheduleControllerTest | absent at baseline (new file authorized by the plan) | — | — | — |
| DiscoveryScheduleSettingRepositoryIT | absent at baseline (new file authorized by the plan) | — | — | — |

## Environment for the IT

- Container runtime: OrbStack Docker daemon reachable during the run (`docker info` server `29.4.0`); testcontainers image `mysql:8.0.36` pre-pulled.
- The new IT must be gated `@EnabledIfSystemProperty(named = "migrationIt", matches = "true")` so this exact command executes it; a skipped IT is not a pass.
- `migrationIt` is also forwarded by the surefire `<systemPropertyVariables>` block in `pom.xml` (same as existing `ExpertAcademicEnrichmentJobRepositoryIT`).

## Notes

- `V143__create_expert_replied_dismissal.sql` is the latest applied migration at this revision, so `V144` is free.
- No `mvn clean package` was run here; the controller runs it once after the last child.
