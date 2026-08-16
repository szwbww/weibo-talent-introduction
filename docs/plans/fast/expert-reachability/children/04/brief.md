# Fast-P Child Brief — 04

- Child: 04
- Plan: docs/plans/2026-08-16/expert-reachability-04-list-badge.md
- Plan identity: commit:1c7cf0e4c11c53d1f4d20f28964fce837f70442b
- Depends on: 03
- Base: child 03 terminal Code head (set at dispatch time)
- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-reachability

## Global constraints (binding, from master plan docs/plans/2026-08-16/expert-reachability-00-execution-order.md)

1. JDK 11 mandatory. Use `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home` for every mvn command; bare mvn fails the build on newer JDKs.
2. Master plan shared invariants I-1..I-6 apply (child-specific I-4-1..I-4-3 in the child plan). Frontend MUST NOT re-derive reachability (I-4-1); missing field renders 可达 未知 neutral gray (I-4-2, I-3); BLOCKED sub-badges textually separate (I-4-3).
3. BLOCKED experts still shown in the list (N-1 / master plan N-1). No new default filter.
4. Full regression gate: `JAVA_HOME=... mvn test` must end `BUILD SUCCESS` with `Tests run: N, Failures: 0, Errors: 0` (baseline at master base: 2456/0/0/4).
5. Git: commit locally only, exactly one implementation commit with message `feat(fast-p): implement 04`. Never push, merge, rebase, amend, or rewrite history. Exclude fast-p report/log files (docs/plans/fast/) from the implementation commit; the controller commits evidence separately.
6. Do not review or implement later children (05/06). Do not repair unrelated behavior. Skip formatters/linters and project-wide suites beyond the required commands.

## Authorized files (4; modify nothing else)

1. src/main/kotlin/com/weibo/talentintroduction/expert/controller/ExpertIndexController.kt (T1)
2. src/main/resources/static/app.js (T2/T3/T4)
3. src/main/resources/static/styles.css (T5)
4. src/test/kotlin/com/weibo/talentintroduction/expert/controller/ExpertIndexControllerTest.kt (T1 test addition)

## Required commands (run all; from plan 验证命令 + master plan 验证命令)

- node --check src/main/resources/static/app.js
- JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=ExpertIndexControllerTest
- JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test   (full regression; must contain Tests run: N, Failures: 0, Errors: 0; exit 0)
- git diff --check   (clean)

## Downstream interfaces

- `ExpertIndexResponse.reachability: String?` (+ `from()` pass-through) consumed by frontend loadContacts ES path.
- `loadContacts` dual paths: MySQL path sets `reachability: null`, ES path sets `reachability: e.reachability ?? null`.
- Badge DOM/skeleton contract S-1 and BLOCKED checkbox disable S-2 exactly as plan; no edits to `.academic-*` / `.expert-row-sub` rule blocks.
- Next child 05 depends on 03, not 04; 05 is independent of this child.

## Plan text (exact approved content; authoritative)

Read the committed plan file: docs/plans/2026-08-16/expert-reachability-04-list-badge.md
Follow its 需求描述 / 关键不变量 I-4-1..I-4-3 / 现状审计 (loadContacts dual-path, styles tokens) / 样式契约 S-1 S-2 / 实现方案 T1-T5 / 变更文件清单 / 验证命令 / 验收标准 exactly. CSS block in S-1 must be copied verbatim (no property add/drop/value change).
