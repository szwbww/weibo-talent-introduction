# Child 02 brief

- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-apollo-assist-repair-master
- Branch: fast/apollo-assist-repair-master
- Exact approved child plan: docs/plans/2026-10-19/apollo-assist-repair-02-replay-evaluation.md
- Plan sha256: d46ebb59a1bed7301ac38743929b009e0808069d83bad2edf0d28b25f9c83099
- Global constraints: docs/plans/2026-10-19/apollo-assist-repair-master.md (read its full invariant/authority sections).
- Authorized Files: exactly the six or nine files in this child's 变更文件清单; no other product/test changes.
- Required commands, invariants, downstream interfaces, T-1 through T-4 and acceptance criteria: the exact child plan is included by reference, read it completely.
- Prior output: Child 01 terminal evidence at ../01/execution.md and ../01/verify-log.md; consume its Parser.rebuild and identityEmployments shared helpers, plus Worker DECIDE behavior. Do not modify child 01 files outside child 02 authorization.
- Use execute-p, fresh plan and worktree identity gates. Only implement this child. Do not use full verify-p/review-p/repair-p in fast-p; independent four-gate light verification follows controller dispatch.
- Commit authority: feat(fast-p): implement 02, only authorized implementation files. No pushes, merges, history rewrites, production deployment, live apply, or restarts.
- Write full execution report to docs/plans/fast/apollo-assist-repair-master/children/02/execution.md, excluded from product commit.
- Keep logs append-only. Product required commands run after implementation, not mid-flight; carry baseline reproduction evidence from controller baseline. If a required command is unexecutable or unauthorized scope is needed, return BLOCKED/PLAN_CONFLICT rather than weaken requirements.
- Provide actual isolated runtime smoke proof of changed behavior, not just source-text or mock wiring assertions. Existing real Worker/MySQL flow is the required executable surface. No new unapproved documentation files; execution report supplies implementation changelog/evidence within authorized artifact scope.
- Baseline evidence: ../../baseline.md. Do not rerun a reported baseline failure just to confirm it.
