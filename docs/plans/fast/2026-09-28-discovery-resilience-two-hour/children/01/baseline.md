# Child 01 — Controller Baseline Command Evidence

- Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-28-discovery-resilience-two-hour`
- Branch: `fast/2026-09-28-discovery-resilience-two-hour`
- Baseline revision: `3f167a2820c3f9bdb344f19da2123d6a9ff2f12a` (seed commit; product code identical to master base `f98e27c7538d091bfcdecfcb6ffc10360a35ba04`)
- Runner: controller (fast-p setup), JDK `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home`
- Run at: 2026-09-28 10:05–10:12 (+08:00); script `/tmp/fastp-baseline-01.sh`

## CMD1 — `mvn test -Dtest=<11 classes>`

exit 0. Per-class results at baseline (surefire `target/surefire-reports/*.txt`):

| Class | Tests | Failures | Errors | Skipped |
|---|---|---:|---:|---:|
| ExpertDiscoveryServiceTest | 150 | 0 | 0 | 0 |
| ExpertDiscoverySchedulerTest | 13 | 0 | 0 | 0 |
| ExpertDiscoveryPropertiesTest | absent at baseline (new file authorized by the plan) | — | — | — |
| DiscoveryCheckpointCodecTest | 11 | 0 | 0 | 0 |
| OpenAlexRequestPolicyTest | 44 | 0 | 0 | 0 |
| DiscoveryPromotionProgressServiceTest | 5 | 0 | 0 | 0 |
| TaskProgressStoreTest | 26 | 0 | 0 | 0 |
| TaskProgressStoreRebindTest | 3 | 0 | 0 | 0 |
| TaskProgressControllerTest | 13 | 0 | 0 | 0 |
| TaskProgressControllerExecutionsTest | 26 | 0 | 0 | 0 |
| DiscoveryPipelineServiceTest | 46 | 0 | 0 | 0 |

Targeted baseline total (10 existing classes): **337 tests, 0 failures, 0 errors**.

## CMD2 — `mvn clean package`

exit 0, BUILD SUCCESS (04:33 min). Full surefire aggregate at that revision: **4200 tests, 0 failures, 0 errors** (some `Skipped: 1` cases in mysql/migration-gated classes). Test-phase-bound Node suite: **1199 tests / 1199 pass / 0 fail**.

## CMD3 — `git diff --check`

exit 0 (clean product tree at the seed commit).

## Notes

- Baseline is fully green; any post-implementation failure in the 11 targeted classes or the full build is a regression, not a pre-existing red.
- No `-DmigrationIt` / `-DmysqlIt` Docker IT is part of this plan's required commands.
