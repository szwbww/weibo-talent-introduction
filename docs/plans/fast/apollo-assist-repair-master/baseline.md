# Preflight baseline

- Source worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction; main, HEAD e33b3cdcd228a6692c7aaeab047e5bafd0e7bd3f.
- All original modified and untracked files remain in the source worktree. Only the exact three approved plans and their apollo-repair-evidence attachments were copied into this isolated worktree; no user changes were staged there.
- Retained branch: fast/apollo-assist-repair-master.
- Docker server: 29.8.2, docker info exit 0.
- JDK11: Zulu 11.0.15. Maven: 3.9.11; every Maven command explicitly supplies JAVA_HOME because ambient Java is 25.
- Installed fast-p validator --help exits 0 and supports --through-child.
- Production deployment, live apply, external new runs, and final whole-system verification are not authorized by this invocation. Human acceptance steps remain deferred; local plan-required commands and isolated runtime proof are required.

## Baseline commands — seeded HEAD 9486d1eac8b1d637de4e444d40e64fc372b1a42b

- `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=QualificationEvidenceParserTest,DiscoveryVerificationRulesTest,DiscoveryVerificationWorkerTest,DiscoveryVerificationQueryServiceTest,DiscoveryVerificationAdmissionGateTest,DiscoverySampleStoreTest,DiscoverySampleCaptureTest,DiscoverySampleReplayTest test`: exit 0, 43 tests, 0 failures/errors/skips; BUILD SUCCESS.
- Bare JDK11 `mvn -Dtest=DiscoveryVerificationFlowIT,DiscoveryVerificationRepositoryIT,DiscoveryVerificationReportingIT test`: exit 1, 3 container-start errors, 0 skipped. Docker-java API 1.32 rejected by daemon minimum 1.40; missing /var/run/docker.sock. This is infrastructure, not a product failure.
- Complete baseline output: runtime artifact `artifact://191`; source fixture/code remained unchanged.
- Docker context inspect confirms OrbStack socket `unix:///Users/lukai/.orbstack/run/docker.sock`, daemon API 1.56 / minimum 1.40.
- Existing repository precedent: `docs/plans/fast/2026-09-22-openalex-daily-budget-design/baseline-java.txt:28-32` prescribes `DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock` and `-Dapi.version=1.40`. Use this runtime-only transport configuration for all fresh MySQL commands; no repository/dependency/config modification and no skips.
- Corrected integration baseline started with that transport configuration; result appended below on completion.
- Corrected integration command: `DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dapi.version=1.40 -Dtest=DiscoveryVerificationFlowIT,DiscoveryVerificationRepositoryIT,DiscoveryVerificationReportingIT test`: exit 0, 30 tests, 0 failures/errors/skips, BUILD SUCCESS, actual isolated Docker MySQL. Output `artifact://194`. Preflight product baseline passes with documented runtime transport settings.
