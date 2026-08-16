# Fast-P Child Brief — 02

- Child: 02
- Plan: docs/plans/2026-08-16/expert-reachability-02-classifier-and-mapping.md
- Plan identity: commit:1c7cf0e4c11c53d1f4d20f28964fce837f70442b
- Depends on: none
- Base: edda3e4e67e8b4511f3c7ca76b09926c56e4f69a
- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-reachability

## Global constraints (binding, from master plan docs/plans/2026-08-16/expert-reachability-00-execution-order.md)

1. JDK 11 mandatory. Use `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home` for every mvn command; bare mvn fails the build on newer JDKs.
2. Master plan shared invariants I-1..I-6 apply (see master plan; child-specific invariants I-2-1..I-2-6 are in the child plan). Child 02 is the first executable plan; the voided plan 01 file stays untouched.
3. This child adds exactly ONE new ES field `reachability` (I-6). Do NOT touch `orcid_info_raw.json` (N-1), `checkOperatorStatusMapping()`, or `syncOperatorStatusBatch()` (N-2).
4. Full regression gate: `JAVA_HOME=... mvn test` must end `BUILD SUCCESS` with `Tests run: N, Failures: 0, Errors: 0` (baseline at master base: green).
5. Git: commit locally only, exactly one implementation commit with message `feat(fast-p): implement 02`. Never push, merge, rebase, amend, or rewrite history. Exclude fast-p report/log files (docs/plans/fast/) from the implementation commit; the controller commits evidence separately.
6. Do not review or implement later children (03/04/05/06). Do not repair unrelated behavior. Skip formatters/linters and project-wide suites beyond the required commands.

## Authorized files (8; modify nothing else)

1. src/main/resources/es/orcid_info_candidate.json (T2)
2. src/main/resources/es/orcid_info_application.json (T2)
3. src/main/kotlin/com/weibo/talentintroduction/expert/domain/ExpertReachability.kt (NEW, T1)
4. src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertReachabilityClassifier.kt (NEW, T1)
5. src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertIndexService.kt (T3)
6. src/main/kotlin/com/weibo/talentintroduction/expert/domain/ExpertProfile.kt (T4)
7. src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchService.kt (T4)
8. src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertReachabilityClassifierTest.kt (NEW, T5)

## Required commands (run all; from plan 验证命令 + master plan 验证命令)

- JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=ExpertReachabilityClassifierTest
- JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=ExpertIndexServiceTest
- JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=ExpertSearchServiceTest
- JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test   (full regression; must contain Tests run: N, Failures: 0, Errors: 0; exit 0)
- git diff --check   (clean)

## Downstream interfaces (contracts child 03/04/05 consume)

- `ExpertReachabilityClassifier.classify(profile: ExpertProfile, suppressedEmails: Set<String>, hardBouncedOrcids: Set<String>): ExpertReachability?` — pure function; null = UNKNOWN; enum has exactly 4 members (BLOCKED_UNSUBSCRIBED / BLOCKED_BOUNCED / HIGH / LOW) with `esValue` property; single constructor dependency `ProviderResolver`.
- `ExpertIndexService.checkReachabilityMapping(): Boolean` — iterates ONLY CANDIDATE + APPLICATION.
- `ExpertProfile.reachability: String?`, `sourceFields()` whitelist + `mapToProfile` pass-through; `ExpertIndexResponse` NOT touched by this child (plan 04 does that).
- ES JSON declares `"reachability": { "type": "keyword" }` in candidate + application only.

## Plan text (exact approved content; authoritative)

Read the committed plan file: docs/plans/2026-08-16/expert-reachability-02-classifier-and-mapping.md
Follow its 需求描述 / 关键不变量 I-2-1..I-2-6 / 四档口径表 / 现状审计 / 实现方案 T1-T5 / 变更文件清单 / 验证命令 / 验收标准 exactly. Do not re-derive the classification rules; the 四档口径 table in the plan is normative (incl. A-1..A-4 corrections: no lastPublicationYear, no emailVerifiedLevel, consumer-provider negative test, UNKNOWN = emailSource missing).
