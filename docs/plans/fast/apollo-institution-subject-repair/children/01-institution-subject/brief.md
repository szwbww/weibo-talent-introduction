# Child 01-institution-subject brief

- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-apollo-institution-subject-repair
- Branch: fast/apollo-institution-subject-repair
- Exact approved child plan: docs/plans/2026-10-19/apollo-institution-subject-repair.md
- Plan sha256: 49d5870d43c9877f947427c5b22277233fb5bf7f326176be33bc4629c5632397
- Master/entry plan: same file. Read the full 需求描述, 关键不变量 I-1..I-7, 实现方案 T-0..T-5, 变更文件清单, 验收标准 and 人工验收清单.
- Child base: b3b3a71a865337c15c556c21225c0eb9a2300b66 (docs-only seed; product tree equals fcd6719ecd03645284c3df0f8a6b95bf4686ba95).
- Authorized Files: exactly the 10 paths in the child plan's 变更文件清单 (5 main, 4 test, 1 test resource). No other product, test, build, config, migration, ES mapping, frontend, or plan file may change.
- Required commands, invariants, downstream interfaces, and acceptance criteria: the exact child plan, plus the T-0 pre-execution review. Do not weaken any invariant.
- T-0 gate: the predecessor fast-p run `apollo-assist-repair-master` is terminal (01/02 LIGHT_PASS_WITH_NOTES, zero repairs). Re-run the plan's greps and re-read Worker/Rules/Gate/QueryService/ReplayTool/Repository before implementation. The predecessor replay tool now exists at src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationReplayTool.kt; the plan's statement that it does not exist is stale. Record its actual signatures/format and the exact replay command in the execution report. Do not modify the replay tool or other unauthorized files.
- Downstream interface required by child 02: `DiscoveryInstitutionNormalizer` must expose the pure resolution/compare and INST_SUBJECT_V1 context DTO/builder/validation API described in T-1/T-2, usable later by the country supplement service; the optional `institutionNormalization` context must remain default-null and internally reconstructible by Rules/Gate/QueryService.
- No deployment, restart, live apply, backfill, or new UI. No push, merge, rebase, squash, amend, or history rewrite.
- Use execute-p. Modify only Authorized Files. Run every required command fresh after implementation. Independently prove changed behavior; SKIP is not PASS, and Docker/MySQL FlowIT is required.
- Commit the implementation locally as: feat(fast-p): implement 01-institution-subject
- Exclude fast-p reports/logs from the implementation commit; the controller commits evidence separately.
- Write the full execution report (including T-0 receipts, commands with exit codes/counts, and smoke/audit evidence) to docs/plans/fast/apollo-institution-subject-repair/children/01-institution-subject/execution.md.
- Return only: READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT, commit SHA, command summary, report path.
