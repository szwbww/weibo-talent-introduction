# Child 01 fix log

Plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction-mail-open-recovery/docs/plans/2026-09-25/mail-open-tracking-01-storage-api.md`
Plan SHA-256: `4f71accf9a93ab99f0716416d9b4b98c858471616b7bc41791a7301c92018b2b`
Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-mail-open-recovery`
Branch: `fast/mail-open-tracking-00-master`

## Epoch 1 — Round 1/3
- Findings: F-01, F-02
- Before: c39198962a728e620bf0648e71021c49601778d2
- Fix commit: 76de1ab3a3257a2b2e292f6c894296d7eb8c80dd
- Status: APPLIED; independent verification pending; Docker-backed test bodies NOT_RUN.
- Implementation commit: `c39198962a728e620bf0648e71021c49601778d2` (source `fa7f9ef2de2b6432450e539297ce27f7fd0a2b10`).
- **This epoch-1 round-1 fix is bound to NEW fix commit `76de1ab3a3257a2b2e292f6c894296d7eb8c80dd`** (source `a23c3dc14c2755139bede55dfed6ac362babfd47`), not to the source SHA or an old worktree's evidence.
- Scope: `MailOpenTrackingRepository.kt` and `MailOpenTrackingRepositoryIT.kt` only. Signal SQL compares the enabled setting exactly against `true` despite a case-insensitive MySQL collation; integration coverage verifies uppercase `TRUE` does not record, and an independent connection sees the committed reservation while an outer contact transaction is uncommitted.
- Fresh focused command: `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=MailOpenTrackingServiceTest,MailOpenTrackingControllerTest -DskipNodeTests=true` — exit 0; 7 run, 0 failures/errors/skips (`artifact://382`).
- Fresh MySQL command: `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -DmysqlIt=true -DmigrationIt=true -Dtest=MailOpenTrackingRepositoryIT,FlywayMigrationIntegrationTest -DskipNodeTests=true` — exit 1; Testcontainers failed in both classes' `@BeforeAll` because Docker is unavailable; **test bodies NOT_RUN**, no behavioral PASS claimed (`artifact://385`).
- No further fix rounds were applied. Product commits remain separate from these uncommitted fast-p artifacts; controller commits evidence only after independent verification.
