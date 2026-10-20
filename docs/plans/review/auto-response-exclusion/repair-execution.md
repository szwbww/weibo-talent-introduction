# Repair Execution — auto-response-exclusion

- Executed at: `2026-10-21T01:05:29+08:00`
- Executor: omp coding assistant (`opencode-go/deepseek-v4.1-flash:max`)
- Approval source: explicit human invocation `$execute-p docs/plans/fix/auto-response-exclusion/repair.md`
- Repair plan: `docs/plans/fix/auto-response-exclusion/repair.md`
- Plan SHA-256: `f1d5fc81a3d3bf239fb45f2d02f894b8d06135e9f44d3194d107f7071b659bec`
- Execution ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-auto-response-exclusion/docs/plans/fix/auto-response-exclusion/repair.md@f1d5fc81a3d3bf239fb45f2d02f894b8d06135e9f44d3194d107f7071b659bec`
- Execution epoch: `NEW`
- Target worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-auto-response-exclusion`
- Target branch: `fast/auto-response-exclusion`
- Worktree ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-auto-response-exclusion@fast/auto-response-exclusion@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-auto-response-exclusion`
- Pre-execution code SHA: `596981b3c067217b0dad7f8d899431653e67145d`
- Post-execution code SHA: `de77c293063d0b502a8ac9c99ceeb7d4340101f2`
- Evidence HEAD: (this docs-only commit)

## Scope

Finding V-1 only. Protected fallback reasons `BODY_TRUNCATED`,
`LEGACY_UID_UNVERIFIABLE`, `RECIPIENT_UNRESOLVED`, `CONTACT_NOT_FOUND`, and
`INTRODUCTION_NOT_SENT` are excluded before first-reply proof during `scan`
and cannot be updated by `apply`, even when a reviewed manifest snapshot
matches the current row.

## Changed Files

- `scripts/reclassify-auto-responses.py` — added the protected-reason contract,
  a scan pre-proof exclusion (`protected-reason` skip), and an apply guard
  (`protected-reason` skip, no write).
- `scripts/test_reclassify_auto_responses.py` — standard-library fake-cursor
  regression tests covering all five protected reasons in scan and apply plus
  an ordinary-row apply control.

## Commands

| Command | Result |
|---|---|
| `python3 -m unittest scripts/test_reclassify_auto_responses.py` | PASS — 3 tests, 0 failures |
| `python3 -m py_compile scripts/reclassify-auto-responses.py` | PASS |
| `env JAVA_HOME=…/zulu-11.0.32.jdk/Contents/Home PATH=…/bin:$PATH mvn test` | PASS — 5306 tests, 0 failures, 0 errors, 21 skipped |
| `env JAVA_HOME=…/zulu-11.0.32.jdk/Contents/Home PATH=…/bin:$PATH mvn test -Pmysql-it -Dtest=MailboxConversationRepositoryIT,MailboxSuspensionServiceIT` | BASELINE_ONLY — 46 tests, 0 failures, 1 known error: `MailboxConversationRepositoryIT.replied filter excludes followed and dismissed experts until a new inbound arrives` (`dismissed_at` has no default value) |
| `node --check src/main/resources/static/mailbox-chat.js` | PASS |
| `node --check src/main/resources/static/app.js` | PASS |
| `node --test src/test/js/*.test.js` | PASS — 1733 pass, 0 fail |

## Deviations

- The first full `mvn test` attempt in the target worktree failed only in
  `RestTemplateConfigTest.a trickling response body is cut off at the absolute
  deadline, not per read {R-1, V-4}` (`SocketException: Connection reset`).
  The class rerun passed (18/18), and the subsequent full-suite rerun passed
  (5306/0/0/21), so the failure is recorded as flaky infrastructure, not a
  product regression.
- An initial `mvn test` invocation ran against the main worktree instead of the
  target worktree. It is excluded from evidence; the suite was rerun with the
  target worktree as the working directory.
- No plan-required command failed for an in-scope reason. The MySQL IT result
  matches the recorded baseline exactly (no new failure).

## Clean State

- Plan SHA-256 rechecked after execution: unchanged.
- Worktree root/branch/git-dir rechecked before staging and after commit.
- Product commit `de77c29` is `HEAD` of `fast/auto-response-exclusion`.
- Changed product/test files are exactly the two authorized files.
