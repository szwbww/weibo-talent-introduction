# Child 01 approved brief

- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction/.worktrees/wecom-inbound-notification
- Branch: fast/wecom-inbound-notification
- Exact approved plan: docs/plans/2026-10-06/wecom-inbound-notification-01-backend.md
- Plan identity: commit:4826cbe111310284cf13bfe7fa395bd9e2e122ad
- Approval: HUMAN:批准该总方案及两个子方案，按 fast-p 执行 (2026-10-08).

Read the entire exact child plan and master docs/plans/2026-10-06/wecom-inbound-notification.md. Authorized Files are exactly the exhaustive child list; required commands, invariants and downstream interfaces are exactly its contract. Do not amend plans or expand file scope. All work must target this worktree, not ambient parent checkout. Follow supplied repository instructions; docs/design.md is authoritative for mail logic. Use JDK 11. Docker is available after orb start; no real Webhook credentials or group sends. The migration V150 reservation is free at baseline.

Use execute-p with plan/worktree identity gates. Fast-p overrides its normal verification handoff: controller dispatches a separate four-gate light verifier; do not invoke verify-p/review-p/repair-p/fix-v inside this child loop. Run required commands only after final changes; no mid-flight builds/lints/tests/formatters. Exercise actual changed path with an isolated smoke scenario, no real group sends. No scope expansion for docs/changelog: execution report documents behavior within evidence scope. Commit only authorized product/test files as feat(fast-p): implement 01; reports/logs are excluded. Write execution.md in this directory. Return READY_FOR_VERIFICATION, BLOCKED or PLAN_CONFLICT with SHA and evidence.

Downstream: exact settings endpoint and JSON response contract required by child 02; preserve all existing mail semantics.
